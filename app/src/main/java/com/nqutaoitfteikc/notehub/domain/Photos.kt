package com.nqutaoitfteikc.notehub.domain

import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

enum class CaptureTimeSource { SENSOR_REALTIME, CAPTURE_STARTED }
data class CaptureClock(val wallMillis: Long, val elapsedNanos: Long)
data class CaptureTime(val millis: Long, val source: CaptureTimeSource)
object CaptureTimeRules {
    fun resolve(clock: CaptureClock, sensorNanos: Long, sensorUsesRealtime: Boolean): CaptureTime {
        val delta = sensorNanos - clock.elapsedNanos
        return if (sensorUsesRealtime && sensorNanos > 0 && delta in -60_000_000_000L..60_000_000_000L)
            CaptureTime(clock.wallMillis + delta / 1_000_000, CaptureTimeSource.SENSOR_REALTIME)
        else CaptureTime(clock.wallMillis, CaptureTimeSource.CAPTURE_STARTED)
    }
}
data class Photo(
    val id: String, val noteId: String?, val sessionId: String, val fileName: String,
    val capturedAt: Long, val rotationDegrees: Int, val zoneId: String,
    val timeSource: CaptureTimeSource, val caption: String = ""
)
data class CapturedImage(val bytes: ByteArray, val time: CaptureTime, val rotationDegrees: Int, val zoneId: String)
interface PhotoRepository {
    fun observe(): Flow<List<Photo>>
    fun pendingCleanupCount(): Flow<Int>
    suspend fun store(sessionId: String, capture: CapturedImage): Photo
    suspend fun commit(sessionId: String, targetNoteId: String?, title: String, description: String, category: String): String
    suspend fun remove(id: String)
    suspend fun discard(sessionId: String)
    suspend fun caption(id: String, value: String)
    suspend fun recoverFiles()
}
class ObservePhotos(private val repository: PhotoRepository) { operator fun invoke() = repository.observe() }
class StorePhoto(private val repository: PhotoRepository) {
    suspend operator fun invoke(sessionId: String, capture: CapturedImage) = repository.store(sessionId, capture)
}
class SavePhotoNote(private val repository: PhotoRepository, private val effects: NoteEffects? = null) {
    suspend operator fun invoke(sessionId: String, targetNoteId: String?, title: String, description: String, category: String): String {
        val valid = NoteDraft(title, description, category).validated()
        if (targetNoteId == null || effects == null) return repository.commit(sessionId, targetNoteId, valid.title, valid.description, valid.category)
        var result: String? = null
        effects.change(targetNoteId) { result = repository.commit(sessionId, targetNoteId, valid.title, valid.description, valid.category) }
        return requireNotNull(result)
    }
}
class RemovePhoto(private val repository: PhotoRepository) { suspend operator fun invoke(id: String) = repository.remove(id) }
class DiscardPhotoDraft(private val repository: PhotoRepository) { suspend operator fun invoke(sessionId: String) = repository.discard(sessionId) }
class SetPhotoCaption(private val repository: PhotoRepository) {
    suspend operator fun invoke(id: String, caption: String) {
        require(caption.length <= 2000) { "照片說明最多 2000 個字元" }
        repository.caption(id, caption.trim())
    }
}
object PhotoNoteRules {
    fun create(id: String, title: String, description: String, category: String, photos: List<Photo>): Note {
        val earliest = requireNotNull(photos.minByOrNull { it.capturedAt }) { "請至少拍攝一張照片" }
        require(earliest.capturedAt > 0 && earliest.capturedAt < Long.MAX_VALUE) { "拍攝時間不正確" }
        ZoneId.of(earliest.zoneId)
        return Note(id, title, earliest.capturedAt, description, category, kind = ScheduleKind.TIMED,
            startMillis = earliest.capturedAt, endMillis = earliest.capturedAt + 1,
            zoneId = earliest.zoneId, photoCapturedAt = earliest.capturedAt)
    }
}
