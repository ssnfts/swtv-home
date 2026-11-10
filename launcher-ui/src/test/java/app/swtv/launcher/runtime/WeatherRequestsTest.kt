package app.swtv.launcher.runtime

import org.junit.Assert.*
import org.junit.Test

class WeatherRequestsTest {
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
}
