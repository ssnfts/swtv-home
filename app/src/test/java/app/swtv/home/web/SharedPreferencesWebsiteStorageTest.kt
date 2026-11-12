package app.swtv.home.web

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

class SharedPreferencesWebsiteStorageTest {
    /** Models the relevant Android behavior: equal cached puts can return true without a disk write. */
    private class CachedPreferences : SharedPreferences {
        val readable = mutableMapOf<String, Any?>()
        val durable = mutableMapOf<String, Any?>()
        var failDisk = false
        var diskAttempts = 0
        override fun getAll(): MutableMap<String, *> = readable.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = readable[key] as? String ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = readable[key] as? Boolean ?: defValue
        override fun contains(key: String) = readable.containsKey(key)
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = error("Unused")
        override fun getInt(key: String?, defValue: Int): Int = error("Unused")
        override fun getLong(key: String?, defValue: Long): Long = error("Unused")
        override fun getFloat(key: String?, defValue: Float): Float = error("Unused")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val updates = mutableMapOf<String, Any?>()
            override fun putString(key: String, value: String?) = apply { updates[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { updates[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = error("Unused")
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = error("Unused")
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = error("Unused")
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = error("Unused")
            override fun remove(key: String?): SharedPreferences.Editor = error("Unused")
            override fun clear(): SharedPreferences.Editor = error("Unused")
            override fun apply(): Unit = error("Synchronous commit required")
            override fun commit(): Boolean {
                val changed = updates.any { (key, value) -> !readable.containsKey(key) || readable[key] != value }
                readable.putAll(updates)
                if (!changed) return true // No disk operation; not a durability confirmation.
                diskAttempts++
                if (failDisk) return false
                durable.clear()
                durable.putAll(readable)
                return true
            }
        }
    }

    @Test fun equalCachedValueRetryForcesActualDiskWrite() {
        val preferences = CachedPreferences()
        val a = SharedPreferencesWebsiteStorage(preferences)
        val b = SharedPreferencesWebsiteStorage(preferences)
        preferences.failDisk = true
        assertFalse(a.write("document"))
        assertEquals("document", preferences.readable["favorites"])
        assertNull(preferences.durable["favorites"])
        preferences.failDisk = false
        assertTrue(b.write("document"))
        assertEquals(2, preferences.diskAttempts)
        assertEquals("document", preferences.durable["favorites"])
    }

    @Test fun failedEditRetainsConfirmedSnapshotAndRetriesAcrossNewAdapters() {
        val preferences = CachedPreferences()
        val first = WebsiteStore(SharedPreferencesWebsiteStorage(preferences))
        val saved = first.save("One", "one.example") as WebsiteResult.Success
        val id = saved.favorite!!.id
        preferences.failDisk = true
        val failure = WebsiteResult.Failure(WebsiteError.PERSISTENCE_FAILED)
        assertEquals(failure, first.save("Renamed", "one.example", id))
        val second = WebsiteStore(SharedPreferencesWebsiteStorage(preferences))
        assertEquals(failure, second.save("Renamed", "one.example", id))
        assertEquals("One", (second.snapshot() as WebsiteResult.Success).favorites.entries.single().title)
        preferences.failDisk = false
        val retried = second.save("Renamed", "one.example", id) as WebsiteResult.Success
        assertEquals(id, retried.favorite!!.id)
        assertEquals("Renamed", (WebsiteCodec.decode(preferences.durable["favorites"] as String) as WebsiteResult.Success).favorites.entries.single().title)
        assertEquals(4, preferences.diskAttempts)
        assertFalse((first.save("Renamed", "one.example", id) as WebsiteResult.Success).changed)
        assertEquals(4, preferences.diskAttempts)
    }
}
