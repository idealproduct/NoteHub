package com.nqutaoitfteikc.notehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** A stable editor subtree: IME resizing changes placement, never the editor's parent/focus. */
@Composable
internal fun PhotoPreviewContent(capturedAt: Long, caption: String, onCaption: (String) -> Unit,
    saving: Boolean, onClose: () -> Unit, onSave: () -> Unit,
    contentInsets: WindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime), image: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(contentInsets).testTag("photo-preview-safe-area")) {
        val compact = maxHeight < 360.dp
        val ultra = maxHeight < 180.dp
        val landscape = maxWidth >= 480.dp && maxWidth > maxHeight
        Layout(modifier = Modifier.align(Alignment.Center).widthIn(max = 1000.dp).fillMaxSize().padding(8.dp), content = {
            TextButton(onClick = onClose, modifier = Modifier.testTag("close-photo-preview")) {
                Text(if (compact) "關閉" else "關閉預覽")
            }
            Box(Modifier.testTag("photo-preview-image-area")) { image(Modifier.fillMaxSize()) }
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("photo-preview-body"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!ultra) Text("拍攝時間：${captureLabel(capturedAt)}")
                OutlinedTextField(caption, onCaption, label = { Text("照片說明") }, enabled = !saving,
                    maxLines = if (ultra) 1 else 3, modifier = Modifier.fillMaxWidth().testTag("photo-caption"))
            }
            Button(enabled = !saving, onClick = onSave, modifier = Modifier.testTag("save-photo-caption")) {
                Text(if (compact) "儲存" else "儲存照片說明")
            }
        }) { children, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val gap = 8.dp.roundToPx()
            val close = children[0].measure(Constraints(maxWidth = if (ultra) w / 4 else w / 2, maxHeight = h))
            val save = children[3].measure(if (compact) Constraints(maxWidth = w / 3, maxHeight = h)
                else Constraints(minWidth = w, maxWidth = w, maxHeight = h))
            val headerHeight = if (compact) maxOf(close.height, save.height) else close.height
            val bodyTop = headerHeight + gap
            val bodyHeight = (h - bodyTop - if (compact) 0 else save.height + gap).coerceAtLeast(0)
            val imageWidth: Int
            val imageHeight: Int
            val editorWidth: Int
            val editorHeight: Int
            if (ultra) {
                imageWidth = 0; imageHeight = 0
                editorWidth = (w - close.width - save.width - gap * 2).coerceAtLeast(0)
                editorHeight = h
            } else if (landscape) {
                imageWidth = (w - gap).coerceAtLeast(0) / 2; imageHeight = bodyHeight
                editorWidth = (w - imageWidth - gap).coerceAtLeast(0); editorHeight = bodyHeight
            } else {
                imageWidth = w
                val reservedEditor = minOf(bodyHeight, 128.dp.roundToPx())
                imageHeight = if (compact) 0 else minOf((bodyHeight * 0.6f).toInt(), (bodyHeight - reservedEditor - gap).coerceAtLeast(0))
                editorWidth = w
                editorHeight = (bodyHeight - imageHeight - if (imageHeight > 0) gap else 0).coerceAtLeast(0)
            }
            val picture = children[1].measure(Constraints.fixed(imageWidth, imageHeight))
            val editor = children[2].measure(Constraints(minWidth = editorWidth, maxWidth = editorWidth, maxHeight = editorHeight))
            layout(w, h) {
                if (ultra) {
                    close.placeRelative(0, (h - close.height) / 2)
                    editor.placeRelative(close.width + gap, (h - editor.height) / 2)
                    save.placeRelative(w - save.width, (h - save.height) / 2)
                } else {
                    close.placeRelative(0, 0)
                    if (imageHeight > 0) picture.placeRelative(0, bodyTop)
                    editor.placeRelative(if (landscape) imageWidth + gap else 0,
                        if (landscape) bodyTop else bodyTop + imageHeight + if (imageHeight > 0) gap else 0)
                    save.placeRelative(if (compact) w - save.width else 0, if (compact) 0 else h - save.height)
                }
            }
        }
    }
}
