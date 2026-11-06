package com.nqutaoitfteikc.notehub

import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DatabaseStartupTest {
    @Test fun productionOpenCallbackAndAllTablesRemainReadableAfterReopen() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="startup-${UUID.randomUUID()}.db"
        var db=NoteDatabase.open(context,name)
        try {
            db.notes().insert(NoteEntity("retained","啟動回歸",123))
            repeat(2) {
                assertEquals(1,db.notes().observe().first().size)
                assertTrue(db.photos().all().isEmpty()); assertTrue(db.reminders().all().isEmpty())
                assertTrue(db.achievements().all().isEmpty())
                db.openHelper.writableDatabase.query("PRAGMA secure_delete").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(1,cursor.getInt(0)) }
                db.close(); db=NoteDatabase.open(context,name)
            }
            assertEquals("啟動回歸",db.notes().get("retained")?.title)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
