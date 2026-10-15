package com.nqutaoitfteikc.notehub

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/** Opt-in, read-only verification of data imported via the real Google UI. Never creates/deletes notes. */
class CalendarImportPersistenceTest {
    private fun proofFile(): File {
        val run = InstrumentationRegistry.getArguments().getString("googlePersistenceRun")
        assumeTrue("僅由 Test-GoogleImportPersistence.ps1 明確啟用", run != null)
        val token = UUID.fromString(run).toString()
        return File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "google-proof-$token.json")
    }
    private fun snapshot(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val digest = MessageDigest.getInstance("SHA-256")
        var sources = 0
        var notes = 0
        SQLiteDatabase.openDatabase(context.getDatabasePath("notehub.db").absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT i.account,i.calendarId,i.instanceKey,i.eventId,i.noteId,i.importedAt,n.id,n.title,n.createdAt,n.description,n.category,n.completed,n.kind,n.startDay,n.endDayExclusive,n.startMillis,n.endMillis,n.zoneId,n.photoCapturedAt FROM calendar_imports i LEFT JOIN notes n ON n.id=i.noteId ORDER BY i.account,i.calendarId,i.instanceKey", null).use { rows ->
                while (rows.moveToNext()) {
                    sources++
                    if (!rows.isNull(6)) notes++
                    for (column in 0 until rows.columnCount) {
                        val value = if (rows.isNull(column)) null else rows.getString(column).toByteArray(Charsets.UTF_8)
                        digest.update(ByteBuffer.allocate(4).putInt(value?.size ?: -1).array())
                        if (value != null) digest.update(value)
                    }
                }
            }
        }
        println("真實匯入來源數=$sources，仍存在的本機副本數=$notes（只讀驗證）")
        assertTrue("需至少一筆真實匯入來源及仍存在的記事副本", sources > 0 && notes > 0)
        return JSONObject().put("sources", sources).put("notes", notes)
            .put("sha256", digest.digest().joinToString("") { "%02x".format(it) }).toString()
    }
    @Test fun aSnapshot() { val file = proofFile(); file.writeText(snapshot()) }
    @Test fun bVerifyAfterColdRestart() {
        val file = proofFile()
        try { assertEquals("冷啟動後來源與記事內容應完全相同", file.readText(), snapshot()) }
        finally { file.delete() }
    }
}
