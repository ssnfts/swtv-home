package app.swtv.home.platform

import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class WallpaperWorkRegressionTest {
    @Test fun slowDecodeCoalescesAndKeepsOnlyLatestReplacement() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val delivered = CountDownLatch(1)
        val decoded = Collections.synchronizedList(mutableListOf<String>())
        var oldCallbacks = 0
        var latest = ""
        val loader = LatestResource<String>({ key ->
            decoded.add(key)
            if (key == "slow") { entered.countDown(); release.await(3, TimeUnit.SECONDS) }
            key
        }, Executor { it.run() })
        try {
            loader.request("slow") { oldCallbacks++ }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            repeat(100) { n -> loader.request("replacement$n") { oldCallbacks++ } }
            loader.request("latest") { latest = it ?: ""; delivered.countDown() }
            assertTrue(loader.pendingCount() <= 1)
            release.countDown()
            assertTrue(delivered.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("slow", "latest"), decoded)
            assertEquals(0, oldCallbacks)
            assertEquals("latest", latest)
            var reused = ""
            loader.request("latest") { reused = it ?: "" }
            assertEquals("latest", reused)
            assertEquals(2, decoded.size)
        } finally { release.countDown(); loader.close() }
    }

    @Test fun sameUriInFlightUpdatesOnlyLatestCallback() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val complete = CountDownLatch(1)
        val decoded = Collections.synchronizedList(mutableListOf<String>())
        var obsolete = 0
        val loader = LatestResource<String>({ key ->
            decoded.add(key); entered.countDown(); release.await(3, TimeUnit.SECONDS); key
        }, Executor { it.run() })
        try {
            loader.request("same") { obsolete++ }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            repeat(100) { loader.request("same") { obsolete++ } }
            loader.request("same") { complete.countDown() }
            assertEquals(0, loader.pendingCount())
            release.countDown()
            assertTrue(complete.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("same"), decoded)
            assertEquals(0, obsolete)
            loader.cancelPending()
        } finally { release.countDown(); loader.close() }
    }
    @Test fun pauseCancellationDropsPendingWorkAndOldSubscriber() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val decodedOld = CountDownLatch(1)
        val discarded = CountDownLatch(1)
        val decoded = Collections.synchronizedList(mutableListOf<String>())
        var callbacks = 0
        val loader = LatestResource<String>({ key ->
            decoded.add(key)
            entered.countDown()
            release.await(3, TimeUnit.SECONDS)
            decodedOld.countDown()
            key
        }, Executor { it.run() }, { discarded.countDown() })
        try {
            loader.request("slow") { callbacks++ }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            loader.request("queued") { callbacks++ }
            loader.cancelPending()
            assertEquals(0, loader.pendingCount())
            release.countDown()
            assertTrue(decodedOld.await(3, TimeUnit.SECONDS))
            assertTrue(discarded.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("slow"), decoded)
            assertEquals(0, callbacks)
        } finally { release.countDown(); loader.close() }
    }
}
