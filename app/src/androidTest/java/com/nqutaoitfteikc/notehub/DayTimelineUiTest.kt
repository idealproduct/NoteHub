package com.nqutaoitfteikc.notehub

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.ui.*
import com.nqutaoitfteikc.notehub.ui.theme.NoteHubTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.*

class DayTimelineUiTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.now()
    private val zone = ZoneId.systemDefault()
    private fun note(id: String, start: LocalDateTime, end: LocalDateTime) = Note(id, "測試事件$id", 0,
        kind = ScheduleKind.TIMED, startMillis = start.atZone(zone).toInstant().toEpochMilli(),
        endMillis = end.atZone(zone).toInstant().toEpochMilli())
    private fun show(notes: List<Note>, open: (String) -> Unit = {}) {
        compose.setContent { NoteHubTheme { Surface { CalendarScreen(HubState(notes = notes, loading = false), {}, open, { _, _ -> }) } } }
    }
    private fun scroll(tag: String) = compose.onNodeWithTag("calendar-content").performScrollToNode(hasTestTag(tag))
    private fun selectScope(scope: String) {
        scroll("search-scope")
        compose.onNodeWithTag("search-scope").performClick()
        compose.onNodeWithTag("scope-$scope").performClick()
    }
    @Test fun monthTapOpensDayAndOverlappingCardsHaveSeparateBoundsAndOpenCallback() {
        val a = note("a", day.atTime(9, 15), day.atTime(11, 45))
        val b = note("b", day.atTime(10, 0), day.atTime(12, 0))
        var opened = ""
        show(listOf(a, b)) { opened = it }
        scroll("day-$day")
        compose.onNodeWithTag("day-$day").performClick()
        scroll("view-dropdown")
        compose.onNodeWithTag("view-dropdown").assertTextContains("日")
        scroll("day-timeline")
        val first = compose.onNodeWithTag("timeline-a").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("timeline-b").fetchSemanticsNode().boundsInRoot
        assertTrue(first.right <= second.left)
        assertTrue(first.top < second.top)
        assertTrue(first.bottom > second.top)
        compose.onNodeWithTag("timeline-a").performClick()
        compose.runOnIdle { assertEquals("a", opened) }
        scroll("note-b")
        compose.onNodeWithTag("note-b").performClick()
        compose.runOnIdle { assertEquals("b", opened) }
        scroll("back-to-month")
        compose.onNodeWithTag("back-to-month").performClick()
        scroll("day-$day")
        compose.onNodeWithTag("day-$day").assertIsDisplayed()
    }
    @Test fun searchDayMonthYearUsesAnchorRatherThanCurrentView() {
        val base = day.withDayOfMonth(1)
        val a = note("today", day.atTime(9, 0), day.atTime(10, 0))
        val b = note("month", (if (day.dayOfMonth == 1) base.plusDays(1) else base).atTime(9, 0),
            (if (day.dayOfMonth == 1) base.plusDays(1) else base).atTime(10, 0))
        val otherMonth = if (day.monthValue == 1) day.plusMonths(1) else day.withMonth(1)
        val c = note("year", otherMonth.atTime(9, 0), otherMonth.atTime(10, 0))
        val d = note("other", day.plusYears(1).atTime(9, 0), day.plusYears(1).atTime(10, 0))
        show(listOf(a, b, c, d))
        compose.onNodeWithTag("search").performTextInput("測試事件")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        selectScope("DAY")
        scroll("note-today")
        compose.onNodeWithTag("note-month").assertDoesNotExist()
        selectScope("MONTH")
        scroll("note-month")
        compose.onNodeWithTag("note-year").assertDoesNotExist()
        selectScope("YEAR")
        scroll("note-year")
        compose.onNodeWithTag("note-other").assertDoesNotExist()
        selectScope("ALL")
        scroll("note-other")
        compose.onNodeWithTag("note-other").assertIsDisplayed()
    }
    @Test fun overnightCardShowsContinuationAndFullDatesRemainInAgenda() {
        val n = note("overnight", day.minusDays(1).atTime(23, 0), day.atTime(2, 0))
        show(listOf(n))
        scroll("day-$day")
        compose.onNodeWithTag("day-$day").performClick()
        scroll("day-timeline")
        compose.onNodeWithTag("timeline-overnight").assertContentDescriptionContains("由前日延續", substring = true)
        scroll("note-overnight")
        compose.onNodeWithTag("note-overnight").assertTextContains(day.minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd")), substring = true)
    }
}
