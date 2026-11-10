package app.swtv.tvhub.contract

import org.junit.Assert.*
import org.junit.Test

class HubPolicyTest {
    private val handler = HubHandler(HubContract.PACKAGE, "HubActivity", true, true, 1)

    @Test fun majorMetadataMustBeAnIntegerOne() {
        assertTrue(HubPolicy.compatible(1))
        listOf(null, "1", 1L, 1.0, true, 0, 2).forEach { assertFalse("$it", HubPolicy.compatible(it)) }
    }

    @Test fun onlyHomeAndExactAllowedExtrasAreAccepted() {
        val extras = HubRequest().extras()
        assertTrue(HubPolicy.allowedRequest(extras))
        assertFalse(HubPolicy.allowedRequest(extras + ("token" to "secret")))
        assertFalse(HubPolicy.allowedRequest(extras - HubContract.EXTRA_MAJOR))
        assertFalse(HubPolicy.allowedRequest(extras + (HubContract.EXTRA_MAJOR to "1")))
        assertFalse(HubPolicy.allowedRequest(HubRequest(destination = "player").extras()))
        assertFalse(HubPolicy.allowedRequest(HubRequest(major = 2).extras()))

        val nonStringHome = object {
            override fun equals(other: Any?): Boolean = HubContract.HOME.equals(other)
            override fun hashCode(): Int = HubContract.HOME.hashCode()
        }
        assertFalse(HubPolicy.allowedRequest(extras + (HubContract.EXTRA_DESTINATION to nonStringHome)))
    }

    @Test fun absentPackageIsMissing() {
        assertEquals(HubResolution.Missing, HubPolicy.resolve(false, emptyList()))
    }

    @Test fun uniqueCompatibleHandlerIsExplicitlySelected() {
        assertEquals(HubResolution.Ready(handler), HubPolicy.resolve(true, listOf(handler)))
    }

    @Test fun incompatibleMajorOrMissingOrWrongTypeMetadataIsRejected() {
        listOf(null, "1", 1L, 2).forEach {
            assertEquals(HubResolution.Incompatible, HubPolicy.resolve(true, listOf(handler.copy(contractMajor = it))))
        }
    }

    @Test fun disabledUnexportedWrongPackageAndEmptyHandlersAreUnavailable() {
        listOf(emptyList(), listOf(handler.copy(enabled = false)), listOf(handler.copy(exported = false)),
            listOf(handler.copy(applicationEnabled = false)), listOf(handler.copy(packageName = "other.app"))).forEach {
            assertEquals(HubResolution.Unavailable, HubPolicy.resolve(true, it))
        }
    }

    @Test fun ambiguousHandlersAreNeverLaunched() {
        assertEquals(HubResolution.Unavailable, HubPolicy.resolve(true, listOf(handler, handler.copy(className = "Second"))))
    }

    @Test fun minorExtensionsDoNotChangeRequiredMajorHandshake() {
        assertEquals(0, HubContract.MINOR)
        // Optional provider metadata is additive and does not grant new request capabilities.
        val metadata = mapOf(HubContract.METADATA_MAJOR to 1,
            "app.swtv.tvhub.contractMinor" to 17, "app.swtv.tvhub.futureCapability" to true)
        assertTrue(HubPolicy.compatibleMetadata(metadata))
        assertTrue(HubPolicy.compatibleMetadata(mapOf(HubContract.METADATA_MAJOR to 1)))
        assertFalse(HubPolicy.compatibleMetadata(metadata + (HubContract.METADATA_MAJOR to 2)))
        assertFalse(HubPolicy.compatibleMetadata(metadata - HubContract.METADATA_MAJOR))
        assertFalse(HubPolicy.compatibleMetadata(metadata + (HubContract.METADATA_MAJOR to "1")))
        assertFalse(HubPolicy.allowedRequest(HubRequest().extras() + ("futureDestination" to "player")))
    }
}
