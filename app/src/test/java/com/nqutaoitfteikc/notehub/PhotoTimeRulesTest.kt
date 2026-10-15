package com.nqutaoitfteikc.notehub

import com.nqutaoitfteikc.notehub.domain.*
import org.junit.Assert.*
import org.junit.Test
class PhotoTimeRulesTest {
    private val clock = CaptureClock(1700000000000L, 100000000000L)
    @Test fun sensorTimestampIsMappedFromCaptureClockWithoutSaveTime() {
        val result = CaptureTimeRules.resolve(clock, clock.elapsedNanos + 12000000L, true)
        assertEquals(clock.wallMillis + 12, result.millis)
        assertEquals(CaptureTimeSource.SENSOR_REALTIME, result.source)
        assertEquals(result, CaptureTimeRules.resolve(clock, clock.elapsedNanos + 12000000L, true))
    }
    @Test fun unknownOrInvalidClockFallsBackToCaptureStartedNotSavedTime() {
        for (result in listOf(CaptureTimeRules.resolve(clock, 0, true), CaptureTimeRules.resolve(clock, 900, false), CaptureTimeRules.resolve(clock, Long.MAX_VALUE, true))) {
            assertEquals(clock.wallMillis, result.millis)
            assertEquals(CaptureTimeSource.CAPTURE_STARTED, result.source)
        }
    }
    @Test fun articleUsesEarliestCaptureAndDoesNotDependOnStorageOrder() {
        val photos = listOf(
            Photo("1", null, "draft", "1.jpg", 2000, 0, "Asia/Taipei", CaptureTimeSource.CAPTURE_STARTED),
            Photo("2", null, "draft", "2.jpg", 1000, 90, "Asia/Taipei", CaptureTimeSource.SENSOR_REALTIME))
        val note = PhotoNoteRules.create("note", "照片", "延遲儲存", "生活", photos)
        assertEquals(1000L, note.createdAt)
        assertEquals(1000L, note.photoCapturedAt)
        assertEquals(1000L, note.startMillis)
        assertEquals(1001L, note.endMillis)
        assertEquals(ScheduleKind.TIMED, note.kind)
    }
}
