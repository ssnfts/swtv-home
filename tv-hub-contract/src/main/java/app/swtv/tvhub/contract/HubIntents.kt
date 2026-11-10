package app.swtv.tvhub.contract

import android.content.ComponentName
import android.content.Intent

/** Public for intent-only API 29 instrumentation; building does not resolve or launch an app. */
object HubIntents {
    fun openHub(request: HubRequest = HubRequest(), handler: HubHandler? = null): Intent {
        require(HubPolicy.allowedRequest(request.extras())) { "Unsupported Hub request" }
        if (handler != null) require(HubPolicy.resolve(true, listOf(handler)) is HubResolution.Ready) {
            "Unsupported Hub handler"
        }
        return Intent(HubContract.ACTION).setPackage(HubContract.PACKAGE)
            .putExtra(HubContract.EXTRA_MAJOR, request.major)
            .putExtra(HubContract.EXTRA_DESTINATION, request.destination).apply {
                if (handler != null) component = ComponentName(handler.packageName, handler.className)
            }
    }
}
