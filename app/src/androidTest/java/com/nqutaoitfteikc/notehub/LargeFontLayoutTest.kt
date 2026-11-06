package com.nqutaoitfteikc.notehub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.nqutaoitfteikc.notehub.ui.*
import com.nqutaoitfteikc.notehub.ui.theme.NoteHubTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import com.nqutaoitfteikc.notehub.domain.CalendarRules

class LargeFontLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun app150PercentAndSystem200PercentWrapPreviewWithinNarrowScreen() {
        compose.setContent {
            val original = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(original.density, 2f)) {
                NoteHubTheme(fontScale = 1.5f) {
                    Surface(Modifier.width(320.dp).height(480.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) { FontSizeSetting(1.5f) {} }
                    }
                }
            }
        }
        compose.onNodeWithTag("font-size-preview").performScrollTo().assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("font-size-preview").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.isNotEmpty()); assertFalse(results.single().hasVisualOverflow)
        assertEquals(3f, results.single().layoutInput.density.fontScale, 0.001f)
        assertTrue(results.single().lineCount > 1)
        compose.onNodeWithText("恢復系統字型大小").performScrollTo().assertIsDisplayed()
    }
    @Test fun largeFontMonthGridKeepsDateDigitsOnOneUnclippedLine() {
        val day = CalendarRules.weekStart(LocalDate.now().withDayOfMonth(1))
        compose.setContent {
            val original = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(original.density, 2f)) {
                NoteHubTheme(fontScale = 1.5f) {
                    Surface(Modifier.width(320.dp).height(480.dp)) { CalendarScreen(HubState(loading = false), {}, {}, { _, _ -> }) }
                }
            }
        }
        compose.onNodeWithTag("calendar-content").performScrollToNode(hasTestTag("day-$day"))
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(day.dayOfMonth.toString()) and hasAnyAncestor(hasTestTag("day-$day")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(1, results.single().lineCount)
        val measured = results.single()
        assertFalse("size=${measured.size}, widthOverflow=${measured.didOverflowWidth}, heightOverflow=${measured.didOverflowHeight}, paragraphHeight=${measured.multiParagraph.height}", measured.hasVisualOverflow)
    }
}
