package com.nqutaoitfteikc.notehub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nqutaoitfteikc.notehub.ui.PhotoPreviewContent
import com.nqutaoitfteikc.notehub.ui.theme.NoteHubTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhotoPreviewLayoutTest {
    @get:Rule val compose = createComposeRule()
    private fun verify(width: Int, height: Int, fontScale: Float = 1f) {
        var saved = ""
        var closed = false
        compose.setContent {
            val current = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(current.density, fontScale)) {
                NoteHubTheme {
                    Surface(Modifier.size(width.dp, height.dp).testTag("test-preview-window")) {
                        var caption by remember { mutableStateOf("") }
                        // This already-sized virtual window represents available content space;
                        // the device IME's full-window inset must not be subtracted a second time.
                        PhotoPreviewContent(0, caption, { caption = it }, false, { closed = true }, { saved = caption }, contentInsets = WindowInsets(0)) {
                            Box(it.background(MaterialTheme.colorScheme.primaryContainer).testTag("test-preview-image"))
                        }
                    }
                }
            }
        }
        val safe = compose.onNodeWithTag("photo-preview-safe-area").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithTag("save-photo-caption").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val close = compose.onNodeWithTag("close-photo-preview").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(save.left >= safe.left && save.right <= safe.right && save.bottom <= safe.bottom)
        assertTrue(close.top >= safe.top && (close.bottom <= save.top || close.right <= save.left))
        if (width >= 480 && width > height) {
            val image = compose.onNodeWithTag("test-preview-image").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val editor = compose.onNodeWithTag("photo-caption").fetchSemanticsNode().boundsInRoot
            assertTrue("橫向照片與說明必須左右分欄", image.right <= editor.left)
        }
        // Exercise close before editing; this fixture records callbacks instead of dismissing a dialog.
        compose.onNodeWithTag("close-photo-preview").performClick()
        compose.runOnIdle { assertTrue(closed) }
        if (height >= 180) compose.onNodeWithTag("photo-caption").performScrollTo()
        compose.onNodeWithTag("photo-caption").performTextInput("尺寸驗證說明")
        // The save action stays reachable with the real IME open.
        compose.onNodeWithTag("save-photo-caption").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("尺寸驗證說明", saved) }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
    }
    @Test fun narrowPortraitKeepsActionsInsideWindow() = verify(320, 480)
    @Test fun shortAvailableWindowStillAllowsCaptionAndSave() = verify(320, 260)
    @Test fun veryShortKeyboardSpaceUsesSingleActionRow() = verify(320, 140)
    @Test fun largeTextCanScrollEditorWithoutLosingActions() = verify(320, 480, 1.8f)
    @Test fun currentWindowKeepsActionsReachableIncludingLandscape() {
        val configuration = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        verify(configuration.screenWidthDp.coerceAtMost(800), configuration.screenHeightDp.coerceAtMost(600))
    }
}
