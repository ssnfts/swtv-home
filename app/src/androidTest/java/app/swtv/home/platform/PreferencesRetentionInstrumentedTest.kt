package app.swtv.home.platform

import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.swtv.theme.ConfigCodec
import app.swtv.theme.Preferences
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Intentionally independent of Phase4 classes: also runs against archived Phase3 recovery code. */
@RunWith(AndroidJUnit4::class)
class PreferencesRetentionInstrumentedTest {
    private fun target(): Context {
        assumeTrue("API29 emulator only", Build.VERSION.SDK_INT == 29 &&
            Build.HARDWARE in setOf("ranchu", "goldfish") && Build.FINGERPRINT.contains("generic"))
        return InstrumentationRegistry.getInstrumentation().targetContext
    }
    @Test fun seedPreferences() {
        assertTrue(target().getSharedPreferences("home", Context.MODE_PRIVATE).edit().putString("config", fixture).commit())
        assertPreferences()
    }
    @Test fun assertPreferences() {
        val context = target()
        val expected = ConfigCodec.decode(fixture)
        assertEquals(expected, Preferences(context).load())
        val raw = context.getSharedPreferences("home", Context.MODE_PRIVATE).getString("config", null)
        assertNotNull(raw)
        assertEquals(1, org.json.JSONObject(raw!!).getInt("schemaVersion"))
        assertTrue(expected.weatherEnabled)
        assertEquals("", expected.weatherCity) // Retains opt-in without any external weather request.
    }
    companion object {
        private val fixture = """{
          "schemaVersion":1,"theme":"dark","accent":"violet","columns":8,
          "reducedMotion":true,"showPhoneApps":false,"dock":["org.beta","org.alpha"],
          "hidden":["org.hidden"],"modules":[
            {"label":"OwnTV","target":"tv.own.owntv","kind":"app"},
            {"label":"HDMI","target":"org.input/.Service/HW1","kind":"input"}],
          "wallpaperUri":"content://images/42","weatherCity":"","weatherEnabled":true,"iptvPackage":"org.iptv"
        }"""
    }
}
