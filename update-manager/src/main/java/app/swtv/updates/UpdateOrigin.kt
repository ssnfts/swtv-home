package app.swtv.updates

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

private const val GITHUB = "github.com"
private const val MANIFEST_NAME = "swtv-home-update-v1.json"
private const val LATEST_MANIFEST_PATH = "/releases/latest/download/$MANIFEST_NAME"
private val FEED_PATH = Regex(
    "/([A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?)/([A-Za-z0-9_.-]{1,100})" +
        Regex.escape(LATEST_MANIFEST_PATH),
)
private val TAGGED_MANIFEST_PATH =
    Regex("/releases/download/swtv-home-v" + RELEASE_VERSION.pattern + "/" + Regex.escape(MANIFEST_NAME))
private val CERTIFICATE = Regex("[0-9a-fA-F]{64}")
private val ASSET_HOSTS = setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com")
private val SIGNATURE_KEYS = setOf("sig", "X-Amz-Signature")

class FeedConfig private constructor(
    val feedUri: URI,
    val owner: String,
    val repository: String,
    val certificateSha256: String,
) {
    private val repositoryPath = "/$owner/$repository"

    fun acceptsApk(versionName: String, uri: URI): Boolean =
        RELEASE_VERSION.matches(versionName) &&
            githubPath(uri) == "/releases/download/swtv-home-v$versionName/swtv-home-$versionName.apk"

    fun allowsManifestRedirect(from: URI, to: URI): Boolean {
        val fromPath = githubPath(from)
        val fromLatest = fromPath == LATEST_MANIFEST_PATH
        return when {
            fromLatest && isTaggedManifest(to) -> true
            fromLatest || isTaggedManifest(from) || isSignedAsset(from) -> isSignedAsset(to)
            else -> false
        }
    }

    fun allowsApkRedirect(versionName: String, from: URI, to: URI): Boolean =
        (acceptsApk(versionName, from) || isSignedAsset(from)) && isSignedAsset(to)

    private fun isTaggedManifest(uri: URI): Boolean =
        githubPath(uri)?.let(TAGGED_MANIFEST_PATH::matches) == true

    private fun githubPath(uri: URI): String? {
        if (!uri.isHttpsOnDefaultPort() || !uri.host.equals(GITHUB, ignoreCase = true) || uri.rawQuery != null) {
            return null
        }
        val path = uri.rawPath
        if (!path.startsWith("$repositoryPath/", ignoreCase = true)) return null
        return path.substring(repositoryPath.length)
    }

    companion object {
        fun parse(feedUrl: String, certificateSha256: String): FeedConfig? {
            val uri = parseUpdateUri(feedUrl) ?: return null
            if (!uri.isHttpsOnDefaultPort() || !uri.host.equals(GITHUB, ignoreCase = true) ||
                uri.rawQuery != null || !CERTIFICATE.matches(certificateSha256)
            ) {
                return null
            }
            val match = FEED_PATH.matchEntire(uri.rawPath) ?: return null
            val (owner, repository) = match.destructured
            if (repository == "." || repository == "..") return null
            return FeedConfig(
                URI("https://$GITHUB/$owner/$repository$LATEST_MANIFEST_PATH"),
                owner, repository, certificateSha256.lowercase(Locale.ROOT),
            )
        }
    }
}

private fun isSignedAsset(uri: URI): Boolean {
    if (!uri.isHttpsOnDefaultPort() || uri.host.lowercase(Locale.ROOT) !in ASSET_HOSTS ||
        uri.rawPath.length < 2
    ) {
        return false
    }
    // The asset host validates the actual SAS or AWS signature. Require a signed URL shape
    // before allowing a request to either dedicated host.
    val signatures = uri.rawQuery?.split('&')?.filter { it.substringBefore('=') in SIGNATURE_KEYS }
        ?: return false
    return signatures.singleOrNull()?.substringAfter('=', "")?.isNotEmpty() == true
}

internal fun parseUpdateUri(value: String): URI? = try {
    URI(value)
} catch (_: URISyntaxException) {
    null
}

internal fun URI.isHttpsOnDefaultPort(): Boolean {
    val hostname = host ?: return false
    val authority = rawAuthority ?: return false
    return scheme.equals("https", ignoreCase = true) &&
        rawFragment == null && rawUserInfo == null && rawPath?.startsWith('/') == true &&
        toString().all { it in '!'..'~' } &&
        (authority.equals(hostname, ignoreCase = true) || authority.equals("$hostname:443", ignoreCase = true))
}
