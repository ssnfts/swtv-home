package app.swtv.home.web

import org.junit.Assert.*
import org.junit.Test

class WebsiteUrlTest {
    private fun valid(text: String): WebsiteAddress =
        (WebsiteUrl.normalize(text) as WebsiteUrlResult.Valid).address

    @Test fun bareHostUsesHttpsAndRootPath() {
        assertEquals("https://bingebang.st/", valid("bingebang.st").value)
    }

    @Test fun schemeHostAndDefaultPortDeduplicateWithoutChangingEncodedComponents() {
        assertEquals("https://example.com/?q=a%2Bb#part", valid("HTTPS://EXAMPLE.COM:443/?q=a%2Bb#part").value)
        assertEquals("https://example.com:8443/A%2fb?q=%2B#F", valid("https://Example.com:8443/A%2fb?q=%2B#F").value)
        assertEquals("https://xn--bcher-kva.example/", valid("https://bücher.example").value)
    }

    @Test fun unsafeSchemesCredentialsAndAmbiguousAuthoritiesAreRejected() {
        for (text in listOf("http://example.com", "javascript:alert(1)", "data:text/plain,x", "file:///tmp/x", "content://x", "intent://x", "https://user:pass@example.com", "https://user@example.com", "//example.com", "https:example.com", "https:///example.com", "https://example.com\\@evil.com", "https://example.com:", "https://example.com:abc", "https://example.com:0", "https://example.com:65536", "https://example.com:0443")) {
            assertTrue(text, WebsiteUrl.normalize(text) is WebsiteUrlResult.Invalid)
        }
    }

    @Test fun invalidHostsWhitespaceControlsAndOverlengthAreRejected() {
        for (text in listOf("", " ", "https://", "https://bad_host.example", "https://-bad.example", "https://a..example", "https://999.1.1.1", "https://example.com.", "https://example.com/a b", "https://example.com/\n", "\thttps://example.com", "https://example.com/" + "x".repeat(2048), "https://example.com/%xx")) {
            assertTrue(text, WebsiteUrl.normalize(text) is WebsiteUrlResult.Invalid)
        }
    }

    @Test fun surroundingSpacesAreTrimmedButNormalizedLengthIsBounded() {
        assertEquals("https://example.com/", valid(" example.com ").value)
        assertTrue(WebsiteUrl.normalize("a".repeat(2048)) is WebsiteUrlResult.Invalid)
        assertEquals("https://127.0.0.1/", valid("127.0.0.1").value)
        assertEquals("https://[::1]/", valid("https://[::1]").value)
    }
}
