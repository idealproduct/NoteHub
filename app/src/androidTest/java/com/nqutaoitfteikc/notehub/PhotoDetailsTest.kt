package com.nqutaoitfteikc.notehub

import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.photos.PhotoFiles
import com.nqutaoitfteikc.notehub.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PhotoDetailsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun detailsShowPhotosDirectlyAndOfferPreviewExportAndReminders() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "details-${UUID.randomUUID()}")
        val files = PhotoFiles(directory)
        val id = UUID.randomUUID().toString()
        files.write("$id.jpg", photoFixture())
        val photo = Photo(id, "n", "s", "$id.jpg", 1600000000000, 0, "UTC", CaptureTimeSource.CAPTURE_STARTED)
        val note = PhotoNoteRules.create("n", "照片記事", "說明", "", listOf(photo))
        var opened: String? = null
        var reminders = false
        try {
            compose.setContent { Surface(Modifier.fillMaxSize()) {
                NoteDetails(note, false, 2, {}, {}, { reminders = true }, { opened = it }, {}, listOf(photo), files, onExport = {})
            } }
            compose.onNodeWithText("查看照片").assertDoesNotExist()
            compose.onNodeWithTag("note-details").performScrollToNode(hasTestTag("open-reminders-n"))
            compose.onNodeWithTag("open-reminders-n").performClick()
            compose.runOnIdle { assertTrue(reminders) }
            compose.onNodeWithTag("note-details").performScrollToNode(hasTestTag("export-photo-$id"))
            compose.onNodeWithText("儲存至相簿").assertIsDisplayed()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("photo-image-$id").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("photo-image-$id").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(id, opened) }
        } finally { files.file("$id.jpg").delete(); directory.delete() }
    }
}
