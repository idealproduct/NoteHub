package com.nqutaoitfteikc.notehub
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nqutaoitfteikc.notehub.domain.ThemeMode
import com.nqutaoitfteikc.notehub.ui.SettingsScreen
import com.nqutaoitfteikc.notehub.ui.theme.NoteHubTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class SettingsTabsUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun shortScreenWithCombinedThreeHundredPercentFontKeepsTabsAndActionsAccessible() {
        var imported=false; var synced=false
        compose.setContent {
            val base=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density,2f)) {
                NoteHubTheme(fontScale=1.5f) { Box(Modifier.requiredSize(320.dp,360.dp)) {
                    SettingsScreen(ThemeMode.SYSTEM,{},1.5f,{}, {imported=true},{synced=true}) {}
                } }
            }
        }
        compose.onNodeWithTag("settings-tab-2").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-content").performScrollToNode(hasText("Google 行事曆匯入"))
        compose.onNodeWithText("Google 行事曆匯入").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(imported); assertFalse(synced) }
        compose.onNodeWithTag("settings-tab-3").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-content").performScrollToNode(hasTestTag("storage-capacity"))
        compose.onNodeWithTag("storage-capacity").assertIsDisplayed()
        compose.onNodeWithTag("settings-tab-0").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-content").performScrollToNode(hasText("跟隨系統"))
        compose.onNodeWithText("跟隨系統").assertIsDisplayed()
    }
}
