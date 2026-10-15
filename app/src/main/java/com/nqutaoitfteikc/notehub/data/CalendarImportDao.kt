package com.nqutaoitfteikc.notehub.data

import androidx.room.*
import com.nqutaoitfteikc.notehub.domain.*
import java.util.UUID

// Keep source keys to distinguish missing local copies; only an explicit selection can recreate one.
@Entity(tableName = "calendar_imports", primaryKeys = ["account", "calendarId", "instanceKey"])
data class CalendarImportEntity(val account: String, val calendarId: String, val instanceKey: String, val eventId: String, val noteId: String, val importedAt: Long)
data class CalendarImportStatus(val calendarId: String, val instanceKey: String, val noteExists: Boolean)
@Dao
interface CalendarImportDao {
    @Query("SELECT * FROM calendar_imports WHERE account = :account") suspend fun forAccount(account: String): List<CalendarImportEntity>
    @Query("SELECT * FROM calendar_imports WHERE account = :account AND calendarId = :calendarId AND instanceKey = :instanceKey") suspend fun get(account: String, calendarId: String, instanceKey: String): CalendarImportEntity?
    @Query("SELECT i.calendarId, i.instanceKey, (n.id IS NOT NULL) AS noteExists FROM calendar_imports i LEFT JOIN notes n ON n.id = i.noteId WHERE i.account = :account") suspend fun statuses(account: String): List<CalendarImportStatus>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(source: CalendarImportEntity): Long
    @Update suspend fun update(source: CalendarImportEntity): Int
}
class RoomCalendarImportRepository(private val database: NoteDatabase) : CalendarImportRepository {
    override suspend fun markExisting(account: String, events: List<CalendarImportEvent>): List<CalendarImportEvent> {
        val sources = database.calendarImports().statuses(account).associateBy { it.calendarId to it.instanceKey }
        return events.map {
            val source = sources[it.calendarId to it.instanceKey]
            it.copy(alreadyImported = source != null, locallyDeleted = source?.noteExists == false)
        }
    }
    override suspend fun commit(account: String, events: List<CalendarImportEvent>): ImportResult {
        require(account.isNotBlank()) { "請重新登入 Google" }
        val drafts = events.map { it to it.draft.validated() }
        return database.withTransaction {
            var inserted = 0
            var restored = 0
            drafts.forEach { (event, draft) ->
                val id = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val source = CalendarImportEntity(account, event.calendarId, event.instanceKey, event.eventId, id, now)
                val isNew = database.calendarImports().insert(source) != -1L
                val restore = if (!isNew && event.locallyDeleted) {
                    val previous = database.calendarImports().get(account, event.calendarId, event.instanceKey)
                    if (previous != null && database.notes().get(previous.noteId) == null) {
                        check(database.calendarImports().update(source) == 1) { "來源狀態已變更，請重新讀取預覽" }
                        true
                    } else false
                } else false
                if (isNew || restore) {
                    database.notes().insert(Note(id, draft.title, now, draft.description, draft.category, false,
                        draft.kind, draft.startDay, draft.endDayExclusive, draft.startMillis, draft.endMillis, draft.zoneId).toEntity())
                    if (isNew) inserted++ else restored++
                }
            }
            ImportResult(inserted, events.size - inserted - restored, restored)
        }
    }
}
