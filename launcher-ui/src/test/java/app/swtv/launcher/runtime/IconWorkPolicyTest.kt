package app.swtv.launcher.runtime

import org.junit.Assert.*
import org.junit.Test

class IconWorkPolicyTest {
    @Test fun iconWorkIsBoundedDeduplicatedAndGenerationSafe() {
        val work = IconWorkPolicy(49)
        val first = work.beginGeneration()
        val tickets = (0 until 120).mapNotNull { work.reserve("pkg.$it", first) }
        assertEquals(49, tickets.size)
        assertEquals(49, work.pendingCount())
        assertNull(work.reserve("pkg.0", first))
        repeat(100) {
            val current = work.beginGeneration()
            assertEquals(0, work.pendingCount())
            assertFalse(work.isCurrent(tickets.first()))
            assertNull(work.reserve("old", first))
            val replacement = work.reserve("pkg.0", current)!!
            work.finish(tickets.first())
            assertTrue(work.isCurrent(replacement))
            assertEquals(1, work.pendingCount())
        }
    }
}
