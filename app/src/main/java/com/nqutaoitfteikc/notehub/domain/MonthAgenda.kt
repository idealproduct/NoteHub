package com.nqutaoitfteikc.notehub.domain

import java.time.LocalDate
import java.time.ZoneId

data class MonthBar(val note: Note, val start: Int, val endExclusive: Int, val lane: Int, val continuesBefore: Boolean, val continuesAfter: Boolean)
object MonthAgenda {
    fun week(start: LocalDate, notes: List<Note>, zone: ZoneId): List<MonthBar> {
        val spans=notes.filter { it.kind!=ScheduleKind.TODO }.mapNotNull { note ->
            val days=(0..6).filter { CalendarRules.occursOn(note,start.plusDays(it.toLong()),zone) }
            if(days.isEmpty()) null else Triple(note,days.first(),days.last()+1)
        }.sortedWith(compareBy<Triple<Note,Int,Int>> { it.second }.thenByDescending { it.third-it.second }.thenBy { it.first.startMillis ?: Long.MIN_VALUE }.thenBy { it.first.id })
        val ends=mutableListOf<Int>()
        return spans.map { (note,from,to) ->
            var lane=ends.indexOfFirst { it<=from }
            if(lane<0) { lane=ends.size; ends.add(to) } else ends[lane]=to
            MonthBar(note,from,to,lane,from==0 && CalendarRules.occursOn(note,start.minusDays(1),zone),to==7 && CalendarRules.occursOn(note,start.plusDays(7),zone))
        }
    }
}
