package app.swtv.updates

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class UpdateOriginTest {
    private val prefix = "https://github.com/swtv/home"
    private val manifestPath = "/releases/latest/download/swtv-home-update-v1.json"
    private val feedUrl = prefix + manifestPath
    private val pin = "a3".repeat(32)
    private val config = requireNotNull(FeedConfig.parse(feedUrl, pin))
    private val taggedManifest = URI("$prefix/releases/download/swtv-home-v0.3.0/swtv-home-update-v1.json")
    private val apk = URI("$prefix/releases/download/swtv-home-v0.3.0/swtv-home-0.3.0.apk")

    @Test fun acceptsOnlyTheExactFeedAndAFullCertificateFingerprint() {
        val normalized = requireNotNull(FeedConfig.parse("HTTPS://GITHUB.COM:443/swtv/home$manifestPath", pin.uppercase()))
        assertEquals(URI(feedUrl), normalized.feedUri)
        assertEquals(pin, normalized.certificateSha256)
        assertNull(FeedConfig.parse(feedUrl, pin.dropLast(1)))
        assertNull(FeedConfig.parse(feedUrl, "gg".repeat(32)))
        val rejected = listOf(
            feedUrl.replace("https", "http"),
            feedUrl.replace("github.com", "github.com.example.test"),
            feedUrl.replace("github.com", "github.com:8443"),
            feedUrl.replace("github.com", "github.com:"),
            feedUrl.replace("github.com", "user@github.com"),
            feedUrl.replace("swtv/home", "swtv/%68ome"),
            feedUrl.replace("swtv/home", "../home"),
            feedUrl.replace("swtv/home", "swtv/hôme"),
            "$feedUrl?query=true", "$feedUrl#", "$feedUrl/",
        )
        rejected.forEach { assertNull(it, FeedConfig.parse(it, pin)) }
    }

    @Test fun bindsTheApkToTheExactVersionAssetAndFeedRepository() {
        assertTrue(config.acceptsApk("0.3.0", apk))
        val rejected = listOf(
            "$apk?some=1", "$apk#download", "$apk/",
            apk.toString().replace("0.3.0.apk", "0.3.1.apk"),
            apk.toString().replace("v0.3.0/", "v0.3.1/"),
            apk.toString().replace("swtv/home", "swtv/another"),
            apk.toString().replace("swtv/home", "swtv/other/../home"),
            "https://release-assets.githubusercontent.com/download?sig=abc",
        )
        rejected.forEach { assertFalse(it, config.acceptsApk("0.3.0", URI(it))) }
    }

    @Test fun allowsOnlyBoundGitHubAndSignedAssetHostRedirects() {
        val githubAsset = URI("https://release-assets.githubusercontent.com/blob/123?sig=a%2Bb")
        val objectsAsset = URI("https://objects.githubusercontent.com/blob/123?X-Amz-Signature=abc")
        assertTrue(config.allowsManifestRedirect(config.feedUri, taggedManifest))
        assertTrue(config.allowsManifestRedirect(taggedManifest, githubAsset))
        assertTrue(config.allowsApkRedirect("0.3.0", apk, objectsAsset))
        assertTrue(config.allowsApkRedirect("0.3.0", objectsAsset, githubAsset))
        assertFalse(config.allowsApkRedirect("0.3.0", apk, taggedManifest))
        assertFalse(config.allowsManifestRedirect(githubAsset, taggedManifest))
        assertFalse(config.allowsManifestRedirect(config.feedUri, apk))
        val rejected = listOf(
            "https://release-assets.githubusercontent.com/blob/123",
            "https://release-assets.githubusercontent.com/blob/123?x=1",
            "https://release-assets.githubusercontent.com/blob/123?sig=",
            "https://release-assets.githubusercontent.com/blob/123?sig=abc&sig=def",
            "https://release-assets.githubusercontent.com.evil.example/blob?sig=abc",
            "https://user@release-assets.githubusercontent.com/blob?sig=abc",
            "http://release-assets.githubusercontent.com/blob?sig=abc",
            "https://release-assets.githubusercontent.com:8443/blob?sig=abc",
            "https://release-assets.githubusercontent.com/blob?sig=abc#fragment",
            taggedManifest.toString().replace("swtv/home", "swtv/other"),
            "$taggedManifest?sig=abc",
            "$prefix/issues/1",
        )
        rejected.forEach {
            assertFalse(it, config.allowsManifestRedirect(config.feedUri, URI(it)))
            assertFalse(it, config.allowsApkRedirect("0.3.0", apk, URI(it)))
        }
    }
}
