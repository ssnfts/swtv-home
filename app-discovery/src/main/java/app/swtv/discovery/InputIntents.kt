package app.swtv.discovery

import android.content.Intent
import android.media.tv.TvContract

object InputIntents {
    fun passthrough(id: String): Intent = Intent(Intent.ACTION_VIEW).setDataAndType(
        TvContract.buildChannelUriForPassthroughInput(id), TvContract.Channels.CONTENT_ITEM_TYPE
    )
}
