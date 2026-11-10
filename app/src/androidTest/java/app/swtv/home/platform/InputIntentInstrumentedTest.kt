package app.swtv.home.platform

import app.swtv.discovery.InputIntents
import android.content.Intent
import android.media.tv.TvContract
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InputIntentInstrumentedTest {
    @Test fun passthroughHandoffHasExplicitActionUriAndMimeType() {
        val id = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"
        val intent = InputIntents.passthrough(id)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("content://android.media.tv/passthrough/com.mediatek.tvinput%2F.hdmi.HDMIInputService%2FHW5"), intent.data)
        assertEquals(TvContract.Channels.CONTENT_ITEM_TYPE, intent.type)
        // Inspect only: never resolve, start an Activity or switch a device input.
    }
}
