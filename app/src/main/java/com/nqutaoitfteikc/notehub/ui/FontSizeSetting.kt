package com.nqutaoitfteikc.notehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun FontSizeSetting(scale: Float, onChange: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("字型大小", style = MaterialTheme.typography.titleMedium)
        Text("在系統字型大小上調整，100% 表示跟隨系統。")
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.testTag("font-size-menu")) { Text("字型大小：${(scale * 100).roundToInt()}%") }
            DropdownMenu(expanded, { expanded = false }) {
                listOf(0.8f, 1f, 1.15f, 1.3f, 1.5f).forEach { value ->
                    val percent = (value * 100).roundToInt()
                    DropdownMenuItem(text = { Text("$percent%${if (value == 1f) "（跟隨系統）" else ""}") },
                        modifier = Modifier.testTag("font-size-$percent"), onClick = { expanded = false; onChange(value) })
                }
            }
        }
        Text("文字預覽：記錄生活，掌握每一天。", modifier = Modifier.testTag("font-size-preview"))
        TextButton(onClick = { onChange(1f) }) { Text("恢復系統字型大小") }
    }
}
