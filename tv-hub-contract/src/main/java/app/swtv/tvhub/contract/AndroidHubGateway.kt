package app.swtv.tvhub.contract

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** The only Android adapter: no network, package-launch fallback, installer, or sensitive IPC. */
class AndroidHubGateway(context: Context) : HubGateway {
    private val context = context.applicationContext

    @Suppress("DEPRECATION") // Int flag APIs support the minimum API 29 without a parallel API surface.
    override fun resolve(): HubResolution {
        val pm = context.packageManager
        val app = try {
            pm.getApplicationInfo(HubContract.PACKAGE, PackageManager.GET_META_DATA)
        } catch (_: PackageManager.NameNotFoundException) {
            return HubResolution.Missing
        }
        // Bundle.get preserves the exact stored type for the policy's integer check.
        val metadata = app.metaData?.let { bundle -> bundle.keySet().associateWith { bundle.get(it) } }.orEmpty()
        if (!HubPolicy.compatibleMetadata(metadata)) return HubResolution.Incompatible
        val major = metadata[HubContract.METADATA_MAJOR]
        val handlers = pm.queryIntentActivities(HubIntents.openHub(), 0).mapNotNull { resolved ->
            resolved.activityInfo?.let {
                HubHandler(it.packageName, it.name, it.enabled, it.exported, major,
                    app.enabled && it.applicationInfo.enabled)
            }
        }
        return HubPolicy.resolve(true, handlers)
    }

    override fun launch(handler: HubHandler, request: HubRequest) {
        context.startActivity(HubIntents.openHub(request, handler)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
