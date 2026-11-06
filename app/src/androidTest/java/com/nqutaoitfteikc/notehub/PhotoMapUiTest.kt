package com.nqutaoitfteikc.notehub

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.ui.*
import com.nqutaoitfteikc.notehub.ui.theme.NoteHubTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhotoMapUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun photoListOpensTheSelectedRealNoteAndRemovesDeletedLocations() {
        var current by mutableStateOf(PhotoMapData(listOf(PhotoMapPoint("p","n","位置測試記事","照片說明",1000,PhotoLocation(25.033,121.5654,4f,1000,true))),2))
        var opened:Pair<String,String>?=null
        compose.setContent { NoteHubTheme { PhotoMapScreen(current) { n,p -> opened=n to p } } }
        compose.onNodeWithTag("open-map-photo-list").performClick()
        compose.onNodeWithText("位置測試記事").assertExists()
        compose.onNodeWithText("開啟照片記事").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("n" to "p",opened); current=PhotoMapData(emptyList(),2) }
        compose.onNodeWithTag("open-map-photo-list").performClick()
        compose.onNodeWithText("位置測試記事").assertDoesNotExist()
        compose.onNodeWithTag("map-photo-list").assertExists()
    }
    @Test fun shortScreenWithCombinedThreeHundredPercentFontKeepsMapAndListAccessible() {
        compose.setContent {
            val base=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density,2f)) {
                NoteHubTheme(fontScale=1.5f) { Box(Modifier.requiredSize(320.dp,260.dp)) { PhotoMapScreen(PhotoMapData()) { _,_ -> } } }
            }
        }
        val bounds=compose.onNodeWithTag("maplibre-view").fetchSemanticsNode().boundsInRoot
        assertTrue("地圖仍須有可操作高度",bounds.height>0)
        compose.onNodeWithTag("open-map-photo-list").assertIsDisplayed().performClick()
        compose.onNodeWithTag("map-photo-list").assertExists()
    }
    @Test fun cumulativeCounterUpdatesWithoutRecreatingAchievements() {
        var total by mutableStateOf(4L)
        compose.setContent { NoteHubTheme { AchievementsScreen(listOf(Achievement(1,1000)),total,{}) } }
        compose.onNodeWithText("累積紀錄次數").assertExists()
        compose.onNodeWithText("下一成就：5 次，還差 1 次").assertExists()
        compose.runOnIdle { total=5 }
        compose.onNodeWithText("下一成就：10 次，還差 5 次").assertExists()
        compose.onNodeWithTag("achievement-5").assertDoesNotExist()
    }
    @Test fun tappingNativePhotoPointOpensItsDetailsList() {
        val point=PhotoMapPoint("tap-photo","tap-note","點選地圖記事","",1000,PhotoLocation(25.033,121.5654,4f,1000,true))
        compose.setContent { NoteHubTheme { PhotoMapScreen(PhotoMapData(listOf(point))) { _,_ -> } } }
        fun find(view:android.view.View):org.maplibre.android.maps.MapView? {
            if(view is org.maplibre.android.maps.MapView) return view
            if(view is android.view.ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        var map:org.maplibre.android.maps.MapLibreMap?=null
        compose.waitUntil(20000) { compose.runOnIdle {
            android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { find(it) }?.getMapAsync { map=it }
        }; map?.style!=null }
        var clickPoint=android.graphics.PointF()
        compose.waitUntil(20000) { var ready=false; compose.runOnIdle {
            val current=requireNotNull(map)
            clickPoint=current.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(25.033,121.5654))
            ready=current.queryRenderedFeatures(clickPoint,com.nqutaoitfteikc.notehub.maps.PhotoMapLayers.POINTS).any { it.hasProperty("photo_id") }
        }; ready }
        compose.onNodeWithTag("maplibre-view").performTouchInput { click(androidx.compose.ui.geometry.Offset(clickPoint.x,clickPoint.y)) }
        // Native gesture recognizer confirms a single tap after its double-tap timeout.
        compose.waitUntil(10000) { compose.onAllNodesWithText("點選地圖記事").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("點選地圖記事").assertExists()
    }

}
