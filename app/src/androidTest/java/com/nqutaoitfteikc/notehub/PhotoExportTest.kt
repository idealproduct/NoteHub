package com.nqutaoitfteikc.notehub

import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.photos.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PhotoExportTest {
    @Test fun exportedCopyPreservesCaptureMetadataAndDoesNotModifyOriginal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "export-test-${UUID.randomUUID()}")
        val destination = File(context.cacheDir, "export-copy-${UUID.randomUUID()}.jpg")
        val files = PhotoFiles(directory)
        val id = UUID.randomUUID().toString()
        val original = photoFixture()
        try {
            files.write("$id.jpg", original)
            val photo = Photo(id, "note", "session", "$id.jpg", 1_600_000_000_123, 90, "UTC", CaptureTimeSource.CAPTURE_STARTED)
            PhotoExporter(context, files).export(photo, Uri.fromFile(destination))
            assertTrue(destination.length() > 0)
            assertArrayEquals(original, files.file(photo.fileName).readBytes())
            val exif = ExifInterface(destination.absolutePath)
            assertEquals(ExifInterface.ORIENTATION_ROTATE_90, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
            assertEquals("2020:09:13 12:26:40", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            assertEquals("123", exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL))
            assertEquals("+00:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
        } finally { files.file("$id.jpg").delete(); directory.delete(); destination.delete() }
    }
}
