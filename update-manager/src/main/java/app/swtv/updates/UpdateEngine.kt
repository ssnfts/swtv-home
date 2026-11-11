package app.swtv.updates

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val signerSha256: Set<String>,
)

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val manifest: ReleaseManifest) : UpdateCheck
}

class VerifiedUpdate internal constructor(
    val manifest: ReleaseManifest,
    val file: File,
    private val owner: Any,
) : Closeable {
    private val closed = AtomicBoolean()

    internal fun belongsTo(owner: Any): Boolean = this.owner === owner && !closed.get()

    override fun close() {
        if (closed.compareAndSet(false, true)) file.delete()
    }
}

class UpdateEngine(
    private val config: FeedConfig,
    private val deviceSdk: Int,
    private val cacheDirectory: File,
    private val installed: () -> ApkIdentity?,
    private val inspectArchive: (File) -> ApkIdentity?,
    private val transport: UpdateTransport = HttpsUpdateTransport(),
) {
    private val stagingOwner = Any()

    fun check(cancel: UpdateCancellation): UpdateCheck {
        cancel.check()
        val current = trustedInstalled()
        val manifest = fetch(
            config.feedUri, MAX_MANIFEST_BYTES.toLong(), UpdateFailure.INVALID_MANIFEST, cancel,
            config::allowsManifestRedirect,
        ) { input ->
            val bytes = ByteArrayOutputStream()
            consume(input, MAX_MANIFEST_BYTES.toLong(), cancel, UpdateFailure.INVALID_MANIFEST, bytes::write)
            ManifestParser.parse(bytes.toByteArray())
        }
        validateManifest(manifest)
        cancel.check()
        if (manifest.versionCode <= current.versionCode) return UpdateCheck.UpToDate
        requireCompatible(manifest)
        return UpdateCheck.Available(manifest)
    }

    fun download(manifest: ReleaseManifest, cancel: UpdateCancellation): VerifiedUpdate {
        cancel.check()
        validateManifest(manifest)
        requireNewer(manifest)
        requireCompatible(manifest)
        val file = storage {
            if (!cacheDirectory.isDirectory && !cacheDirectory.mkdirs() && !cacheDirectory.isDirectory) {
                throw UpdateException(UpdateFailure.STORAGE)
            }
            File.createTempFile("swtv-update-", ".apk", cacheDirectory)
        }

        var verified = false
        try {
            storage {
                file.outputStream().use { output ->
                    fetch(
                        manifest.apkUrl, manifest.apkBytes, UpdateFailure.INVALID_ARTIFACT, cancel,
                        { from, to -> config.allowsApkRedirect(manifest.versionName, from, to) },
                    ) { input ->
                        verifyBytes(input, manifest, cancel) { bytes, offset, count ->
                            storage { output.write(bytes, offset, count) }
                        }
                    }
                }
            }
            validateArchive(file, manifest)
            requireNewer(manifest)
            cancel.check()
            verified = true
            return VerifiedUpdate(manifest, file, stagingOwner)
        } finally {
            if (!verified) file.delete()
        }
    }

    /** Recheck the staged bytes and Android metadata immediately before installer staging. */
    fun revalidate(update: VerifiedUpdate, cancel: UpdateCancellation) {
        cancel.check()
        if (!update.belongsTo(stagingOwner)) invalidArtifact()
        val (manifest, file) = update.manifest to update.file
        validateManifest(manifest)
        requireNewer(manifest)
        requireCompatible(manifest)
        storage {
            if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) || file.length() != manifest.apkBytes) {
                invalidArtifact()
            }
            file.inputStream().use { verifyBytes(it, manifest, cancel) }
        }
        validateArchive(file, manifest)
        requireNewer(manifest)
        cancel.check()
    }

    private fun validateManifest(manifest: ReleaseManifest) {
        manifest.validateFields()
        if (!config.acceptsApk(manifest.versionName, manifest.apkUrl)) {
            throw UpdateException(UpdateFailure.INVALID_MANIFEST)
        }
    }

    private fun requireNewer(manifest: ReleaseManifest) {
        if (manifest.versionCode <= trustedInstalled().versionCode) {
            throw UpdateException(UpdateFailure.CURRENT_OR_STALE)
        }
    }

    private fun requireCompatible(manifest: ReleaseManifest) {
        if (manifest.minSdk > deviceSdk) throw UpdateException(UpdateFailure.INCOMPATIBLE)
    }

    private fun trustedInstalled(): ApkIdentity {
        val identity = readIdentity(installed)
        if (identity.packageName != LAUNCHER_PACKAGE || identity.versionCode <= 0) invalidArtifact()
        requirePinnedSigner(identity)
        return identity
    }

    private fun validateArchive(file: File, manifest: ReleaseManifest) {
        // Inspect archive metadata here. Android's installer verifies the actual APK signature.
        val identity = readIdentity { inspectArchive(file) }
        if (identity.packageName != manifest.packageName || identity.versionCode != manifest.versionCode ||
            identity.versionName != manifest.versionName || identity.minSdk != manifest.minSdk
        ) {
            invalidArtifact()
        }
        requirePinnedSigner(identity)
    }

    private fun requirePinnedSigner(identity: ApkIdentity) {
        if (!identity.signerSha256.singleOrNull().equals(config.certificateSha256, ignoreCase = true)) {
            throw UpdateException(UpdateFailure.SIGNER_MISMATCH)
        }
    }

    private fun <T> fetch(
        uri: URI,
        maxBytes: Long,
        invalid: UpdateFailure,
        cancel: UpdateCancellation,
        redirectAllowed: (URI, URI) -> Boolean,
        read: (InputStream) -> T,
    ): T = try {
        transport.withBody(uri, maxBytes, cancel, redirectAllowed, read)
    } catch (error: UpdateException) {
        if (error.failure == UpdateFailure.RESPONSE_SIZE) throw UpdateException(invalid)
        throw error
    } catch (_: IOException) {
        cancel.check()
        throw UpdateException(UpdateFailure.NETWORK)
    }
}

private fun verifyBytes(
    input: InputStream,
    manifest: ReleaseManifest,
    cancel: UpdateCancellation,
    write: (ByteArray, Int, Int) -> Unit = { _, _, _ -> },
) {
    val digest = MessageDigest.getInstance("SHA-256")
    val count = consume(input, manifest.apkBytes, cancel, UpdateFailure.INVALID_ARTIFACT) { bytes, offset, length ->
        digest.update(bytes, offset, length)
        write(bytes, offset, length)
    }
    if (count != manifest.apkBytes || digest.digest().toLowerHex() != manifest.apkSha256) invalidArtifact()
}

private fun consume(
    input: InputStream,
    limit: Long,
    cancel: UpdateCancellation,
    invalid: UpdateFailure,
    write: (ByteArray, Int, Int) -> Unit,
): Long {
    val buffer = ByteArray(16 * 1024)
    var received = 0L
    while (true) {
        cancel.check()
        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), limit - received + 1).toInt())
        cancel.check()
        if (count == -1) return received
        received += count
        if (count == 0 || received > limit) throw UpdateException(invalid)
        write(buffer, 0, count)
    }
}

private const val HEX_DIGITS = "0123456789abcdef"

private fun ByteArray.toLowerHex(): String = buildString(size * 2) {
    for (byte in this@toLowerHex) {
        val value = byte.toInt() and 0xff
        append(HEX_DIGITS[value ushr 4])
        append(HEX_DIGITS[value and 0xf])
    }
}

private inline fun readIdentity(read: () -> ApkIdentity?): ApkIdentity = try {
    read() ?: invalidArtifact()
} catch (_: IOException) {
    invalidArtifact()
} catch (_: SecurityException) {
    invalidArtifact()
}

private inline fun <T> storage(action: () -> T): T = try {
    action()
} catch (_: IOException) {
    throw UpdateException(UpdateFailure.STORAGE)
} catch (_: SecurityException) {
    throw UpdateException(UpdateFailure.STORAGE)
}

private fun invalidArtifact(): Nothing = throw UpdateException(UpdateFailure.INVALID_ARTIFACT)
