package com.nqutaoitfteikc.notehub

import android.app.NotificationManager
import android.app.PendingIntent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.data.ReminderEntity
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as NoteHubApplication).container
    private fun snapshot() = runBlocking { container.observeReminders().first() }
    private fun entity(r: Reminder) = ReminderEntity(r.id, r.noteId, r.triggerAt, r.revision, r.enabled, r.deliveredAt, r.status.name)
    private suspend fun makeNote(): String {
        val title = "提醒驗收-${UUID.randomUUID()}"
        container.saveNote(null, NoteDraft(title))
        return container.observeNotes().first().single { it.title == title }.id
    }
    @Test fun independentRealAlarmsFireAndDuplicateDeliveryIsSuppressed() = runBlocking {
        assumeTrue("此測試需要已授權通知與精準鬧鐘", container.reminders.platform.canNotify() && container.reminders.platform.canExact())
        val noteId = makeNote()
        val notifications = context.getSystemService(NotificationManager::class.java)
        try {
            container.saveReminder(null, noteId, System.currentTimeMillis() + 3000)
            container.saveReminder(null, noteId, System.currentTimeMillis() + 7000)
            val reminders = snapshot().filter { it.noteId == noteId }
            assertEquals(2, reminders.size)
            assertEquals(2, reminders.map { it.id }.distinct().size)
            assertTrue(reminders.all { it.status == ReminderStatus.EXACT })
            val first = container.reminders.platform.alarmIntent(entity(reminders[0]), PendingIntent.FLAG_NO_CREATE)
            val second = container.reminders.platform.alarmIntent(entity(reminders[1]), PendingIntent.FLAG_NO_CREATE)
            assertNotNull(first); assertNotNull(second); assertNotEquals(first, second)
            withTimeout(30000) {
                while (snapshot().filter { it.noteId == noteId }.any { it.deliveredAt == null }) delay(150)
            }
            val visible = notifications.activeNotifications.filter { it.tag in reminders.map { r -> r.id } }
            assertEquals(2, visible.size)
            val posted = visible.associate { it.tag to it.postTime }
            for (r in reminders) container.reminders.deliver(r.id, r.revision)
            assertEquals(posted, notifications.activeNotifications.filter { it.tag in posted }.associate { it.tag to it.postTime })
            // The notification's actual PendingIntent must successfully open the existing note.
            visible.first().notification.contentIntent.send()
            container.setCompleted(noteId, true)
            withTimeout(5000) {
                while (notifications.activeNotifications.any { it.tag in posted }) delay(50)
            }
        } finally { container.deleteNote(noteId) }
    }
    @Test fun editingCancellationCompletionAndDeletionAreIndependent() = runBlocking {
        val noteId = makeNote()
        try {
            container.saveReminder(null, noteId, System.currentTimeMillis() + 3600000)
            container.saveReminder(null, noteId, System.currentTimeMillis() + 7200000)
            val original = snapshot().filter { it.noteId == noteId }
            val old = original.first()
            container.saveReminder(old.id, noteId, System.currentTimeMillis() + 10800000)
            val updated = snapshot().single { it.id == old.id }
            assertNotEquals(old.revision, updated.revision)
            assertNull(container.reminders.platform.alarmIntent(entity(old), PendingIntent.FLAG_NO_CREATE))
            container.reminders.deliver(old.id, old.revision)
            assertNull(snapshot().single { it.id == old.id }.deliveredAt)
            container.deleteReminder(original.last().id)
            assertEquals(1, snapshot().count { it.noteId == noteId })
            container.setCompleted(noteId, true)
            assertEquals(ReminderStatus.PAUSED_COMPLETED, snapshot().single { it.id == old.id }.status)
            assertNull(container.reminders.platform.alarmIntent(entity(updated), PendingIntent.FLAG_NO_CREATE))
            container.setCompleted(noteId, false)
            val status = snapshot().single { it.id == old.id }.status
            assertTrue(status in setOf(ReminderStatus.EXACT, ReminderStatus.APPROXIMATE, ReminderStatus.BLOCKED_NOTIFICATION))
            container.deleteNote(noteId)
            assertTrue(snapshot().none { it.noteId == noteId })
            assertNull(container.reminders.platform.alarmIntent(entity(updated), PendingIntent.FLAG_NO_CREATE))
        } finally { container.deleteNote(noteId) }
    }
}
