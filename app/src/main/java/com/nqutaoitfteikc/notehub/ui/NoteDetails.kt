package com.nqutaoitfteikc.notehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nqutaoitfteikc.notehub.domain.Note
import java.time.ZoneId

@Composable
internal fun NoteDetails(note: Note, saving: Boolean, reminderCount: Int, onBack: () -> Unit,
    onEdit: () -> Unit, onReminders: () -> Unit, onPhotos: () -> Unit, onComplete: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("note-details"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("返回") }
        Text("事件詳細資訊", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(note.title, style = MaterialTheme.typography.headlineMedium)
        Text(note.category.ifEmpty { "未分類" }, color = MaterialTheme.colorScheme.primary)
        Text(scheduleLabel(note, ZoneId.systemDefault()))
        if (note.zoneId != null) Text("原始時區：${note.zoneId} · 目前以 ${ZoneId.systemDefault().id} 顯示", style = MaterialTheme.typography.bodySmall)
        Row {
            Checkbox(note.completed, enabled = !saving, onCheckedChange = onComplete,
                modifier = Modifier.semantics { contentDescription = "${if (note.completed) "標記未完成" else "標記完成"}：${note.title}" })
            Text(if (note.completed) "已完成" else "未完成", Modifier.padding(top = 12.dp))
        }
        HorizontalDivider()
        Text("描述", style = MaterialTheme.typography.titleMedium)
        Text(note.description.ifEmpty { "沒有描述" })
        Button(enabled = !saving, onClick = onEdit, modifier = Modifier.fillMaxWidth()
            .semantics { contentDescription = "編輯：${note.title}" }) { Text("編輯事件") }
        OutlinedButton(enabled = !saving, onClick = onReminders, modifier = Modifier.fillMaxWidth().testTag("open-reminders-${note.id}")) { Text("管理提醒（$reminderCount 筆）") }
        if (note.photoCapturedAt != null) OutlinedButton(enabled = !saving, onClick = onPhotos,
            modifier = Modifier.fillMaxWidth().testTag("open-photos-${note.id}")) { Text("查看照片") }
    }
}
