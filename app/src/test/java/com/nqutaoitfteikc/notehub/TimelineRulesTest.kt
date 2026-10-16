package com.nqutaoitfteikc.notehub

import com.nqutaoitfteikc.notehub.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class TimelineRulesTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private val day = LocalDate.of(2026, 10, 25)
    private fun timed(id: String, from: String, to: String) = Note(id, id, 0, kind = ScheduleKind.TIMED,
        startMillis = LocalDateTime.parse(from).atZone(zone).toInstant().toEpochMilli(),
        endMillis = LocalDateTime.parse(to).atZone(zone).toInstant().toEpochMilli())

    @Test fun minuteOffsetsAndDurationsArePreserved() {
        val b = TimelineRules.blocks(listOf(timed("a", "2026-10-25T09:15", "2026-10-25T11:45")), day, zone).single()
        assertEquals(555.0, b.startMinute, 0.0)
        assertEquals(705.0, b.endMinute, 0.0)
    }
    @Test fun overlapChainsUseSeparateLanesButTouchingIntervalsReuseLanes() {
        val notes = listOf(timed("a", "2026-10-25T09:00", "2026-10-25T11:00"),
            timed("b", "2026-10-25T10:00", "2026-10-25T12:00"),
            timed("c", "2026-10-25T11:00", "2026-10-25T13:00"),
            timed("d", "2026-10-25T13:00", "2026-10-25T14:00"))
        val b = TimelineRules.blocks(notes, day, zone).associateBy { it.note.id }
        assertNotEquals(b.getValue("a").column, b.getValue("b").column)
        assertNotEquals(b.getValue("b").column, b.getValue("c").column)
        assertEquals(b.getValue("a").column, b.getValue("c").column)
        assertEquals(2, b.getValue("c").columns)
        assertEquals(1, b.getValue("d").columns)
    }
    @Test fun nestedAndIdenticalIntervalsNeverShareALane() {
        val b = TimelineRules.blocks(listOf(timed("a", "2026-10-25T09:00", "2026-10-25T13:00"),
            timed("b", "2026-10-25T10:00", "2026-10-25T11:00"),
            timed("c", "2026-10-25T10:00", "2026-10-25T11:00")), day, zone)
        assertEquals(3, b.map { it.column }.distinct().size)
        assertTrue(b.all { it.columns == 3 })
    }
    @Test fun crossDayClipsEachDayAndMidnightEndDoesNotCreatePhantomEvent() {
        val n = timed("overnight", "2026-10-24T23:30", "2026-10-26T00:00")
        val a = TimelineRules.blocks(listOf(n), day.minusDays(1), zone).single()
        assertEquals(1410.0, a.startMinute, 0.0)
        assertTrue(a.continuesAfter)
        val b = TimelineRules.blocks(listOf(n), day, zone).single()
        assertEquals(0.0, b.startMinute, 0.0)
        assertEquals(1440.0, b.endMinute, 0.0)
        assertTrue(b.continuesBefore)
        assertFalse(b.continuesAfter)
        assertTrue(TimelineRules.blocks(listOf(n), day.plusDays(1), zone).isEmpty())
    }
    @Test fun scopeUsesIntersectingCalendarDayMonthYearIncludingAllDayAndExcludesTodos() {
        val n = timed("boundary", "2026-09-30T23:00", "2026-10-01T01:00")
        assertTrue(TimelineRules.inScope(n, day, SearchScope.MONTH, zone))
        assertFalse(TimelineRules.inScope(n, day, SearchScope.DAY, zone))
        assertTrue(TimelineRules.inScope(n, day, SearchScope.YEAR, zone))
        assertFalse(TimelineRules.inScope(n, day.plusYears(1), SearchScope.YEAR, zone))
        val allDay = Note("a", "a", 0, kind = ScheduleKind.ALL_DAY, startDay = day.minusDays(1).toEpochDay(), endDayExclusive = day.plusDays(1).toEpochDay())
        assertTrue(TimelineRules.inScope(allDay, day, SearchScope.DAY, zone))
        assertFalse(TimelineRules.inScope(allDay, day.plusDays(1), SearchScope.DAY, zone))
        assertFalse(TimelineRules.inScope(Note("t", "t", 0), day, SearchScope.YEAR, zone))
        assertTrue(TimelineRules.inScope(Note("t", "t", 0), day, SearchScope.ALL, zone))
    }
    @Test fun dstRepeatedHourUsesDistinctInstantsAndDayHas25Hours() {
        val z = ZoneId.of("America/New_York")
        val d = LocalDate.of(2026, 11, 1)
        val n = Note("dst", "dst", 0, kind = ScheduleKind.TIMED,
            startMillis = d.atStartOfDay(z).toInstant().toEpochMilli(),
            endMillis = d.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli())
        assertEquals(1500.0, TimelineRules.blocks(listOf(n), d, z).single().endMinute, 0.0)
    }
}
