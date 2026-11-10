package app.swtv.discovery

import org.junit.Assert.*
import org.junit.Test

class CatalogPickerTest {
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
