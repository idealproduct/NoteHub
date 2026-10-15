package com.nqutaoitfteikc.notehub

import com.nqutaoitfteikc.notehub.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class CalendarRulesTest {
    private val taipei = ZoneId.of("Asia/Taipei")
    private fun day(value: String) = LocalDate.parse(value)

    @Test fun allDaySpansLeapDayAndExcludesEndInEveryZone() {
        val note = Note("1", "連假", 0, kind = ScheduleKind.ALL_DAY,
            startDay = day("2024-02-28").toEpochDay(), endDayExclusive = day("2024-03-02").toEpochDay())
        for (zone in listOf(taipei, ZoneId.of("America/Los_Angeles"))) {
            assertTrue(CalendarRules.occursOn(note, day("2024-02-29"), zone))
            assertTrue(CalendarRules.occursOn(note, day("2024-03-01"), zone))
            assertFalse(CalendarRules.occursOn(note, day("2024-03-02"), zone))
        }
    }
    @Test fun midnightEndDoesNotSpillIntoNextDay() {
        val note = Note("1", "夜間", 0, kind = ScheduleKind.TIMED,
            startMillis = CalendarRules.resolveLocal(day("2026-10-15"), LocalTime.of(23, 0), taipei),
            endMillis = CalendarRules.resolveLocal(day("2026-10-16"), LocalTime.MIDNIGHT, taipei), zoneId = taipei.id)
        assertTrue(CalendarRules.occursOn(note, day("2026-10-15"), taipei))
        assertFalse(CalendarRules.occursOn(note, day("2026-10-16"), taipei))
        assertFalse(CalendarRules.occursOn(note, day("2026-10-14"), taipei))
    }
    @Test fun dstUsesCalendarDayBoundariesAndRejectsMissingLocalTime() {
        val zone = ZoneId.of("America/New_York")
        val date = day("2026-03-08")
        val start = CalendarRules.resolveLocal(date, LocalTime.MIDNIGHT, zone)
        val end = CalendarRules.resolveLocal(date.plusDays(1), LocalTime.MIDNIGHT, zone)
        assertEquals(23L * 60 * 60 * 1000, end - start)
        assertTrue(CalendarRules.occursOn(Note("1", "夏令", 0, kind = ScheduleKind.TIMED, startMillis = start, endMillis = end, zoneId = zone.id), date, zone))
        try { CalendarRules.resolveLocal(date, LocalTime.of(2, 30), zone); fail("不存在的時間應被拒絕") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun weekStartsMondayAcrossYearBoundary() {
        assertEquals(day("2025-12-29"), CalendarRules.weekStart(day("2026-01-01")))
    }
    @Test fun todoIsNotAssignedToCreationDateAndSearchIncludesDescriptionAndCategory() {
        val notes = listOf(Note("1", "工作", 0, description = "Review", category = "專案"), Note("2", "其他", 0, completed = true))
        assertFalse(CalendarRules.occursOn(notes[0], day("1970-01-01"), taipei))
        assertEquals(listOf(notes[0]), CalendarRules.filter(notes, "review", "專案", CompletionFilter.PENDING))
        assertEquals(listOf(notes[0]), CalendarRules.filter(notes, "專案", null, CompletionFilter.ALL))
        assertEquals(listOf(notes[1]), CalendarRules.filter(notes, "", null, CompletionFilter.COMPLETED))
    }
    @Test fun draftValidatesIntervalsAndClearsIrrelevantFields() {
        assertNull(NoteDraft("待辦", startMillis = 123, endMillis = 456).validated().startMillis)
        for (draft in listOf(
            NoteDraft("全天", kind = ScheduleKind.ALL_DAY, startDay = 5, endDayExclusive = 5),
            NoteDraft("會議", kind = ScheduleKind.TIMED, startMillis = 5, endMillis = 4, zoneId = taipei.id))) {
            try { draft.validated(); fail("應拒絕反向區間") } catch (_: IllegalArgumentException) { }
        }
    }
}
