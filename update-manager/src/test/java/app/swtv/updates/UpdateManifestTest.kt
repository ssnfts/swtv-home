package app.swtv.updates

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

private const val VALID_MANIFEST = """{"schema":1,"packageName":"app.swtv.home","versionCode":3,"versionName":"0.3.0","minSdk":29,"apkUrl":"https://github.com/swtv/home/releases/download/swtv-home-v0.3.0/swtv-home-0.3.0.apk","apkBytes":15,"apkSha256":"0000000000000000000000000000000000000000000000000000000000000000"}"""

class UpdateManifestTest {
    @Test fun parsesCanonicalManifestWithExactTypes() {
        val expected = ReleaseManifest(
            1, "app.swtv.home", 3L, "0.3.0", 29,
            URI("https://github.com/swtv/home/releases/download/swtv-home-v0.3.0/swtv-home-0.3.0.apk"),
            15L, "0".repeat(64),
        )
        assertEquals(expected, ManifestParser.parse(" \r\n$VALID_MANIFEST\t".toByteArray()))
    }

    @Test fun rejectsDuplicateMissingAndUnknownMembers() {
        val invalid = listOf(
            VALID_MANIFEST.replace("\"schema\":1", "\"schema\":1,\"schema\":1"),
            VALID_MANIFEST.replace(",\"minSdk\":29", ""),
            VALID_MANIFEST.replace("\"minSdk\":29", "\"minSdk\":29,\"channel\":\"stable\""),
        )
        invalid.forEach { assertInvalid(it.toByteArray()) }
    }

    @Test fun rejectsNumericCoercionInvalidRangesAndNoncanonicalFields() {
        val invalidValues = listOf(
            "\"schema\":1" to "\"schema\":\"1\"",
            "\"schema\":1" to "\"schema\":2",
            "\"versionCode\":3" to "\"versionCode\":3.0",
            "\"versionCode\":3" to "\"versionCode\":3e0",
            "\"versionCode\":3" to "\"versionCode\":03",
            "\"versionCode\":3" to "\"versionCode\":-3",
            "\"versionCode\":3" to "\"versionCode\":2147483648",
            "\"versionCode\":3" to "\"versionCode\":9223372036854775808",
            "\"versionName\":\"0.3.0\"" to "\"versionName\":\"v0.3.0\"",
            "\"minSdk\":29" to "\"minSdk\":true",
            "\"minSdk\":29" to "\"minSdk\":0",
            "\"apkBytes\":15" to "\"apkBytes\":0",
            "\"apkBytes\":15" to "\"apkBytes\":33554433",
            "0".repeat(64) to "A".repeat(64),
            "app.swtv.home" to "app.swtv.other",
            "app.swtv.home" to "app.swtv.\\u0068ome",
        )
        invalidValues.forEach { (original, replacement) ->
            assertInvalid(VALID_MANIFEST.replace(original, replacement).toByteArray())
        }
    }

    @Test fun rejectsOversizeMalformedUtf8AndTrailingData() {
        assertInvalid((" ".repeat(65_536) + VALID_MANIFEST).toByteArray())
        assertInvalid(byteArrayOf(0xc3.toByte(), 0x28))
        assertInvalid("${VALID_MANIFEST}{}".toByteArray())
        assertInvalid(VALID_MANIFEST.dropLast(1).toByteArray())
        assertInvalid(VALID_MANIFEST.replace("minSdk", "min\nSdk").toByteArray())
    }

    private fun assertInvalid(bytes: ByteArray) {
        val failure = assertThrows(UpdateException::class.java) { ManifestParser.parse(bytes) }
        assertEquals(UpdateFailure.INVALID_MANIFEST, failure.failure)
    }
}
