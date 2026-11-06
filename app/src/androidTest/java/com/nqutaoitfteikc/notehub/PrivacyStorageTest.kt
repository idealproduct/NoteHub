package com.nqutaoitfteikc.notehub

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.data.*
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.photos.*
import com.nqutaoitfteikc.notehub.privacy.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PrivacyStorageTest {
    @Test fun encryptedPhotosAndCoordinatesReopenAndRejectTampering() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val token=UUID.randomUUID().toString(); val alias="notehub-test-$token"; val folder=File(context.cacheDir,token)
        val name="$token.db"; var db=Room.databaseBuilder(context,NoteDatabase::class.java,name).build()
        val vault=LocalVault(alias); val files=PhotoFiles(folder,vault)
        try {
            var repo=RoomPhotoRepository(db,files,vault) {}
            val location=PhotoLocation(25.033,121.5654,4f,1000,true)
            val photo=repo.store(UUID.randomUUID().toString(),CapturedImage(photoFixture(),CaptureTime(1000,CaptureTimeSource.CAPTURE_STARTED),90,"UTC",LocationSnapshot(location,PhotoLocationStatus.RECORDED)))
            val row=requireNotNull(db.photos().get(photo.id))
            assertNull(row.latitude); assertNull(row.longitude); assertNotNull(row.sealedLocation)
            val encrypted=files.file(photo.fileName).readBytes()
            assertTrue(vault.encrypted(encrypted)); assertArrayEquals(photoFixture(),files.read(photo.fileName))
            db.close(); db=Room.databaseBuilder(context,NoteDatabase::class.java,name).build()
            repo=RoomPhotoRepository(db,PhotoFiles(folder,LocalVault(alias)),LocalVault(alias)) {}
            assertEquals(location,repo.observe().first().single().location)
            val damaged=encrypted.copyOf().apply { this[lastIndex]=(last().toInt() xor 1).toByte() }
            files.file(photo.fileName).writeBytes(damaged)
            try { files.read(photo.fileName); fail("竄改密文不得讀取") } catch(_:javax.crypto.AEADBadTagException) { }
            try { vault.open(encrypted,"other-file.jpg"); fail("不可移植密文") } catch(_:javax.crypto.AEADBadTagException) { }
        } finally { db.close(); context.deleteDatabase(name); folder.listFiles()?.forEach { it.delete() }; folder.delete(); java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) } }
    }
    @Test fun legacyPlainPhotosAndCoordinatesUpgradeWithoutChangingCaptureEvidence() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val token=UUID.randomUUID().toString(); val alias="notehub-test-$token"; val folder=File(context.cacheDir,token)
        val db=Room.inMemoryDatabaseBuilder(context,NoteDatabase::class.java).build()
        val plain=PhotoFiles(folder); val vault=LocalVault(alias); val encrypted=PhotoFiles(folder,vault)
        try {
            val old=RoomPhotoRepository(db,plain) {}
            val location=PhotoLocation(25.033,121.5654,4f,1000,true)
            val photo=old.store(UUID.randomUUID().toString(),CapturedImage(photoFixture(),CaptureTime(1001,CaptureTimeSource.CAPTURE_STARTED),0,"UTC",LocationSnapshot(location,PhotoLocationStatus.RECORDED)))
            val new=RoomPhotoRepository(db,encrypted,vault) {}
            repeat(2) { new.recoverFiles() }
            assertTrue(vault.encrypted(plain.file(photo.fileName).readBytes()))
            assertNull(db.photos().get(photo.id)!!.latitude)
            assertEquals(location,new.observe().first().single().location)
            assertEquals(1001L,new.observe().first().single().capturedAt)
            assertArrayEquals(photoFixture(),encrypted.read(photo.fileName))
        } finally { db.close(); folder.listFiles()?.forEach { it.delete() }; folder.delete(); java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) } }
    }
    @Test fun mapResourceBoundaryRejectsUnexpectedDestinationsAndOfflineBlocksAllHttps() {
        val good="https://tiles.openfreemap.org/styles/liberty"
        assertFalse(MapResourceGate.permitted(good,false)); assertTrue(MapResourceGate.permitted(good,true))
        listOf("http://tiles.openfreemap.org/a","https://tiles.openfreemap.org.evil.invalid/a","https://evil@tiles.openfreemap.org/a","file:///data/user/0/private","https://tiles.openfreemap.org:8080/a","https://example.com/?gps=1").forEach { assertFalse(MapResourceGate.permitted(it,true)) }
    }
}
