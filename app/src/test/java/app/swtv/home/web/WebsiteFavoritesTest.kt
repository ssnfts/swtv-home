package app.swtv.home.web

import org.junit.Assert.*
import org.junit.Test

class WebsiteFavoritesTest {
    private fun success(result: WebsiteResult) = result as WebsiteResult.Success
    private fun add(sites: WebsiteFavorites, n: Int) = success(sites.save("Site $n", "site$n.example")).favorites

    @Test fun duplicateAddKeepsOriginalIdTitleAndOrder() {
        val first = success(WebsiteFavorites.empty().save("First", "EXAMPLE.COM:443"))
        assertEquals(1, first.favorites.entries.size)
        assertNotNull(first.favorite)
        val second = success(first.favorites.save("Other", "https://example.com/"))
        assertFalse(second.changed)
        assertEquals(first.favorite, second.favorite)
        assertEquals(first.favorites.entries, second.favorites.entries)
    }

    @Test fun editRetainsIdAndPositionWhileConflictAndMissingIdsAreTyped() {
        val sites = add(add(WebsiteFavorites.empty(), 1), 2)
        val id = sites.entries[0].id
        val edit = success(sites.save("Renamed", "site3.example/path", id))
        assertEquals(id, edit.favorites.entries[0].id)
        assertEquals("Renamed", edit.favorites.entries[0].title)
        assertEquals(sites.entries[1], edit.favorites.entries[1])
        assertEquals(WebsiteResult.Failure(WebsiteError.DUPLICATE_EDIT), sites.save("Conflict", "site2.example", id))
        assertEquals(WebsiteResult.Failure(WebsiteError.NOT_FOUND), sites.save("Missing", "site3.example", "missing"))
    }

    @Test fun titlesDeriveFromHostButInvalidNonblankTitlesFail() {
        assertEquals("example.com", success(WebsiteFavorites.empty().save("  ", "example.com")).favorite!!.title)
        for (title in listOf("x".repeat(101), "bad\nname", "\u0000")) {
            assertEquals(WebsiteResult.Failure(WebsiteError.INVALID_TITLE), WebsiteFavorites.empty().save(title, "example.com"))
        }
        assertEquals(WebsiteResult.Failure(WebsiteError.INVALID_ADDRESS), WebsiteFavorites.empty().save("x", "http://example.com"))
    }

    @Test fun capacityDoesNotPreventDedupOrEditing() {
        val sites = (1..32).fold(WebsiteFavorites.empty(), ::add)
        assertEquals(32, sites.entries.size)
        assertEquals(WebsiteResult.Failure(WebsiteError.CAPACITY), sites.save("33", "site33.example"))
        assertFalse(success(sites.save("dup", "site1.example")).changed)
        assertEquals(32, success(sites.save("Edit", "site33.example", sites.entries[0].id)).favorites.entries.size)
    }

    @Test fun removeAndMoveUseIdsPreserveNeighborsAndClampBounds() {
        val sites = (1..3).fold(WebsiteFavorites.empty(), ::add)
        val moved = success(sites.move(sites.entries[0].id, Int.MAX_VALUE)).favorites
        assertEquals(listOf(sites.entries[1], sites.entries[2], sites.entries[0]), moved.entries)
        assertEquals(sites.entries, success(moved.move(sites.entries[0].id, Int.MIN_VALUE)).favorites.entries)
        assertFalse(success(sites.move(sites.entries[0].id, -1)).changed)
        assertEquals(listOf(sites.entries[0], sites.entries[2]), success(sites.remove(sites.entries[1].id)).favorites.entries)
        assertEquals(WebsiteResult.Failure(WebsiteError.NOT_FOUND), sites.remove("missing"))
        assertEquals(WebsiteResult.Failure(WebsiteError.NOT_FOUND), sites.move("missing", 1))
    }

    @Test fun exposedListCannotMutateCollection() {
        val sites = add(WebsiteFavorites.empty(), 1)
        try {
            (sites.entries as MutableList).clear()
            fail("Favorites must be immutable")
        } catch (_: UnsupportedOperationException) { }
        assertEquals(1, sites.entries.size)
    }
}
