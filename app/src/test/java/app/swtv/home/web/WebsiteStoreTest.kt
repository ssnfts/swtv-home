package app.swtv.home.web

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WebsiteStoreTest {
    private class MemoryStorage(var value: String? = null) : WebsiteStringStorage {
        var writes = 0
        var failWrite = false
        var throwRead = false
        var throwWrite = false
        var readDelayMillis = 0L
        override fun read(): String? {
            if (throwRead) error("Read unavailable")
            if (readDelayMillis > 0) Thread.sleep(readDelayMillis)
            return value
        }
        override fun write(value: String): Boolean {
            writes++
            if (throwWrite) error("Write unavailable")
            if (failWrite) return false
            this.value = value
            return true
        }
    }
    private fun success(result: WebsiteResult) = result as WebsiteResult.Success
    private fun raw(entries: String, version: String = "1") = """{"schemaVersion":$version,"entries":[$entries]}"""
    private fun record(id: String = "one", title: String = "One", url: String = "https://one.example/") =
        """{"id":"$id","title":"$title","url":"$url"}"""

    @Test fun missingStoreIsEmptyAndCodecRoundTripRetainsIdsAndOrder() {
        val memory = MemoryStorage()
        val store = WebsiteStore(memory)
        assertTrue(success(store.snapshot()).favorites.entries.isEmpty())
        val a = success(store.save("One", "one.example")).favorite!!
        val b = success(store.save("Two", "two.example/path?q=%2B#part")).favorite!!
        assertEquals(2, memory.writes)
        val reopened = WebsiteStore(memory)
        assertEquals(listOf(a, b), success(reopened.snapshot()).favorites.entries)
        assertEquals(listOf(a, b), success(WebsiteCodec.decode(WebsiteCodec.encode(success(store.snapshot()).favorites))).favorites.entries)
    }

    @Test fun malformedOversizedAndDuplicateDocumentsAreUnreadable() {
        val badDocuments = listOf("", "{", "[]", "null", raw(record(), "\"1\""), "{}", raw("42"), raw(record(title = "")), raw(record(url = "http://one.example/")), raw(record(id = "bad id")), raw(record() + "," + record()), raw(record() + "," + record("two", "Two")), raw((1..33).joinToString(",") { record("id$it", "Site", "https://site$it.example/") }), " ".repeat(WebsiteCodec.MAX_DOCUMENT_LENGTH + 1), raw(record()) + "garbage")
        for (document in badDocuments) {
            assertEquals(document.take(100), WebsiteResult.Failure(WebsiteError.UNREADABLE_DATA), WebsiteCodec.decode(document))
        }
    }

    @Test fun unknownSchemaIsDistinctAndNeverOverwrittenByAnyMutation() {
        for ((document, expected) in listOf(raw(record(), "2") to WebsiteError.UNKNOWN_SCHEMA, "{broken" to WebsiteError.UNREADABLE_DATA)) {
            val memory = MemoryStorage(document)
            val store = WebsiteStore(memory)
            val failure = WebsiteResult.Failure(expected)
            assertEquals(failure, store.snapshot())
            assertEquals(failure, store.save("New", "new.example"))
            assertEquals(failure, store.remove("one"))
            assertEquals(failure, store.move("one", 1))
            assertEquals(document, memory.value)
            assertEquals(0, memory.writes)
        }
    }

    @Test fun failedWritesAndReadsAreReportedWithoutClaimingSuccess() {
        val memory = MemoryStorage()
        val store = WebsiteStore(memory)
        memory.failWrite = true
        assertEquals(WebsiteResult.Failure(WebsiteError.PERSISTENCE_FAILED), store.save("One", "one.example"))
        assertNull(memory.value)
        memory.failWrite = false
        memory.throwWrite = true
        assertEquals(WebsiteResult.Failure(WebsiteError.PERSISTENCE_FAILED), store.save("One", "one.example"))
        memory.throwRead = true
        assertEquals(WebsiteResult.Failure(WebsiteError.UNREADABLE_DATA), store.snapshot())
        assertEquals(WebsiteResult.Failure(WebsiteError.UNREADABLE_DATA), store.save("One", "one.example"))
    }

    @Test fun repositoriesRereadSharedStateAndNoOpsAvoidWrites() {
        val memory = MemoryStorage()
        val a = WebsiteStore(memory)
        val b = WebsiteStore(memory)
        val one = success(a.save("One", "one.example")).favorite!!
        val two = success(b.save("Two", "two.example")).favorite!!
        assertEquals(listOf(one, two), success(a.snapshot()).favorites.entries)
        success(a.save("Duplicate", "HTTPS://ONE.EXAMPLE:443/"))
        success(b.move(one.id, -1))
        assertEquals(2, memory.writes)
        assertEquals(listOf(two, one), success(b.move(one.id, 1)).favorites.entries)
        assertEquals(listOf(one), success(a.remove(two.id)).favorites.entries)
        assertEquals(WebsiteResult.Failure(WebsiteError.NOT_FOUND), b.remove(two.id))
    }

    @Test fun concurrentRepositoryInstancesDoNotLoseUpdates() {
        val memory = MemoryStorage()
        memory.readDelayMillis = 5
        val pool = Executors.newFixedThreadPool(6)
        try {
            val work = (1..24).map { n -> Callable { WebsiteStore(memory).save("Site $n", "site$n.example") } }
            val results = pool.invokeAll(work)
            results.forEach { assertTrue(it.get(10, TimeUnit.SECONDS) is WebsiteResult.Success) }
            assertEquals(24, success(WebsiteStore(memory).snapshot()).favorites.entries.size)
            assertEquals(24, memory.writes)
        } finally { pool.shutdownNow() }
    }

    @Test fun permissiveJsonExtensionsAreNotAcceptedAsSavedData() {
        for (document in listOf("{schemaVersion:1,entries:[]}", "{'schemaVersion':1,'entries':[]}", """{"schemaVersion":1,"entries":[],}""", """{"schemaVersion":1,"entries":[,]}""", """{"schemaVersion":1,"entries":[]/*comment*/}""")) {
            assertEquals(document, WebsiteResult.Failure(WebsiteError.UNREADABLE_DATA), WebsiteCodec.decode(document))
        }
    }
}
