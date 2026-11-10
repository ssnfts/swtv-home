package app.swtv.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class Phase2CompatibilityTest {
    @Test fun phase2Schema1FixturePreservesAllSettingsAndRoundTrips() {
        val fixture = requireNotNull(javaClass.getResource("/phase2-schema1.json")).readText()
        val expected = LauncherConfig(
            theme = "dark", accent = "violet", columns = 8,
            reducedMotion = true, showPhoneApps = false,
            dock = listOf("org.beta", "org.alpha"), hidden = setOf("org.hidden"),
            modules = listOf(ModuleTile("OwnTV", "tv.own.owntv", "app"),
                ModuleTile("HDMI", "org.input/.Service/HW1", "input")),
            wallpaperUri = "content://images/42", weatherCity = "Sydney",
            weatherEnabled = true, iptvPackage = "org.iptv"
        )
        val decoded = ConfigCodec.decode(fixture)
        assertEquals(expected, decoded)
        assertEquals(expected, ConfigCodec.decode(ConfigCodec.encode(decoded)))
        assertEquals("tv.own.owntv", LauncherConfig().iptvPackage)
    }
}
