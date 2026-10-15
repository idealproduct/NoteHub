package com.nqutaoitfteikc.notehub

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.data.*
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MigrationCrudTest {
    @Test fun migratesVersionOneAndPreservesCrudAcrossReopens() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        // Exact v1 schema and identity from the committed Room schema JSON.
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS notes (id TEXT NOT NULL, title TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES(42, '398c4a446df70a8349f8997144e3bf4b')")
            db.execSQL("INSERT INTO notes VALUES('legacy', '舊版記事', 123)")
            db.version = 1
        }
        fun open() = Room.databaseBuilder(context, NoteDatabase::class.java, name).addMigrations(NoteDatabase.MIGRATION_1_2, NoteDatabase.MIGRATION_2_3, NoteDatabase.MIGRATION_3_4, NoteDatabase.MIGRATION_4_5).build()
        var db = open()
        try {
            var repository = RoomNoteRepository(db.notes())
            assertEquals(Note("legacy", "舊版記事", 123), repository.get("legacy"))
            SaveNote(repository)("legacy", NoteDraft("修改標題", "內容", "工作", ScheduleKind.ALL_DAY, 20000, 20003))
            SetCompleted(repository)("legacy", true)
            SaveNote(repository)(null, NoteDraft("待刪除"))
            val deletedId = repository.observe().first().single { it.title == "待刪除" }.id
            DeleteNote(repository)(deletedId)
            db.close()
            db = open()
            repository = RoomNoteRepository(db.notes())
            val saved = requireNotNull(repository.get("legacy"))
            assertEquals(123L, saved.createdAt)
            assertEquals("修改標題", saved.title)
            assertEquals("內容", saved.description)
            assertEquals("工作", saved.category)
            assertTrue(saved.completed)
            assertEquals(20003L, saved.endDayExclusive)
            assertNull(repository.get(deletedId))
            SetCompleted(repository)("legacy", false)
            SaveNote(repository)("legacy", NoteDraft("無時間待辦"))
            assertFalse(requireNotNull(repository.get("legacy")).completed)
            assertNull(requireNotNull(repository.get("legacy")).startDay)
            assertEquals(1, repository.observe().first().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}



