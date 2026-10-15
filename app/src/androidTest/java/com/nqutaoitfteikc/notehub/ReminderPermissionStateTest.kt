package com.nqutaoitfteikc.notehub

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderPermissionStateTest {
    @Test fun reflectsActualPermissionChangesAndRestoresPersistedReminder() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NoteHubApplication).container
        val token = args.getString("permissionRun") ?: "權限驗收-${UUID.randomUUID()}"
        val existing = container.observeNotes().first().find { it.title == token }
        if (existing == null) container.saveNote(null, NoteDraft(token))
        val note = container.observeNotes().first().single { it.title == token }
        val keep = args.getString("keepPermissionData") == "true"
        try {
            if (existing == null) container.saveReminder(null, note.id, System.currentTimeMillis() + 3600000)
            container.restoreReminders()
            val reminder = container.observeReminders().first().single { it.noteId == note.id }
            val expected = args.getString("expectedReminderStatus")?.let(ReminderStatus::valueOf)
                ?: if (!container.reminders.platform.canNotify()) ReminderStatus.BLOCKED_NOTIFICATION
                else if (container.reminders.platform.canExact()) ReminderStatus.EXACT else ReminderStatus.APPROXIMATE
            assertEquals(expected, reminder.status)
            assertNull(reminder.deliveredAt)
        } finally { if (!keep) container.deleteNote(note.id) }
    }
}
