package app.swtv.home.updates

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class UpdateWorkerTest {
    @Test fun boundsPendingWorkAndDropsObsoleteRequests() {
        val began = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val obsoleteRan = AtomicBoolean()
        UpdateWorker().use { worker ->
            assertTrue(worker.submit { began.countDown(); release.await(5, TimeUnit.SECONDS) })
            assertTrue(began.await(5, TimeUnit.SECONDS))
            assertTrue(worker.submit { obsoleteRan.set(true) })
            try {
                assertFalse("Only one pending operation is allowed", worker.submit { obsoleteRan.set(true) })
                worker.cancelQueued()
                assertTrue(worker.submit { finished.countDown() })
            } finally { release.countDown() }
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertFalse("Cancelled queued work must never run", obsoleteRan.get())
        }
    }
    @Test fun closedWorkerRejectsNewOperations() {
        val worker = UpdateWorker()
        worker.close()
        assertFalse(worker.submit { fail("Closed flow cannot run new work") })
    }
}
