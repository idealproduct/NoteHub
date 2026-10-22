package com.nqutaoitfteikc.notehub

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

class PhotoReminderTest {
    @Test fun photoNoteAlarmsFireIndependentlyAndPreserveCaptureTime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = (context.applicationContext as NoteHubApplication).container
        assumeTrue(app.reminders.platform.canNotify() && app.reminders.platform.canExact())
        val session = UUID.randomUUID().toString()
        val captureTime = System.currentTimeMillis() - 10000
        var noteId: String? = null
        try {
            app.storePhoto(session, CapturedImage(photoFixture(), CaptureTime(captureTime, CaptureTimeSource.CAPTURE_STARTED), 0, "UTC"))
            val id = app.savePhotoNote(session, null, "照片提醒測試-${UUID.randomUUID()}", "", "")
            noteId = id
            app.saveReminder(null, id, System.currentTimeMillis() + 3000)
            app.saveReminder(null, id, System.currentTimeMillis() + 7000)
            val originals = app.observeReminders().first().filter { it.noteId == id }
            assertEquals(2, originals.size)
            assertTrue(originals.all { it.status == ReminderStatus.EXACT })
            withTimeout(30000) {
                while (app.observeReminders().first().filter { it.noteId == id }.any { it.deliveredAt == null }) delay(150)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            assertEquals(2, manager.activeNotifications.count { n -> originals.any { it.id == n.tag } })
            assertEquals(captureTime, app.observeNotes().first().single { it.id == id }.photoCapturedAt)
            app.deleteReminder(originals.first().id)
            withTimeout(5000) {
                while (manager.activeNotifications.count { n -> originals.any { it.id == n.tag } } != 1) delay(50)
            }
            assertEquals(1, manager.activeNotifications.count { n -> originals.any { it.id == n.tag } })
        } finally {
            noteId?.let { app.deleteNote(it) }
            app.discardPhotoDraft(session)
            app.photos.recoverFiles()
        }
    }
}
