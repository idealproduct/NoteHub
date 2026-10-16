package com.nqutaoitfteikc.notehub.ui

import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.photos.PhotoFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun captureLabel(time: Long): String = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS", Locale.TAIWAN))

@Composable
fun PhotoImage(photo: Photo, files: PhotoFiles, modifier: Modifier = Modifier, maxSize: Int = 1200) {
    val result by produceState<Result<ImageBitmap>?>(null, photo.fileName, photo.rotationDegrees, maxSize) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = files.file(photo.fileName)
                if (!file.isFile) throw IOException("找不到照片檔案")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("照片無法讀取")
                var sample = 1
                while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: throw IOException("照片無法讀取")
                val rotated = if (photo.rotationDegrees == 0) bitmap else android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                    Matrix().apply { postRotate(photo.rotationDegrees.toFloat()) }, true).also { if (it !== bitmap) bitmap.recycle() }
                rotated.asImageBitmap()
            }
        }
    }
    val image = result?.getOrNull()
    if (image != null) Image(image, "照片，拍攝於 ${captureLabel(photo.capturedAt)}", modifier.testTag("photo-image-${photo.id}"), contentScale = ContentScale.Fit)
    else Box(modifier.padding(12.dp)) {
        Text(if (result == null) "讀取照片中…" else "照片檔案遺失或無法讀取，拍攝紀錄仍保留", color = if (result?.isFailure == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PhotoHubScreen(state: HubState, model: HubViewModel, onCamera: (String, String?) -> Unit, onNote: (String) -> Unit) {
    val drafts = state.photos.filter { it.noteId == null }.groupBy { it.sessionId }.toList().sortedByDescending { it.second.minOf { photo -> photo.capturedAt } }
    val notes = state.notes.filter { it.photoCapturedAt != null }.sortedByDescending { it.photoCapturedAt }
    var discard by rememberSaveable { mutableStateOf<String?>(null) }
    if (discard != null) AlertDialog(onDismissRequest = { discard = null }, title = { Text("捨棄這組拍攝草稿？") },
        text = { Text("草稿中的照片將從本機刪除。") }, confirmButton = { TextButton(enabled = !state.saving, onClick = { model.discardPhotoDraft(requireNotNull(discard)) { discard = null } }) { Text("確認捨棄") } },
        dismissButton = { TextButton(onClick = { discard = null }) { Text("保留草稿") } })
    LazyColumn(Modifier.fillMaxSize().testTag("photo-hub"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("照片記事", style = MaterialTheme.typography.headlineLarge) }
        item { Text("拍攝時間保留在每張照片中，晚一點儲存或編輯描述也不會改變。") }
        item { Button(enabled = !state.saving && state.capturingSession == null, onClick = { onCamera(java.util.UUID.randomUUID().toString(), null) }, modifier = Modifier.testTag("new-photo-note")) { Text("拍照記事") } }
        if (state.pendingPhotoCleanup > 0) item {
            Text("尚有 ${state.pendingPhotoCleanup} 個照片檔案等待清理")
            TextButton(onClick = model::recoverPhotoFiles) { Text("重試檔案清理") }
        }
        if (state.loading) item { CircularProgressIndicator() }
        if (drafts.isNotEmpty()) item { Text("未完成的拍攝草稿", style = MaterialTheme.typography.titleLarge) }
        items(drafts, key = { "draft-${it.first}" }) { (sessionId, photos) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${captureLabel(photos.minOf { it.capturedAt })} · ${photos.size} 張")
                    PhotoImage(photos.first(), model.photoFiles, Modifier.fillMaxWidth().height(140.dp), 600)
                    FlowRow {
                        TextButton(onClick = { onCamera(sessionId, null) }, modifier = Modifier.testTag("resume-draft-$sessionId")) { Text("繼續拍攝與儲存") }
                        TextButton(onClick = { discard = sessionId }) { Text("捨棄草稿") }
                    }
                }
            }
        }
        item { Text("已儲存記事", style = MaterialTheme.typography.titleLarge) }
        if (notes.isEmpty()) item { Text("尚無照片記事，拍下第一個想記住的片刻吧。") }
        items(notes, key = { it.id }) { note ->
            val photos = state.photos.filter { it.noteId == note.id }
            Card(Modifier.fillMaxWidth().clickable { onNote(note.id) }.testTag("photo-note-${note.id}")) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(note.title, style = MaterialTheme.typography.titleLarge)
                    Text("拍攝：${captureLabel(requireNotNull(note.photoCapturedAt))} · ${photos.size} 張")
                    photos.firstOrNull()?.let { PhotoImage(it, model.photoFiles, Modifier.fillMaxWidth().height(160.dp), 600) }
                    if (note.description.isNotEmpty()) Text(note.description, maxLines = 3)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PhotoGalleryScreen(note: Note, photos: List<Photo>, state: HubState, model: HubViewModel, onBack: () -> Unit, onEdit: () -> Unit, onCamera: (String) -> Unit) {
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val galleryScroll = rememberLazyListState()
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    if (removeId != null) AlertDialog(onDismissRequest = { removeId = null }, title = { Text("移除此照片？") }, text = { Text("照片檔案將刪除，記事時間與其他照片不變。") },
        confirmButton = { TextButton(enabled = !state.saving, onClick = { model.removePhoto(requireNotNull(removeId)); removeId = null }) { Text("確認移除照片") } },
        dismissButton = { TextButton(onClick = { removeId = null }) { Text("取消") } })
    val preview = photos.find { it.id == previewId }
    if (preview != null) {
        BackHandler { previewId = null }
        var caption by rememberSaveable(preview.id) { mutableStateOf(preview.caption) }
        Surface(Modifier.fillMaxSize()) {
            PhotoPreviewContent(preview.capturedAt, caption, { caption = it }, state.saving,
                onClose = { previewId = null },
                onSave = { model.photoCaption(preview.id, caption) { previewId = null } },
                // Scaffold already reserves system bars; only the extra IME area is needed here.
                contentInsets = WindowInsets.ime.exclude(WindowInsets.systemBars)) { modifier ->
                PhotoImage(preview, model.photoFiles, modifier, 2048)
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("photo-gallery"), state = galleryScroll, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { FlowRow { TextButton(onClick = onBack) { Text("返回") }; TextButton(onClick = onEdit) { Text("編輯記事描述") } } }
        item { Text(note.title, style = MaterialTheme.typography.headlineLarge) }
        item { Text("記事時間：${captureLabel(requireNotNull(note.photoCapturedAt))}", modifier = Modifier.testTag("photo-note-time")) }
        item { Text(note.description.ifEmpty { "尚未輸入描述" }) }
        item { Button(enabled = !state.saving && state.capturingSession == null, onClick = { onCamera(java.util.UUID.randomUUID().toString()) }, modifier = Modifier.testTag("append-photos")) { Text("加入更多照片") } }
        if (photos.isEmpty()) item { Text("這篇記事目前沒有照片，原始拍攝時間仍保留。") }
        items(photos, key = { it.id }) { photo ->
            Card(Modifier.fillMaxWidth().testTag("gallery-photo-${photo.id}")) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoImage(photo, model.photoFiles, Modifier.fillMaxWidth().height(240.dp).clickable { previewId = photo.id })
                    Text("拍攝：${captureLabel(photo.capturedAt)}")
                    if (photo.caption.isNotEmpty()) Text(photo.caption)
                    FlowRow {
                        TextButton(onClick = { previewId = photo.id }, modifier = Modifier.testTag("preview-photo-${photo.id}")) { Text("預覽與照片說明") }
                        TextButton(enabled = !state.saving, onClick = { removeId = photo.id }, modifier = Modifier.testTag("remove-photo-${photo.id}")) { Text("移除照片") }
                    }
                }
            }
        }
    }
}
