package com.nqutaoitfteikc.notehub

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderMigrationTest {
    @Test fun versionTwoMigratesWithoutChangingExistingEventsAndCascadesReminders() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "v2-reminders-${UUID.randomUUID()}.db"
        val schema = JSONObject(instrumentation.context.assets.open("com.nqutaoitfteikc.notehub.data.NoteDatabase/2.json").bufferedReader().readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indexes = entity.optJSONArray("indices")
                if (indexes != null) for (j in 0 until indexes.length()) db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO notes(id,title,createdAt) VALUES('v2','保留舊事項',123)")
            db.version = 2
        }
        val db = Room.databaseBuilder(context, NoteDatabase::class.java, name).addMigrations(NoteDatabase.MIGRATION_2_3, NoteDatabase.MIGRATION_3_4, NoteDatabase.MIGRATION_4_5).build()
        try {
            assertEquals("保留舊事項", db.notes().get("v2")?.title)
            db.reminders().insert(ReminderEntity("r1", "v2", 999999, "v1", true, null, "PENDING"))
            db.reminders().insert(ReminderEntity("r2", "v2", 999999, "v2", true, null, "PENDING"))
            assertEquals(2, db.reminders().forNote("v2").size)
            db.notes().delete("v2")
            assertTrue(db.reminders().all().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}


