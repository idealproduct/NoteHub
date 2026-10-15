package com.nqutaoitfteikc.notehub

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.data.*
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.photos.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

internal fun photoFixture(): ByteArray = ByteArrayOutputStream().use { output ->
    val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
    try { bitmap.eraseColor(Color.BLUE); check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)); output.toByteArray() }
    finally { bitmap.recycle() }
}
@RunWith(AndroidJUnit4::class)
class PhotoRepositoryTest {
    @Test fun delayedCommitDescriptionAppendAndRemovalPreserveCaptureTime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "photos-${UUID.randomUUID()}.db"
        val directory = File(context.cacheDir, "photos-${UUID.randomUUID()}")
        val database = Room.databaseBuilder(context, NoteDatabase::class.java, name).build()
        var requests = 0
        val repository = RoomPhotoRepository(database, PhotoFiles(directory)) { requests++ }
        try {
            val session = UUID.randomUUID().toString()
            val first = repository.store(session, CapturedImage(photoFixture(), CaptureTime(100000, CaptureTimeSource.CAPTURE_STARTED), 90, "Asia/Taipei"))
            val second = repository.store(session, CapturedImage(photoFixture(), CaptureTime(200000, CaptureTimeSource.SENSOR_REALTIME), 0, "Asia/Taipei"))
            val id = repository.commit(session, null, "照片記事", "延後儲存", "生活")
            assertEquals(id, repository.commit(session, null, "重複點擊", "", ""))
            val note = requireNotNull(database.notes().get(id))
            assertEquals(100000L, note.createdAt); assertEquals(100000L, note.photoCapturedAt)
            SaveNote(RoomNoteRepository(database.notes()))(id, NoteDraft("修改描述", "另一段描述"))
            assertEquals(100000L, database.notes().get(id)?.startMillis)
            repository.caption(second.id, "保留說明")
            val append = UUID.randomUUID().toString()
            repository.store(append, CapturedImage(photoFixture(), CaptureTime(300000, CaptureTimeSource.CAPTURE_STARTED), 0, "Asia/Taipei"))
            repository.commit(append, id, "修改描述", "另一段描述", "生活")
            assertEquals(100000L, database.notes().get(id)?.photoCapturedAt)
            repository.remove(first.id)
            repository.recoverFiles()
            assertFalse(repository.files.file(first.fileName).exists())
            assertEquals(2, repository.observe().first().count { it.noteId == id })
            assertEquals(100000L, database.notes().get(id)?.photoCapturedAt)
            repository.deleteNoteInTransaction(id) { database.notes().delete(id) }
            repository.recoverFiles()
            assertTrue(repository.observe().first().isEmpty())
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            assertTrue(requests >= 2)
        } finally { database.close(); context.deleteDatabase(name); directory.listFiles().orEmpty().forEach { it.delete() }; directory.delete() }
    }
    @Test fun draftSurvivesCleanupAndFailedDeletionCanRetry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cleanup-${UUID.randomUUID()}.db"
        val directory = File(context.cacheDir, "cleanup-${UUID.randomUUID()}")
        val db = Room.databaseBuilder(context, NoteDatabase::class.java, name).build()
        val repository = RoomPhotoRepository(db, PhotoFiles(directory)) { }
        try {
            val session = UUID.randomUUID().toString()
            val photo = repository.store(session, CapturedImage(photoFixture(), CaptureTime(123000, CaptureTimeSource.CAPTURE_STARTED), 0, "Asia/Taipei"))
            val orphan = File(directory, "${UUID.randomUUID()}.jpg").apply { writeBytes(photoFixture()) }
            repository.recoverFiles()
            assertFalse(orphan.exists()); assertTrue(repository.files.file(photo.fileName).isFile)
            val blockedName = "${UUID.randomUUID()}.jpg"
            val blocked = repository.files.file(blockedName).apply { mkdir() }
            val child = File(blocked, "blocked").apply { writeText("test") }
            db.photos().enqueueDeletion(PhotoDeletionEntity(blockedName))
            try { repository.recoverFiles(); fail("非空目錄應阻止刪除") } catch (_: IOException) { }
            assertEquals(1, db.photos().pendingCleanupCount().first())
            child.delete(); blocked.delete()
            repository.recoverFiles()
            assertEquals(0, db.photos().pendingCleanupCount().first())
            repository.discard(session); repository.recoverFiles()
            assertFalse(repository.files.file(photo.fileName).exists())
            try { repository.files.file("../outside.jpg"); fail("應拒絕越界路徑") } catch (_: IllegalArgumentException) { }
            try { repository.store(UUID.randomUUID().toString(), CapturedImage(byteArrayOf(1,2,3), CaptureTime(123000, CaptureTimeSource.CAPTURE_STARTED), 0, "Asia/Taipei")); fail("應拒絕假 JPEG") } catch (_: IllegalArgumentException) { }
        } finally { db.close(); context.deleteDatabase(name); directory.listFiles().orEmpty().forEach { it.delete() }; directory.delete() }
    }
    @Test fun versionThreeMigratesAndPreservesNotesAndReminders() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "v3-${UUID.randomUUID()}.db"
        val schema = JSONObject(instrumentation.context.assets.open("com.nqutaoitfteikc.notehub.data.NoteDatabase/3.json").bufferedReader().readText()).getJSONObject("database")
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
            db.execSQL("INSERT INTO notes(id,title,createdAt) VALUES('v3','舊記事',123)")
            db.execSQL("INSERT INTO reminders VALUES('r','v3',999999,'rev',1,NULL,'PENDING')")
            db.version = 3
        }
        val db = Room.databaseBuilder(context, NoteDatabase::class.java, name).addMigrations(NoteDatabase.MIGRATION_3_4, NoteDatabase.MIGRATION_4_5).build()
        try {
            assertEquals("舊記事", db.notes().get("v3")?.title)
            assertNull(db.notes().get("v3")?.photoCapturedAt)
            assertEquals(1, db.reminders().all().size)
            assertTrue(db.photos().all().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

