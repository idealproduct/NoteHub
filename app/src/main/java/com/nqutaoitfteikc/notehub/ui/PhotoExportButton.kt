package com.nqutaoitfteikc.notehub.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.nqutaoitfteikc.notehub.domain.Photo
import com.nqutaoitfteikc.notehub.photos.PhotoExporter
import com.nqutaoitfteikc.notehub.photos.PhotoFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PhotoExportButton(photo: Photo, files: PhotoFiles) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable(photo.id) { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            try { PhotoExporter(context, files).export(photo, uri); message = "照片已匯出" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                android.util.Log.e("NoteHubPhotos", "照片匯出失敗", e)
                message = "照片匯出失敗，請確認目的地權限與可用空間後重試"
            } finally { busy = false }
        }
    }
    TextButton(enabled = !busy, onClick = {
        try { launcher.launch("NoteHub_${photo.capturedAt}_${photo.id.take(8)}.jpg") }
        catch (e: android.content.ActivityNotFoundException) { message = "此裝置沒有檔案儲存工具，請啟用系統檔案應用程式後重試" }
    }) {
        Text(if (busy) "匯出中…" else "匯出照片")
    }
    message?.let { Text(it) }
}
