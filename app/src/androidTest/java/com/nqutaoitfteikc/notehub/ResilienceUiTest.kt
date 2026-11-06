package com.nqutaoitfteikc.notehub

import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.reminders.ReminderPlatform
import com.nqutaoitfteikc.notehub.ui.HubViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ResilienceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = (compose.activity.application as NoteHubApplication).container
    private fun model() = ViewModelProvider(compose.activity, HubViewModel.factory(app))[HubViewModel::class.java]
    @Test fun malformedNotificationRouteShowsAnErrorWithoutCrashingNavigation() {
        compose.runOnIdle { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .callActivityOnNewIntent(compose.activity, Intent(compose.activity.intent).putExtra(ReminderPlatform.EXTRA_NOTE_ID, "missing/../../#?invalid")) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("app-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("calendar-content").assertExists()
        compose.onNodeWithText("此事項已不存在，或通知連結無效").assertExists()
        compose.onNodeWithText("關閉訊息").performClick()
    }
    @Test fun repeatedBatchRequestsDoNotDuplicateAndMissingPhotoDoesNotStopOtherExports() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val session = UUID.randomUUID().toString()
        val time = System.currentTimeMillis() + 100000 + java.util.Random().nextInt(100000)
        var noteId: String? = null
        try {
            val photos = runBlocking {
                (0..2).map { app.storePhoto(session, CapturedImage(photoFixture(), CaptureTime(time + it, CaptureTimeSource.CAPTURE_STARTED), 0, "UTC")) }
            }
            noteId = runBlocking { app.savePhotoNote(session, null, "批次穩定度-${UUID.randomUUID()}", "", "") }
            app.photos.files.file(photos[1].fileName).delete()
            compose.waitUntil(10000) { model().state.value.photos.count { it.noteId == noteId } == 3 }
            compose.runOnIdle { repeat(20) { model().exportPhotos(photos.map { it.id }) } }
            compose.waitUntil(20000) { !model().state.value.exportBusy && model().state.value.exportMessage != null }
            assertTrue(model().state.value.exportMessage!!.contains("已儲存 2 張，1 張失敗"))
            val resolver = compose.activity.contentResolver
            resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("NoteHub_${time}_%"), null)!!.use { assertEquals(1, it.count) }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) { model().state.value.exportMessage != null }
            assertTrue(model().state.value.exportMessage!!.contains("已儲存 2 張"))
        } finally {
            val resolver = compose.activity.contentResolver
            for (offset in 0..2) resolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("NoteHub_${time + offset}_%"))
            runBlocking { noteId?.let { app.deleteNote(it) }; app.discardPhotoDraft(session); app.photos.recoverFiles() }
            compose.runOnIdle { model().clearExportMessage() }
        }
    }
    @Test fun fontMenuPersistsChoiceAcrossRecreationAndCanRestoreSystemSize() {
        val original = runBlocking { app.observeFontScale.first() }
        try {
            compose.onNodeWithText("設定", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("settings-content").performScrollToNode(hasTestTag("font-size-menu"))
            compose.onNodeWithTag("font-size-menu").performClick()
            compose.onNodeWithTag("font-size-150").performClick()
            compose.waitUntil(10000) { model().state.value.fontScale == 1.5f }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("settings-content").performScrollToNode(hasTestTag("font-size-menu"))
            compose.onNodeWithTag("font-size-menu").assertTextContains("150%", substring = true)
            compose.onNodeWithText("恢復系統字型大小").performScrollTo().performClick()
            compose.waitUntil(10000) { model().state.value.fontScale == 1f }
        } finally { runBlocking { app.setFontScale(original) } }
    }
}
