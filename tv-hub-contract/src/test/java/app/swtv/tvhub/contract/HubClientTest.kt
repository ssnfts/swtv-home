package app.swtv.tvhub.contract

import org.junit.Assert.*
import org.junit.Test

class HubClientTest {
    private val handler = HubHandler(HubContract.PACKAGE, "HubActivity", true, true, 1)
    private class Gateway(var resolution: HubResolution) : HubGateway {
        var launches = 0
        var sent: HubRequest? = null
        var failResolve = false
        var failLaunch = false
        override fun resolve(): HubResolution {
            if (failResolve) throw SecurityException("visibility changed")
            return resolution
        }
        override fun launch(handler: HubHandler, request: HubRequest) {
            launches++
            sent = request
            if (failLaunch) throw IllegalStateException("removed after query")
        }
    }

    @Test fun launchesOnlyACompatibleHomeRequest() {
        val gateway = Gateway(HubResolution.Ready(handler))
        assertEquals(HubLaunchResult.Launched, HubClient(gateway).open())
        assertEquals(1, gateway.launches)
        assertEquals(HubRequest(), gateway.sent)
    }

    @Test fun returnsDistinctFailuresWithoutLaunchOrFallback() {
        listOf(HubResolution.Missing to HubLaunchResult.Missing,
            HubResolution.Incompatible to HubLaunchResult.Incompatible,
            HubResolution.Unavailable to HubLaunchResult.Unavailable).forEach { (resolution, result) ->
            val gateway = Gateway(resolution)
            assertEquals(result, HubClient(gateway).open())
            assertEquals(0, gateway.launches)
        }
    }

    @Test fun rejectsUnsupportedDestinationsBeforeLaunch() {
        val gateway = Gateway(HubResolution.Ready(handler))
        assertEquals(HubLaunchResult.Unavailable, HubClient(gateway).open(HubRequest(destination = "player")))
        assertEquals(0, gateway.launches)
    }

    @Test fun treatsUninstallAndQueryRacesAsUnavailable() {
        val gateway = Gateway(HubResolution.Ready(handler))
        gateway.failLaunch = true
        assertEquals(HubLaunchResult.Unavailable, HubClient(gateway).open())
        gateway.failResolve = true
        assertEquals(HubLaunchResult.Unavailable, HubClient(gateway).open())
    }

    @Test fun revalidatesGatewayHandlerBeforeLaunch() {
        val gateway = Gateway(HubResolution.Ready(handler.copy(contractMajor = "1")))
        assertEquals(HubLaunchResult.Incompatible, HubClient(gateway).open())
        assertEquals(0, gateway.launches)
    }
}
