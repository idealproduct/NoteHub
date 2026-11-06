package com.nqutaoitfteikc.notehub

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.ui.PhotoBatchExport
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BatchExportUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun userCanChooseMultiplePhotosWithinOneNoteAndSelectAll() {
        val photos = (1..3).map { Photo("p$it", "note", "session", "unused.jpg", 1600000000000 + it, 0, "UTC", CaptureTimeSource.CAPTURE_STARTED) }
        var exported = emptyList<String>()
        compose.setContent { Surface(Modifier.width(320.dp)) { PhotoBatchExport(photos, false) { exported = it } } }
        compose.onNodeWithTag("batch-export-toggle").performClick()
        compose.onNodeWithTag("export-selected").assertIsNotEnabled()
        compose.onNodeWithTag("select-export-p1").performClick()
        compose.onNodeWithTag("select-export-p3").performScrollTo().performClick()
        compose.onNodeWithTag("export-selected").performClick()
        compose.runOnIdle { assertTrue(exported.isEmpty()) }
        compose.onNodeWithTag("confirm-private-export").performClick()
        compose.runOnIdle { assertEquals(listOf("p1", "p3"), exported) }
        compose.onNodeWithText("全選").performClick()
        compose.onNodeWithTag("export-selected").performClick()
        compose.onNodeWithTag("confirm-private-export").performClick()
        compose.runOnIdle { assertEquals(listOf("p1", "p2", "p3"), exported) }
        compose.onNodeWithText("取消全選").performClick()
        compose.onNodeWithTag("export-selected").assertIsNotEnabled()
    }
    @Test fun largeNoteLoadsOnlyVisibleSelectionRowsAndCanSelectLastPhoto() {
        val photos = (1..1000).map { Photo("p$it", "note", "session", "unused.jpg", 1600000000000 + it, 0, "UTC", CaptureTimeSource.CAPTURE_STARTED) }
        var exported = emptyList<String>()
        compose.setContent { Surface(Modifier.width(320.dp)) { PhotoBatchExport(photos, false) { exported = it } } }
        compose.onNodeWithTag("batch-export-toggle").performClick()
        compose.onNodeWithTag("select-export-p1000").assertDoesNotExist()
        compose.onNodeWithTag("batch-photo-list").performScrollToNode(hasTestTag("select-export-p1000"))
        compose.onNodeWithTag("select-export-p1000").performClick()
        compose.onNodeWithTag("export-selected").performClick()
        compose.onNodeWithTag("confirm-private-export").performClick()
        compose.runOnIdle { assertEquals(listOf("p1000"), exported) }
    }
}
