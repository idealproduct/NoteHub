package com.nqutaoitfteikc.notehub

import com.nqutaoitfteikc.notehub.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class StabilityRulesTest {
    @Test fun invalidFontPreferencesAlwaysResolveToFiniteSupportedValues() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { assertEquals(1f, FontSizeRules.safe(it), 0f) }
        assertEquals(0.8f, FontSizeRules.safe(-100f), 0f)
        assertEquals(1.5f, FontSizeRules.safe(100f), 0f)
        assertEquals(1.3f, FontSizeRules.safe(1.3f), 0f)
    }
    @Test fun extremeDatesAreRejectedAsValidationErrorsInsteadOfOverflowing() {
        listOf(LocalDate.MIN, LocalDate.MAX, LocalDate.of(0, 1, 1)).forEach { date ->
            try { CalendarRules.resolveLocal(date, LocalTime.NOON, ZoneOffset.UTC); fail("應拒絕超出支援範圍的年份") }
            catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("年份")) }
        }
        assertTrue(CalendarRules.resolveLocal(LocalDate.of(9999, 12, 31), LocalTime.NOON, ZoneOffset.UTC) > 0)
    }
    @Test fun extremeTimedDraftCannotEnterTheDatabaseThroughValidation() {
        try {
            NoteDraft("異常日期", kind = ScheduleKind.TIMED, startMillis = Long.MAX_VALUE - 1, endMillis = Long.MAX_VALUE, zoneId = "UTC").validated()
            fail("應拒絕超出支援範圍的時間")
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("年份")) }
    }
}
