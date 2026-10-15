package com.nqutaoitfteikc.notehub.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nqutaoitfteikc.notehub.domain.*
import java.io.IOException
import java.time.ZoneId

@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
@Composable
fun CameraScreen(sessionId: String, targetNote: Note?, state: HubState, model: HubViewModel, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        permissionMessage = if (granted) null else "相機尚未授權，請允許相機權限；已拍攝的草稿仍會保留。"
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME)
            permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var lens by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var retry by remember { mutableIntStateOf(0) }
    val previewView = remember(context) { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FIT_CENTER } }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var streaming by remember { mutableStateOf(false) }
    var sensorRealtime by remember { mutableStateOf(false) }
    var switchAvailable by remember { mutableStateOf(false) }
    var bindError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(permission, owner, lens, retry) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG).build()
        val observer = Observer<PreviewView.StreamState> { streaming = it == PreviewView.StreamState.STREAMING }
        previewView.previewStreamState.observe(owner, observer)
        capture = null; streaming = false; bindError = null
        if (permission) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val back = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val front = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    require(back || front) { "找不到可用的相機" }
                    switchAvailable = back && front
                    val selector = if (lens == CameraSelector.LENS_FACING_BACK && back || !front) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    val camera = cameraProvider.bindToLifecycle(owner, selector, preview, imageCapture)
                    sensorRealtime = Camera2CameraInfo.from(camera.cameraInfo).getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME
                    capture = imageCapture
                } catch (e: Exception) {
                    Log.e("NoteHubPhotos", "相機初始化失敗", e)
                    bindError = "無法啟動相機，請確認權限、關閉其他相機程式後重試"
                }
            }, mainExecutor)
        }
        onDispose {
            disposed = true
            capture = null; streaming = false
            previewView.previewStreamState.removeObserver(observer)
            provider?.unbind(preview, imageCapture)
        }
    }
    val photos = state.photos.filter { it.sessionId == sessionId && it.noteId == null }
    val busy = state.saving || state.capturingSession != null
    var title by rememberSaveable(sessionId) { mutableStateOf(targetNote?.title ?: "") }
    var description by rememberSaveable(sessionId) { mutableStateOf(targetNote?.description ?: "") }
    var category by rememberSaveable(sessionId) { mutableStateOf(targetNote?.category ?: "") }
    var error by rememberSaveable(sessionId) { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    BackHandler { if (!busy) onBack() }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("捨棄已拍攝的照片？") },
        text = { Text("這組尚未儲存的照片將從本機刪除。") },
        confirmButton = { TextButton(enabled = !busy, onClick = { model.discardPhotoDraft(sessionId, onBack) }) { Text("確認捨棄") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("保留草稿") } })
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = !busy, onClick = onBack) { Text("返回並保留照片") }
            Text("拍照記事", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.weight(1f).testTag("camera-content"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (!permission) Column {
                    Text("拍照需要相機權限，照片只會存放在此裝置。")
                    Button(onClick = { requestPermission.launch(Manifest.permission.CAMERA) }, modifier = Modifier.testTag("grant-camera")) { Text("允許使用相機") }
                    TextButton(onClick = {
                        try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                        catch (e: Exception) { Log.e("NoteHubPhotos", "無法開啟權限設定", e); permissionMessage = "無法開啟系統設定，請手動前往 NoteHub 的相機權限" }
                    }) { Text("開啟相機權限設定") }
                } else AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().height(300.dp).testTag("camera-preview"))
                permissionMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                bindError?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("重新啟動相機") } }
            }
            item {
                Text(if (streaming) "相機預覽就緒" else if (permission) "正在啟動相機…" else "等待相機授權", modifier = Modifier.testTag("camera-status"))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = permission && capture != null && streaming && !busy, modifier = Modifier.testTag("take-photo"), onClick = {
                        val imageCapture = capture ?: return@Button
                        val token = model.beginCapture(sessionId) ?: return@Button
                        imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                        val realtime = sensorRealtime
                        val zone = ZoneId.systemDefault().id
                        var clock: CaptureClock? = null
                        try {
                            imageCapture.takePicture(model.photoCaptureExecutor, object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureStarted() { clock = CaptureClock(System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos()) }
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    try {
                                        val at = requireNotNull(clock) { "相機未提供拍攝開始時間" }
                                        require(image.format == ImageFormat.JPEG) { "相機未提供 JPEG 格式" }
                                        val buffer = image.planes[0].buffer.duplicate().apply { rewind() }
                                        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                                        val captured = CapturedImage(bytes, CaptureTimeRules.resolve(at, image.imageInfo.timestamp, realtime), image.imageInfo.rotationDegrees, zone)
                                        mainExecutor.execute { model.storeCapture(token, sessionId, captured) }
                                    } catch (e: Exception) { mainExecutor.execute { model.failCapture(token, e) } }
                                    finally { image.close() }
                                }
                                override fun onError(exception: ImageCaptureException) { mainExecutor.execute { model.failCapture(token, exception) } }
                            })
                        } catch (e: Exception) { model.failCapture(token, e) }
                    }) { Text(if (state.capturingSession == sessionId) "拍攝與保存中…" else "拍攝照片") }
                    if (switchAvailable) TextButton(enabled = !busy, onClick = { lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }) { Text("切換鏡頭") }
                }
            }
            item {
                Text("已拍 ${photos.size} 張", style = MaterialTheme.typography.titleMedium)
                photos.minOfOrNull { it.capturedAt }?.let { Text("最早拍攝：${captureLabel(it)}", modifier = Modifier.testTag("capture-time")) }
                Text("返回會保留照片草稿；文字請按下方儲存，才會寫入記事。", style = MaterialTheme.typography.bodySmall)
            }
            item { OutlinedTextField(title, { title = it }, label = { Text("照片記事標題") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("photo-note-title")) }
            item { OutlinedTextField(description, { description = it }, label = { Text("記事描述") }, minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("photo-note-description")) }
            item { OutlinedTextField(category, { category = it }, label = { Text("分類") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("photo-note-category")) }
            item {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = photos.isNotEmpty() && !busy, modifier = Modifier.testTag("save-photo-note"), onClick = {
                    error = null
                    try {
                        NoteDraft(title, description, category).validated()
                        model.savePhotoNote(sessionId, targetNote?.id, title, description, category, onSaved)
                    } catch (e: IllegalArgumentException) { error = e.message ?: "請檢查記事內容" }
                }) { Text(if (state.saving) "儲存中…" else if (targetNote == null) "儲存照片記事" else "加入這篇照片記事") }
                if (photos.isNotEmpty()) TextButton(enabled = !busy, onClick = { discard = true }) { Text("捨棄這組草稿") }
            }
            items(photos, key = { it.id }) { photo ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoImage(photo, model.photoFiles, Modifier.fillMaxWidth().height(200.dp))
                        Text("拍攝：${captureLabel(photo.capturedAt)}")
                        TextButton(enabled = !busy, onClick = { model.removePhoto(photo.id) }, modifier = Modifier.testTag("remove-draft-${photo.id}")) { Text("移除此張照片") }
                    }
                }
            }
        }
    }
}
