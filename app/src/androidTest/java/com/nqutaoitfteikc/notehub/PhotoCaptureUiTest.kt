package com.nqutaoitfteikc.notehub

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhotoCaptureUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as NoteHubApplication).container
    private fun photos() = runBlocking { container.observePhotos().first() }
    private fun notes() = runBlocking { container.observeNotes().first() }
    private fun cameraReady() { compose.waitUntil(30000) { compose.onAllNodes(hasTestTag("take-photo") and isEnabled()).fetchSemanticsNodes().isNotEmpty() } }
    private fun captureOne(): Photo {
        compose.onNodeWithTag("camera-content").performScrollToNode(hasTestTag("take-photo"))
        cameraReady()
        val ids = photos().map { it.id }.toSet()
        val before = System.currentTimeMillis()
        compose.onNodeWithTag("take-photo").performClick()
        compose.waitUntil(30000) { photos().any { it.id !in ids } }
        val photo = photos().single { it.id !in ids }
        assertTrue(photo.capturedAt in (before - 1000)..System.currentTimeMillis())
        assertTrue(container.photos.files.file(photo.fileName).length() > 0)
        return photo
    }
    @Test fun realCameraMultiplePhotosDescriptionAppendAndPreview() {
        assumeTrue("實際拍攝測試需相機授權", context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        val args = InstrumentationRegistry.getArguments()
        val token = args.getString("photoPersistenceRun") ?: UUID.randomUUID().toString()
        val keep = args.getString("keepPhotoData") == "true"
        val title = "相機實拍-$token"
        var noteId: String? = null
        var session: String? = null
        var appendSession: String? = null
        var success = false
        try {
            compose.onNodeWithText("照片").performClick()
            compose.onNodeWithTag("new-photo-note").performClick()
            val first = captureOne(); session = first.sessionId
            val second = captureOne()
            assertEquals(first.sessionId, second.sessionId)
            // Real JPEG captures are already durable before any note save.
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("camera-content").performScrollToNode(hasTestTag("photo-note-title"))
            compose.onNodeWithTag("photo-note-title").performTextInput(title)
            compose.onNodeWithTag("camera-content").performScrollToNode(hasTestTag("photo-note-description"))
            compose.onNodeWithTag("photo-note-description").performTextInput("拍完之後才輸入的描述")
            val saveAt = System.currentTimeMillis()
            compose.onNodeWithTag("camera-content").performScrollToNode(hasTestTag("save-photo-note"))
            compose.onNodeWithTag("save-photo-note").performClick()
            compose.waitUntil(10000) { notes().any { it.title == title } }
            noteId = notes().single { it.title == title }.id
            val originalTime = minOf(first.capturedAt, second.capturedAt)
            assertTrue(saveAt > originalTime)
            assertEquals(originalTime, notes().single { it.id == noteId }.createdAt)
            assertEquals(originalTime, notes().single { it.id == noteId }.photoCapturedAt)
            compose.onNodeWithTag("photo-gallery").performScrollToNode(hasTestTag("preview-photo-${second.id}"))
            compose.onNodeWithTag("preview-photo-${second.id}").performClick()
            compose.onNodeWithTag("photo-caption").performTextInput("第二張照片說明")
            compose.onNodeWithTag("save-photo-caption").performClick()
            compose.waitUntil(10000) { photos().single { it.id == second.id }.caption == "第二張照片說明" }
            compose.onNodeWithTag("photo-gallery").performScrollToNode(hasText("編輯記事描述"))
            compose.onNodeWithText("編輯記事描述").performClick()
            compose.onNodeWithTag("editor-description").performTextReplacement("修改過的記事描述")
            compose.onNodeWithTag("editor-save").performClick()
            compose.waitUntil(10000) { notes().single { it.id == noteId }.description == "修改過的記事描述" }
            assertEquals(originalTime, notes().single { it.id == noteId }.photoCapturedAt)
            compose.onNodeWithTag("photo-gallery").performScrollToNode(hasTestTag("append-photos"))
            compose.onNodeWithTag("append-photos").performClick()
            val third = captureOne(); appendSession = third.sessionId
            compose.onNodeWithTag("camera-content").performScrollToNode(hasTestTag("save-photo-note"))
            compose.onNodeWithTag("save-photo-note").performClick()
            compose.waitUntil(10000) { photos().count { it.noteId == noteId } == 3 }
            assertEquals(originalTime, notes().single { it.id == noteId }.photoCapturedAt)
            compose.onNodeWithTag("photo-gallery").performScrollToNode(hasTestTag("remove-photo-${first.id}"))
            compose.onNodeWithTag("remove-photo-${first.id}").performClick()
            compose.onNodeWithText("確認移除照片").performClick()
            compose.waitUntil(10000) { photos().none { it.id == first.id } }
            assertEquals(originalTime, notes().single { it.id == noteId }.photoCapturedAt)
            assertEquals(second.capturedAt, photos().single { it.id == second.id }.capturedAt)
            runBlocking { writePhotoSnapshot(context, token, container, requireNotNull(noteId)) }
            success = true
        } finally {
            if (!keep || !success) {
                runBlocking {
                    noteId?.let { container.deleteNote(it) }
                    session?.let { container.discardPhotoDraft(it) }
                    appendSession?.let { container.discardPhotoDraft(it) }
                    container.photos.recoverFiles()
                }
                photoSnapshotFile(context, token).delete()
            }
        }
    }
}
