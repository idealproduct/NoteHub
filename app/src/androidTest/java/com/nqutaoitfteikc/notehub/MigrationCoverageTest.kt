package com.nqutaoitfteikc.notehub

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MigrationCoverageTest {
    @Test fun everyHistoricalSchemaMigratesWithExistingDataIntact() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for (version in 1..4) {
            val name = "migration-all-$version-${UUID.randomUUID()}.db"
            val schema = JSONObject(instrumentation.context.assets.open("com.nqutaoitfteikc.notehub.data.NoteDatabase/$version.json").bufferedReader().readText()).getJSONObject("database")
            context.openOrCreateDatabase(name, 0, null).use { db ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indexes = entity.optJSONArray("indices")
                    if (indexes != null) for (j in 0 until indexes.length()) db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                db.execSQL("INSERT INTO notes(id,title,createdAt) VALUES('retained','升級保留',123456)")
                if (version >= 2) db.execSQL("UPDATE notes SET description='原描述',category='原分類',completed=1,kind='TIMED',startMillis=1000,endMillis=90000000,zoneId='Asia/Taipei' WHERE id='retained'")
                if (version >= 3) db.execSQL("INSERT INTO reminders VALUES('reminder','retained',999999999,'revision',1,NULL,'PENDING')")
                if (version >= 4) {
                    db.execSQL("UPDATE notes SET photoCapturedAt=1000,endMillis=1001 WHERE id='retained'")
                    db.execSQL("INSERT INTO photos VALUES('photo','retained','session','photo.jpg',1000,90,'Asia/Taipei','CAPTURE_STARTED','照片描述')")
                    db.execSQL("INSERT INTO photo_deletions VALUES('pending.jpg')")
                }
                db.version = version
            }
            val db = Room.databaseBuilder(context, NoteDatabase::class.java, name).addMigrations(
                NoteDatabase.MIGRATION_1_2, NoteDatabase.MIGRATION_2_3, NoteDatabase.MIGRATION_3_4, NoteDatabase.MIGRATION_4_5).build()
            try {
                val note = requireNotNull(db.notes().get("retained"))
                assertEquals(5, db.openHelper.readableDatabase.version)
                assertEquals("升級保留", note.title); assertEquals(123456L, note.createdAt)
                if (version >= 2) {
                    assertEquals("原描述", note.description); assertEquals("原分類", note.category); assertTrue(note.completed)
                    assertEquals("TIMED", note.kind); assertEquals(1000L, note.startMillis)
                    assertEquals(if (version >= 4) 1001L else 90000000L, note.endMillis)
                    assertEquals("Asia/Taipei", note.zoneId)
                } else { assertEquals("TODO", note.kind); assertFalse(note.completed) }
                assertEquals(if (version >= 3) 1 else 0, db.reminders().all().size)
                assertEquals(if (version >= 4) 1 else 0, db.photos().all().size)
                if (version >= 4) {
                    assertEquals(1000L, note.photoCapturedAt)
                    assertEquals("照片描述", db.photos().all().single().caption)
                    assertEquals("pending.jpg", db.photos().deletions().single().fileName)
                }
                assertTrue(db.calendarImports().statuses("test-account").isEmpty())
                assertEquals(1, db.notes().update(note.copy(title = "升級後仍可編輯")))
                assertEquals("升級後仍可編輯", db.notes().get("retained")?.title)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }
}
