package app.swtv.home.platform

import org.junit.Assert.*
import org.junit.Test

class DockInputRegressionTest {
    private val full = listOf("A", "B", "C", "D", "E", "F")

    @Test fun canonicalDockCompactsRemovedAppThenFillsAndEditsVisibleSlot() {
        val compact = DockPolicy.canonical(full, setOf("B", "C", "D", "E", "F", "G"))
        assertEquals(listOf("B", "C", "D", "E", "F"), compact)
        assertEquals(listOf("B", "C", "D", "E", "F", "G"), DockPolicy.pin(compact, "G"))
        assertEquals(listOf("G", "C", "D", "E", "F"), DockPolicy.assign(compact, 0, "G"))
        assertEquals(full, DockPolicy.canonical(full, null)) // Unknown discovery is not removal.
    }

    @Test fun occupiedSlotReplacesOnlyOccupantAndPinnedChoiceSwaps() {
        assertEquals(listOf("A", "G", "C", "D", "E", "F"), DockPolicy.assign(full, 1, "G"))
        assertEquals(listOf("A", "E", "C", "D", "B", "F"), DockPolicy.assign(full, 1, "E"))
        assertEquals(full, DockPolicy.assign(full, 1, "B"))
        assertEquals(listOf("A", "G", "C"), DockPolicy.assign(full.take(3), 1, "G"))
        assertEquals(listOf("A", "B", "C", "G"), DockPolicy.assign(full.take(3), 5, "G"))
        assertEquals(listOf("A", "C", "B"), DockPolicy.assign(full.take(3), 5, "B"))
        assertNull(DockPolicy.pin(full, "G"))
        assertEquals(full, DockPolicy.pin(full, "B"))
    }

    @Test fun onlyConnectedOrStandbyPassthroughInputsAreEligible() {
        assertTrue(InputPolicy.available(true, 0))
        assertTrue(InputPolicy.available(true, 1))
        assertFalse(InputPolicy.available(true, 2))
        assertFalse(InputPolicy.available(true, -1))
        assertFalse(InputPolicy.available(false, 0))
    }
}
