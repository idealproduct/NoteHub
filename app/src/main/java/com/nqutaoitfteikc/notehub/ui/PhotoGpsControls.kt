package com.nqutaoitfteikc.notehub.ui

import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.location.GpsPhotoLocation
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun rememberPhotoGps(enabled:Boolean):GpsPhotoLocation {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val gps=remember(context) { GpsPhotoLocation(context) }
    DisposableEffect(owner,gps,enabled) {
        val observer=LifecycleEventObserver { _,event -> when(event) {
            Lifecycle.Event.ON_RESUME -> if(enabled) gps.start() else gps.stop()
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_DESTROY -> gps.stop()
            else -> Unit
        } }
        owner.lifecycle.addObserver(observer)
        if(enabled && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) gps.start() else gps.stop()
        onDispose { owner.lifecycle.removeObserver(observer); gps.stop() }
    }
    return gps
}

internal fun photoLocationLabel(location:PhotoLocation?,status:PhotoLocationStatus):String = when {
    location!=null -> String.format(Locale.ROOT,"GPS：%.6f, %.6f · 誤差約 %.1f 公尺%s",location.latitude,location.longitude,location.accuracyMeters,if(location.isMock) "（模擬定位）" else "")
    else -> when(status) {
        PhotoLocationStatus.PERMISSION_DENIED -> "未授權定位，可不附位置拍照"
        PhotoLocationStatus.APPROXIMATE_ONLY -> "目前僅允許約略位置；需允許精確位置才能記錄 GPS"
        PhotoLocationStatus.GPS_DISABLED -> "GPS 尚未開啟或裝置不支援，可不附位置拍照"
        PhotoLocationStatus.SEARCHING -> "正在取得精確 GPS（需誤差 50 公尺內）；尚未定位仍可拍照，但不附位置"
        PhotoLocationStatus.STALE -> "GPS 定位已超過 15 秒，等待新定位；此時拍照不附位置"
        PhotoLocationStatus.INACCURATE -> "目前定位誤差過大，等待更精確 GPS；此時拍照不附位置"
        PhotoLocationStatus.ERROR -> "無法讀取 GPS，請檢查定位權限與系統設定；仍可不附位置拍照"
        else -> "未紀錄拍攝位置"
    }
}

@Composable
internal fun PhotoGpsControls(gps:GpsPhotoLocation,enabled:Boolean,onEnabled:(Boolean)->Unit) {
    val context=LocalContext.current
    val live by gps.state.collectAsState()
    var snapshot by remember { mutableStateOf(live) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(gps,live) { while(true) { snapshot=gps.snapshot(android.os.SystemClock.elapsedRealtimeNanos()); delay(1000) } }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if(enabled) gps.start() else gps.stop() }
    fun open(action:String) {
        try { context.startActivity(if(action==Settings.ACTION_APPLICATION_DETAILS_SETTINGS) Intent(action,"package:${context.packageName}".toUri()) else Intent(action)) }
        catch(e:Exception) { android.util.Log.e("NoteHubLocation","無法開啟定位設定",e); error="無法開啟系統設定，請手動檢查 NoteHub 的定位權限" }
    }
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("本次拍攝附上精確位置",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
            Switch(enabled,onCheckedChange={onEnabled(it)},modifier=Modifier.testTag("photo-gps-toggle"))
        }
        if(enabled) {
        Text(photoLocationLabel(snapshot.location,snapshot.status),Modifier.testTag("gps-status"),style=MaterialTheme.typography.bodyMedium)
        if(snapshot.location==null) {
            if(snapshot.status in listOf(PhotoLocationStatus.PERMISSION_DENIED,PhotoLocationStatus.APPROXIMATE_ONLY))
                OutlinedButton(onClick={ permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)) },modifier=Modifier.testTag("enable-photo-gps")) { Text("啟用拍攝精確定位") }
            TextButton(onClick={open(if(snapshot.status==PhotoLocationStatus.GPS_DISABLED) Settings.ACTION_LOCATION_SOURCE_SETTINGS else Settings.ACTION_APPLICATION_DETAILS_SETTINGS)}) { Text("開啟定位設定") }
        }
        }
        Text("定位預設關閉；開啟後只在此頁使用 GPS。拍攝前請確認現場公告與背景內容，App 不判定是否可拍攝。位置僅存本機，匯出不含 GPS 與拍攝時間。",style=MaterialTheme.typography.bodySmall)

        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }
}
