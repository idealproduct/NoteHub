package com.nqutaoitfteikc.notehub
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.nqutaoitfteikc.notehub.ui.*
import com.nqutaoitfteikc.notehub.ui.theme.NoteHubTheme
import com.nqutaoitfteikc.notehub.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class CalendarCompactUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun collapsedToolsLeaveCalendarVisibleAndDateTapOpensDay() {
        val today=LocalDate.now(); val start=today.withDayOfMonth(1)
        val note=Note("span","跨日測試",1,kind=ScheduleKind.ALL_DAY,startDay=start.toEpochDay(),endDayExclusive=start.plusDays(10).toEpochDay())
        compose.setContent { NoteHubTheme { Surface(Modifier.fillMaxSize().safeDrawingPadding()) { CalendarScreen(HubState(notes=listOf(note),loading=false),{}, {}, {_,_->}) } } }
        compose.waitForIdle()
        android.os.SystemClock.sleep(600)
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val shot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { java.io.File(context.cacheDir,"calendar-compact-ui.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } } finally { shot.recycle() }
        compose.onNodeWithTag("search").assertDoesNotExist()
        compose.onNodeWithTag("month-grid").assertIsDisplayed()
        compose.onNodeWithTag("calendar-tools").performClick()
        compose.onNodeWithTag("search").assertExists()
        compose.onNodeWithTag("category-dropdown").performClick()
        compose.onAllNodesWithText("全部分類").onLast().performClick()
        compose.onNodeWithTag("calendar-tools").performClick()
        compose.onNodeWithTag("calendar-content").performScrollToNode(hasTestTag("day-$start"))
        compose.onNodeWithTag("day-$start").performClick()
        compose.onNodeWithTag("back-to-month").assertExists()
    }
    @Test fun largeFontControlsWrapAndMonthBarsRemainOperable() {
        val date=LocalDate.now().withDayOfMonth(1)
        val note=Note("bar","放大字型事件",1,kind=ScheduleKind.ALL_DAY,startDay=date.toEpochDay(),endDayExclusive=date.plusDays(3).toEpochDay())
        var picked:LocalDate?=null
        compose.setContent {
            val base=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density,2f)) {
                NoteHubTheme(fontScale=1.5f) { MonthGrid(date,listOf(note),ZoneOffset.UTC) { picked=it } }
            }
        }
        compose.onNodeWithTag("month-event-bar-0").assertExists().performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(date,picked) }
    }
}
