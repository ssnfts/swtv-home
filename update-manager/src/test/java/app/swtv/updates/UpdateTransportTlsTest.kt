package app.swtv.updates

import java.io.Closeable
import java.net.Authenticator
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpCookie
import java.net.InetAddress
import java.net.PasswordAuthentication
import java.net.SocketException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateTransportTlsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun wholeCallDeadlineAbortsBlockedResponseHeaders() {
        withWorker { worker ->
            val received = CountDownLatch(1)
            TlsServer { _, release ->
                received.countDown()
                release.await(20, TimeUnit.SECONDS)
            }.use { server ->
                val transport = transport(server, budgetMillis = 2_000)
                val result = worker.submit<UpdateFailure> {
                    failure {
                        transport.withBody(REQUEST, 32, UpdateCancellation(), { _, _ -> false }) { it.readBytes() }
                    }
                }
                assertTrue("The TLS server did not receive the request", received.await(5, TimeUnit.SECONDS))
                assertEquals(UpdateFailure.NETWORK, result.get(6, TimeUnit.SECONDS))
            }
        }
    }

    @Test fun cancellationAbortsBlockedDownloadBodyAndDeletesPartialFile() {
        withWorker { worker ->
            val sent = CountDownLatch(1)
            TlsServer { socket, release ->
                socket.send("HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\n1")
                sent.countDown()
                release.await(20, TimeUnit.SECONDS)
            }.use { server ->
                val apk = URI("https://github.com/test/home/releases/download/swtv-home-v0.3.0/swtv-home-0.3.0.apk")
                val pin = "a3".repeat(32)
                val feed = requireNotNull(FeedConfig.parse(REQUEST.toString(), pin))
                val cache = temporary.newFolder()
                val manifest = ReleaseManifest(1, "app.swtv.home", 3, "0.3.0", 29, apk, 3, "0".repeat(64))
                val current = ApkIdentity("app.swtv.home", 2, "0.2.0", 29, setOf(pin))
                val engine = UpdateEngine(feed, 29, cache, { current }, { error("Incomplete APK was inspected") }, transport(server))
                val cancellation = UpdateCancellation()
                val result = worker.submit<UpdateFailure> { failure { engine.download(manifest, cancellation).close() } }
                assertTrue("The TLS server did not send the body", sent.await(5, TimeUnit.SECONDS))
                val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (cache.listFiles()?.singleOrNull()?.length() != 1L && System.nanoTime() < limit) {
                    Thread.sleep(10)
                }
                assertEquals(1L, cache.listFiles()?.singleOrNull()?.length())
                cancellation.cancel()
                assertEquals(UpdateFailure.CANCELLED, result.get(3, TimeUnit.SECONDS))
                assertTrue(cache.listFiles().orEmpty().isEmpty())
            }
        }
    }

    @Test fun ambientCookiesAndAuthenticatorCannotSupplyCredentialsOnChallenge() {
        val authCalls = AtomicInteger()
        val requests = AtomicInteger()
        val previousAuthenticator = Authenticator::class.java.getMethod("getDefault").invoke(null) as Authenticator?
        val previousCookies = CookieHandler.getDefault()
        TlsServer { socket, _ ->
            requests.incrementAndGet()
            socket.send("HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"release\"\r\n" +
                "Content-Length: 0\r\nConnection: close\r\n\r\n")
        }.use { server ->
            val cookies = CookieManager()
            val cookie = HttpCookie("ambient", "fixture").apply { path = "/"; secure = true; version = 0 }
            cookies.cookieStore.add(server.target(REQUEST), cookie)
            try {
                CookieHandler.setDefault(cookies)
                Authenticator.setDefault(object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        authCalls.incrementAndGet()
                        return PasswordAuthentication("fixture", "fixture".toCharArray())
                    }
                })
                val transport = transport(server)
                assertEquals(UpdateFailure.NETWORK, failure {
                    transport.withBody(REQUEST, 32, UpdateCancellation(), { _, _ -> false }) { it.readBytes() }
                })
                assertEquals(1, requests.get())
                assertEquals(0, authCalls.get())
                val headers = server.requestHeaders.single()
                assertFalse(headers.keys.any { it.equals("Cookie", ignoreCase = true) })
                assertFalse(headers.keys.any { it.equals("Authorization", ignoreCase = true) })
            } finally {
                Authenticator.setDefault(previousAuthenticator)
                CookieHandler.setDefault(previousCookies)
            }
        }
    }

    @Test fun aRetryAfterHeaderDoesNotCauseAnotherRequest() {
        TlsServer { socket, _ ->
            socket.send("HTTP/1.1 503 Service Unavailable\r\nRetry-After: 0\r\n" +
                "Content-Length: 0\r\nConnection: close\r\n\r\n")
        }.use { server ->
            assertEquals(UpdateFailure.NETWORK, failure {
                transport(server).withBody(REQUEST, 32, UpdateCancellation(), { _, _ -> false }) { it.readBytes() }
            })
            assertEquals(1, server.requestHeaders.size)
        }
    }

    private fun transport(server: TlsServer, budgetMillis: Long = 120_000): HttpsUpdateTransport =
        HttpsUpdateTransport(
            OkHttpClient.Builder()
                .sslSocketFactory(TLS.context.socketFactory, TLS.trustManager)
                .addInterceptor { chain ->
                    val request = chain.request()
                    chain.proceed(request.newBuilder().url(server.target(request.url.toUri()).toString()).build())
                },
            budgetMillis,
        )

    private fun failure(action: () -> Unit): UpdateFailure =
        assertThrows(UpdateException::class.java) { action() }.failure

    private fun withWorker(action: (ExecutorService) -> Unit) {
        val worker = Executors.newSingleThreadExecutor { Thread(it, "update-test-client").apply { isDaemon = true } }
        try {
            action(worker)
        } finally {
            worker.shutdownNow()
        }
    }

    private class TlsServer(handler: (SSLSocket, CountDownLatch) -> Unit) : Closeable {
        private val release = CountDownLatch(1)
        private val worker = Executors.newSingleThreadExecutor()
        private val active = AtomicReference<SSLSocket>()
        private val server = TLS.context.serverSocketFactory.createServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val requestHeaders = CopyOnWriteArrayList<Map<String, List<String>>>()

        init {
            worker.execute {
                try {
                    while (!server.isClosed) {
                        (server.accept() as SSLSocket).use { socket ->
                            active.set(socket)
                            if (server.isClosed) return@execute
                            val reader = socket.inputStream.bufferedReader(Charsets.US_ASCII)
                            if (reader.readLine() != null) {
                                val headers = reader.lineSequence().takeWhile(String::isNotEmpty)
                                    .groupBy({ it.substringBefore(':') }, { it.substringAfter(':').trim() })
                                requestHeaders.add(headers)
                                handler(socket, release)
                            }
                            active.set(null)
                        }
                    }
                } catch (error: SocketException) {
                    if (!server.isClosed) throw error
                }
            }
        }

        fun target(uri: URI): URI =
            URI("https", null, "127.0.0.1", server.localPort, uri.path, uri.query, null)

        override fun close() {
            server.close()
            release.countDown()
            active.get()?.close()
            worker.shutdown()
        }
    }

    private data class TestTls(val context: SSLContext, val trustManager: X509TrustManager)

    companion object {
        private fun SSLSocket.send(response: String) {
            outputStream.write(response.toByteArray(Charsets.US_ASCII))
            outputStream.flush()
        }

        private val REQUEST =
            URI("https://github.com/test/home/releases/latest/download/swtv-home-update-v1.json")

        private val TLS by lazy {
            val directory = Files.createTempDirectory("swtv-update-test-tls")
            val storeFile = directory.resolve("server.p12")
            val password = "disposable-fixture"
            val keytool = Paths.get(System.getProperty("java.home"), "bin", "keytool")
            val executable = if (Files.exists(keytool)) keytool else keytool.resolveSibling("keytool.exe")
            try {
                val process = ProcessBuilder(
                    executable.toString(), "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                    "-dname", "CN=localhost", "-ext", "SAN=IP:127.0.0.1", "-validity", "2",
                    "-storetype", "PKCS12", "-keystore", storeFile.toString(), "-storepass", password, "-noprompt",
                ).redirectErrorStream(true).start()
                if (!process.waitFor(20, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    error("Test TLS key generation timed out")
                }
                check(process.exitValue() == 0) { process.inputStream.bufferedReader().use { it.readText() } }
                val store = KeyStore.getInstance("PKCS12")
                Files.newInputStream(storeFile).use { store.load(it, password.toCharArray()) }
                val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                keys.init(store, password.toCharArray())
                val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                trust.init(store)
                val context = SSLContext.getInstance("TLS")
                context.init(keys.keyManagers, trust.trustManagers, null)
                TestTls(context, trust.trustManagers.filterIsInstance<X509TrustManager>().single())
            } finally {
                Files.deleteIfExists(storeFile)
                Files.deleteIfExists(directory)
            }
        }
    }
}
