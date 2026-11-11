package app.swtv.updates

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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
    private val lock = Any()
    private val aborts = mutableSetOf<() -> Unit>()
    @Volatile private var cancelled = false

    fun cancel() {
        val active = synchronized(lock) {
            if (cancelled) return
            cancelled = true
            aborts.toList().also { aborts.clear() }
        }
        active.forEach { it() }
    }

    fun check() {
        if (cancelled) throw UpdateException(UpdateFailure.CANCELLED)
    }

    internal inline fun <T> whileActive(noinline abort: () -> Unit, action: () -> T): T {
        synchronized(lock) {
            check()
            aborts.add(abort)
        }
        try {
            return action()
        } finally {
            synchronized(lock) { aborts.remove(abort) }
        }
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
    builder: OkHttpClient.Builder,
    private val budgetMillis: Long = 120_000,
    private val nanoTime: () -> Long = System::nanoTime,
) : UpdateTransport {
    constructor() : this(OkHttpClient.Builder())

    private val client = builder
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .proxy(Proxy.NO_PROXY)
        .cache(null)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // OkHttp can otherwise follow up a 421 or a 503 even with connection retries off.
            if (response.code == 421 || response.code == 503) {
                response.close()
                throw IOException("Unexpected release HTTP status")
            }
            response
        }
        .build()

    override fun <T> withBody(
        uri: URI,
        maxBytes: Long,
        cancel: UpdateCancellation,
        redirectAllowed: (URI, URI) -> Boolean,
        read: (InputStream) -> T,
    ): T {
        require(maxBytes in 1L..MAX_APK_BYTES)
        val deadline = Deadline(nanoTime, budgetMillis, cancel)
        var current = uri
        var redirects = 0
        try {
            while (true) {
                if (!current.isHttpsOnDefaultPort()) networkFailure()
                val call = client.newCall(
                    Request.Builder().url(current.toString()).get().header("Accept-Encoding", "identity").build(),
                )
                call.timeout().timeout(deadline.remainingMillis(), TimeUnit.MILLISECONDS)
                current = cancel.whileActive(call::cancel) {
                    call.execute().use { response ->
                        deadline.remainingMillis()
                        when (response.code) {
                            HttpURLConnection.HTTP_OK -> return readBody(response, maxBytes, deadline, read)
                            in REDIRECT_CODES -> {
                                if (++redirects > 4) networkFailure()
                                val location = response.headers.values("Location").singleOrNull()
                                    ?.takeIf(String::isNotEmpty)?.let(::parseUpdateUri) ?: networkFailure()
                                val next = current.resolve(location)
                                if (!next.isHttpsOnDefaultPort() || !redirectAllowed(current, next)) networkFailure()
                                next
                            }
                            else -> networkFailure()
                        }
                    }
                }
            }
        } catch (_: IOException) {
            cancel.check()
            networkFailure()
        } catch (_: SecurityException) {
            networkFailure()
        }
    }

    private fun <T> readBody(response: Response, maxBytes: Long, deadline: Deadline, read: (InputStream) -> T): T {
        val length = response.contentLengthWithin(maxBytes)
        val encodings = response.headers.values("Content-Encoding")
        if (encodings.isNotEmpty() && !encodings.singleOrNull().equals("identity", ignoreCase = true)) {
            networkFailure()
        }
        val body = response.body ?: networkFailure()
        val stream = BoundedBody(body.byteStream(), maxBytes, length, deadline)
        val result = read(stream)
        if (stream.read() != -1) responseSizeFailure()
        deadline.remainingMillis()
        return result
    }

    private class Deadline(
        private val nanoTime: () -> Long,
        budgetMillis: Long,
        private val cancel: UpdateCancellation,
    ) {
        private val budget = TimeUnit.MILLISECONDS.toNanos(budgetMillis)
        private val start = nanoTime()

        fun remainingMillis(): Long {
            cancel.check()
            val remaining = budget - (nanoTime() - start)
            if (remaining <= 0) networkFailure()
            return TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1)
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

private fun Response.contentLengthWithin(maxBytes: Long): Long? {
    val values = headers.values("Content-Length")
    if (values.isEmpty()) return null
    val value = values.singleOrNull()?.trim() ?: responseSizeFailure()
    if (!CONTENT_LENGTH.matches(value)) responseSizeFailure()
    val length = value.toLongOrNull() ?: responseSizeFailure()
    if (length > maxBytes) responseSizeFailure()
    return length
}

private fun networkFailure(): Nothing = throw UpdateException(UpdateFailure.NETWORK)
private fun responseSizeFailure(): Nothing = throw UpdateException(UpdateFailure.RESPONSE_SIZE)
