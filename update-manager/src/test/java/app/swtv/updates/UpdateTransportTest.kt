package app.swtv.updates

import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class UpdateTransportTest {
    private val start = URI("https://github.com/test/home/releases/latest/download/asset")
    private val cdn = URI("https://release-assets.githubusercontent.com/blob?sig=value")

    @Test fun followsRedirectsManuallyAndClosesBothResponses() {
        val redirect = Reply(302, mapOf("Location" to listOf(cdn.toString())))
        val asset = Reply(200, mapOf("Content-Length" to listOf("3")), byteArrayOf(1, 2, 3))
        val responses = mapOf(start.toString() to redirect, cdn.toString() to asset)
        val requests = mutableListOf<Request>()
        val transport = HttpsUpdateTransport(OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            responses.getValue(chain.request().url.toString()).respond(chain.request())
        })
        val body = transport.withBody(start, 3, UpdateCancellation(), { from, to ->
            from == start && to == cdn
        }) { it.readBytes() }
        assertArrayEquals(byteArrayOf(1, 2, 3), body)
        assertTrue(redirect.closed)
        assertTrue(asset.closed)
        assertEquals(listOf(start.toString(), cdn.toString()), requests.map { it.url.toString() })
        assertEquals("GET", requests.last().method)
        assertEquals("identity", requests.last().header("Accept-Encoding"))
    }

    @Test fun rejectsDisallowedAndCleartextRedirectsBeforeOpeningTarget() {
        val destinations = listOf("https://untrusted.example/asset", "http://github.com/test/home/asset")
        destinations.forEach { destination ->
            val opened = mutableListOf<String>()
            val transport = HttpsUpdateTransport(OkHttpClient.Builder().addInterceptor { chain ->
                opened += chain.request().url.toString()
                Reply(302, mapOf("Location" to listOf(destination))).respond(chain.request())
            })
            assertFailure(UpdateFailure.NETWORK) {
                transport.withBody(start, 10, UpdateCancellation(), { _, to ->
                    to.scheme == "http"
                }) { it.readBytes() }
            }
            assertEquals(listOf(start.toString()), opened)
        }
    }

    @Test fun enforcesMaximumRedirectsAndSharesOneDeadlineAcrossCalls() {
        var hops = 0
        val loop = HttpsUpdateTransport(OkHttpClient.Builder().addInterceptor { chain ->
            hops++
            Reply(302, mapOf("Location" to listOf("/hop-$hops"))).respond(chain.request())
        })
        assertFailure(UpdateFailure.NETWORK) {
            loop.withBody(start, 10, UpdateCancellation(), { _, _ -> true }) { it.readBytes() }
        }
        assertEquals(5, hops)

        var elapsed = 0L
        val deadlines = mutableListOf<Long>()
        val slow = HttpsUpdateTransport(OkHttpClient.Builder().addInterceptor { chain ->
            deadlines += chain.call().timeout().timeoutNanos()
            elapsed += TimeUnit.SECONDS.toNanos(40)
            val location = "/hop-" + deadlines.size
            Reply(302, mapOf("Location" to listOf(location))).respond(chain.request())
        }, nanoTime = { elapsed })
        assertFailure(UpdateFailure.NETWORK) {
            slow.withBody(start, 10, UpdateCancellation(), { _, _ -> true }) { it.readBytes() }
        }
        assertEquals(listOf(120L, 80L, 40L).map(TimeUnit.SECONDS::toNanos), deadlines)
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
            val response = Reply(200, headers, bytes)
            val transport = HttpsUpdateTransport(OkHttpClient.Builder().addInterceptor { chain ->
                response.respond(chain.request())
            })
            assertFailure(UpdateFailure.RESPONSE_SIZE) {
                transport.withBody(start, 3, UpdateCancellation(), { _, _ -> false }) { it.readBytes() }
            }
            assertTrue(response.closed)
        }
    }

    private fun assertFailure(expected: UpdateFailure, action: () -> Unit) {
        assertEquals(expected, assertThrows(UpdateException::class.java) { action() }.failure)
    }

    private class Reply(
        private val code: Int,
        private val headers: Map<String, List<String>>,
        private val bytes: ByteArray = byteArrayOf(),
    ) {
        var closed = false
            private set

        fun respond(request: Request): Response {
            val source = object : ForwardingSource(Buffer().write(bytes)) {
                override fun close() {
                    closed = true
                    super.close()
                }
            }.buffer()
            val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .message("")
                .code(code)
                .body(source.asResponseBody(contentLength = bytes.size.toLong()))
            headers.forEach { (name, values) -> values.forEach { response.addHeader(name, it) } }
            return response.build()
        }
    }
}
