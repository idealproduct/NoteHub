package com.nqutaoitfteikc.notehub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class CalendarImportUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun configurationGatesLoginAndNeverClaimsGoogleSuccess() {
        compose.onNodeWithText("設定", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Google 行事曆匯入").performScrollTo().performClick()
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            compose.onNodeWithText("尚未設定 Google OAuth").assertIsDisplayed()
            compose.onNodeWithText("使用 Google 帳號登入").assertIsNotEnabled()
        } else {
            compose.onNodeWithText("使用 Google 帳號登入").assertIsEnabled()
            compose.onNodeWithText("授權讀取行事曆").assertDoesNotExist()
        }
        compose.onNodeWithText("返回設定").performClick()
        compose.onNodeWithText("Google 行事曆匯入").assertExists()
    }
}
