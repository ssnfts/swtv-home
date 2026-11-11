package app.swtv.updates

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateEngineTest {
    @get:Rule val temporary = TemporaryFolder()
    private val apkBytes = ByteArray(32 * 1024 + 1) { (it % 251).toByte() }
    private val pin = "3b".repeat(32)
    private val feed = requireNotNull(FeedConfig.parse(
        "https://github.com/swtv/home/releases/latest/download/swtv-home-update-v1.json", pin,
    ))
    private val release = ReleaseManifest(
        1, "app.swtv.home", 3, "0.3.0", 29,
        URI("https://github.com/swtv/home/releases/download/swtv-home-v0.3.0/swtv-home-0.3.0.apk"),
        apkBytes.size.toLong(), sha256(apkBytes),
    )
    private var installed = ApkIdentity("app.swtv.home", 2, "0.2.0", 29, setOf(pin))
    private var archive = ApkIdentity("app.swtv.home", 3, "0.3.0", 29, setOf(pin))
    private var manifestBody = manifestJson(release)
    private var artifactBody: () -> InputStream = { ByteArrayInputStream(apkBytes) }
    private var connections = 0
    private lateinit var cache: File

    @Test fun checksNewReleasesAndReportsEqualOrOldAsCurrent() {
        val engine = engine()
        assertEquals(UpdateCheck.Available(release), engine.check(UpdateCancellation()))
        installed = installed.copy(versionCode = release.versionCode)
        assertEquals(UpdateCheck.UpToDate, engine.check(UpdateCancellation()))
        installed = installed.copy(versionCode = release.versionCode + 1)
        assertEquals(UpdateCheck.UpToDate, engine.check(UpdateCancellation()))
        assertEquals(3, connections)
    }

    @Test fun rejectsIncompatibleManifestAndUntrustedInstalledSigner() {
        val engine = engine()
        manifestBody = manifestJson(release.copy(minSdk = 30))
        assertFailure(UpdateFailure.INCOMPATIBLE) { engine.check(UpdateCancellation()) }
        installed = installed.copy(signerSha256 = setOf(pin, "ab".repeat(32)))
        assertFailure(UpdateFailure.SIGNER_MISMATCH) { engine.check(UpdateCancellation()) }
        assertEquals(1, connections)
    }

    @Test fun downloadRechecksManifestVersionAndPackageBeforeConnecting() {
        val engine = engine()
        assertFailure(UpdateFailure.INVALID_MANIFEST) {
            engine.download(release.copy(apkUrl = URI("https://example.test/fake.apk")), UpdateCancellation())
        }
        installed = installed.copy(packageName = "other.package")
        assertFailure(UpdateFailure.INVALID_ARTIFACT) { engine.download(release, UpdateCancellation()) }
        installed = installed.copy(packageName = release.packageName, versionCode = release.versionCode)
        assertFailure(UpdateFailure.CURRENT_OR_STALE) { engine.download(release, UpdateCancellation()) }
        assertEquals(0, connections)
        assertCacheEmpty()
    }

    @Test fun streamsVerifiedDownloadAndOwnsOnlyItsTempFile() {
        val engine = engine()
        artifactBody = {
            object : ByteArrayInputStream(apkBytes) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    assertTrue(len <= 16 * 1024)
                    return super.read(b, off, len)
                }
            }
        }
        val unrelated = cache.resolve("unrelated").apply { writeText("keep") }
        val update = engine.download(release, UpdateCancellation())
        update.use {
            assertEquals(release, it.manifest)
            assertEquals(cache.canonicalFile, it.file.parentFile?.canonicalFile)
            assertArrayEquals(apkBytes, it.file.readBytes())
            engine.revalidate(it, UpdateCancellation())
        }
        update.close()
        assertFalse(update.file.exists())
        assertTrue(unrelated.isFile)
        assertFailure(UpdateFailure.INVALID_ARTIFACT) { engine.revalidate(update, UpdateCancellation()) }
    }

    @Test fun rejectsShortOversizeAndTamperedDownloadsAndCleansPartialFiles() {
        val engine = engine()
        val invalid = listOf(apkBytes.dropLast(1).toByteArray(), apkBytes + 0, apkBytes.reversedArray())
        invalid.forEach { body ->
            artifactBody = { ByteArrayInputStream(body) }
            assertFailure(UpdateFailure.INVALID_ARTIFACT) { engine.download(release, UpdateCancellation()) }
            assertCacheEmpty()
        }
    }

    @Test fun rejectsMismatchInEveryArchiveMetadataFieldAndSignerSet() {
        val engine = engine()
        val original = archive
        val invalidArchives = listOf(
            original.copy(packageName = "app.swtv.other"),
            original.copy(versionCode = 2),
            original.copy(versionName = "0.3.1"),
            original.copy(minSdk = 28),
        )
        invalidArchives.forEach {
            archive = it
            assertFailure(UpdateFailure.INVALID_ARTIFACT) { engine.download(release, UpdateCancellation()) }
            assertCacheEmpty()
        }
        archive = original.copy(signerSha256 = setOf(pin, "ab".repeat(32)))
        assertFailure(UpdateFailure.SIGNER_MISMATCH) { engine.download(release, UpdateCancellation()) }
        assertCacheEmpty()
    }

    @Test fun revalidatesCurrentInstallationAndStagedBytesBeforeHandoff() {
        val engine = engine()
        engine.download(release, UpdateCancellation()).use { staged ->
            installed = installed.copy(versionCode = release.versionCode)
            assertFailure(UpdateFailure.CURRENT_OR_STALE) { engine.revalidate(staged, UpdateCancellation()) }
            installed = installed.copy(versionCode = 2, signerSha256 = setOf("00".repeat(32)))
            assertFailure(UpdateFailure.SIGNER_MISMATCH) { engine.revalidate(staged, UpdateCancellation()) }
            installed = installed.copy(signerSha256 = setOf(pin))
            staged.file.writeBytes(apkBytes.reversedArray())
            assertFailure(UpdateFailure.INVALID_ARTIFACT) { engine.revalidate(staged, UpdateCancellation()) }
        }
        assertCacheEmpty()
    }

    @Test fun cancelsBeforeNetworkAndDuringStreamingWithoutLeakingFiles() {
        val engine = engine()
        val canceled = UpdateCancellation().apply { cancel() }
        assertFailure(UpdateFailure.CANCELLED) { engine.check(canceled) }
        assertEquals(0, connections)

        val cancel = UpdateCancellation()
        artifactBody = {
            object : ByteArrayInputStream(apkBytes) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val count = super.read(b, off, len)
                    cancel.cancel()
                    return count
                }
            }
        }
        assertFailure(UpdateFailure.CANCELLED) { engine.download(release, cancel) }
        assertCacheEmpty()
    }

    @Test fun rejectsOversizedManifestEvenWhenTransportIsInjected() {
        val engine = engine()
        manifestBody = " ".repeat(65_537).toByteArray()
        assertFailure(UpdateFailure.INVALID_MANIFEST) { engine.check(UpdateCancellation()) }
        assertCacheEmpty()
    }

    @Test fun reportsStagingFailureBeforeConnecting() {
        cache = temporary.newFile("not-a-directory")
        val engine = engine()
        assertFailure(UpdateFailure.STORAGE) { engine.download(release, UpdateCancellation()) }
        assertEquals(0, connections)
        assertTrue(cache.isFile)
    }

    private fun engine(): UpdateEngine {
        if (!::cache.isInitialized) cache = temporary.newFolder()
        return UpdateEngine(feed, 29, cache, { installed }, { archive }, object : UpdateTransport {
            override fun <T> withBody(
                uri: URI,
                maxBytes: Long,
                cancel: UpdateCancellation,
                redirectAllowed: (URI, URI) -> Boolean,
                read: (InputStream) -> T,
            ): T {
                connections++
                val input = if (uri == feed.feedUri) ByteArrayInputStream(manifestBody) else artifactBody()
                return input.use(read)
            }
        })
    }

    private fun assertCacheEmpty() = assertTrue(cache.listFiles().orEmpty().isEmpty())

    private fun assertFailure(expected: UpdateFailure, action: () -> Unit) {
        assertEquals(expected, assertThrows(UpdateException::class.java) { action() }.failure)
    }

    private fun manifestJson(manifest: ReleaseManifest): ByteArray = with(manifest) {
        """{"schema":$schema,"packageName":"$packageName","versionCode":$versionCode,"versionName":"$versionName","minSdk":$minSdk,"apkUrl":"$apkUrl","apkBytes":$apkBytes,"apkSha256":"$apkSha256"}""".toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
