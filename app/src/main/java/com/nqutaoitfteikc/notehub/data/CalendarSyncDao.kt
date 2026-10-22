package com.nqutaoitfteikc.notehub.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// No cascading FK: a missing note is a deletion that must be reviewed before touching Google.
@Entity(tableName = "calendar_sync", primaryKeys = ["account", "calendarId", "eventId"])
data class CalendarSyncLink(val account: String, val calendarId: String, val eventId: String,
    val noteId: String, val localHash: String, val remoteHash: String, val conflict: String = "")

@Dao
interface CalendarSyncDao {
    @Query("SELECT * FROM calendar_sync WHERE account = :account AND calendarId = :calendarId")
    suspend fun links(account: String, calendarId: String): List<CalendarSyncLink>
    @Query("SELECT * FROM calendar_sync WHERE conflict != ''") fun conflicts(): Flow<List<CalendarSyncLink>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(link: CalendarSyncLink)
}
