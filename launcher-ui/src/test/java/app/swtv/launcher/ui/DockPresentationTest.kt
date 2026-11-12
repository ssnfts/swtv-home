package app.swtv.launcher.ui

import app.swtv.discovery.DockPolicy
import org.junit.Assert.*
import org.junit.Test

class DockPresentationTest {
    @Test fun emptyDockHasOneAddEntryTargetingFirstSlot() {
        assertEquals(listOf(DockPresentation.Entry(0, null)), DockPresentation.entries(emptyList()))
    }

    @Test fun partialDockRetainsOrderAndOffersOnlyNextFreeSlot() {
        assertEquals(listOf(DockPresentation.Entry(0, "b"), DockPresentation.Entry(1, "a"),
            DockPresentation.Entry(2, null)), DockPresentation.entries(listOf("b", "a")))
    }

    @Test fun fullDockHasSixAppsAndAllSlotsRemainReplaceable() {
        val pins = (0..5).map { "app$it" }
        assertEquals(pins, DockPresentation.entries(pins).map { it.packageName })
        (0..5).forEach { slot ->
            assertEquals("replacement", DockPolicy.assign(pins, slot, "replacement")[slot])
        }
    }

    @Test fun missingPackagePinKeepsItsKeyAndCanBeClearedWithoutLosingNeighbors() {
        // Discovery is deliberately not an input to presentation or the editable sequence.
        val pins = DockPresentation.pins(listOf("installed", "temporarily.missing", "other"))
        assertEquals("dock:temporarily.missing", DockPresentation.entries(pins)[1].key)
        assertEquals(listOf("installed", "other"), pins - "temporarily.missing")
    }

    @Test fun removedSavedDockFocusFallsBackToFirstAvailableControl() {
        val keys = listOf("settings") + DockPresentation.entries(emptyList()).map { it.key }
        assertEquals("settings", FocusPolicy.focus("dock:removed", keys))
        assertEquals("slot:0", FocusPolicy.focus("slot:0", keys))
        assertNull(FocusPolicy.focus("dock:removed", emptyList()))
    }
}
