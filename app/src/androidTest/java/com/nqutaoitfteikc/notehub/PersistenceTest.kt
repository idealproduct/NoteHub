package com.nqutaoitfteikc.notehub
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nqutaoitfteikc.notehub.data.*
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    @Test fun unknownStoredThemeFallsBackToSystemAndCanBeChanged() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "theme-fallback-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            store.edit { it[stringPreferencesKey("theme_mode")] = "UNKNOWN_FUTURE_THEME" }
            val repository = LocalSettingsRepository(store)
            assertEquals(ThemeMode.SYSTEM, repository.theme.first())
            repository.setTheme(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, repository.theme.first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
    @Test fun roomPersistsAcrossCloseAndReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, NoteDatabase::class.java, name).build()
        try {
            RoomNoteRepository(db.notes()).insert(Note("id", "保留記事", 42))
            db.close()
            db = Room.databaseBuilder(context, NoteDatabase::class.java, name).build()
            assertEquals(listOf(Note("id", "保留記事", 42)), RoomNoteRepository(db.notes()).observe().first())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun datastorePersistsAcrossInstances() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var repository = LocalSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(ThemeMode.SYSTEM, repository.theme.first())
            repository.setTheme(ThemeMode.DARK)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repository = LocalSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(ThemeMode.DARK, repository.theme.first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
}
