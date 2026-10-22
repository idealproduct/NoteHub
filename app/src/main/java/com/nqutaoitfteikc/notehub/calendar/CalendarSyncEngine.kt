package com.nqutaoitfteikc.notehub.calendar

import androidx.room.withTransaction
import com.nqutaoitfteikc.notehub.data.*
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

/** The ledger keeps two baselines: Google seconds and immutable local photo milliseconds can differ. */
class CalendarSyncEngine(private val database: NoteDatabase, private val effects: NoteEffects,
    private val remote: CalendarSyncRemote = GoogleCalendarSyncApi()) {
    val mutex = Mutex()
    private val dao get() = database.calendarSync()
    private fun localHash(note: NoteEntity?) = note?.let { CalendarSyncProjection.hash(it.toDomain()) } ?: "-"
    private fun remoteHash(settings: CalendarSyncSettings, event: JSONObject?) = event?.let {
        CalendarSyncProjection.fingerprint(CalendarSyncProjection.draft(settings.calendar, it))
    } ?: "-"

    // Caller holds mutex, including configuration changes and manual conflict resolutions.
    suspend fun run(settings: CalendarSyncSettings, token: String): Int {
        val calendar = settings.calendar
        val links = dao.links(settings.account, calendar.id).associateBy { it.eventId }.toMutableMap()
        val sources = database.calendarImports().forAccount(settings.account).filter { it.calendarId == calendar.id }.associateBy { it.eventId }
        val importedBefore = database.calendarImports().allImportedNoteIds().toSet()
        val natives = database.notes().observe().first().filter { it.id !in importedBefore && it.kind != ScheduleKind.TODO.name }
            .associateBy { CalendarSyncProjection.eventId(settings.account, calendar.id, it.id) }
        val events = remote.list(calendar, token).associateBy { it.getString("id") }
        for ((id, body) in events) {
            if (id in links || body.optString("status") == "cancelled") continue
            val source = sources[id]
            val native = natives[id]
            if (source != null || native != null) {
                val noteId = source?.noteId ?: requireNotNull(native).id
                val existing = database.notes().get(noteId)
                val lh = localHash(existing); val rh = remoteHash(settings, body)
                val link = CalendarSyncLink(settings.account, calendar.id, id, noteId, lh, rh,
                    if (lh == rh) "" else "首次連結的本機副本與 Google 不同，請選擇保留版本")
                dao.put(link); links[id] = link
            } else {
                val draft = CalendarSyncProjection.draft(calendar, body)
                // Reuse the import identity transaction so manual imports and sync never create duplicate copies.
                val parsed = CalendarEventParser.parse(calendar, body).copy(draft = draft)
                RoomCalendarImportRepository(database).commit(settings.account, listOf(parsed))
                val imported = requireNotNull(database.calendarImports().get(settings.account, calendar.id, parsed.instanceKey))
                val note = database.notes().get(imported.noteId)
                val lh = localHash(note); val rh = remoteHash(settings, body)
                val link = CalendarSyncLink(settings.account, calendar.id, id, imported.noteId, lh, rh,
                    if (note == null) "本機副本先前已刪除，請選擇是否恢復" else if (lh != rh) "本機副本與 Google 不同，請選擇版本" else "")
                dao.put(link); links[id] = link
            }
        }
        for (link in links.values) {
            val event = if (events.containsKey(link.eventId)) events[link.eventId]?.takeUnless { it.optString("status") == "cancelled" }
                else remote.get(calendar.id, link.eventId, token)
            reconcile(settings, token, link, event)
        }
        val importedIds = database.calendarImports().allImportedNoteIds().toSet()
        val linkedIds = links.values.map { it.noteId }.toSet()
        val locals = database.notes().observe().first().filter { it.id !in importedIds && it.id !in linkedIds && it.kind != ScheduleKind.TODO.name }
        for (note in locals) {
            val id = CalendarSyncProjection.eventId(settings.account, calendar.id, note.id)
            val body = try { remote.create(calendar.id, id, note.toDomain(), token) }
            catch (e: CalendarApiException) {
                if (e.status != 409) throw e
                remote.get(calendar.id, id, token) ?: throw e
            }
            val lh = localHash(note); val rh = remoteHash(settings, body)
            dao.put(CalendarSyncLink(settings.account, calendar.id, id, note.id, lh, rh,
                if (lh == rh) "" else "Google 已有相同識別的事件但內容不同，請選擇版本"))
        }
        return dao.links(settings.account, calendar.id).count { it.conflict.isNotEmpty() }
    }
    private suspend fun reconcile(settings: CalendarSyncSettings, token: String, link: CalendarSyncLink, event: JSONObject?) {
        if (link.localHash == "retired") return
        if (link.conflict.isNotEmpty()) return
        val note = database.notes().get(link.noteId)
        val lh = localHash(note); val rh = remoteHash(settings, event)
        if (lh == link.localHash && rh == link.remoteHash) return
        if (lh == "-" || rh == "-") {
            dao.put(link.copy(conflict = "其中一邊已刪除，請確認要刪除另一邊或恢復副本")); return
        }
        if (lh == rh) { dao.put(link.copy(localHash = lh, remoteHash = rh)); return }
        if (lh != link.localHash && rh != link.remoteHash) {
            dao.put(link.copy(conflict = "本機與 Google 都有修改，尚未覆蓋任何一邊")); return
        }
        if (lh != link.localHash) upload(settings, token, link, requireNotNull(note), requireNotNull(event))
        else download(settings, link, note, event)
    }
    private suspend fun upload(settings: CalendarSyncSettings, token: String, link: CalendarSyncLink, note: NoteEntity, event: JSONObject) {
        if (note.kind == ScheduleKind.TODO.name) {
            dao.put(link.copy(conflict = "本機改為無日期待辦，Google 無法表示此類事項")); return
        }
        try {
            val updated = remote.update(settings.calendar.id, event, note.toDomain(), token)
            dao.put(link.copy(localHash = localHash(note), remoteHash = remoteHash(settings, updated), conflict = ""))
        } catch (e: CalendarApiException) {
            if (e.status != 412) throw e
            dao.put(link.copy(conflict = "Google 在同步期間變更，請重新選擇版本"))
        }
    }
    private suspend fun download(settings: CalendarSyncSettings, link: CalendarSyncLink, expected: NoteEntity?, event: JSONObject?) {
        val draft = event?.let { CalendarSyncProjection.draft(settings.calendar, it) }
        val action: suspend () -> Unit = {
            database.withTransaction {
                check(database.notes().get(link.noteId) == expected) { "本機事項在同步期間變更，請重試" }
                val updated = when {
                    draft == null -> null
                    expected == null -> NoteEntity(link.noteId, draft.title, System.currentTimeMillis(), draft.description,
                        draft.category, kind = draft.kind.name, startDay = draft.startDay, endDayExclusive = draft.endDayExclusive,
                        startMillis = draft.startMillis, endMillis = draft.endMillis, zoneId = draft.zoneId)
                    expected.photoCapturedAt != null -> expected.copy(title = draft.title, description = draft.description)
                    else -> expected.copy(title = draft.title, description = draft.description, kind = draft.kind.name,
                        startDay = draft.startDay, endDayExclusive = draft.endDayExclusive, startMillis = draft.startMillis,
                        endMillis = draft.endMillis, zoneId = draft.zoneId)
                }
                if (updated == null) database.notes().delete(link.noteId)
                else if (expected == null) database.notes().insert(updated) else check(database.notes().update(updated) == 1)
                dao.put(link.copy(localHash = localHash(updated), remoteHash = remoteHash(settings, event), conflict = ""))
            }
        }
        if (draft == null) effects.delete(link.noteId, action) else effects.change(link.noteId, action)
    }
    suspend fun resolve(settings: CalendarSyncSettings, token: String, eventId: String, useGoogle: Boolean) {
        val link = dao.links(settings.account, settings.calendar.id).single { it.eventId == eventId }
        val note = database.notes().get(link.noteId)
        val event = remote.get(settings.calendar.id, eventId, token)
        if (useGoogle) download(settings, link, note, event)
        else if (note == null) {
            if (event != null) remote.delete(settings.calendar.id, event, token)
            dao.put(link.copy(localHash = "-", remoteHash = "-", conflict = ""))
        } else if (event != null) upload(settings, token, link, note, event)
        else {
            require(note.kind != ScheduleKind.TODO.name) { "請先為本機事項指定日期" }
            // Deleted Google IDs cannot be reused. Link a fresh deterministic generation before retrying.
            val id = if (link.remoteHash == "pending") link.eventId else CalendarSyncProjection.eventId(settings.account, settings.calendar.id, note.id + link.eventId)
            dao.put(link.copy(eventId = id, localHash = localHash(note), remoteHash = "pending", conflict = "恢復尚未完成，請採用本機重試或採用 Google 目前狀態"))
            val restored = try { remote.create(settings.calendar.id, id, note.toDomain(), token) }
            catch (e: CalendarApiException) { if (e.status == 409) remote.get(settings.calendar.id, id, token) ?: throw e else throw e }
            database.withTransaction {
                if (link.eventId != id) dao.put(link.copy(localHash = "retired", remoteHash = "-", conflict = ""))
                val lh = localHash(note); val rh = remoteHash(settings, restored)
                dao.put(link.copy(eventId = id, localHash = lh, remoteHash = rh,
                    conflict = if (lh == rh) "" else "恢復的 Google 事件內容已變更，請選擇保留版本"))
            }
        }
    }
}
