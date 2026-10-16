package com.nqutaoitfteikc.notehub

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.ui.HubViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsQualityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as NoteHubApplication).container
    @Test fun allThemeOptionsPersistAcrossActivityRecreation() {
        val previous = runBlocking { container.observeTheme().first() }
        try {
            compose.onNodeWithText("設定", useUnmergedTree = true).performClick()
            listOf(ThemeMode.LIGHT to "淺色", ThemeMode.DARK to "深色", ThemeMode.SYSTEM to "跟隨系統").forEach { (mode, label) ->
                compose.onNodeWithText(label).performScrollTo().performClick()
                compose.waitUntil(10000) { runBlocking { container.observeTheme().first() } == mode }
                compose.activityRule.scenario.recreate()
                compose.onNodeWithText(label).assertIsSelected()
                assertEquals(mode, runBlocking { container.observeTheme().first() })
                compose.runOnIdle {
                    val systemDark = compose.activity.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    val light = mode == ThemeMode.LIGHT || (mode == ThemeMode.SYSTEM && !systemDark)
                    val bars = androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                    assertEquals(light, bars.isAppearanceLightStatusBars)
                    assertEquals(light, bars.isAppearanceLightNavigationBars)
                }
            }
        } finally { runBlocking { container.setTheme(previous) } }
    }
    @Test fun settingsShowActualCameraStatusVersionAndLocalStoragePolicy() {
        compose.onNodeWithText("設定", useUnmergedTree = true).performClick()
        val context = compose.activity
        val camera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val label = when { !camera -> "相機：此裝置未提供鏡頭"; allowed -> "相機：已允許"; else -> "相機：未允許" }
        compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("相機權限設定").assertExists()
        compose.onNodeWithTag("settings-content").performScrollToNode(hasText("記事、照片與設定只保存在此裝置", substring = true))
        compose.onNodeWithText("記事、照片與設定只保存在此裝置", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("settings-content").performScrollToNode(hasText("NoteHub ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）", substring = true))
        compose.onNodeWithText("NoteHub ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）", substring = true).assertIsDisplayed()
    }
    @Test fun failedValidationShowsDismissibleErrorWithoutCreatingANote() {
        val before = runBlocking { container.observeNotes().first().map { it.id }.toSet() }
        compose.runOnIdle {
            val model = ViewModelProvider(compose.activity, HubViewModel.factory(container))[HubViewModel::class.java]
            model.save(null, NoteDraft("  ")) { error("無效記事不得儲存") }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("app-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("請輸入標題").assertIsDisplayed()
        compose.onNodeWithText("關閉訊息").performClick()
        compose.onNodeWithTag("app-error").assertDoesNotExist()
        assertEquals(before, runBlocking { container.observeNotes().first().map { it.id }.toSet() })
    }
}
