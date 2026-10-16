package com.nqutaoitfteikc.notehub.domain

import java.time.LocalDate
import java.time.ZoneId

enum class SearchScope { ALL, DAY, MONTH, YEAR }

data class TimelineBlock(val note: Note, val startMinute: Double, val endMinute: Double,
    val column: Int, val columns: Int, val continuesBefore: Boolean, val continuesAfter: Boolean)

object TimelineRules {
    fun inScope(note: Note, anchor: LocalDate, scope: SearchScope, zone: ZoneId): Boolean {
        if (scope == SearchScope.ALL) return true
        if (note.kind == ScheduleKind.TODO) return false
        val start = when (scope) {
            SearchScope.MONTH -> anchor.withDayOfMonth(1)
            SearchScope.YEAR -> anchor.withDayOfYear(1)
            else -> anchor
        }
        val end = when (scope) {
            SearchScope.MONTH -> start.plusMonths(1)
            SearchScope.YEAR -> start.plusYears(1)
            else -> start.plusDays(1)
        }
        return if (note.kind == ScheduleKind.ALL_DAY)
            requireNotNull(note.startDay) < end.toEpochDay() && requireNotNull(note.endDayExclusive) > start.toEpochDay()
        else requireNotNull(note.startMillis) < end.atStartOfDay(zone).toInstant().toEpochMilli() &&
            requireNotNull(note.endMillis) > start.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Half-open intervals, clipped to the actual local day (including 23/25-hour DST days). */
    fun blocks(notes: List<Note>, day: LocalDate, zone: ZoneId): List<TimelineBlock> {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val clipped = notes.filter { it.kind == ScheduleKind.TIMED && CalendarRules.occursOn(it, day, zone) }
            .map { note -> TimelineBlock(note, (maxOf(start, requireNotNull(note.startMillis)) - start) / 60000.0,
                (minOf(end, requireNotNull(note.endMillis)) - start) / 60000.0, 0, 1,
                (requireNotNull(note.startMillis) < start), (requireNotNull(note.endMillis) > end)) }
            .sortedWith(compareBy<TimelineBlock> { it.startMinute }.thenByDescending { it.endMinute }.thenBy { it.note.id })
        val result = mutableListOf<TimelineBlock>()
        var group = mutableListOf<TimelineBlock>()
        var groupEnd = -1.0
        fun flush() {
            val laneEnds = mutableListOf<Double>()
            val placed = group.map { block ->
                val lane = laneEnds.indexOfFirst { it <= block.startMinute }.let { if (it < 0) laneEnds.size else it }
                if (lane == laneEnds.size) laneEnds.add(block.endMinute) else laneEnds[lane] = block.endMinute
                block.copy(column = lane)
            }
            result += placed.map { it.copy(columns = laneEnds.size) }
            group = mutableListOf()
        }
        clipped.forEach { block ->
            if (group.isNotEmpty() && block.startMinute >= groupEnd) { flush(); groupEnd = -1.0 }
            group.add(block)
            groupEnd = maxOf(groupEnd, block.endMinute)
        }
        if (group.isNotEmpty()) flush()
        return result
    }
}
