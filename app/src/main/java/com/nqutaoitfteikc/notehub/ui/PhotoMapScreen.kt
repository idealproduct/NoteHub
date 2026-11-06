package com.nqutaoitfteikc.notehub.ui

import android.content.ComponentCallbacks
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nqutaoitfteikc.notehub.location.GpsPhotoLocation
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import android.content.res.Configuration
import android.graphics.RectF
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.maps.PhotoMapLayers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
fun PhotoMapScreen(data:PhotoMapData, locationFactory:(android.content.Context)->GpsPhotoLocation={GpsPhotoLocation(it)}, onOpen:(String,String)->Unit) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val points by rememberUpdatedState(data.points)
    var pointIndex by remember { mutableStateOf(emptyMap<String,PhotoMapPoint>()) }
    val latestIndex by rememberUpdatedState(pointIndex)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var online by rememberSaveable { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var listOpen by rememberSaveable { mutableStateOf(false) }
    var selectedCluster by remember { mutableStateOf<org.maplibre.geojson.Feature?>(null) }
    var selectedIds by remember { mutableStateOf<List<String>?>(null) }
    var selectedTotal by remember { mutableIntStateOf(0) }
    var latitude by rememberSaveable { mutableDoubleStateOf(23.7) }
    var longitude by rememberSaveable { mutableDoubleStateOf(121.0) }
    var zoom by rememberSaveable { mutableDoubleStateOf(6.0) }
    var moved by rememberSaveable { mutableStateOf(false) }
    var json by remember { mutableStateOf("{\"type\":\"FeatureCollection\",\"features\":[]}") }
    val scope=rememberCoroutineScope()
    val gps=remember(context) { locationFactory(context) }
    var locating by remember { mutableStateOf(false) }
    var locateJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var currentLocation by remember { mutableStateOf<PhotoLocation?>(null) }
    var positionMessage by remember { mutableStateOf<String?>(null) }
    fun locate() {
        if(locating || map==null) return
        locateJob=scope.launch {
            locating=true; error=null; positionMessage=null
            try {
                gps.start()
                val result=kotlinx.coroutines.withTimeoutOrNull(20000) {
                    gps.state.first { it.status in listOf(PhotoLocationStatus.RECORDED,PhotoLocationStatus.GPS_DISABLED,PhotoLocationStatus.PERMISSION_DENIED,PhotoLocationStatus.APPROXIMATE_ONLY,PhotoLocationStatus.ERROR) }
                }
                val location=result?.location
                if(location!=null) {
                    currentLocation=location
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude,location.longitude),15.0))
                    moved=true
                    positionMessage="已移至目前位置${if(location.isMock) "（模擬定位）" else ""}，誤差約 ${String.format(java.util.Locale.ROOT,"%.1f",location.accuracyMeters)} 公尺"
                } else error=when(result?.status) {
                    PhotoLocationStatus.GPS_DISABLED -> "GPS 尚未開啟或裝置不支援，請開啟系統定位後再試"
                    PhotoLocationStatus.PERMISSION_DENIED,PhotoLocationStatus.APPROXIMATE_ONLY -> "請允許精確定位後再試；歷史照片地圖仍可使用"
                    else -> "目前尚未取得精確 GPS；請移至開闊處並重試定位"
                }
            } catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(e:Exception) { android.util.Log.e("NoteHubMap","目前位置讀取失敗",e); error="無法取得目前位置，請檢查定位權限與 GPS 後重試" }
            finally { gps.stop(); locating=false }
        }
    }
    val locatePermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) locate()
        else error="請於定位權限選擇精確位置後，再點「定位到我」；歷史照片地圖仍可使用"
    }
    fun requestLocate() {
        currentLocation=null; positionMessage=null
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) locate()
        else locatePermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    DisposableEffect(owner,gps) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_PAUSE) { locateJob?.cancel(); gps.stop() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); locateJob?.cancel(); gps.stop() }
    }
    LaunchedEffect(style,currentLocation) { style?.let { currentStyle ->
        currentLocation?.let { PhotoMapLayers.showCurrentLocation(currentStyle,it) } ?: PhotoMapLayers.clearCurrentLocation(currentStyle)
    } }
    val mapView=remember(context,retry,online) {
        try {
            MapLibre.getInstance(context)
            com.nqutaoitfteikc.notehub.privacy.MapResourceGate.configure(context,online)
            MapView(context).apply { onCreate(null) }
        } catch(e:Exception) { android.util.Log.e("NoteHubMap","地圖初始化失敗",e); null }
    }
    LaunchedEffect(data.points) {
        try {
            val prepared=withContext(Dispatchers.Default) { PhotoMapLayers.geoJson(data.points) to data.points.associateBy { it.photoId } }
            pointIndex=prepared.second
            json=prepared.first
            selectedIds=null; selectedCluster=null
        } catch(e:kotlinx.coroutines.CancellationException) { throw e }
        catch(e:Exception) { android.util.Log.e("NoteHubMap","無法準備照片位置",e); error="無法準備位置標記，請重新開啟地圖" }
    }
    val currentJson by rememberUpdatedState(json)
    fun fitAll() {
        val current=map ?: return
        val available=points
        if(available.isEmpty()) return
        try {
            if(available.all { it.location.latitude==available[0].location.latitude && it.location.longitude==available[0].location.longitude })
                current.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(available[0].location.latitude,available[0].location.longitude),16.0))
            else current.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(available.map { LatLng(it.location.latitude,it.location.longitude) }).build(),48))
            moved=true
        } catch(e:Exception) { android.util.Log.e("NoteHubMap","無法調整地圖範圍",e); error="無法調整地圖範圍，請再試一次" }
    }
    DisposableEffect(mapView,owner,online) {
        val view=mapView
        com.nqutaoitfteikc.notehub.privacy.MapResourceGate.configure(context,online)
        var active=true
        if(view==null) { loading=false; error="地圖暫時無法啟動，仍可使用本機照片位置清單" }
        var started=false; var resumed=false
        fun startView() { if(view!=null && !started) { view.onStart(); started=true } }
        fun resumeView() { startView(); if(view!=null && !resumed) { view.onResume(); resumed=true } }
        fun pauseView() { if(view!=null && resumed) { view.onPause(); resumed=false } }
        fun stopView() { pauseView(); if(view!=null && started) { view.onStop(); started=false } }
        val lifecycle=LifecycleEventObserver { _,event ->
            if(view!=null) when(event) {
                Lifecycle.Event.ON_START -> startView()
                Lifecycle.Event.ON_RESUME -> resumeView()
                Lifecycle.Event.ON_PAUSE -> pauseView()
                Lifecycle.Event.ON_STOP -> stopView()
                else -> Unit
            }
        }
        val callbacks=object:ComponentCallbacks {
            override fun onConfigurationChanged(newConfig:Configuration) = Unit
            override fun onLowMemory() { view?.onLowMemory() }
        }
        owner.lifecycle.addObserver(lifecycle)
        context.applicationContext.registerComponentCallbacks(callbacks)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startView()
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resumeView()
        view?.addOnDidFailLoadingMapListener { if(active) { loading=false; error="底圖載入失敗；可先查看本機位置清單，連線後重試" } }
        view?.addOnDidFinishLoadingMapListener { if(active) loading=false }
        view?.getMapAsync { ready ->
            if(active) {
                map=ready
                ready.cameraPosition=CameraPosition.Builder().target(LatLng(latitude,longitude)).zoom(zoom).build()
                ready.setMaxZoomPreference(20.0)
                ready.uiSettings.isAttributionEnabled=true
                ready.uiSettings.isCompassEnabled=true
                ready.addOnCameraIdleListener {
                    if(active) {
                        ready.cameraPosition.let { camera ->
                            camera.target?.let { latitude=it.latitude; longitude=it.longitude }
                            zoom=camera.zoom
                        }
                    }
                }
                ready.addOnMapClickListener { coordinate ->
                    if(!active) false else try {
                        val screen=ready.projection.toScreenLocation(coordinate)
                        val radius=20*context.resources.displayMetrics.density
                        val hits=ready.queryRenderedFeatures(RectF(screen.x-radius,screen.y-radius,screen.x+radius,screen.y+radius),PhotoMapLayers.CLUSTERS,PhotoMapLayers.POINTS)
                        val cluster=hits.firstOrNull { it.hasProperty("cluster_id") }
                        val source=style?.getSourceAs<GeoJsonSource>(PhotoMapLayers.SOURCE)
                        if(cluster!=null && source!=null) {
                            val target=cluster.geometry() as? Point
                            val expansion=source.getClusterExpansionZoom(cluster).toDouble()
                            if(target!=null && ready.cameraPosition.zoom<18.0) {
                                ready.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude(),target.longitude()),expansion.coerceAtMost(20.0)))
                            } else {
                                selectedCluster=cluster
                                selectedIds=source.getClusterLeaves(cluster,100,0).features().orEmpty().mapNotNull { it.getStringProperty("photo_id") }.distinct()
                                selectedTotal=cluster.getNumberProperty("point_count").toInt(); listOpen=true
                            }
                            moved=true; true
                        } else {
                            val ids=hits.filter { it.hasProperty("photo_id") }.map { it.getStringProperty("photo_id") }.filter { it in latestIndex }.distinct()
                            if(ids.isNotEmpty()) { selectedCluster=null; selectedIds=ids; selectedTotal=ids.size; listOpen=true; true } else false
                        }
                    } catch(e:Exception) { android.util.Log.e("NoteHubMap","無法讀取地圖標記",e); error="無法讀取這組標記，請使用照片位置清單"; false }
                }
                ready.setStyle(if(online) Style.Builder().fromUri(PhotoMapLayers.STYLE) else Style.Builder().fromJson(PhotoMapLayers.OFFLINE_STYLE)) { loaded ->
                    if(active) {
                        try { PhotoMapLayers.install(loaded,currentJson,withLabels=online); style=loaded }
                        catch(e:Exception) { android.util.Log.e("NoteHubMap","位置圖層初始化失敗",e); error="無法建立位置圖層，請重新載入地圖"; loading=false }
                        if(!moved && points.isNotEmpty()) {
                            val first=points.first().location
                            ready.cameraPosition=CameraPosition.Builder().target(LatLng(first.latitude,first.longitude)).zoom(12.0).build()
                            moved=true
                        }
                    }
                }
            }
        }
        onDispose {
            active=false
            owner.lifecycle.removeObserver(lifecycle)
            context.applicationContext.unregisterComponentCallbacks(callbacks)
            style=null; map=null
            stopView(); view?.onDestroy()
            com.nqutaoitfteikc.notehub.privacy.MapResourceGate.configure(context,false)
        }
    }
    LaunchedEffect(style,json) { style?.getSourceAs<GeoJsonSource>(PhotoMapLayers.SOURCE)?.setGeoJson(json) }
    LaunchedEffect(mapView,style) {
        if(style==null && mapView!=null) {
            kotlinx.coroutines.delay(25000)
            if(style==null) { loading=false; error="底圖載入逾時；本機位置仍保留，請檢查網路後重試" }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact=maxHeight<380.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.7f
    Column(Modifier.fillMaxSize().testTag("photo-map-screen")) {
        if(compact) Row(Modifier.fillMaxWidth().padding(horizontal=8.dp)) {
            IconButton(onClick={selectedIds=null; selectedCluster=null; listOpen=true},modifier=Modifier.testTag("open-map-photo-list").semantics { contentDescription="照片位置清單" }) { NavigationSymbol("photos") }
            IconButton(enabled=data.points.isNotEmpty() && map!=null,onClick=::fitAll,modifier=Modifier.semantics { contentDescription="顯示全部位置" }) { NavigationSymbol("map") }
            IconButton(enabled=map!=null && !locating,onClick=::requestLocate,modifier=Modifier.testTag("locate-me").semantics { contentDescription="定位到我" }) {
                if(locating) CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp) else NavigationSymbol("locate")
            }
        } else FlowRow(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("照片地圖",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={selectedIds=null; selectedCluster=null; listOpen=true},modifier=Modifier.testTag("open-map-photo-list")) { Text("清單（${data.points.size}）") }
            TextButton(enabled=data.points.isNotEmpty() && map!=null,onClick=::fitAll) { Text("顯示全部") }
            TextButton(enabled=map!=null && !locating,onClick=::requestLocate,modifier=Modifier.testTag("locate-me")) { Text(if(locating) "定位中…" else "定位到我") }
        }
        TextButton(onClick={online=!online},modifier=Modifier.testTag("map-network-toggle")) { Text(if(online) "關閉線上底圖" else "離線位置圖 · 載入線上底圖") }
        if(!compact) Text(positionMessage ?: "${data.points.size} 張已定位 · ${data.withoutLocation} 張未紀錄位置",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            mapView?.let { view -> key(view) { AndroidView(factory={view},modifier=Modifier.fillMaxSize().testTag("maplibre-view")) } }
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(error!=null) Card(Modifier.fillMaxWidth().heightIn(max=140.dp).padding(8.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                    TextButton(onClick={error=null}) { Text("關閉訊息") }
                    Text(error.orEmpty(),style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={locateJob?.cancel(); gps.stop(); error=null; loading=true; retry++}) { Text("重新載入地圖") }
                }
            }
        }
        if(!compact && online) Text("底圖：OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors",Modifier.horizontalScroll(rememberScrollState()).padding(6.dp),style=MaterialTheme.typography.labelSmall,maxLines=1)
    }
    }
    if(listOpen) ModalBottomSheet(onDismissRequest={listOpen=false}) {
        val selected=selectedIds?.let { ids -> ids.mapNotNull { pointIndex[it] } } ?: data.points
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.8f).testTag("map-photo-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text("拍照記事位置",style=MaterialTheme.typography.titleLarge); Text("${data.points.size} 張已定位 · ${data.withoutLocation} 張未紀錄位置") }
            if(selected.isEmpty()) item { Text("尚無已保存位置的照片。請於拍照頁授權精確定位，取得 GPS 後再拍攝；舊照片不會補上推測位置。") }
            if(selectedIds!=null && selectedTotal>selected.size) item {
                Text("此群組共 $selectedTotal 張，目前顯示 ${selected.size} 張")
                TextButton(onClick={
                    try {
                        val cluster=selectedCluster
                        val source=style?.getSourceAs<GeoJsonSource>(PhotoMapLayers.SOURCE)
                        if(cluster!=null && source!=null) {
                            val more=source.getClusterLeaves(cluster,100,selectedIds.orEmpty().size.toLong()).features().orEmpty().mapNotNull { it.getStringProperty("photo_id") }
                            selectedIds=(selectedIds.orEmpty()+more).distinct()
                        }
                    } catch(e:Exception) { android.util.Log.e("NoteHubMap","載入更多位置失敗",e); error="無法載入更多位置，請重新選取這組標記" }
                }) { Text("載入更多照片位置") }
                TextButton(onClick={selectedIds=null; selectedCluster=null}) { Text("查看完整位置清單") }
            }
            items(selected,key={it.photoId}) { point ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(point.title,style=MaterialTheme.typography.titleMedium)
                        Text(captureLabel(point.capturedAt))
                        Text(photoLocationLabel(point.location,PhotoLocationStatus.RECORDED),style=MaterialTheme.typography.bodySmall)
                        if(point.caption.isNotBlank()) Text(point.caption)
                        Button(onClick={listOpen=false; onOpen(point.noteId,point.photoId)}) { Text("開啟照片記事") }
                    }
                }
            }
        }
    }
}

