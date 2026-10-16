package com.nqutaoitfteikc.notehub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test

class CalendarDropdownTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun open(tag: String) {
        compose.onNodeWithTag("calendar-content").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }

    @Test fun menusDismissWithoutChangingSelectionAndFiltersSurviveRecreation() {
        open("view-dropdown")
        compose.onNodeWithTag("view-DAY").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("view-DAY").assertDoesNotExist()
        compose.onNodeWithTag("view-dropdown").assertTextContains("月", substring = true)
        open("view-dropdown")
        compose.onNodeWithTag("view-LIST").performClick()
        open("status-dropdown")
        compose.onNodeWithText("已完成").performClick()
        open("category-dropdown")
        Espresso.pressBack()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("calendar-content").performScrollToNode(hasTestTag("view-dropdown"))
        compose.onNodeWithTag("view-dropdown").assertTextContains("清單")
        compose.onNodeWithTag("status-dropdown").assertTextContains("已完成")
        compose.onNodeWithTag("category-dropdown").assertTextContains("全部分類")
        compose.onNodeWithTag("view-LIST").assertDoesNotExist()
        open("status-dropdown")
        compose.onNodeWithText("全部狀態").performClick()
        compose.onNodeWithTag("status-dropdown").assertTextContains("全部狀態")
    }
}
