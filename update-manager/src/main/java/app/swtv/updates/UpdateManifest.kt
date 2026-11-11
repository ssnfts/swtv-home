package app.swtv.updates

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

internal const val LAUNCHER_PACKAGE = "app.swtv.home"
internal const val MAX_MANIFEST_BYTES = 64 * 1024
internal const val MAX_APK_BYTES = 32L * 1024 * 1024
internal val RELEASE_VERSION = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
private val LOWER_SHA256 = Regex("[0-9a-f]{64}")

data class ReleaseManifest(
    val schema: Int,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: URI,
    val apkBytes: Long,
    val apkSha256: String,
) {
    internal fun validateFields() {
        if (schema != 1 || packageName != LAUNCHER_PACKAGE || versionCode !in 1L..Int.MAX_VALUE.toLong() ||
            !RELEASE_VERSION.matches(versionName) || minSdk < 1 || apkBytes !in 1L..MAX_APK_BYTES ||
            !LOWER_SHA256.matches(apkSha256) || !apkUrl.isHttpsOnDefaultPort() || apkUrl.rawQuery != null
        ) {
            invalidManifest()
        }
    }
}

object ManifestParser {
    private val strings = setOf("packageName", "versionName", "apkUrl", "apkSha256")
    private val integers = setOf("schema", "versionCode", "minSdk", "apkBytes")

    /**
     * Release tooling emits a flat JSON object with unescaped ASCII strings and positive decimal
     * integers. Member order and JSON whitespace are unrestricted. Other JSON values, including
     * escaped strings, are outside this format.
     */
    fun parse(bytes: ByteArray): ReleaseManifest {
        if (bytes.size > MAX_MANIFEST_BYTES) invalidManifest()
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            invalidManifest()
        }
        val reader = Reader(text)
        val fields = mutableMapOf<String, String>()
        reader.expect('{')
        do {
            val name = reader.string()
            if (name in fields) invalidManifest()
            reader.expect(':')
            fields[name] = when (name) {
                in strings -> reader.string()
                in integers -> reader.integer()
                else -> invalidManifest()
            }
        } while (reader.take(','))
        reader.expect('}')
        reader.expectEnd()
        if (fields.size != strings.size + integers.size) invalidManifest()

        return ReleaseManifest(
            schema = fields.getValue("schema").toIntOrNull() ?: invalidManifest(),
            packageName = fields.getValue("packageName"),
            versionCode = fields.getValue("versionCode").toLongOrNull() ?: invalidManifest(),
            versionName = fields.getValue("versionName"),
            minSdk = fields.getValue("minSdk").toIntOrNull() ?: invalidManifest(),
            apkUrl = parseUpdateUri(fields.getValue("apkUrl")) ?: invalidManifest(),
            apkBytes = fields.getValue("apkBytes").toLongOrNull() ?: invalidManifest(),
            apkSha256 = fields.getValue("apkSha256"),
        ).also { it.validateFields() }
    }

    private class Reader(private val text: String) {
        private var position = 0

        fun expect(character: Char) {
            if (!take(character)) invalidManifest()
        }

        fun take(character: Char): Boolean {
            skipWhitespace()
            if (text.getOrNull(position) != character) return false
            position++
            return true
        }

        fun expectEnd() {
            skipWhitespace()
            if (position != text.length) invalidManifest()
        }

        fun string(): String {
            expect('"')
            val start = position
            while (position < text.length) {
                val character = text[position++]
                if (character == '"') return text.substring(start, position - 1)
                if (character !in ' '..'~' || character == '\\') invalidManifest()
            }
            invalidManifest()
        }

        fun integer(): String {
            skipWhitespace()
            val start = position
            if (position >= text.length || text[position] !in '1'..'9') invalidManifest()
            while (position < text.length && text[position] in '0'..'9') position++
            return text.substring(start, position)
        }

        private fun skipWhitespace() {
            while (position < text.length && text[position] in " \t\r\n") position++
        }
    }
}

private fun invalidManifest(): Nothing = throw UpdateException(UpdateFailure.INVALID_MANIFEST)
