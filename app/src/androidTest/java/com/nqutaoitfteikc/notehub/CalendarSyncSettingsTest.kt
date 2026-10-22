package com.nqutaoitfteikc.notehub

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.calendar.*
import com.nqutaoitfteikc.notehub.domain.GoogleCalendar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class CalendarSyncSettingsTest {
    @Test fun optInFrequencyAndStatusPersistAndReadOnlyCalendarIsRejected() = runBlocking {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "sync-${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var repository = CalendarSyncSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertFalse(repository.flow.first().enabled)
            try { repository.enable("fixture", GoogleCalendar("c", "唯讀", "UTC"), 6); fail("不應接受唯讀行事曆") }
            catch (_: IllegalArgumentException) { }
            repository.enable("fixture", GoogleCalendar("c", "測試", "UTC", "owner"), 12)
            repository.status("測試完成", true)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repository = CalendarSyncSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            val saved = repository.flow.first()
            assertTrue(saved.enabled); assertEquals(12, saved.hours); assertEquals("c", saved.calendar.id)
            assertEquals("測試完成", saved.status); assertTrue(saved.lastSuccess > 0)
            repository.disable()
            assertFalse(repository.flow.first().enabled)
            assertEquals("fixture", repository.flow.first().account)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
}
