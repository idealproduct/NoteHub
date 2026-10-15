package com.nqutaoitfteikc.notehub.domain
import kotlinx.coroutines.flow.Flow
import java.util.UUID

enum class ScheduleKind { TODO, ALL_DAY, TIMED }

data class Note(
    val id: String, val title: String, val createdAt: Long,
    val description: String = "", val category: String = "", val completed: Boolean = false,
    val kind: ScheduleKind = ScheduleKind.TODO,
    val startDay: Long? = null, val endDayExclusive: Long? = null,
    val startMillis: Long? = null, val endMillis: Long? = null, val zoneId: String? = null,
    val photoCapturedAt: Long? = null
)

data class NoteDraft(
    val title: String, val description: String = "", val category: String = "",
    val kind: ScheduleKind = ScheduleKind.TODO,
    val startDay: Long? = null, val endDayExclusive: Long? = null,
    val startMillis: Long? = null, val endMillis: Long? = null, val zoneId: String? = null
) {
    fun validated(): NoteDraft {
        val clean = copy(title = title.trim(), category = category.trim(), description = description.trim())
        require(clean.title.isNotEmpty()) { "請輸入標題" }
        require(clean.title.length <= 200) { "標題最多 200 個字元" }
        require(clean.category.length <= 40) { "分類最多 40 個字元" }
        require(clean.description.length <= 20000) { "描述最多 20000 個字元" }
        return when (kind) {
            ScheduleKind.TODO -> clean.copy(startDay = null, endDayExclusive = null, startMillis = null, endMillis = null, zoneId = null)
            ScheduleKind.ALL_DAY -> {
                require(startDay != null && endDayExclusive != null && endDayExclusive > startDay) { "結束日期不可早於開始日期" }
                java.time.LocalDate.ofEpochDay(startDay)
                java.time.LocalDate.ofEpochDay(endDayExclusive)
                clean.copy(startMillis = null, endMillis = null, zoneId = null)
            }
            ScheduleKind.TIMED -> {
                require(startMillis != null && endMillis != null && endMillis > startMillis) { "結束時間必須晚於開始時間" }
                require(zoneId != null) { "請指定事件時區" }
                java.time.ZoneId.of(zoneId)
                clean.copy(startDay = null, endDayExclusive = null)
            }
        }
    }
}

interface NoteRepository {
    fun observe(): Flow<List<Note>>
    suspend fun insert(note: Note)
    suspend fun get(id: String): Note?
    suspend fun update(note: Note)
    suspend fun delete(id: String)
    suspend fun setCompleted(id: String, completed: Boolean)
}

class SaveNote(private val repository: NoteRepository, private val now: () -> Long = System::currentTimeMillis, private val effects: NoteEffects? = null) {
    suspend operator fun invoke(id: String?, draft: NoteDraft) {
        val valid = draft.validated()
        val previous = id?.let { repository.get(it) ?: throw IllegalArgumentException("事項已不存在，請返回清單") }
        var note = Note(previous?.id ?: UUID.randomUUID().toString(), valid.title, previous?.createdAt ?: now(),
            valid.description, valid.category, previous?.completed ?: false, valid.kind,
            valid.startDay, valid.endDayExclusive, valid.startMillis, valid.endMillis, valid.zoneId)
        previous?.photoCapturedAt?.let { capturedAt ->
            note = note.copy(kind = ScheduleKind.TIMED, startDay = null, endDayExclusive = null,
                startMillis = capturedAt, endMillis = capturedAt + 1, zoneId = previous.zoneId,
                photoCapturedAt = capturedAt)
        }
        val write: suspend () -> Unit = { if (previous == null) repository.insert(note) else repository.update(note) }
        if (effects != null) effects.change(note.id, write) else write()
    }
}
class DeleteNote(private val repository: NoteRepository, private val effects: NoteEffects? = null) {
    suspend operator fun invoke(id: String) {
        if (effects != null) effects.delete(id) { repository.delete(id) } else repository.delete(id)
    }
}
class SetCompleted(private val repository: NoteRepository, private val effects: NoteEffects? = null) {
    suspend operator fun invoke(id: String, completed: Boolean) {
        if (effects != null) effects.change(id) { repository.setCompleted(id, completed) } else repository.setCompleted(id, completed)
    }
}
class ObserveNotes(private val repository: NoteRepository) {
    operator fun invoke() = repository.observe()
}
class AddNote(private val repository: NoteRepository, private val now: () -> Long = System::currentTimeMillis, private val newId: () -> String = { UUID.randomUUID().toString() }) {
    suspend operator fun invoke(title: String) {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "請輸入標題" }
        require(clean.length <= 200) { "標題最多 200 個字元" }
        repository.insert(Note(newId(), clean, now()))
    }
}
enum class ThemeMode { SYSTEM, LIGHT, DARK }
interface SettingsRepository {
    val theme: Flow<ThemeMode>
    suspend fun setTheme(mode: ThemeMode)
}
class ObserveTheme(private val repository: SettingsRepository) {
    operator fun invoke() = repository.theme
}
class SetTheme(private val repository: SettingsRepository) {
    suspend operator fun invoke(mode: ThemeMode) = repository.setTheme(mode)
}
