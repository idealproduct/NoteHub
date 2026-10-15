package com.nqutaoitfteikc.notehub.domain

import java.time.*
import java.time.temporal.TemporalAdjusters

enum class CalendarView { MONTH, WEEK, DAY, LIST }
enum class CompletionFilter { ALL, PENDING, COMPLETED }

object CalendarRules {
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun occursOn(note: Note, date: LocalDate, zone: ZoneId): Boolean = when (note.kind) {
        ScheduleKind.TODO -> false
        ScheduleKind.ALL_DAY -> date.toEpochDay() >= requireNotNull(note.startDay) && date.toEpochDay() < requireNotNull(note.endDayExclusive)
        ScheduleKind.TIMED -> {
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            requireNotNull(note.startMillis) < end && requireNotNull(note.endMillis) > start
        }
    }

    fun filter(notes: List<Note>, query: String, category: String?, completion: CompletionFilter): List<Note> {
        val needle = query.trim()
        return notes.filter { note ->
            (category == null || note.category == category) &&
                (completion == CompletionFilter.ALL || note.completed == (completion == CompletionFilter.COMPLETED)) &&
                (needle.isEmpty() || listOf(note.title, note.description, note.category).any { it.contains(needle, ignoreCase = true) })
        }
    }

    fun onDay(notes: List<Note>, date: LocalDate, zone: ZoneId): List<Note> = notes.filter { occursOn(it, date, zone) }
        .sortedWith(compareBy<Note> { if (it.kind == ScheduleKind.ALL_DAY) 0 else 1 }.thenBy { it.startMillis ?: 0 }.thenBy { it.title })

    // A gap during daylight saving time is rejected instead of silently shifting the user's time.
    // During an overlap the earlier offset is used consistently and is disclosed in the editor.
    fun resolveLocal(date: LocalDate, time: LocalTime, zone: ZoneId): Long {
        val local = LocalDateTime.of(date, time)
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.isNotEmpty()) { "此時間因日光節約時間調整而不存在，請選擇其他時間" }
        return local.atOffset(offsets.first()).toInstant().toEpochMilli()
    }
}
