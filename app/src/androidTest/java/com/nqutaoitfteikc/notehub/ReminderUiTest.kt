package com.nqutaoitfteikc.notehub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as NoteHubApplication).container
    private fun reminders(id: String) = runBlocking { container.observeReminders().first().filter { it.noteId == id } }
    @Test fun addEditDisableAndDeleteIndependentReminders() {
        val title = "提醒畫面-${UUID.randomUUID()}"
        runBlocking { container.saveNote(null, NoteDraft(title)) }
        val noteId = runBlocking { container.observeNotes().first().single { it.title == title }.id }
        try {
            compose.onNodeWithTag("calendar-content").performScrollToNode(hasTestTag("view-LIST"))
            compose.onNodeWithTag("view-LIST").performClick()
            compose.onNodeWithTag("calendar-content").performScrollToNode(hasTestTag("open-reminders-$noteId"))
            compose.onNodeWithTag("open-reminders-$noteId").performClick()
            compose.onNodeWithText("提醒日期").performScrollTo().performTextReplacement(LocalDate.now().plusDays(2).toString())
            compose.onNodeWithTag("save-reminder").performScrollTo().performClick()
            compose.waitUntil(10000) { reminders(noteId).size == 1 }
            val first = reminders(noteId).single()
            compose.onNodeWithTag("save-reminder").performScrollTo().performClick()
            compose.waitUntil(10000) { reminders(noteId).size == 2 }
            compose.onNodeWithTag("edit-reminder-${first.id}").performScrollTo().performClick()
            compose.onNodeWithTag("reminder-enabled").performScrollTo().performClick()
            compose.onNodeWithTag("save-reminder").performScrollTo().performClick()
            compose.waitUntil(10000) { reminders(noteId).single { it.id == first.id }.status == ReminderStatus.DISABLED }
            assertEquals(2, reminders(noteId).size)
            assertNotEquals(first.revision, reminders(noteId).single { it.id == first.id }.revision)
            compose.onNodeWithTag("delete-reminder-${first.id}").performScrollTo().performClick()
            compose.onNodeWithText("確認移除").performClick()
            compose.waitUntil(10000) { reminders(noteId).size == 1 }
            assertTrue(reminders(noteId).none { it.id == first.id })
        } finally { runBlocking { container.deleteNote(noteId) } }
    }
}
