package app.swtv.tvhub.contract

object HubContract {
    const val MAJOR = 1
    const val MINOR = 0
    const val PACKAGE = "app.swtv.tvhub"
    const val ACTION = "app.swtv.tvhub.action.OPEN_HUB"
    const val METADATA_MAJOR = "app.swtv.tvhub.contractMajor"
    const val EXTRA_MAJOR = "app.swtv.tvhub.extra.CONTRACT_MAJOR"
    const val EXTRA_DESTINATION = "app.swtv.tvhub.extra.DESTINATION"
    const val HOME = "home"
}

data class HubRequest(val major: Int = HubContract.MAJOR, val destination: String = HubContract.HOME) {
    fun extras(): Map<String, Any> = mapOf(HubContract.EXTRA_MAJOR to major, HubContract.EXTRA_DESTINATION to destination)
}
data class HubHandler(val packageName: String, val className: String, val enabled: Boolean,
    val exported: Boolean, val contractMajor: Any?, val applicationEnabled: Boolean = true)
sealed interface HubResolution {
    data object Missing : HubResolution
    data object Incompatible : HubResolution
    data object Unavailable : HubResolution
    data class Ready(val handler: HubHandler) : HubResolution
}
object HubPolicy {
    /** Only the required major participates in compatibility, even when a provider adds metadata. */
    fun compatibleMetadata(metadata: Map<String, Any?>): Boolean =
        compatible(metadata[HubContract.METADATA_MAJOR])

    fun compatible(major: Any?): Boolean = major is Int && major == HubContract.MAJOR

    fun allowedRequest(extras: Map<String, Any?>): Boolean =
        extras.keys == setOf(HubContract.EXTRA_MAJOR, HubContract.EXTRA_DESTINATION) &&
            compatible(extras[HubContract.EXTRA_MAJOR]) &&
            (extras[HubContract.EXTRA_DESTINATION] as? String) == HubContract.HOME

    fun resolve(installed: Boolean, handlers: List<HubHandler>): HubResolution {
        if (!installed) return HubResolution.Missing
        val candidates = handlers.filter {
            it.packageName == HubContract.PACKAGE && it.className.isNotBlank() &&
                it.enabled && it.applicationEnabled && it.exported
        }
        if (candidates.size != 1) return HubResolution.Unavailable
        val handler = candidates.single()
        return if (compatible(handler.contractMajor)) HubResolution.Ready(handler)
            else HubResolution.Incompatible
    }
}
