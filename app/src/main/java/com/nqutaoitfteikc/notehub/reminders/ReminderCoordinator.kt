package com.nqutaoitfteikc.notehub.reminders

import android.util.Log
import androidx.room.withTransaction
import com.nqutaoitfteikc.notehub.data.*
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ReminderCoordinator(private val database: NoteDatabase, val platform: ReminderPlatform,
                          private val now: () -> Long = System::currentTimeMillis) : ReminderRepository, NoteEffects {
    private val dao = database.reminders()
    private val mutex = Mutex()
    override fun observe() = dao.observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(id: String?, noteId: String, triggerAt: Long, enabled: Boolean) = mutex.withLock {
        require(triggerAt > now()) { "提醒必須設定在未來的時間" }
        val old = id?.let { requireNotNull(dao.get(it)) { "提醒已不存在" } }
        require(old == null || old.noteId == noteId) { "提醒不屬於此事項" }
        val note = requireNotNull(database.notes().get(noteId)) { "事項已不存在" }
        val reminder = ReminderEntity(old?.id ?: UUID.randomUUID().toString(), noteId, triggerAt,
            UUID.randomUUID().toString(), enabled, null, ReminderStatus.PENDING.name)
        if (old == null) dao.insert(reminder) else dao.update(reminder)
        old?.let { platform.cancelAlarm(it); platform.cancelNotification(it.id) }
        reconcile(reminder, note)
    }
    override suspend fun delete(id: String): Unit = mutex.withLock {
        dao.get(id)?.let { reminder ->
            dao.delete(id)
            platform.cancelAlarm(reminder)
            platform.cancelNotification(id)
        }
        Unit
    }
    override suspend fun change(noteId: String, action: suspend () -> Unit) = mutex.withLock {
        action()
        val note = database.notes().get(noteId) ?: return@withLock
        dao.forNote(noteId).forEach { platform.cancelNotification(it.id); reconcile(it, note) }
    }
    override suspend fun delete(noteId: String, action: suspend () -> Unit) = mutex.withLock {
        val reminders = dao.forNote(noteId)
        action() // Room cascades deletion; a stale broadcast cannot find a reminder afterwards.
        reminders.forEach { platform.cancelAlarm(it); platform.cancelNotification(it.id) }
    }
    override suspend fun restore() = mutex.withLock {
        dao.all().forEach { reminder ->
            val note = database.notes().get(reminder.noteId)
            if (note != null) reconcile(reminder, note)
            else { platform.cancelAlarm(reminder); platform.cancelNotification(reminder.id) }
        }
    }
    private suspend fun reconcile(reminder: ReminderEntity, note: NoteEntity) {
        platform.cancelAlarm(reminder)
        val status = ReminderRules.decide(reminder.toDomain(), note.completed, platform.canNotify(), platform.canExact(), now())
        try {
            val actual = if (status == ReminderStatus.EXACT || status == ReminderStatus.APPROXIMATE)
                platform.schedule(reminder, maxOf(reminder.triggerAt, now() + 1000)) else status
            dao.status(reminder.id, actual.name)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.e("NoteHubReminders", "無法排程提醒 ${reminder.id}", e)
            dao.status(reminder.id, ReminderStatus.ERROR.name)
        }
    }
    suspend fun deliver(id: String, revision: String) = mutex.withLock {
        val reminder = dao.get(id) ?: return@withLock
        val note = database.notes().get(reminder.noteId) ?: return@withLock
        if (!ReminderRules.acceptsDelivery(reminder.toDomain(), revision, now())) {
            // A superseded broadcast must never change the status of the current revision.
            if (reminder.revision == revision && reminder.deliveredAt == null) reconcile(reminder, note)
            return@withLock
        }
        if (note.completed || !platform.canNotify()) { reconcile(reminder, note); return@withLock }
        val claimed = database.withTransaction { dao.claim(id, revision, now()) == 1 }
        if (!claimed) return@withLock
        try {
            if (!platform.notify(reminder, note)) dao.unclaim(id, revision, ReminderStatus.BLOCKED_NOTIFICATION.name)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.e("NoteHubReminders", "無法發送提醒 $id", e)
            dao.unclaim(id, revision, ReminderStatus.ERROR.name)
        }
    }
}
