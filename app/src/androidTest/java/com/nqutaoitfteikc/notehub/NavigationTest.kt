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
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as NoteHubApplication).container
    private fun notes() = runBlocking { container.observeNotes().first() }
    private fun scrollTo(matcher: SemanticsMatcher) { compose.onNodeWithTag("calendar-content").performScrollToNode(matcher) }
    private fun listMode() { scrollTo(hasTestTag("view-LIST")); compose.onNodeWithTag("view-LIST").performClick() }
    private fun search(value: String) { scrollTo(hasTestTag("search")); compose.onNodeWithTag("search").performTextReplacement(value) }
    private fun waitSaved(title: String) { compose.waitUntil(10000) { notes().any { it.title == title } }; compose.waitUntil(10000) { compose.onAllNodesWithTag("calendar-content").fetchSemanticsNodes().isNotEmpty() } }

    @Test fun localizedDateAndTimePickersOpenAndCancel() {
        compose.onNodeWithTag("add-note").performClick()
        compose.onNodeWithText("指定時間").performScrollTo().performClick()
        compose.onNodeWithTag("start-date").performScrollTo()
        compose.onAllNodesWithText("選擇日期").onFirst().performClick()
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("取消"))
            .perform(androidx.test.espresso.action.ViewActions.click())
        compose.onNodeWithTag("start-date").assertTextContains(LocalDate.now().toString())
        compose.onAllNodesWithText("選擇時間").onFirst().performScrollTo().performClick()
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("確定"))
            .perform(androidx.test.espresso.action.ViewActions.click())
        compose.onNodeWithTag("start-time").assertTextContains("09:00")
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("捨棄修改").performClick()
    }

    @Test fun crudSearchCategoryCompletionAndTheme() {
        val previousTheme = runBlocking { container.observeTheme().first() }
        val token = "測試-${UUID.randomUUID()}"
        val title = "$token-待辦"
        val changed = "$token-已修改"
        var id: String? = null
        try {
            compose.onNodeWithTag("add-note").performClick()
            compose.onNodeWithTag("editor-title").performTextInput(title)
            compose.onNodeWithTag("editor-description").performTextInput("可搜尋的描述-$token")
            compose.onNodeWithTag("editor-category").performTextInput(token)
            compose.onNodeWithTag("editor-save").performClick()
            waitSaved(title)
            id = notes().single { it.title == title }.id
            listMode()
            search("可搜尋的描述-$token")
            scrollTo(hasText(title))
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNodeWithContentDescription("標記完成：$title").performClick()
            compose.waitUntil(10000) { notes().single { it.id == id }.completed }
            scrollTo(hasText("已完成"))
            compose.onNodeWithText("已完成").performClick()
            scrollTo(hasText(title))
            compose.onNodeWithContentDescription("編輯：$title").performClick()
            compose.onNodeWithTag("editor-title").performTextReplacement(changed)
            compose.onNodeWithTag("editor-save").performClick()
            waitSaved(changed)
            compose.activityRule.scenario.recreate()
            scrollTo(hasText(changed))
            compose.onNodeWithText(changed).assertIsDisplayed()
            assertTrue(notes().single { it.id == id }.completed)
            search(token)
            scrollTo(hasText("全部分類"))
            compose.onAllNodesWithText(token).filter(hasClickAction()).onFirst().performClick()
            scrollTo(hasText(changed))
            compose.onNodeWithText(changed).assertIsDisplayed()
            compose.onNodeWithContentDescription("編輯：$changed").performClick()
            compose.onNodeWithText("刪除").performClick()
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithTag("editor-title").assertTextContains(changed)
            compose.onNodeWithText("刪除").performClick()
            compose.onNodeWithText("確認刪除").performClick()
            compose.waitUntil(10000) { notes().none { it.id == id } }
            compose.activityRule.scenario.recreate()
            assertTrue(notes().none { it.id == id })
            compose.onNodeWithText("設定").performClick()
            compose.onNodeWithText("深色").performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasText("深色") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("深色").assertIsSelected()
            compose.onNodeWithText("跟隨系統").performClick()
        } finally {
            runBlocking { id?.let { container.deleteNote(it) }; container.setTheme(previousTheme) }
        }
    }

    @Test fun allDayEditorAndCalendarViewsShowRealIntervals() {
        val token = "行事曆-${UUID.randomUUID()}"
        val day = LocalDate.now().withDayOfMonth(10)
        var id: String? = null
        var timedId: String? = null
        try {
            compose.onNodeWithTag("add-note").performClick()
            compose.onNodeWithTag("editor-title").performTextInput(token)
            compose.onNodeWithText("全天").performScrollTo().performClick()
            compose.onNodeWithTag("start-date").performScrollTo().performTextReplacement(day.toString())
            compose.onNodeWithTag("end-date").performScrollTo().performTextReplacement(day.plusDays(2).toString())
            compose.onNodeWithTag("editor-save").performClick()
            waitSaved(token)
            id = notes().single { it.title == token }.id
            assertEquals(day.plusDays(3).toEpochDay(), notes().single { it.id == id }.endDayExclusive)
            search(token)
            scrollTo(hasTestTag("day-${day.plusDays(1)}"))
            compose.onNodeWithTag("day-${day.plusDays(1)}").performClick()
            scrollTo(hasTestTag("note-$id"))
            compose.onNodeWithTag("note-$id").assertIsDisplayed()
            scrollTo(hasTestTag("day-${day.plusDays(3)}"))
            compose.onNodeWithTag("day-${day.plusDays(3)}").performClick()
            scrollTo(hasText("這一天沒有符合條件的事項"))
            compose.onNodeWithText("這一天沒有符合條件的事項").assertIsDisplayed()
            scrollTo(hasTestTag("day-$day"))
            compose.onNodeWithTag("day-$day").performClick()
            scrollTo(hasTestTag("view-WEEK"))
            compose.onNodeWithTag("view-WEEK").performClick()
            scrollTo(hasTestTag("week-$day"))
            compose.onNodeWithTag("week-$day").performClick()
            scrollTo(hasTestTag("note-$id"))
            compose.onNodeWithTag("note-$id").assertIsDisplayed()
            // Seed an additional real Room event to exercise midnight boundaries in the UI.
            val zone = ZoneId.systemDefault()
            runBlocking { container.saveNote(null, NoteDraft("$token-夜間", kind = ScheduleKind.TIMED,
                startMillis = CalendarRules.resolveLocal(day, LocalTime.of(23, 0), zone),
                endMillis = CalendarRules.resolveLocal(day.plusDays(1), LocalTime.MIDNIGHT, zone), zoneId = zone.id)) }
            timedId = notes().single { it.title == "$token-夜間" }.id
            scrollTo(hasText("$token-夜間"))
            compose.onNodeWithText("$token-夜間").assertIsDisplayed()
            listMode()
            scrollTo(hasText("$token-夜間"))
            compose.onNodeWithText("$token-夜間").assertIsDisplayed()
        } finally { runBlocking { id?.let { container.deleteNote(it) }; timedId?.let { container.deleteNote(it) } } }
    }

    @Test fun editorRejectsInvalidRangeAndKeepsDraftOnRecreation() {
        val token = "時間-${UUID.randomUUID()}"
        var id: String? = null
        try {
            compose.onNodeWithTag("add-note").performClick()
            compose.onNodeWithTag("editor-title").performTextInput(token)
            compose.onNodeWithText("指定時間").performScrollTo().performClick()
            compose.onNodeWithTag("start-date").performScrollTo().performTextReplacement("2026-10-15")
            compose.onNodeWithTag("end-date").performScrollTo().performTextReplacement("2026-10-15")
            compose.onNodeWithTag("start-time").performScrollTo().performTextReplacement("23:00")
            compose.onNodeWithTag("end-time").performScrollTo().performTextReplacement("22:00")
            compose.onNodeWithTag("editor-save").performClick()
            compose.onNodeWithText("結束時間必須晚於開始時間").assertIsDisplayed()
            assertTrue(notes().none { it.title == token })
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("editor-title").performScrollTo().assertTextContains(token)
            compose.onNodeWithTag("end-date").performScrollTo().performTextReplacement("2026-10-16")
            compose.onNodeWithTag("editor-save").performClick()
            waitSaved(token)
            id = notes().single { it.title == token }.id
            assertEquals(ScheduleKind.TIMED, notes().single { it.id == id }.kind)
        } finally { runBlocking { id?.let { container.deleteNote(it) } } }
    }
}

