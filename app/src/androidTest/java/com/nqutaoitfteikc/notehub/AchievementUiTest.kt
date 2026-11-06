package com.nqutaoitfteikc.notehub

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nqutaoitfteikc.notehub.domain.Achievement
import com.nqutaoitfteikc.notehub.ui.*
import com.nqutaoitfteikc.notehub.ui.theme.NoteHubTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AchievementUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun onlyUnlockedAwardsAreListedAndLargeFontStillAllowsOpening() {
        var opened=0L
        compose.setContent { NoteHubTheme(fontScale=1.5f) { AchievementsScreen(listOf(Achievement(1,123)),4,{opened=it.milestone}) } }
        compose.onNodeWithTag("achievement-5").assertDoesNotExist()
        compose.onNodeWithTag("achievements-list").performScrollToNode(hasTestTag("achievement-1"))
        compose.onNodeWithTag("achievement-1").performClick()
        compose.runOnIdle { assertEquals(1L,opened) }
    }
    @Test fun testMenuRequiresExplicitActionAndHasNoSvgDownloadOrOldExplanation() {
        var action:Pair<Long,Boolean>?=null
        compose.setContent { NoteHubTheme { AchievementsScreen(emptyList(),2,{}, {m,r->action=m to r}) } }
        compose.onNodeWithTag("achievements-list").performScrollToNode(hasText("選擇成就：1 次 ▾"))
        compose.onNodeWithText("選擇成就：1 次 ▾").performClick()
        compose.onNodeWithText("5 次 · ${com.nqutaoitfteikc.notehub.achievements.AchievementArt.design(5).title}").performClick()
        compose.runOnIdle { assertNull(action) }
        compose.onNodeWithText("達成成就").performClick()
        compose.runOnIdle { assertEquals(5L to false,action) }
        compose.onNodeWithText("重置該成就").performClick()
        compose.runOnIdle { assertEquals(5L to true,action) }
        compose.onNodeWithText("從 0.12.0",substring=true).assertDoesNotExist()
    }
    @Test fun animatedVectorRendersAndCanBePaused() {
        compose.setContent { NoteHubTheme { AchievementDialog(Achievement(30,123),false,{}, {}) } }
        compose.onNodeWithContentDescription("月桂守望 · 30，動畫成就圖").assertExists()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        var painted=false
        val deadline=android.os.SystemClock.uptimeMillis()+15000
        while(!painted && android.os.SystemClock.uptimeMillis()<deadline) {
            android.os.SystemClock.sleep(400)
            val shot=instrumentation.uiAutomation.takeScreenshot()
            try {
                var pixels=0
                for(y in 0 until shot.height step 8) for(x in 0 until shot.width step 8) {
                    val c=shot.getPixel(x,y)
                    val r=android.graphics.Color.red(c); val g=android.graphics.Color.green(c); val b=android.graphics.Color.blue(c)
                    if(r in 12..44 && g in 18..40 && b in 45..78 && b-r>20) pixels++
                }
                painted=pixels>1000
            } finally { shot.recycle() }
        }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { java.io.File(context.cacheDir,"achievement-ui.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
        finally { screenshot.recycle() }
        assertTrue("SVG 必須實際繪製深色星空背景，不能僅存在空白 WebView",painted)
        val before=instrumentation.uiAutomation.takeScreenshot()
        android.os.SystemClock.sleep(700)
        val after=instrumentation.uiAutomation.takeScreenshot()
        var changed=0
        try {
            for(y in 0 until before.height step 4) for(x in 0 until before.width step 4) {
                val a=before.getPixel(x,y); val b=after.getPixel(x,y)
                if(android.graphics.Color.blue(a)-android.graphics.Color.red(a)>20 && android.graphics.Color.green(a)<190 &&
                    kotlin.math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b))+kotlin.math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b))+kotlin.math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))>30) changed++
            }
        } finally { before.recycle(); after.recycle() }
        assertTrue("SVG 星芒與環飾應隨時間產生畫面變化",changed>20)
        compose.onNodeWithText("暫停裝飾動畫").performScrollTo().performClick()
        compose.onNodeWithContentDescription("月桂守望 · 30，靜態成就圖").assertExists()
    }
    @Test fun congratulationsCanSharePngAndDismissWithoutExportPermission() {
        var share:Boolean?=null; var dismissed=false
        compose.setContent {
            val base=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density,2f)) {
                NoteHubTheme(fontScale=1.5f) { AchievementDialog(Achievement(30,123),true,{dismissed=true},{share=it}) }
            }
        }
        compose.onNodeWithTag("share-achievement-png").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(false,share) }
        compose.onNodeWithTag("close-achievement").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }
    @Test fun rendererTerminationFallsBackAndSharingRemainsAvailable() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=29)
        var shared=false
        compose.setContent { NoteHubTheme { AchievementDialog(Achievement(30,123),false,{}, {shared=!it}) } }
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun find(view:android.view.View):android.webkit.WebView? {
            if(view is android.webkit.WebView) return view
            if(view is android.view.ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        var terminated=false
        compose.waitUntil(15000) {
            instrumentation.runOnMainSync {
                val web=android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { find(it) }
                if(web?.progress==100) terminated=web.webViewRenderProcess?.terminate()==true
            }
            terminated
        }
        compose.waitUntil(10000) { compose.onAllNodesWithText("此裝置暫時無法播放 SVG 動畫；成就與圖片仍保留，可繼續分享。").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("share-achievement-png").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(shared) }
    }

}
