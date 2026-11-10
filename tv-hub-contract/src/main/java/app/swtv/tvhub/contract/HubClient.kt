package app.swtv.tvhub.contract

enum class HubLaunchResult { Missing, Incompatible, Unavailable, Launched }

interface HubGateway {
    fun resolve(): HubResolution
    fun launch(handler: HubHandler, request: HubRequest)
}

class HubClient(private val gateway: HubGateway) {
    fun open(request: HubRequest = HubRequest()): HubLaunchResult {
        if (!HubPolicy.allowedRequest(request.extras())) return HubLaunchResult.Unavailable
        return try {
            val resolution = when (val found = gateway.resolve()) {
                is HubResolution.Ready -> HubPolicy.resolve(true, listOf(found.handler))
                else -> found
            }
            when (resolution) {
                HubResolution.Missing -> HubLaunchResult.Missing
                HubResolution.Incompatible -> HubLaunchResult.Incompatible
                HubResolution.Unavailable -> HubLaunchResult.Unavailable
                is HubResolution.Ready -> {
                    gateway.launch(resolution.handler, request)
                    HubLaunchResult.Launched
                }
            }
        } catch (_: Exception) {
            // Includes package removal, disabled components, security and launch races.
            HubLaunchResult.Unavailable
        }
    }
}
