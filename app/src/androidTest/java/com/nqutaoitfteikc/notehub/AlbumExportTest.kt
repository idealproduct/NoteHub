package com.nqutaoitfteikc.notehub

import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.photos.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class AlbumExportTest {
    @Test fun albumCopyIsPublishedWithoutCaptureTimeAndOriginalBytesRemainUnchanged() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "album-${UUID.randomUUID()}")
        val files = PhotoFiles(folder)
        val id = UUID.randomUUID().toString()
        val bytes = photoFixture()
        var exported: android.net.Uri? = null
        try {
            files.write("$id.jpg", bytes)
            val photo = Photo(id, "note", "session", "$id.jpg", 1_600_000_000_123, 90, "UTC", CaptureTimeSource.CAPTURE_STARTED)
            exported = PhotoExporter(context, files).toAlbum(photo)
            context.contentResolver.query(exported, arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.IS_PENDING, MediaStore.Images.Media.RELATIVE_PATH), null, null, null)!!.use { row ->
                assertTrue(row.moveToFirst()); assertNotEquals(photo.capturedAt, row.getLong(0)); assertEquals(0, row.getInt(1))
                assertEquals("Pictures/NoteHub/", row.getString(2))
            }
            context.contentResolver.openInputStream(exported)!!.use { input ->
                val exif = ExifInterface(input)
                assertEquals(ExifInterface.ORIENTATION_ROTATE_90, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
                assertNull(exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL))
            }
            assertArrayEquals(bytes, files.file(photo.fileName).readBytes())
        } finally { exported?.let { context.contentResolver.delete(it, null, null) }; files.file("$id.jpg").delete(); folder.delete() }
    }
    @Test fun missingSourceRemovesItsIncompleteMediaStoreEntry() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val time = System.currentTimeMillis() + java.util.Random().nextInt(100000)
        val photo = Photo(id, "note", "session", "$id.jpg", time, 0, "UTC", CaptureTimeSource.CAPTURE_STARTED)
        fun count()=context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Images.Media._ID),"${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",arrayOf("NoteHub_%"),null)!!.use { it.count }
        val before=count()
        try { PhotoExporter(context, PhotoFiles(File(context.cacheDir, "absent-$id"))).toAlbum(photo); fail("遺失的原檔不應匯出成功") }
        catch (_: java.io.IOException) { }
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("NoteHub_%"), null)!!.use { assertEquals(before, it.count) }
    }
}
