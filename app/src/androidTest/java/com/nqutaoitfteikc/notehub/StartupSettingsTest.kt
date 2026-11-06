package com.nqutaoitfteikc.notehub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.nqutaoitfteikc.notehub.ui.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Read-only checks of real AppContainer; no notes, reminders, achievements or account changes. */
class StartupSettingsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun actualStartupLoadsAllDataAndSettingsTabsRetainSelection() {
        val container=(compose.activity.application as NoteHubApplication).container
        lateinit var model:HubViewModel
        compose.runOnIdle { model=ViewModelProvider(compose.activity,HubViewModel.factory(container))[HubViewModel::class.java] }
        compose.waitUntil(30000) { !model.state.value.loading }
        runBlocking {
            container.observePhotos().first(); container.observePhotoMap().first()
            container.observeAchievements().first(); container.observeAchievementTotal().first()
            container.observeReminders().first(); container.observeNotes().first()
        }
        compose.onNodeWithTag("app-error").assertDoesNotExist()
        compose.onNodeWithText("設定",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("settings-tab-0").assertIsSelected()
        compose.onNodeWithTag("settings-content").performScrollToNode(hasText("跟隨系統"))
        compose.onNodeWithText("跟隨系統").assertExists()
        compose.onNodeWithTag("settings-tab-1").performClick()
        compose.onNodeWithText("裝置權限").assertExists()
        compose.onNodeWithTag("settings-tab-2").performClick()
        compose.onNodeWithText("Google 行事曆匯入").assertExists()
        compose.onNodeWithTag("settings-tab-3").performClick()
        compose.onNodeWithTag("settings-content").performScrollToNode(hasTestTag("storage-capacity"))
        compose.onNodeWithTag("storage-capacity").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("settings-tab-3").assertIsSelected()
        compose.onNodeWithTag("app-error").assertDoesNotExist()
    }
}
