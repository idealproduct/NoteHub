package com.nqutaoitfteikc.notehub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CalendarSyncUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun syncSettingsAreReachableAndNavigationDoesNotEnableWrites() {
        val app = (compose.activity.application as NoteHubApplication).container
        val before = runBlocking { app.syncSettings.flow.first() }
        compose.onNodeWithText("設定", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("settings-tab-2").performClick()
        compose.onNodeWithTag("settings-content").performScrollToNode(hasText("Google 雙向定期同步"))
        compose.onNodeWithText("Google 雙向定期同步").performClick()
        compose.onNodeWithText("Google 雙向同步").assertIsDisplayed()
        compose.onNodeWithTag("sync-content").performScrollToNode(hasText("授權 Google 事件讀寫"))
        compose.onNodeWithText("授權 Google 事件讀寫").assertExists()
        assertEquals(before.enabled, runBlocking { app.syncSettings.flow.first().enabled })
        compose.onNodeWithTag("sync-content").performScrollToNode(hasText("返回設定"))
        compose.onNodeWithText("返回設定").performClick()
        compose.onNodeWithTag("settings-content").assertExists()
    }
}
