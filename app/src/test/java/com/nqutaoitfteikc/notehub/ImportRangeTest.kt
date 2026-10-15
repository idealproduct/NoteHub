package com.nqutaoitfteikc.notehub

import com.nqutaoitfteikc.notehub.domain.ImportRange
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ImportRangeTest {
    @Test fun apiBoundsAlwaysContainRfc3339SecondsAndPreserveInclusiveLocalDates() {
        val range = ImportRange(LocalDate.of(2026,10,15), LocalDate.of(2026,10,16), ZoneId.of("Asia/Taipei"))
        assertEquals("2026-10-14T16:00:00Z", range.timeMin)
        assertEquals("2026-10-16T16:00:00Z", range.timeMax)
        val utc = ImportRange(LocalDate.of(2026,10,15), LocalDate.of(2026,10,15), ZoneId.of("UTC"))
        assertEquals("2026-10-15T00:00:00Z", utc.timeMin)
    }
    @Test fun inclusiveDatesUseLocalMidnightAcrossDst() {
        val range = ImportRange(LocalDate.of(2026,3,8), LocalDate.of(2026,3,8), ZoneId.of("America/New_York"))
        val start = OffsetDateTime.parse(range.timeMin).toInstant()
        val end = OffsetDateTime.parse(range.timeMax).toInstant()
        assertEquals(23, Duration.between(start, end).toHours())
    }
    @Test(expected = IllegalArgumentException::class) fun reversedDatesAreRejected() {
        ImportRange(LocalDate.of(2026,1,2), LocalDate.of(2026,1,1), ZoneId.of("UTC"))
    }
    @Test(expected = IllegalArgumentException::class) fun unboundedRangeIsRejected() {
        ImportRange(LocalDate.of(2026,1,1), LocalDate.of(2028,1,1), ZoneId.of("UTC"))
    }
}
