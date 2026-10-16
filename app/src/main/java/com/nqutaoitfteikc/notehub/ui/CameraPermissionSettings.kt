package com.nqutaoitfteikc.notehub.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun CameraPermissionSettings() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
        message = if (it) null else "尚未允許相機，仍可使用一般記事；拍攝時可再次確認權限或前往系統設定。"
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val allowed = remember(refresh) { ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("相機權限", style = MaterialTheme.typography.titleMedium)
        Text(when { !hasCamera -> "相機：此裝置未提供鏡頭"; allowed -> "相機：已允許"; else -> "相機：未允許" })
        Text("只在您開啟拍照記事時使用相機，照片存於 App 私有目錄，不會上傳或寫入系統相簿。")
        if (hasCamera && !allowed) TextButton(onClick = { request.launch(Manifest.permission.CAMERA) }) { Text("允許相機") }
        TextButton(onClick = {
            try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }
            catch (_: ActivityNotFoundException) { message = "此裝置沒有對應設定頁，請手動前往 NoteHub 權限設定" }
            catch (_: SecurityException) { message = "系統無法開啟此設定，請手動確認相機權限" }
        }) { Text("相機權限設定") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
