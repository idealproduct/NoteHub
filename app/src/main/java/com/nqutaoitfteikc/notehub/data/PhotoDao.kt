package com.nqutaoitfteikc.notehub.data

import androidx.room.*
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "photos", foreignKeys = [ForeignKey(entity = NoteEntity::class,
    parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("noteId"), Index("sessionId"), Index(value = ["fileName"], unique = true)])
data class PhotoEntity(@PrimaryKey val id: String, val noteId: String?, val sessionId: String,
    val fileName: String, val capturedAt: Long, val rotationDegrees: Int, val zoneId: String,
    val timeSource: String, val caption: String)
fun PhotoEntity.toDomain() = Photo(id, noteId, sessionId, fileName, capturedAt, rotationDegrees, zoneId, CaptureTimeSource.valueOf(timeSource), caption)
@Entity(tableName = "photo_deletions")
data class PhotoDeletionEntity(@PrimaryKey val fileName: String)
@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY capturedAt, id") fun observe(): Flow<List<PhotoEntity>>
    @Query("SELECT * FROM photos") suspend fun all(): List<PhotoEntity>
    @Query("SELECT * FROM photos WHERE id = :id") suspend fun get(id: String): PhotoEntity?
    @Query("SELECT * FROM photos WHERE sessionId = :sessionId ORDER BY capturedAt, id") suspend fun session(sessionId: String): List<PhotoEntity>
    @Query("SELECT * FROM photos WHERE noteId = :id") suspend fun forNote(id: String): List<PhotoEntity>
    @Insert suspend fun insert(photo: PhotoEntity)
    @Query("UPDATE photos SET noteId = :noteId WHERE sessionId = :sessionId AND noteId IS NULL") suspend fun attach(sessionId: String, noteId: String)
    @Query("UPDATE photos SET caption = :caption WHERE id = :id") suspend fun caption(id: String, caption: String): Int
    @Query("DELETE FROM photos WHERE id = :id") suspend fun delete(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun enqueueDeletion(file: PhotoDeletionEntity)
    @Query("SELECT * FROM photo_deletions") suspend fun deletions(): List<PhotoDeletionEntity>
    @Query("SELECT COUNT(*) FROM photo_deletions") fun pendingCleanupCount(): Flow<Int>
    @Query("DELETE FROM photo_deletions WHERE fileName = :fileName") suspend fun finishDeletion(fileName: String)
}
