package com.nqutaoitfteikc.notehub.ui

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nqutaoitfteikc.notehub.domain.Photo

@Composable
internal fun PhotoExportButton(photo: Photo, busy: Boolean, onExport: (List<String>) -> Unit) {
    AlbumExportAction(listOf(photo.id), busy, onExport, "儲存至相簿", "export-photo-${photo.id}")
}

@Composable
internal fun PhotoBatchExport(photos: List<Photo>, busy: Boolean, onExport: (List<String>) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val validIds = photos.map { it.id }.toSet()
    val current = selected.filter { it in validIds }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { expanded = !expanded }, enabled = !busy, modifier = Modifier.testTag("batch-export-toggle")) { Text("批次儲存至相簿") }
        if (expanded) {
            Text("選擇這篇記事的照片（${current.size}／${photos.size}）")
            TextButton(onClick = { selected = if (current.size == photos.size) emptyList() else photos.map { it.id } }, enabled = !busy) {
                Text(if (current.size == photos.size) "取消全選" else "全選")
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("batch-photo-list")) {
                itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(photo.id in current, enabled = !busy, modifier = Modifier.testTag("select-export-${photo.id}"), onCheckedChange = { checked ->
                            selected = if (checked) (current + photo.id).distinct() else current - photo.id
                        })
                        Text("第 ${index + 1} 張 · ${captureLabel(photo.capturedAt)}" + if (photo.caption.isBlank()) "" else "\n${photo.caption.take(100)}",
                            Modifier.weight(1f).padding(vertical = 12.dp))
                    }
                }
            }
            AlbumExportAction(current, busy, onExport, "將選取的 ${current.size} 張儲存至相簿", "export-selected")
        }
    }
}

@Composable
private fun AlbumExportAction(ids: List<String>, busy: Boolean, onExport: (List<String>) -> Unit, label: String, tag: String) {
    val context = LocalContext.current
    var review by remember { mutableStateOf<List<String>?>(null) }
    var pending by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onExport(pending) else message = "未允許儲存權限，無法將照片加入相簿；原始照片仍保留"
        pending = emptyList()
    }
    TextButton(enabled = !busy && ids.isNotEmpty(), modifier = Modifier.testTag(tag), onClick = {
        message = null
        review=ids.toList()
    }) { Text(if (busy) "匯出中…" else label) }
    review?.let { chosen ->
        AlertDialog(onDismissRequest={review=null},title={Text("匯出前請檢查照片內容")},
            text={Text("將產生 ${chosen.size} 張乾淨副本，不附 GPS、拍攝時間、裝置或註解資訊。地標、招牌與畫面仍可能透露位置；請確認背景沒有禁攝標誌、敏感設施或個人資訊。相簿若有雲端備份，副本可能由相簿服務上傳。")},
            confirmButton={TextButton(onClick={
                review=null
                if(Build.VERSION.SDK_INT<=28 && ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) { pending=chosen; launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                else onExport(chosen)
            },modifier=Modifier.testTag("confirm-private-export")) { Text("已檢查，匯出副本") }},
            dismissButton={TextButton(onClick={review=null}) { Text("返回檢查") }})
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
