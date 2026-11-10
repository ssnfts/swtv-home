package app.swtv.home.platform

import android.content.ComponentName
import app.swtv.tvhub.contract.HubContract
import app.swtv.tvhub.contract.HubHandler
import app.swtv.tvhub.contract.HubIntents
import app.swtv.tvhub.contract.HubRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HubIntentInstrumentedTest {
    @Test fun homeRequestContainsOnlyAllowedActionPackageAndExtras() {
        val intent = HubIntents.openHub()
        assertEquals(HubContract.ACTION, intent.action)
        assertEquals(HubContract.PACKAGE, intent.`package`)
        assertEquals(setOf(HubContract.EXTRA_MAJOR, HubContract.EXTRA_DESTINATION), intent.extras!!.keySet())
        assertEquals(1, intent.getIntExtra(HubContract.EXTRA_MAJOR, -1))
        assertEquals("home", intent.getStringExtra(HubContract.EXTRA_DESTINATION))
        assertNull(intent.data)
        assertNull(intent.type)
        assertNull(intent.clipData)
        assertNull(intent.component)
        // Inspect only: never query or launch any external application.
    }

    @Test fun resolvedRequestPinsTheValidatedComponentAndPackage() {
        val handler = HubHandler(HubContract.PACKAGE, "app.swtv.tvhub.HubActivity", true, true, 1)
        val intent = HubIntents.openHub(handler = handler)
        assertEquals(ComponentName(handler.packageName, handler.className), intent.component)
        assertEquals(HubContract.PACKAGE, intent.`package`)
    }

    @Test fun invalidRequestAndHandlerCannotBuildLaunchIntents() {
        assertThrows(IllegalArgumentException::class.java) { HubIntents.openHub(HubRequest(destination = "player")) }
        assertThrows(IllegalArgumentException::class.java) {
            HubIntents.openHub(handler = HubHandler("other.app", "OtherActivity", true, true, 1))
        }
    }
}
