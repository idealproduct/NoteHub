package com.nqutaoitfteikc.notehub

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.maps.PhotoMapLayers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.*
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.expressions.Expression
import java.io.File

class PhotoMapNativeTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var activeView:MapView
    private fun MapLibreMap.visible(vararg layers:String)=queryRenderedFeatures(android.graphics.RectF(0f,0f,activeView.width.toFloat(),activeView.height.toFloat()),*layers)
    private fun point(id:Int,lon:Double)=PhotoMapPoint("p$id","note$id","隔離地圖測試","",1000,PhotoLocation(25.033,lon,5f,1000,true))
    private fun setup(points:List<PhotoMapPoint>,online:Boolean=false):Pair<MapLibreMap,Style> {
        val json=runBlocking { PhotoMapLayers.geoJson(points) }
        var map:MapLibreMap?=null; var style:Style?=null
        compose.setContent {
            val context=androidx.compose.ui.platform.LocalContext.current
            val view=remember { MapLibre.getInstance(context); com.nqutaoitfteikc.notehub.privacy.MapResourceGate.configure(context,online); MapView(context).apply { onCreate(null); onStart(); onResume() } }
            DisposableEffect(view) { onDispose { view.onPause(); view.onStop(); view.onDestroy() } }
            AndroidView(factory={view},modifier=Modifier.fillMaxSize())
            LaunchedEffect(view) { activeView=view; view.getMapAsync { ready ->
                map=ready
                ready.cameraPosition=CameraPosition.Builder().target(LatLng(25.033,121.5654)).zoom(4.0).build()
                val builder=if(online) Style.Builder().fromUri(PhotoMapLayers.STYLE) else Style.Builder().fromJson("""{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#e4ebef"}}]}""")
                ready.setStyle(builder) { loaded -> PhotoMapLayers.install(loaded,json,withLabels=online); style=loaded }
            } }
        }
        compose.waitUntil(if(online) 60000 else 20000) { style!=null }
        return requireNotNull(map) to requireNotNull(style)
    }
    @Test fun threePointsMergeWhenZoomedOutAndSeparateWhenZoomedIn() {
        val (map,style)=setup(listOf(point(1,121.5639),point(2,121.5654),point(3,121.5669)))
        var merged=false
        compose.waitUntil(20000) { compose.runOnIdle {
            merged=map.visible(PhotoMapLayers.CLUSTERS).any { it.getNumberProperty("point_count").toInt()==3 }
        }; merged }
        compose.runOnIdle {
            val source=style.getSourceAs<GeoJsonSource>(PhotoMapLayers.SOURCE)!!
            val cluster=source.querySourceFeatures(Expression.has("point_count")).first { it.getNumberProperty("point_count").toInt()==3 }
            assertEquals(3,source.getClusterLeaves(cluster,10,0).features()!!.size)
            assertTrue(source.getClusterExpansionZoom(cluster)>4)
            map.cameraPosition=CameraPosition.Builder().target(LatLng(25.033,121.5654)).zoom(16.0).build()
        }
        compose.waitUntil(20000) { var ids=emptySet<String>(); compose.runOnIdle {
            ids=map.visible(PhotoMapLayers.POINTS).filter { it.hasProperty("photo_id") }.map { it.getStringProperty("photo_id") }.toSet()
        }; ids==setOf("p1","p2","p3") }
        compose.runOnIdle { map.cameraPosition=CameraPosition.Builder().target(LatLng(25.033,121.5654)).zoom(4.0).build() }
        compose.waitUntil(20000) { var count=0; compose.runOnIdle { count=map.visible(PhotoMapLayers.CLUSTERS).size }; count==1 }
    }
    @Test fun tenThousandPhotosUseOneNativeSourceAndRenderAsOneCluster() {
        val points=(0 until 10000).map { point(it,121.5654+(it%100)*.00001) }
        val (map,style)=setup(points)
        val start=android.os.SystemClock.elapsedRealtime()
        compose.waitUntil(30000) { var found=false; compose.runOnIdle {
            found=map.visible(PhotoMapLayers.CLUSTERS).any { it.getNumberProperty("point_count").toInt()==10000 }
        }; found }
        compose.runOnIdle {
            assertEquals(1,style.sources.count { it.id==PhotoMapLayers.SOURCE })
            assertEquals(1,map.visible(PhotoMapLayers.CLUSTERS).size)
            assertTrue(map.visible(PhotoMapLayers.POINTS).isEmpty())
        }
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"map-native-performance.txt").writeText("10000 fixture photos, one source, one visible native cluster; wait after style ready: ${android.os.SystemClock.elapsedRealtime()-start} ms")
    }
    @Test fun openFreeMapLoadsActualVectorBasemapWithoutAnApiKey() {
        val (map,_)=setup(emptyList(),online=true)
        compose.runOnIdle { map.cameraPosition=CameraPosition.Builder().target(LatLng(25.033,121.5654)).zoom(13.0).build() }
        compose.waitUntil(60000) { var count=0; compose.runOnIdle { count=map.visible().size }; count>20 }
        val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"maplibre-basemap.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
        finally { screenshot.recycle() }
    }
    @Test fun coincidentPhotosStayClusteredAtMaximumZoom() {
        val (map,_)=setup((0 until 500).map { point(it,121.5654) })
        compose.runOnIdle { map.cameraPosition=CameraPosition.Builder().target(LatLng(25.033,121.5654)).zoom(20.0).build() }
        compose.waitUntil(20000) { var found=false; compose.runOnIdle {
            found=map.visible(PhotoMapLayers.CLUSTERS).any { it.getNumberProperty("point_count").toInt()==500 }
        }; found }
    }

}
