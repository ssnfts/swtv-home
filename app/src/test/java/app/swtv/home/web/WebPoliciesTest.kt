package app.swtv.home.web

import org.junit.Assert.*
import org.junit.Test

class WebPoliciesTest {
    @Test fun shareAcceptsOneAddressWithoutSavingAnything() {
        assertEquals("https://example.com/", ShareAddress.parse("example.com"))
        assertEquals("https://example.com/a?q=1", ShareAddress.parse("A page\nhttps://example.com/a?q=1"))
        assertNull(ShareAddress.parse("https://example.com https://other.example"))
        assertNull(ShareAddress.parse("https://example.com http://other.example"))
        assertNull(ShareAddress.parse("javascript:alert(1)"))
        assertNull(ShareAddress.parse("x".repeat(8193)))
        assertNull(ShareAddress.parse(null))
    }
    @Test fun navigationRequiresExplicitHttpsAndValidatedAuthority() {
        assertNull(WebNavigation.address("example.com"))
        assertNull(WebNavigation.address("https://user@example.com"))
        assertNull(WebNavigation.address("intent://example.com"))
        assertEquals("example.com", WebNavigation.address("https://example.com/a")?.host)
    }
    @Test fun pointerClampsAndEscapesAtUpperBoundary() {
        val pointer = PointerPosition(10, 10)
        assertEquals(PointerPosition(0, 0), pointer.move(-100, -100, 80, 60))
        assertEquals(PointerPosition(79, 59), pointer.move(1000, 1000, 80, 60))
        assertEquals(PointerPosition(0, 0), pointer.move(10, 10, 0, 0))
        assertTrue(PointerPosition(5, 0).escapesUp())
        assertFalse(pointer.escapesUp())
    }
    @Test fun fullscreenBackPrecedesHistoryAndFinalBackCloses() {
        assertEquals(WebBack.EXIT_FULLSCREEN, WebBack.decide(true, true))
        assertEquals(WebBack.HISTORY, WebBack.decide(false, true))
        assertEquals(WebBack.CLOSE, WebBack.decide(false, false))
    }
    @Test fun pausedAndReplacedRequestsNeverUpdateTheViewer() {
        val session = WebSession()
        assertFalse(session.foreground)
        session.resume()
        val first = session.nextRequest()
        assertTrue(session.accepts(first))
        val second = session.nextRequest()
        assertFalse(session.accepts(first))
        session.pause()
        assertFalse(session.accepts(second))
        session.resume()
        assertFalse(session.accepts(second))
        val third = session.nextRequest()
        session.close()
        session.resume()
        assertFalse(session.foreground)
        assertFalse(session.accepts(third))
    }
}
