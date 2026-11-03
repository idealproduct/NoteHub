package com.nqutaoitfteikc.notehub.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CalendarSyncScreen(model: CalendarSyncViewModel, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val config by model.config.collectAsStateWithLifecycle()
    val conflicts by model.conflicts.collectAsStateWithLifecycle()
    val titles by model.noteTitles.collectAsStateWithLifecycle()
    val activity = LocalContext.current.syncActivity()
    val working = state.busy || state.awaiting
    var confirm by remember { mutableStateOf(false) }
    var resolution by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var intervalMenu by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { model.resolved(it.resultCode, it.data) }
    LaunchedEffect(state.resolution) {
        state.resolution?.let {
            model.launched()
            try { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            catch (_: Exception) { model.launchFailed() }
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("sync-content"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("返回設定") }
            Text("Google 雙向同步", style = MaterialTheme.typography.headlineMedium)
            Text("同步標題、描述、全天與指定時間。分類、完成狀態、照片檔案及提醒只保存在本機；照片記事（含文字、時間與位置）不再同步；舊雲端副本需於 Google 手動管理。無日期待辦不會上傳。")
            Text("Google 新事件讀取範圍為過去 30 天至未來 335 天；已連結事件即使移出範圍也會持續更新。本機有日期且未從其他行事曆匯入的事項會上傳至您選定的一個可寫入行事曆。")
        }
        item {
            Text(if (config.enabled) "定期同步已啟用：每 ${config.hours} 小時" else "定期同步未啟用", style = MaterialTheme.typography.titleMedium)
            if (config.account.isNotBlank()) Text("${config.account} · ${config.calendar.title}")
            Text(config.status)
            if (config.lastSuccess > 0) Text("上次完成：${captureLabel(config.lastSuccess)}")
            Text("需連線網路，省電與系統背景限制可能延後。強制停止後需重新開啟 App。停用會等目前同步結束，保留兩邊已有資料。")
            if (config.enabled) {
                OutlinedButton(onClick = model::now, enabled = !working) { Text("立即同步") }
                TextButton(onClick = model::disable, enabled = !working) { Text("停用定期同步") }
            }
        }
        item {
            Button(onClick = { activity?.let(model::signIn) }, enabled = !working && activity != null) { Text("登入／切換 Google 帳號") }
            state.account?.let { Text("本次選擇：$it") }
            OutlinedButton(onClick = model::authorize, enabled = !working && (state.account != null || config.account.isNotBlank())) { Text("授權 Google 事件讀寫") }
            if (working) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (state.authorized) {
            item { Text("選擇同步行事曆", style = MaterialTheme.typography.titleLarge); if (state.calendars.isEmpty()) Text("沒有可寫入的行事曆，請確認帳號或分享權限。") }
            items(state.calendars, key = { it.id }) { calendar ->
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(state.selected == calendar.id, { model.select(calendar.id) }, enabled = !working)
                    Text(calendar.title, Modifier.weight(1f).padding(top = 12.dp))
                }
            }
            item {
                Box {
                    OutlinedButton(onClick = { intervalMenu = true }, enabled = !working) { Text("同步頻率：每 ${state.hours} 小時") }
                    DropdownMenu(intervalMenu, { intervalMenu = false }) {
                        listOf(1, 6, 12, 24).forEach { hours -> DropdownMenuItem({ Text("每 $hours 小時") }, { model.hours(hours); intervalMenu = false }) }
                    }
                }
                Button(onClick = { confirm = true }, enabled = !working && state.selected != null) { Text("啟用／更新雙向同步設定") }
            }
        }
        item { Text("衝突與刪除確認", style = MaterialTheme.typography.titleLarge); Text("兩邊同時修改、首次內容不同及刪除皆需手動決定。選擇版本時會重新取得 Google 最新資料。") }
        val visible = conflicts.filter { it.account == config.account && it.calendarId == config.calendar.id }
        if (visible.isEmpty()) item { Text("沒有待處理的衝突") }
        items(visible, key = { it.eventId }) { conflict ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(conflict.conflict)
                    Text(titles[conflict.noteId] ?: "本機已刪除的記事（${conflict.noteId.take(8)}）", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { resolution = conflict.eventId to true }, enabled = !working && state.authorized && config.enabled) { Text("採用 Google 目前狀態") }
                    TextButton(onClick = { resolution = conflict.eventId to false }, enabled = !working && state.authorized && config.enabled) { Text("採用本機目前狀態") }
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("啟用雙向同步？") },
        text = { Text("本機有日期的原生事項（排除照片記事）將上傳至選定行事曆，Google 事件也會存入本機。後續單邊修改會自動更新另一邊。照片不會上傳；刪除與衝突需要另行確認。變更帳號或行事曆會建立新的同步連結，原有副本保留。") },
        confirmButton = { TextButton(onClick = { confirm = false; model.enable() }) { Text("同意並啟用") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
    resolution?.let { choice -> AlertDialog(onDismissRequest = { resolution = null }, title = { Text("確認採用${if (choice.second) " Google " else "本機"}狀態？") },
        text = { Text("這會覆蓋另一邊的同步欄位。如果所選的一邊已刪除，另一邊也會被刪除；刪除本機照片記事會一併移除照片與提醒。此操作以現在的資料為準。") },
        confirmButton = { TextButton(onClick = { resolution = null; model.resolve(choice.first, choice.second) }) { Text("確認套用") } },
        dismissButton = { TextButton(onClick = { resolution = null }) { Text("取消") } }) }
}
private tailrec fun Context.syncActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.syncActivity(); else -> null }
