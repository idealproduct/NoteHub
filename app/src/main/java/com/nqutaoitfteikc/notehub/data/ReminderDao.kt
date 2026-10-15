package com.nqutaoitfteikc.notehub.data

import androidx.room.*
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reminders", foreignKeys = [ForeignKey(entity = NoteEntity::class,
    parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("noteId"), Index("triggerAt")])
data class ReminderEntity(
    @PrimaryKey val id: String, val noteId: String, val triggerAt: Long, val revision: String,
    val enabled: Boolean, val deliveredAt: Long?, val status: String
)
fun ReminderEntity.toDomain() = Reminder(id, noteId, triggerAt, revision, enabled, deliveredAt, ReminderStatus.valueOf(status))
@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY triggerAt, id") fun observe(): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders ORDER BY triggerAt, id") suspend fun all(): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE noteId = :noteId") suspend fun forNote(noteId: String): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun get(id: String): ReminderEntity?
    @Insert suspend fun insert(reminder: ReminderEntity)
    @Update suspend fun update(reminder: ReminderEntity)
    @Query("DELETE FROM reminders WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE reminders SET status = :status WHERE id = :id AND status != :status") suspend fun status(id: String, status: String)
    @Query("UPDATE reminders SET deliveredAt = :now, status = 'DELIVERED' WHERE id = :id AND revision = :revision AND deliveredAt IS NULL AND enabled = 1 AND EXISTS(SELECT 1 FROM notes WHERE notes.id = reminders.noteId AND notes.completed = 0)")
    suspend fun claim(id: String, revision: String, now: Long): Int
    @Query("UPDATE reminders SET deliveredAt = NULL, status = :status WHERE id = :id AND revision = :revision") suspend fun unclaim(id: String, revision: String, status: String)
}
