package com.nqutaoitfteikc.notehub

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal fun photoSnapshotFile(context: Context, token: String) = File(context.cacheDir, "photo-restart-${token.hashCode()}.json")
internal fun photoHash(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input -> val buffer = ByteArray(8192); while (true) { val length = input.read(buffer); if (length < 0) break; digest.update(buffer, 0, length) } }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
internal suspend fun writePhotoSnapshot(context: Context, token: String, container: AppContainer, id: String) {
    val note = container.observeNotes().first().single { it.id == id }
    val photos = container.observePhotos().first().filter { it.noteId == id }
    val array = JSONArray()
    photos.forEach { photo -> array.put(JSONObject().put("id", photo.id).put("capturedAt", photo.capturedAt)
        .put("fileName", photo.fileName).put("caption", photo.caption).put("rotationDegrees", photo.rotationDegrees)
        .put("timeSource", photo.timeSource.name).put("sha256", photoHash(container.photos.files.file(photo.fileName)))) }
    photoSnapshotFile(context, token).writeText(JSONObject().put("noteId", id).put("createdAt", note.createdAt)
        .put("photoCapturedAt", note.photoCapturedAt).put("title", note.title).put("description", note.description).put("photos", array).toString())
}
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PhotoRestartTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as NoteHubApplication).container
    private val token get() = InstrumentationRegistry.getArguments().getString("photoPersistenceRun") ?: defaultToken
    @Test fun aPrepareStorageFixture() = runBlocking {
        val session = UUID.randomUUID().toString()
        container.storePhoto(session, CapturedImage(photoFixture(), CaptureTime(1600000000000, CaptureTimeSource.CAPTURE_STARTED), 90, "Asia/Taipei"))
        container.storePhoto(session, CapturedImage(photoFixture(), CaptureTime(1600000000500, CaptureTimeSource.SENSOR_REALTIME), 0, "Asia/Taipei"))
        val id = container.savePhotoNote(session, null, "持久化驗收-$token", "時間不可改寫", "驗收")
        writePhotoSnapshot(context, token, container, id)
    }
    @Test fun bVerifyAndCleanUp() {
        val expected = JSONObject(photoSnapshotFile(context, token).readText())
        val id = expected.getString("noteId")
        try {
            val note = runBlocking { container.observeNotes().first().single { it.id == id } }
            val photos = runBlocking { container.observePhotos().first().filter { it.noteId == id } }
            assertEquals(expected.getLong("createdAt"), note.createdAt)
            assertEquals(expected.getLong("photoCapturedAt"), note.photoCapturedAt)
            assertEquals(expected.getString("description"), note.description)
            val array = expected.getJSONArray("photos")
            assertEquals(array.length(), photos.size)
            compose.onNodeWithText("照片").performClick()
            compose.onNodeWithTag("photo-hub").performScrollToNode(hasTestTag("photo-note-$id"))
            compose.onNodeWithTag("photo-note-$id").performClick()
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                val photo = photos.single { it.id == row.getString("id") }
                assertEquals(row.getLong("capturedAt"), photo.capturedAt)
                assertEquals(row.getString("caption"), photo.caption)
                assertEquals(row.getInt("rotationDegrees"), photo.rotationDegrees)
                assertEquals(row.getString("timeSource"), photo.timeSource.name)
                val file = container.photos.files.file(photo.fileName)
                assertEquals(row.getString("sha256"), photoHash(file))
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })
                assertNotNull(bitmap); bitmap?.recycle()
                compose.onNodeWithTag("photo-gallery").performScrollToNode(hasTestTag("gallery-photo-${photo.id}"))
                compose.waitUntil(10000) { compose.onAllNodesWithTag("photo-image-${photo.id}").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("photo-gallery").performScrollToNode(hasTestTag("photo-image-${photo.id}"))
                compose.onNodeWithTag("photo-image-${photo.id}").assertIsDisplayed()
            }
        } finally {
            runBlocking { container.deleteNote(id); container.photos.recoverFiles() }
            photoSnapshotFile(context, token).delete()
        }
    }
    companion object { private val defaultToken = "照片程序驗收-${UUID.randomUUID()}" }
}
