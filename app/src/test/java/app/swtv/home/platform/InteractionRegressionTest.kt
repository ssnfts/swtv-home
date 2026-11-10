package app.swtv.home.platform

import org.junit.Assert.*
import org.junit.Test

class InteractionRegressionTest {
    @Test fun minuteBoundaryKeepsActiveRequestAndUpdatesLatestStatusTarget() {
        val requests = WeatherRequests()
        val oldStatus = mutableListOf<String>()
        val newStatus = mutableListOf<String>()
        // Request starts at 12:00:59.900; minute tick occurs while HTTPS is still active.
        val ticket = requests.begin("Perth") { oldStatus.add(it) }
        assertTrue(requests.observe("Perth") { newStatus.add(it) })
        assertTrue(requests.isCurrent(ticket))
        assertTrue(requests.complete(ticket, "Perth · 23°C"))
        assertTrue(oldStatus.isEmpty())
        assertEquals(listOf("Perth · 23°C"), newStatus)
        assertNull(requests.activeCity)
    }

    @Test fun cancelAndCityReplacementCannotApplyOldResult() {
        val requests = WeatherRequests()
        var display = ""
        val old = requests.begin("Sydney") { display = it }
        assertFalse(requests.observe("Perth") { display = it })
        val replacement = requests.begin("Perth") { display = it }
        assertFalse(requests.complete(old, "Sydney"))
        requests.cancel()
        assertFalse(requests.complete(replacement, "Perth"))
        assertEquals("", display)
    }

    private data class Entry(val id: String, val label: String)

    @Test fun openPickerThenReorderOrRemoveNeverChangesSelectedIdentity() {
        val a = Entry("app.a", "A")
        val b = Entry("app.b", "B")
        val c = Entry("input/c", "C")
        val picker = CatalogPicker(listOf(a, b, c)) { it.id }
        assertEquals(b, picker.resolve(1, listOf(c, a, b)))
        assertEquals(c, picker.resolve(2, listOf(c)))
        assertNull(picker.resolve(1, listOf(a, c)))
        assertNull(picker.resolve(99, listOf(a)))
        val updated = b.copy(label = "Updated installed entry")
        assertEquals(updated, picker.resolve(1, listOf(updated)))
    }
}
