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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nqutaoitfteikc.notehub.domain.ScheduleKind
import com.nqutaoitfteikc.notehub.domain.CalendarImportEvent
import java.time.*

@Composable
fun CalendarImportScreen(model: CalendarImportViewModel, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val working = state.busy || state.awaitingAuthorization
    val activity = LocalContext.current.activity()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { model.resolved(it.resultCode, it.data) }
    LaunchedEffect(state.resolution) {
        state.resolution?.let {
            model.resolutionLaunched()
            try { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            catch (error: Exception) {
                android.util.Log.w("CalendarImport", "無法開啟授權畫面", error)
                model.resolutionFailed()
            }
        }
    }
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack, enabled = !working) { Text("返回設定") }
            Text("Google 行事曆匯入", style = MaterialTheme.typography.headlineMedium)
            Text("經您授權後唯讀取得事件，預覽確認後才存入此裝置。沒有寫回或雲端備份。")
        }
        if (!model.configured) item {
            Text("尚未設定 Google OAuth", style = MaterialTheme.typography.titleLarge)
            Text("請依專案 GOOGLE_SETUP.md 設定 Web client ID、Android 套件與簽章、Calendar API 及測試使用者，再重新建置。其餘本機功能仍可使用。")
        }
        item {
            state.account?.let { Text("已登入：$it") }
            if (state.account == null) Button(onClick = { activity?.let(model::signIn) }, enabled = model.configured && activity != null && !working) { Text("使用 Google 帳號登入") }
            else {
                OutlinedButton(onClick = model::signOut, enabled = !working) { Text("登出此 App") }
                Text("登出會清除本次存取狀態，保留本機記事；撤銷授權可至 Google 帳戶的第三方連線設定。")
                Button(onClick = model::authorize, enabled = !working) { Text(if (state.authorized) "重新確認唯讀授權" else "授權讀取行事曆") }
            }
        }
        if (working) item { CircularProgressIndicator(); Text("處理中，請稍候…") }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        state.result?.let { result -> item { Text("匯入完成：新增 ${result.inserted} 筆，重新匯入 ${result.restored} 筆，跳過 ${result.skipped} 筆。可回記事頁查看。") } }
        if (state.authorized) {
            item {
                Text("選擇行事曆", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = model::reload, enabled = !working) { Text("重新載入行事曆") }
                if (state.calendarsLoaded && state.calendars.isEmpty() && !working) Text("目前沒有可讀取的行事曆")
            }
            items(state.calendars, key = { "calendar:${it.id}" }) { calendar ->
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(calendar.id in state.selectedCalendars, { model.selectCalendar(calendar.id, it) }, enabled = !working)
                    Column(Modifier.weight(1f)) { Text(calendar.title); Text(calendar.zone, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                Text("匯入日期（含起訖日）", style = MaterialTheme.typography.titleLarge)
                DateInput("開始日期", state.first, !working) { model.range(it, state.last) }
                DateInput("結束日期（含當日）", state.last, !working) { model.range(state.first, it) }
                Text("範圍時區：${state.rangeZone}，最多 367 天。重複事件會展開為每次發生的事件。")
                Button(onClick = model::preview, enabled = !working && state.selectedCalendars.isNotEmpty()) { Text("讀取事件預覽") }
            }
        }
        state.preview?.let { preview ->
            item {
                Text("預覽 ${preview.events.size} 筆", style = MaterialTheme.typography.titleLarge)
                Text("已取消事件略過 ${preview.cancelled} 筆。仍存在的本機副本會跳過；已刪除副本預設不勾選，可自行勾選重新匯入。不覆蓋本機修改，Google 後續變更不會自動同步，Google 提醒不會自動建立本機鬧鐘。")
                Button(onClick = { confirm = true }, enabled = !working && state.selectedEvents.isNotEmpty()) { Text("確認匯入 ${state.selectedEvents.size} 筆") }
            }
            items(preview.events, key = { "event:${it.calendarId.length}:${it.calendarId}${it.instanceKey}" }) { event ->
                CalendarImportEventCard(event, (event.calendarId to event.instanceKey) in state.selectedEvents, working) {
                    model.selectEvent(event.calendarId to event.instanceKey, it)
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("將事件複製到本機？") },
        text = { Text("將所選 ${state.selectedEvents.size} 筆事件存為本機記事。勾選的已刪除副本會重新匯入，不恢復舊的本機修改或提醒；Google 行事曆不會被修改。") },
        confirmButton = { TextButton(onClick = { confirm = false; model.confirm() }) { Text("開始匯入") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}

@Composable
internal fun CalendarImportEventCard(event: CalendarImportEvent, selected: Boolean, working: Boolean, onSelect: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            Checkbox(selected, onSelect, enabled = !working && event.canSelect)
            Column(Modifier.weight(1f)) {
                Text(event.draft.title, style = MaterialTheme.typography.titleMedium)
                Text(event.draft.category)
                Text(if (event.draft.kind == ScheduleKind.ALL_DAY) "全天：${LocalDate.ofEpochDay(event.draft.startDay!!)} 至 ${LocalDate.ofEpochDay(event.draft.endDayExclusive!!).minusDays(1)}"
                    else "${Instant.ofEpochMilli(event.draft.startMillis!!).atZone(ZoneId.of(event.draft.zoneId))} 至 ${Instant.ofEpochMilli(event.draft.endMillis!!).atZone(ZoneId.of(event.draft.zoneId))}")
                if (event.draft.description.isNotEmpty()) Text(event.draft.description)
                event.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (event.locallyDeleted) Text("本機副本已刪除，可勾選重新匯入")
                else if (event.alreadyImported) Text("已匯入，將跳過")
            }
        }
    }
}
private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

