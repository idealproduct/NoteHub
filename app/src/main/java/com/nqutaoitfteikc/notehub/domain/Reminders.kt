package com.nqutaoitfteikc.notehub.domain

import kotlinx.coroutines.flow.Flow

enum class ReminderStatus {
    PENDING, EXACT, APPROXIMATE, BLOCKED_NOTIFICATION, DISABLED, PAUSED_COMPLETED, EXPIRED, DELIVERED, ERROR
}
data class Reminder(
    val id: String, val noteId: String, val triggerAt: Long, val revision: String,
    val enabled: Boolean = true, val deliveredAt: Long? = null,
    val status: ReminderStatus = ReminderStatus.PENDING
)
interface ReminderRepository {
    fun observe(): Flow<List<Reminder>>
    suspend fun save(id: String?, noteId: String, triggerAt: Long, enabled: Boolean)
    suspend fun delete(id: String)
    suspend fun restore()
}
interface NoteEffects {
    suspend fun change(noteId: String, action: suspend () -> Unit)
    suspend fun delete(noteId: String, action: suspend () -> Unit)
}
class ObserveReminders(private val repository: ReminderRepository) {
    operator fun invoke() = repository.observe()
}
class SaveReminder(private val repository: ReminderRepository, private val now: () -> Long = System::currentTimeMillis) {
    suspend operator fun invoke(id: String?, noteId: String, triggerAt: Long, enabled: Boolean = true) {
        require(triggerAt > now()) { "提醒必須設定在未來的時間" }
        repository.save(id, noteId, triggerAt, enabled)
    }
}
class DeleteReminder(private val repository: ReminderRepository) {
    suspend operator fun invoke(id: String) = repository.delete(id)
}
class RestoreReminders(private val repository: ReminderRepository) {
    suspend operator fun invoke() = repository.restore()
}
object ReminderRules {
    const val LATE_GRACE_MILLIS = 15 * 60 * 1000L
    fun decide(reminder: Reminder, completed: Boolean, notifications: Boolean, exact: Boolean, now: Long): ReminderStatus = when {
        reminder.deliveredAt != null -> ReminderStatus.DELIVERED
        !reminder.enabled -> ReminderStatus.DISABLED
        completed -> ReminderStatus.PAUSED_COMPLETED
        reminder.triggerAt < now - LATE_GRACE_MILLIS -> ReminderStatus.EXPIRED
        !notifications -> ReminderStatus.BLOCKED_NOTIFICATION
        exact -> ReminderStatus.EXACT
        else -> ReminderStatus.APPROXIMATE
    }
    fun acceptsDelivery(reminder: Reminder, revision: String, now: Long): Boolean =
        reminder.enabled && reminder.deliveredAt == null && reminder.revision == revision &&
            now >= reminder.triggerAt && now - reminder.triggerAt <= LATE_GRACE_MILLIS
}
