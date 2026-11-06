package com.nqutaoitfteikc.notehub
import com.nqutaoitfteikc.notehub.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test
class MonthAgendaTest {
    private val day=LocalDate.of(2026,11,2)
    private fun all(id:String,from:Long,to:Long)=Note(id,id,1,kind=ScheduleKind.ALL_DAY,startDay=day.plusDays(from).toEpochDay(),endDayExclusive=day.plusDays(to).toEpochDay())
    @Test fun spanningWeeksRemainContinuousAndHalfOpenEndDoesNotLeak() {
        val note=all("span",1,10)
        val first=MonthAgenda.week(day,listOf(note),ZoneOffset.UTC).single()
        val second=MonthAgenda.week(day.plusDays(7),listOf(note),ZoneOffset.UTC).single()
        assertEquals(1,first.start); assertEquals(7,first.endExclusive); assertTrue(first.continuesAfter)
        assertEquals(0,second.start); assertEquals(3,second.endExclusive); assertTrue(second.continuesBefore); assertFalse(second.continuesAfter)
    }
    @Test fun overlapsGetDifferentLanesAndLaterEventsReuseFreeLane() {
        val bars=MonthAgenda.week(day,listOf(all("long",0,3),all("short",1,2),all("later",3,4)),ZoneOffset.UTC)
        assertNotEquals(bars.single { it.note.id=="long" }.lane,bars.single { it.note.id=="short" }.lane)
        assertEquals(bars.single { it.note.id=="long" }.lane,bars.single { it.note.id=="later" }.lane)
    }
    @Test fun midnightTimedEndAndUndatedTodosAreHandledCorrectly() {
        val start=day.atTime(23,0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val note=Note("night","night",1,kind=ScheduleKind.TIMED,startMillis=start,endMillis=start+3600000,zoneId="UTC")
        val bars=MonthAgenda.week(day,listOf(note,Note("todo","todo",1)),ZoneOffset.UTC)
        assertEquals(1,bars.size); assertEquals(1,bars.single().endExclusive)
    }
}
