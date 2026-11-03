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
    @Test fun poisonedMetadataAndHiddenPayloadCannotLeakThroughExport() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val token=UUID.randomUUID().toString(); val directory=File(context.cacheDir,token)
        val destination=File(context.cacheDir,"clean-$token.jpg"); val files=PhotoFiles(directory)
        try {
            files.write("$token.jpg",photoFixture())
            val source=files.file("$token.jpg")
            ExifInterface(source).apply {
                setLatLong(25.033,121.5654)
                setAttribute(ExifInterface.TAG_MAKE,"PRIVATE-CAMERA")
                setAttribute(ExifInterface.TAG_MODEL,"PRIVATE-MODEL")
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,"2020:09:13 12:26:40")
                setAttribute(ExifInterface.TAG_USER_COMMENT,"PRIVATE-NOTE")
                saveAttributes()
            }
            source.appendBytes("PRIVATE-TRAILING-PAYLOAD".toByteArray())
            val original=source.readBytes()
            val photo=Photo(token,"note","session","$token.jpg",1600000000123,90,"UTC",CaptureTimeSource.CAPTURE_STARTED)
            PhotoExporter(context,files).export(photo,Uri.fromFile(destination))
            val exif=ExifInterface(destination)
            assertNull(exif.latLong)
            listOf(ExifInterface.TAG_MAKE,ExifInterface.TAG_MODEL,ExifInterface.TAG_DATETIME_ORIGINAL,ExifInterface.TAG_USER_COMMENT,ExifInterface.TAG_OFFSET_TIME_ORIGINAL,ExifInterface.TAG_IMAGE_UNIQUE_ID).forEach { assertNull(exif.getAttribute(it)) }
            assertFalse(destination.readBytes().toString(Charsets.ISO_8859_1).contains("PRIVATE-"))
            assertArrayEquals(original,source.readBytes())
            val image=android.graphics.BitmapFactory.decodeFile(destination.path)
            assertNotNull(image); image.recycle()
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete(); destination.delete() }
    }
    @Test fun exportedCopyRemovesCaptureMetadataAndDoesNotModifyOriginal() = runBlocking {
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
            assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            assertNull(exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL))
            assertNull(exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
        } finally { files.file("$id.jpg").delete(); directory.delete(); destination.delete() }
    }
}
