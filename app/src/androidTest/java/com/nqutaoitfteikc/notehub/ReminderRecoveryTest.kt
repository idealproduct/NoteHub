package com.nqutaoitfteikc.notehub

import android.app.PendingIntent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.data.ReminderEntity
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReminderRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as NoteHubApplication).container
    private val token get() = InstrumentationRegistry.getArguments().getString("recoveryRun") ?: defaultToken
    @Test fun aPrepare() = runBlocking {
        assumeTrue(container.reminders.platform.canNotify() && container.reminders.platform.canExact())
        container.saveNote(null, NoteDraft(token))
        val id = container.observeNotes().first().single { it.title == token }.id
        container.saveReminder(null, id, System.currentTimeMillis() + 1800000)
        container.saveReminder(null, id, System.currentTimeMillis() + 3600000)
        val rows = container.observeReminders().first().filter { it.noteId == id }
        assertEquals(2, rows.size)
        container.saveNote(id, NoteDraft(token, description = rows.joinToString(",") { it.triggerAt.toString() }))
    }
    @Test fun bVerifyRestoredTokensAndCleanUp() = runBlocking {
        assumeTrue(container.reminders.platform.canNotify() && container.reminders.platform.canExact())
        val note = container.observeNotes().first().single { it.title == token }
        val id = note.id
        try {
            val rows = container.observeReminders().first().filter { it.noteId == id }
            assertEquals(2, rows.size)
            assertEquals(note.description.split(",").map(String::toLong), rows.map { it.triggerAt })
            for (row in rows) {
                assertEquals(ReminderStatus.EXACT, row.status)
                // No restore() call here: after a real reboot these tokens must be recreated by the receivers/worker.
                val entity = ReminderEntity(row.id, row.noteId, row.triggerAt, row.revision, row.enabled, row.deliveredAt, row.status.name)
                if (InstrumentationRegistry.getArguments().getString("tokensVerifiedExternally") != "true")
                    assertNotNull(container.reminders.platform.alarmIntent(entity, PendingIntent.FLAG_NO_CREATE))
            }
        } finally { container.deleteNote(id) }
    }
    companion object { private val defaultToken = "重開機驗收-${UUID.randomUUID()}" }
}
