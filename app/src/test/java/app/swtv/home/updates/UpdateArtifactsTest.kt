package app.swtv.home.updates

import app.swtv.updates.UpdateCancellation
import app.swtv.updates.UpdateException
import java.io.Closeable
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class UpdateArtifactsTest {
    private class Artifact : Closeable {
        val file = Files.createTempFile("staging-regression", ".apk").toFile()
        override fun close() { file.delete() }
    }

    @Test fun pauseDuringStagingRequiresFreshDownloadAndStaleWorkerCannotConsumeReplacement() {
        val artifacts = UpdateArtifacts<Artifact>()
        val consent = InstallConsent()
        val token = UpdateCancellation()
        val update = Artifact()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val ended = CountDownLatch(1)
        artifacts.replace(update)
        UpdateWorker().use { worker ->
            try {
                assertTrue(worker.submit {
                    try { artifacts.claim(update, token::check)?.use {
                        started.countDown(); release.await(5, TimeUnit.SECONDS)
                        token.check()
                    } } catch (_: UpdateException) { } finally { ended.countDown() }
                })
                assertTrue(started.await(5, TimeUnit.SECONDS))
                // Actual pause policy cancels first, then reads the artifact slot to choose retry UI.
                consent.foreground = false; token.cancel()
                assertNull("Staging owns the bytes; paused UI must offer Download instead of Install", artifacts.peek())
                consent.foreground = true
                assertFalse("Resuming cannot install a worker-owned artifact", consent.canStart(artifacts.peek() != null, true))
                Artifact().use { newer ->
                    artifacts.replace(newer)
                    release.countDown()
                    assertTrue(ended.await(5, TimeUnit.SECONDS))
                    assertFalse("Cancelled staging releases its private file", update.file.exists())
                    assertSame("Stale worker cleanup cannot remove a newer artifact", newer, artifacts.peek())
                    assertTrue(newer.file.exists())
                }
            } finally { release.countDown(); artifacts.close(); update.close() }
        }
    }

    @Test fun permissionSettingsPauseBeforeStagingRetainsLiveInstallArtifact() {
        UpdateArtifacts<Artifact>().use { artifacts ->
            val update = Artifact(); artifacts.replace(update)
            val token = UpdateCancellation(); token.cancel()
            assertThrows(UpdateException::class.java) { artifacts.claim(update, token::check) }
            assertSame(update, artifacts.peek())
            assertTrue(update.file.exists())
        }
    }
}
