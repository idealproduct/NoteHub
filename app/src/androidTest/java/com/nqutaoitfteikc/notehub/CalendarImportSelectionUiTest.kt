package com.nqutaoitfteikc.notehub

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.ui.CalendarImportEventCard
import org.junit.Rule
import org.junit.Test

// Component fixtures only; these tests do not claim Google authentication or API success.
class CalendarImportSelectionUiTest {
    @get:Rule val compose = createComposeRule()
    private fun event(deleted: Boolean) = CalendarImportEvent("fixture", "event", "event",
        NoteDraft("測試副本", kind = ScheduleKind.ALL_DAY, startDay = 1, endDayExclusive = 2),
        alreadyImported = true, locallyDeleted = deleted)
    @Test fun deletedCopyStartsUncheckedAndCanBeCheckedAndUnchecked() {
        compose.setContent {
            var checked by remember { mutableStateOf(false) }
            MaterialTheme { CalendarImportEventCard(event(true), checked, false) { checked = it } }
        }
        compose.onNode(isToggleable()).assertIsOff().assertIsEnabled().performClick().assertIsOn()
        compose.onNodeWithText("本機副本已刪除，可勾選重新匯入").assertIsDisplayed()
        compose.onNode(isToggleable()).performClick().assertIsOff()
    }
    @Test fun existingLocalCopyCannotBeSelectedForOverwrite() {
        compose.setContent { MaterialTheme { CalendarImportEventCard(event(false), false, false) { error("不應可選取") } } }
        compose.onNode(isToggleable()).assertIsOff().assertIsNotEnabled()
        compose.onNodeWithText("已匯入，將跳過").assertIsDisplayed()
    }
}
