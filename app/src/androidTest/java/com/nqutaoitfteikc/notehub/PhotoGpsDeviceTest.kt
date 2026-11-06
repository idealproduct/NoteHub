package com.nqutaoitfteikc.notehub

import android.content.Context
import android.location.*
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.location.GpsPhotoLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class PhotoGpsDeviceTest {
    @get:Rule val compose=createComposeRule()
    @Suppress("DEPRECATION")
    @Test fun foregroundGpsAcceptsFreshPreciseFixRejectsOldAndStopsListening() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val manager=context.getSystemService(LocationManager::class.java)
        val provider="notehub-fixture-${UUID.randomUUID()}"
        val gps=GpsPhotoLocation(context,provider)
        compose.setContent { Text("前景定位隔離測試") }
        try {
            manager.addTestProvider(provider,false,false,false,false,false,false,false,Criteria.POWER_LOW,Criteria.ACCURACY_FINE)
            manager.setTestProviderEnabled(provider,true)
            compose.runOnIdle { gps.start() }
            val elapsed=android.os.SystemClock.elapsedRealtimeNanos()
            manager.setTestProviderLocation(provider,Location(provider).apply {
                latitude=25.033; longitude=121.5654; accuracy=4f
                time=System.currentTimeMillis(); elapsedRealtimeNanos=elapsed
            })
            val ready=withTimeout(10000) { gps.state.first { it.status==PhotoLocationStatus.RECORDED } }
            assertEquals(25.033,ready.location!!.latitude,0.0)
            assertTrue(ready.location.isMock)
            assertEquals(PhotoLocationStatus.STALE,gps.snapshot(elapsed+16_000_000_000L).status)
            compose.runOnIdle { gps.stop() }
            assertNull(gps.state.value.location)
            assertEquals(PhotoLocationStatus.NOT_RECORDED,gps.state.value.status)
            manager.setTestProviderLocation(provider,Location(provider).apply {
                latitude=24.0; longitude=120.0; accuracy=4f; time=System.currentTimeMillis(); elapsedRealtimeNanos=android.os.SystemClock.elapsedRealtimeNanos()
            })
            kotlinx.coroutines.delay(400)
            assertEquals(PhotoLocationStatus.NOT_RECORDED,gps.state.value.status)
        } finally { compose.runOnIdle { gps.stop() }; manager.removeTestProvider(provider) }
    }
    @Suppress("DEPRECATION")
    @Test fun locateMeButtonMovesTheMapWithoutAddingPhotoHistory() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val manager=context.getSystemService(LocationManager::class.java)
        val provider="notehub-locate-${UUID.randomUUID()}"
        val gps=GpsPhotoLocation(context,provider)
        fun find(view:android.view.View):org.maplibre.android.maps.MapView? {
            if(view is org.maplibre.android.maps.MapView) return view
            if(view is android.view.ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        try {
            manager.addTestProvider(provider,false,false,false,false,false,false,false,Criteria.POWER_LOW,Criteria.ACCURACY_FINE)
            manager.setTestProviderEnabled(provider,true)
            compose.setContent { com.nqutaoitfteikc.notehub.ui.theme.NoteHubTheme {
                com.nqutaoitfteikc.notehub.ui.PhotoMapScreen(PhotoMapData(),locationFactory={gps}) { _,_ -> }
            } }
            compose.waitUntil(15000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("locate-me") and androidx.compose.ui.test.isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("locate-me").performClick()
            compose.waitUntil(10000) { gps.state.value.status==PhotoLocationStatus.SEARCHING }
            manager.setTestProviderLocation(provider,Location(provider).apply {
                latitude=24.12345; longitude=120.65432; accuracy=4f; time=System.currentTimeMillis(); elapsedRealtimeNanos=android.os.SystemClock.elapsedRealtimeNanos()
            })
            var map:org.maplibre.android.maps.MapLibreMap?=null
            compose.waitUntil(20000) { var centered=false; compose.runOnIdle {
                android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { find(it) }?.getMapAsync { map=it }
                val target=map?.cameraPosition?.target
                centered=target!=null && kotlin.math.abs(target.latitude-24.12345)<.0001 && kotlin.math.abs(target.longitude-120.65432)<.0001
            }; centered }
            compose.waitUntil(20000) { var sourceReady=false; compose.runOnIdle {
                sourceReady=map?.style?.getSource(com.nqutaoitfteikc.notehub.maps.PhotoMapLayers.CURRENT)!=null
            }; sourceReady }
            compose.runOnIdle {
                assertTrue(map!!.style!!.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(com.nqutaoitfteikc.notehub.maps.PhotoMapLayers.SOURCE)!!.querySourceFeatures(null).isEmpty())
            }
        } finally { compose.runOnIdle { gps.stop() }; manager.removeTestProvider(provider) }
    }

}
