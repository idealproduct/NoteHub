package com.nqutaoitfteikc.notehub

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.exifinterface.media.ExifInterface
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.data.*
import com.nqutaoitfteikc.notehub.photos.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PhotoLocationRepositoryTest {
    @Test fun multipleLocationsAndCaptureTimesSurviveSaveEditReopenAndRemoval() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val token=UUID.randomUUID().toString(); val name="locations-$token.db"
        val folder=File(context.cacheDir,"locations-$token")
        val files=PhotoFiles(folder)
        var db=Room.databaseBuilder(context,NoteDatabase::class.java,name).build()
        val exports=File(context.cacheDir,"location-export-$token.jpg")
        try {
            var repository=RoomPhotoRepository(db,files) {}
            val session=UUID.randomUUID().toString()
            val firstLocation=PhotoLocation(25.033,121.5654,4.5f,1000,true)
            val secondLocation=PhotoLocation(25.04,121.57,8f,2000,true)
            val first=repository.store(session,CapturedImage(photoFixture(),CaptureTime(1100,CaptureTimeSource.CAPTURE_STARTED),0,"UTC",LocationSnapshot(firstLocation,PhotoLocationStatus.RECORDED)))
            val second=repository.store(session,CapturedImage(photoFixture(),CaptureTime(2100,CaptureTimeSource.CAPTURE_STARTED),0,"UTC",LocationSnapshot(secondLocation,PhotoLocationStatus.RECORDED)))
            val note=repository.commit(session,null,"定位測試","延遲儲存","地圖")
            repository.caption(first.id,"修改說明不改位置")
            db.close(); db=Room.databaseBuilder(context,NoteDatabase::class.java,name).build()
            repository=RoomPhotoRepository(db,files) {}
            val photos=repository.observe().first()
            assertEquals(firstLocation,photos.single { it.id==first.id }.location)
            assertEquals(1100L,photos.single { it.id==first.id }.capturedAt)
            assertEquals(secondLocation,photos.single { it.id==second.id }.location)
            assertEquals(2,ObservePhotoMap(repository,RoomNoteRepository(db.notes()))().first().points.size)
            PhotoExporter(context,files).export(photos.first(),android.net.Uri.fromFile(exports))
            ExifInterface(exports).let { assertNull(it.latLong) }
            repository.remove(first.id)
            assertEquals(listOf(second.id),ObservePhotoMap(repository,RoomNoteRepository(db.notes()))().first().points.map { it.photoId })
            db.notes().delete(note)
            assertTrue(ObservePhotoMap(repository,RoomNoteRepository(db.notes()))().first().points.isEmpty())
        } finally {
            db.close(); context.deleteDatabase(name); exports.delete()
            folder.listFiles()?.forEach { it.delete() }; folder.delete()
        }
    }
    @Test fun mismatchedLocationStatusIsRejectedBeforeAnyFileOrRowIsStored() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,NoteDatabase::class.java).build()
        val folder=File(context.cacheDir,"invalid-location-${UUID.randomUUID()}")
        try {
            val repository=RoomPhotoRepository(db,PhotoFiles(folder)) {}
            try { repository.store(UUID.randomUUID().toString(),CapturedImage(photoFixture(),CaptureTime(1000,CaptureTimeSource.CAPTURE_STARTED),0,"UTC",LocationSnapshot(status=PhotoLocationStatus.RECORDED))); fail("狀態不應被接受") }
            catch(_:IllegalArgumentException) { }
            assertTrue(db.photos().all().isEmpty()); assertFalse(folder.exists())
        } finally { db.close() }
    }
}
