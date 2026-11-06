package com.nqutaoitfteikc.notehub

import android.content.Intent
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.achievements.*
import com.nqutaoitfteikc.notehub.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AchievementExportTest {
    @Test fun retiredAutoExportDoesNotWriteAndExplicitShareIsPngReadOnly() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,NoteDatabase::class.java).build()
        val m=8000000000L+java.util.Random().nextInt(1000000)
        val exporter=AchievementExporter(context,db.achievements())
        try {
            db.achievements().unlock(AchievementEntity(m,123))
            val row=requireNotNull(db.achievements().get(m))
            assertNull(row.albumUri); assertNull(row.svgUri); assertFalse(row.exported)
            @Suppress("DEPRECATION") val send=exporter.shareIntent(m,false).getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals("image/png",send.type)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,send.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            val uri=send.clipData!!.getItemAt(0).uri
            context.contentResolver.openInputStream(uri)!!.use { val image=android.graphics.BitmapFactory.decodeStream(it); assertNotNull(image); image.recycle() }
            try { exporter.shareIntent(m,true); fail("SVG 不可下載或分享") } catch(_:IllegalArgumentException) { }
            assertFalse(File(context.filesDir,"achievement-share/NoteHub-$m.svg").exists())
        } finally { File(context.filesDir,"achievement-share/NoteHub-$m.png").delete(); db.close() }
    }
    @Test fun unearnedAchievementCannotBeShared() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,NoteDatabase::class.java).build()
        try { try { AchievementExporter(context,db.achievements()).shareIntent(1,false); fail("未達成不可分享") } catch(_:IllegalArgumentException) { } }
        finally { db.close() }
    }
}
