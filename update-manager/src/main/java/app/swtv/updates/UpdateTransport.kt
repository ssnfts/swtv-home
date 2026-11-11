package app.swtv.updates

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.CookieHandler
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

enum class UpdateFailure(val message: String) {
    INVALID_MANIFEST("The release manifest is invalid."),
    INVALID_ARTIFACT("The downloaded APK did not pass verification."),
    INCOMPATIBLE("The update requires a newer Android version."),
    CURRENT_OR_STALE("This release is already installed or older than the installed version."),
    STORAGE("The update could not be saved or read from private storage."),
    NETWORK("The release download could not be completed."),
    CANCELLED("The update was cancelled."),
    SIGNER_MISMATCH("The installed app or update has an unexpected signing certificate."),
    RESPONSE_SIZE("The server returned an unexpected response size."),
}

class UpdateException(val failure: UpdateFailure) : Exception(failure.message)

class UpdateCancellation {
    private val cancelled = AtomicBoolean()

    fun cancel() {
        cancelled.set(true)
    }

    fun check() {
        if (cancelled.get()) throw UpdateException(UpdateFailure.CANCELLED)
    }
}

interface UpdateTransport {
    /** Read the body to EOF in the callback. The stream is closed before this method returns. */
    fun <T> withBody(
        uri: URI,
        maxBytes: Long,
        cancel: UpdateCancellation,
        redirectAllowed: (URI, URI) -> Boolean,
        read: (InputStream) -> T,
    ): T
}

class HttpsUpdateTransport internal constructor(
    private val open: (URI) -> HttpsURLConnection,
    private val nanoTime: () -> Long = System::nanoTime,
) : UpdateTransport {
    constructor() : this(::openPublicHttps)

    override fun <T> withBody(
        uri: URI,
        maxBytes: Long,
        cancel: UpdateCancellation,
        redirectAllowed: (URI, URI) -> Boolean,
        read: (InputStream) -> T,
    ): T {
        require(maxBytes in 1L..MAX_APK_BYTES)
        val deadline = Deadline(nanoTime, cancel)
        var current = uri
        var redirects = 0
        try {
            while (true) {
                deadline.remainingMillis()
                if (!current.isHttpsOnDefaultPort()) networkFailure()
                val connection = open(current)
                try {
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    connection.requestMethod = "GET"
                    connection.connectTimeout = minOf(10_000, deadline.remainingMillis())
                    connection.readTimeout = minOf(15_000, deadline.remainingMillis())
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    val status = connection.responseCode
                    deadline.remainingMillis()
                    when (status) {
                        HttpURLConnection.HTTP_OK -> {
                            val length = connection.contentLengthWithin(maxBytes)
                            val encodings = connection.headers("Content-Encoding")
                            if (encodings.isNotEmpty() &&
                                !encodings.singleOrNull().equals("identity", ignoreCase = true)
                            ) {
                                networkFailure()
                            }
                            return connection.inputStream.use { body ->
                                val stream = BoundedBody(body, maxBytes, length, deadline)
                                val result = read(stream)
                                if (stream.read() != -1) responseSizeFailure()
                                deadline.remainingMillis()
                                result
                            }
                        }
                        in REDIRECT_CODES -> {
                            if (++redirects > 4) networkFailure()
                            val location = connection.headers("Location").singleOrNull()
                                ?.takeIf(String::isNotEmpty)?.let(::parseUpdateUri) ?: networkFailure()
                            val next = current.resolve(location)
                            if (!next.isHttpsOnDefaultPort() || !redirectAllowed(current, next)) networkFailure()
                            current = next
                        }
                        else -> networkFailure()
                    }
                } finally {
                    connection.disconnect()
                }
            }
        } catch (_: IOException) {
            cancel.check()
            networkFailure()
        } catch (_: SecurityException) {
            networkFailure()
        }
    }

    private class Deadline(private val nanoTime: () -> Long, private val cancel: UpdateCancellation) {
        private val start = nanoTime()

        fun remainingMillis(): Int {
            cancel.check()
            val remaining = TimeUnit.SECONDS.toNanos(120) - (nanoTime() - start)
            if (remaining <= 0) networkFailure()
            return TimeUnit.NANOSECONDS.toMillis(remaining).toInt().coerceAtLeast(1)
        }
    }

    private class BoundedBody(
        private val input: InputStream,
        maxBytes: Long,
        private val length: Long?,
        private val deadline: Deadline,
    ) : InputStream() {
        private val limit = length ?: maxBytes
        private var consumed = 0L
        private var ended = false

        override fun read(): Int {
            if (ended) return -1
            val value = readWithinDeadline { input.read() }
            record(if (value == -1) -1 else 1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
            if (offset < 0 || count < 0 || offset > buffer.size - count) throw IndexOutOfBoundsException()
            if (count == 0) return 0
            if (ended) return -1
            val allowed = minOf(count.toLong(), limit - consumed + 1).toInt()
            val read = readWithinDeadline { input.read(buffer, offset, allowed) }
            if (read == 0) networkFailure()
            record(read)
            return read
        }

        private inline fun readWithinDeadline(read: () -> Int): Int {
            deadline.remainingMillis()
            val result = try {
                read()
            } catch (error: IOException) {
                deadline.remainingMillis()
                if (length != null && consumed < length && (error is EOFException || error is ProtocolException)) {
                    responseSizeFailure()
                }
                throw error
            }
            deadline.remainingMillis()
            return result
        }

        private fun record(read: Int) {
            if (read == -1) {
                if (length != null && consumed != length) responseSizeFailure()
                ended = true
                return
            }
            consumed += read
            if (consumed > limit) responseSizeFailure()
        }
    }
}

private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
private val CONTENT_LENGTH = Regex("[0-9]+")

private fun openPublicHttps(uri: URI): HttpsURLConnection {
    // The launcher uses no process-wide authenticator. Reject an installed cookie handler
    // instead of risking implicit cookies in this public request.
    if (CookieHandler.getDefault() != null) networkFailure()
    return uri.toURL().openConnection(Proxy.NO_PROXY) as HttpsURLConnection
}

private fun HttpsURLConnection.headers(name: String): List<String> =
    headerFields.entries.filter { it.key.equals(name, ignoreCase = true) }.flatMap { it.value }

private fun HttpsURLConnection.contentLengthWithin(maxBytes: Long): Long? {
    val values = headers("Content-Length")
    if (values.isEmpty()) return null
    val value = values.singleOrNull()?.trim() ?: responseSizeFailure()
    if (!CONTENT_LENGTH.matches(value)) responseSizeFailure()
    val length = value.toLongOrNull() ?: responseSizeFailure()
    if (length > maxBytes) responseSizeFailure()
    return length
}

private fun networkFailure(): Nothing = throw UpdateException(UpdateFailure.NETWORK)
private fun responseSizeFailure(): Nothing = throw UpdateException(UpdateFailure.RESPONSE_SIZE)
