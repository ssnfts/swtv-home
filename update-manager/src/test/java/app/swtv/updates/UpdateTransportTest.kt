package app.swtv.updates

import java.io.ByteArrayInputStream
import java.net.URI
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.*
import org.junit.Test

class UpdateTransportTest {
    private val start = URI("https://github.com/test/home/releases/latest/download/asset")
    private val cdn = URI("https://release-assets.githubusercontent.com/blob?sig=value")

    @Test fun followsRedirectsManuallyAndClosesBothConnections() {
        val redirect = Connection(start, 302, mapOf("Location" to listOf(cdn.toString())))
        val asset = Connection(cdn, 200, mapOf("Content-Length" to listOf("3")), byteArrayOf(1, 2, 3))
        val connections = mapOf(start to redirect, cdn to asset)
        val transport = HttpsUpdateTransport({ connections.getValue(it) })
        val body = transport.withBody(start, 3, UpdateCancellation(), { from, to ->
            from == start && to == cdn
        }) { it.readBytes() }
        assertArrayEquals(byteArrayOf(1, 2, 3), body)
        assertTrue(redirect.disconnected)
        assertTrue(asset.disconnected)
        assertFalse(asset.instanceFollowRedirects)
        assertEquals("GET", asset.requestMethod)
        assertEquals("identity", asset.getRequestProperty("Accept-Encoding"))
        assertNull(asset.getRequestProperty("Cookie"))
        assertNull(asset.getRequestProperty("Authorization"))
    }

    @Test fun rejectsDisallowedAndCleartextRedirectsBeforeOpeningTarget() {
        val destinations = listOf("https://untrusted.example/asset", "http://github.com/test/home/asset")
        destinations.forEach { destination ->
            val opened = mutableListOf<URI>()
            val transport = HttpsUpdateTransport({
                opened += it
                Connection(it, 302, mapOf("Location" to listOf(destination)))
            })
            assertFailure(UpdateFailure.NETWORK) {
                transport.withBody(start, 10, UpdateCancellation(), { _, to ->
                    to.scheme == "http"
                }) { it.readBytes() }
            }
            assertEquals(listOf(start), opened)
        }
    }

    @Test fun enforcesMaximumRedirectsAndSingleDeadline() {
        var hops = 0
        val loop = HttpsUpdateTransport({
            hops++
            Connection(it, 302, mapOf("Location" to listOf("/hop-$hops")))
        })
        assertFailure(UpdateFailure.NETWORK) {
            loop.withBody(start, 10, UpdateCancellation(), { _, _ -> true }) { it.readBytes() }
        }
        assertEquals(5, hops)

        var elapsed = 0L
        val deadlines = mutableListOf<Int>()
        val slow = HttpsUpdateTransport({ uri ->
            object : Connection(uri, 302, mapOf("Location" to listOf("/hop-${deadlines.size}"))) {
                override fun getResponseCode(): Int {
                    deadlines += readTimeout
                    elapsed += 40_000_000_000L
                    return super.getResponseCode()
                }
            }
        }, { elapsed })
        assertFailure(UpdateFailure.NETWORK) {
            slow.withBody(start, 10, UpdateCancellation(), { _, _ -> true }) { it.readBytes() }
        }
        assertEquals(listOf(15_000, 15_000, 15_000), deadlines)
    }

    @Test fun enforcesDeclaredAndStreamingBodyLengths() {
        val bodies = listOf(
            mapOf("Content-Length" to listOf("4")) to byteArrayOf(1),
            mapOf("Content-Length" to listOf("3")) to byteArrayOf(1, 2),
            mapOf("Content-Length" to listOf("2")) to byteArrayOf(1, 2, 3),
            mapOf("Content-Length" to listOf("2", "2")) to byteArrayOf(1, 2),
            emptyMap<String, List<String>>() to byteArrayOf(1, 2, 3, 4),
        )
        bodies.forEach { (headers, bytes) ->
            val response = Connection(start, 200, headers, bytes)
            val transport = HttpsUpdateTransport({ response })
            assertFailure(UpdateFailure.RESPONSE_SIZE) {
                transport.withBody(start, 3, UpdateCancellation(), { _, _ -> false }) { it.readBytes() }
            }
            assertTrue(response.disconnected)
        }
    }

    private fun assertFailure(expected: UpdateFailure, action: () -> Unit) {
        assertEquals(expected, assertThrows(UpdateException::class.java) { action() }.failure)
    }

    private open class Connection(
        uri: URI,
        private val code: Int,
        private val headers: Map<String, List<String>>,
        private val bytes: ByteArray = byteArrayOf(),
    ) : HttpsURLConnection(uri.toURL()) {
        var disconnected = false

        override fun getResponseCode() = code
        override fun getHeaderFields() = headers
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = ""
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate>? = null
    }
}
