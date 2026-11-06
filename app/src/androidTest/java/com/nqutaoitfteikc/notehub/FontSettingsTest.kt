package com.nqutaoitfteikc.notehub

import androidx.datastore.preferences.core.*
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.data.LocalSettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class FontSettingsTest {
    @Test fun fontPreferencePersistsAndInvalidStoredFloatFallsBackSafely() = runBlocking {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "font-${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            var repository = LocalSettingsRepository(store)
            store.edit { it[floatPreferencesKey("font_scale")] = Float.NaN }
            assertEquals(1f, repository.fontScale.first(), 0f)
            repository.setFontScale(1.5f)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repository = LocalSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(1.5f, repository.fontScale.first(), 0f)
            try { repository.setFontScale(Float.POSITIVE_INFINITY); fail("不應儲存無效大小") } catch (_: IllegalArgumentException) { }
            assertEquals(1.5f, repository.fontScale.first(), 0f)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
}
