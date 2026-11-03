package com.nqutaoitfteikc.notehub

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.calendar.*
import com.nqutaoitfteikc.notehub.data.*
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Offline transport fixture: these assertions do not certify the real Google service. */
class CalendarSyncTest {
    private val config = CalendarSyncSettings(true, "fixture-account", GoogleCalendar("calendar", "測試日曆", "UTC", "owner"))
    private class Remote : CalendarSyncRemote {
        val events = linkedMapOf<String, JSONObject>()
        var inserts = 0; var updates = 0; var deletes = 0
        var rejectUpdate = false; var failAfterCreate = false
        private fun copy(value: JSONObject) = JSONObject(value.toString())
        override suspend fun list(calendar: GoogleCalendar, token: String) = events.values.map(::copy)
        override suspend fun get(calendar: String, id: String, token: String) = events[id]?.let(::copy)
        override suspend fun create(calendar: String, id: String, note: Note, token: String): JSONObject {
            if (events.containsKey(id)) throw CalendarApiException(409, "測試重複識別")
            inserts++
            val event = CalendarSyncProjection.body(note).put("id", id).put("etag", "\"$inserts\"")
            events[id] = copy(event)
            if (failAfterCreate) { failAfterCreate = false; throw java.io.IOException("測試回應遺失") }
            return event
        }
        override suspend fun update(calendar: String, remote: JSONObject, note: Note, token: String): JSONObject {
            if (rejectUpdate) throw CalendarApiException(412, "測試版本衝突")
            updates++
            val event = CalendarSyncProjection.body(note).put("id", remote.getString("id")).put("etag", "\"updated-$updates\"")
            events[event.getString("id")] = copy(event)
            return event
        }
        override suspend fun delete(calendar: String, remote: JSONObject, token: String) { deletes++; events.remove(remote.getString("id")) }
    }
    private suspend fun fixture(block: suspend (NoteDatabase, CalendarSyncEngine, Remote) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java).build()
        val remote = Remote()
        val effects = object : NoteEffects {
            override suspend fun change(noteId: String, action: suspend () -> Unit) = action()
            override suspend fun delete(noteId: String, action: suspend () -> Unit) = action()
        }
        try { block(db, CalendarSyncEngine(db, effects, remote), remote) } finally { db.close() }
    }
    private fun local(id: String = "local") = NoteEntity(id, "原始標題", 1000, kind = "TIMED", startMillis = 100000, endMillis = 200000, zoneId = "UTC")
    private fun event(id: String, title: String = "Google 原始") = JSONObject()
        .put("id", id).put("summary", title).put("etag", "\"v1\"")
        .put("start", JSONObject().put("dateTime", "2026-10-22T09:00:00Z"))
        .put("end", JSONObject().put("dateTime", "2026-10-22T10:00:00Z"))

    @Test fun twoWayCreateUpdateAndRepeatAreIdempotent() = runBlocking { fixture { db, engine, remote ->
        db.notes().insert(local())
        remote.events["remote"] = event("remote")
        assertEquals(0, engine.run(config, "fixture"))
        assertEquals(2, db.notes().observe().first().size)
        assertEquals(2, remote.events.size)
        engine.run(config, "fixture")
        assertEquals(1, remote.inserts)
        db.notes().update(local().copy(title = "本機修改"))
        engine.run(config, "fixture")
        assertEquals(1, remote.updates)
        remote.events["remote"]!!.put("summary", "Google 修改")
        engine.run(config, "fixture")
        assertTrue(db.notes().observe().first().any { it.title == "Google 修改" })
        engine.run(config, "fixture")
        assertEquals(2, remote.events.size); assertEquals(1, remote.updates)
    } }
    @Test fun simultaneousChangesAndEtagFailureNeverOverwriteSilently() = runBlocking { fixture { db, engine, remote ->
        db.notes().insert(local()); engine.run(config, "fixture")
        val id = remote.events.keys.single()
        db.notes().update(local().copy(title = "本機新標題"))
        remote.events[id]!!.put("summary", "Google 新標題")
        assertEquals(1, engine.run(config, "fixture"))
        assertEquals("Google 新標題", remote.events[id]!!.getString("summary"))
        engine.resolve(config, "fixture", id, true)
        assertEquals("Google 新標題", db.notes().get("local")!!.title)
        db.notes().update(db.notes().get("local")!!.copy(title = "再次修改"))
        remote.rejectUpdate = true
        assertEquals(1, engine.run(config, "fixture"))
        assertEquals("Google 新標題", remote.events[id]!!.getString("summary"))
    } }
    @Test fun deletionsRequireExplicitResolutionAndRestorationDoesNotDuplicate() = runBlocking { fixture { db, engine, remote ->
        db.notes().insert(local()); engine.run(config, "fixture")
        val originalId = remote.events.keys.single()
        remote.events.remove(originalId)
        assertEquals(1, engine.run(config, "fixture"))
        assertNotNull(db.notes().get("local"))
        engine.resolve(config, "fixture", originalId, false)
        assertEquals(1, remote.events.size)
        assertEquals(0, engine.run(config, "fixture"))
        assertEquals(1, db.notes().observe().first().size)
        db.notes().delete("local")
        assertEquals(1, engine.run(config, "fixture"))
        assertEquals(1, remote.events.size)
        engine.resolve(config, "fixture", remote.events.keys.single(), false)
        assertTrue(remote.events.isEmpty()); assertEquals(1, remote.deletes)
        assertEquals(0, engine.run(config, "fixture"))
    } }
    @Test fun lostCreateResponseRecoversWithoutDuplicatingLocalOrGoogleEvents() = runBlocking { fixture { db, engine, remote ->
        db.notes().insert(local())
        remote.failAfterCreate = true
        try { engine.run(config, "fixture"); fail("應回報測試網路失敗") } catch (_: java.io.IOException) { }
        assertEquals(1, remote.events.size)
        assertEquals(0, engine.run(config, "fixture"))
        assertEquals(1, remote.inserts); assertEquals(1, db.notes().observe().first().size)
    } }
    @Test fun lostRestoreResponseKeepsPendingIdentityAndDoesNotCreateAnotherLocalCopy() = runBlocking { fixture { db, engine, remote ->
        db.notes().insert(local()); engine.run(config, "fixture")
        val originalId = remote.events.keys.single()
        remote.events.clear(); engine.run(config, "fixture")
        remote.failAfterCreate = true
        try { engine.resolve(config, "fixture", originalId, false); fail("應回報測試回應遺失") }
        catch (_: java.io.IOException) { }
        engine.run(config, "fixture")
        assertEquals(1, db.notes().observe().first().size)
        engine.resolve(config, "fixture", originalId, false)
        assertEquals(0, engine.run(config, "fixture"))
        assertEquals(1, remote.events.size)
        assertEquals(2, remote.inserts)
    } }
    @Test fun allDayPhotoTimeAndUndatedTodoRetainTheirSemantics() = runBlocking { fixture { db, engine, remote ->
        db.notes().insert(local("photo").copy(startMillis = 100123, endMillis = 100124, photoCapturedAt = 100123))
        db.notes().insert(NoteEntity("todo", "沒有日期", 1))
        remote.events["allDay"] = JSONObject().put("id", "allDay").put("etag", "v1").put("summary", "跨日全天")
            .put("start", JSONObject().put("date", "2026-10-22")).put("end", JSONObject().put("date", "2026-10-25"))
        engine.run(config, "fixture")
        assertEquals(1, remote.events.size)
        val photoId = CalendarSyncProjection.eventId(config.account, config.calendar.id, "photo")
        assertFalse(remote.events.containsKey(photoId))
        // Pre-existing cloud copies are left untouched, not imported as a second local photo.
        remote.events[photoId]=event(photoId,"既有雲端副本")
        engine.run(config,"fixture")
        assertEquals(100123L,db.notes().get("photo")!!.photoCapturedAt)
        assertEquals("原始標題",db.notes().get("photo")!!.title)
        assertEquals(0,remote.inserts); assertEquals(0,remote.updates); assertEquals(0,remote.deletes)
        assertEquals(0, engine.run(config, "fixture"))
        val allDay = db.notes().observe().first().single { it.title == "跨日全天" }
        assertEquals(3L, requireNotNull(allDay.endDayExclusive) - requireNotNull(allDay.startDay))
    } }
}
