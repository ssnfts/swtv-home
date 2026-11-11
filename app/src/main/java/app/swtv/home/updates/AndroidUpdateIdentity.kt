package app.swtv.home.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.swtv.home.BuildConfig
import app.swtv.updates.ApkIdentity
import app.swtv.updates.FeedConfig
import app.swtv.updates.HttpsUpdateTransport
import app.swtv.updates.UpdateEngine
import app.swtv.updates.UpdateTransport
import java.io.File
import java.security.MessageDigest

/** Reads only current APK content signers; signing history is never a trust source. */
class AndroidUpdateIdentity(context: Context) {
    private val context = context.applicationContext
    fun installed(): ApkIdentity? = try {
        identity(context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES))
    } catch (_: PackageManager.NameNotFoundException) { null }
    fun archive(file: File): ApkIdentity? =
        context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)?.let(::identity)

    private fun identity(info: PackageInfo): ApkIdentity? {
        val name = info.versionName ?: return null
        val application = info.applicationInfo ?: return null
        val signers = info.signingInfo?.apkContentsSigners ?: return null
        if (signers.isEmpty()) return null
        return ApkIdentity(info.packageName, info.longVersionCode, name, application.minSdkVersion,
            signers.map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) } }.toSet())
    }

    companion object {
        fun releaseConfig(context: Context): FeedConfig? {
            if (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE != "release") return null
            val data = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
                ?: return null
            return FeedConfig.parse(data.getString("app.swtv.home.update.FEED_URL") ?: "",
                data.getString("app.swtv.home.update.CERT_SHA256") ?: "")
        }

        /** Constructor seam for instrumentation; normal app configuration comes only from its own metadata. */
        fun engine(context: Context, config: FeedConfig, transport: UpdateTransport = HttpsUpdateTransport()): UpdateEngine {
            val identity = AndroidUpdateIdentity(context)
            return UpdateEngine(config, android.os.Build.VERSION.SDK_INT, File(context.cacheDir, "release-updates"),
                identity::installed, identity::archive, transport)
        }
    }
}
