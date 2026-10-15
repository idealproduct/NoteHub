package com.nqutaoitfteikc.notehub.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.reminders.ReminderPlatform
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

@Composable
fun ReminderPermissions(platform: ReminderPlatform, restore: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        if (!granted) message = "通知尚未開啟。提醒已保存，開啟通知後才會排程。"
        restore()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val notify = remember(refresh) { platform.canNotify() }
    val exact = remember(refresh) { platform.canExact() }
    val battery = remember(refresh) { platform.batteryUnrestricted() }
    fun open(intent: Intent) {
        try { context.startActivity(intent) }
        catch (_: ActivityNotFoundException) { message = "此裝置沒有對應設定頁，請從系統設定開啟 NoteHub 權限" }
        catch (_: SecurityException) { message = "系統無法開啟此設定，請手動前往設定確認" }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("提醒權限與限制", style = MaterialTheme.typography.titleMedium)
        Text(if (notify) "通知：已開啟" else "通知：未開啟，提醒暫不排程")
        Text(if (exact) "精準鬧鐘：可使用" else "精準鬧鐘：未允許，將使用可能延遲的排程")
        Text(if (battery) "電池：未受最佳化限制" else "電池：系統或廠牌省電設定可能影響提醒；待機時多個提醒也可能被合併或延遲")
        if (!notify && Build.VERSION.SDK_INT >= 33) TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("允許通知") }
        TextButton(onClick = { open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("通知設定") }
        if (Build.VERSION.SDK_INT >= 31 && !exact) TextButton(onClick = {
            open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }) { Text("允許精準鬧鐘") }
        TextButton(onClick = { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("檢查省電設定") }
        TextButton(onClick = { restore(); refresh++ }) { Text("重新檢查並恢復排程") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun RemindersScreen(note: Note, reminders: List<Reminder>, model: HubViewModel, onBack: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val initial = remember { ZonedDateTime.now(zone).plusHours(1) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var date by rememberSaveable { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable { mutableStateOf(initial.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val saving = model.state.collectAsState().value.saving
    if (pendingDelete != null) AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("移除此提醒？") },
        text = { Text("這一筆排程與已顯示的通知都會取消，其他提醒會保留。") },
        confirmButton = { TextButton(enabled = !saving, onClick = { model.deleteReminder(requireNotNull(pendingDelete)); pendingDelete = null }) { Text("確認移除") } },
        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } })
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, enabled = !saving) { Text("返回") }
            Text("事項提醒", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).testTag("reminders-content"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(note.title, style = MaterialTheme.typography.titleLarge)
            ReminderPermissions(model.reminderPlatform, model::restoreReminders)
            if (note.completed) Text("此事項已完成，所有未送出的提醒暫停。恢復未完成後，只會恢復尚未送出且未過期的提醒。")
            Text(if (editing == null) "新增獨立提醒" else "編輯提醒", style = MaterialTheme.typography.titleMedium)
            DateInput("提醒日期", date, !saving) { date = it }
            TimeInput("提醒時間", time, !saving) { time = it }
            Text("時區：${zone.id}。以實際時間保存；切換時區後不會改變觸發瞬間。")
            Text("重開機後，15 分鐘內錯過的提醒會補排；更早的提醒標示為過期。強制停止 App 後，需手動開啟才能恢復。", style = MaterialTheme.typography.bodySmall)
            Row {
                Switch(checked = enabled, enabled = !saving, onCheckedChange = { enabled = it }, modifier = Modifier.testTag("reminder-enabled"))
                Text("啟用此提醒", modifier = Modifier.padding(12.dp))
            }
            TextButton(enabled = !saving, onClick = {
                val soon = ZonedDateTime.now(zone).plusMinutes(1)
                date = soon.toLocalDate().toString(); time = soon.format(DateTimeFormatter.ofPattern("HH:mm"))
            }) { Text("設為約 1 分鐘後") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !saving, modifier = Modifier.testTag("save-reminder"), onClick = {
                error = null
                try {
                    val at = CalendarRules.resolveLocal(LocalDate.parse(date, DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)),
                        LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)), zone)
                    require(at > System.currentTimeMillis()) { "提醒必須設定在未來的時間" }
                    model.saveReminder(editing, note.id, at, enabled) { editing = null }
                } catch (_: DateTimeException) { error = "日期或時間格式不正確，請使用選擇器或 yyyy-MM-dd 與 HH:mm" }
                catch (e: IllegalArgumentException) { error = e.message ?: "請檢查提醒時間" }
            }) { Text(if (saving) "儲存中…" else if (editing == null) "新增提醒" else "儲存提醒修改") }
            if (editing != null) TextButton(onClick = { editing = null; enabled = true }) { Text("取消編輯") }
            HorizontalDivider()
            Text("獨立提醒（${reminders.size}）", style = MaterialTheme.typography.titleLarge)
            if (reminders.isEmpty()) Text("尚未設定提醒")
            reminders.forEach { reminder ->
                Card(Modifier.fillMaxWidth().testTag("reminder-${reminder.id}")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Instant.ofEpochMilli(reminder.triggerAt).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")))
                        Text(reminder.status.label())
                        Row {
                            TextButton(enabled = !saving, modifier = Modifier.testTag("edit-reminder-${reminder.id}"), onClick = {
                                editing = reminder.id
                                val value = Instant.ofEpochMilli(reminder.triggerAt).atZone(zone)
                                date = value.toLocalDate().toString(); time = value.format(DateTimeFormatter.ofPattern("HH:mm")); enabled = reminder.enabled
                            }) { Text("編輯時間與啟用狀態") }
                            TextButton(enabled = !saving, modifier = Modifier.testTag("delete-reminder-${reminder.id}"), onClick = { pendingDelete = reminder.id }) { Text("移除") }
                        }
                    }
                }
            }
        }
    }
}
fun ReminderStatus.label() = when (this) {
    ReminderStatus.PENDING -> "等待排程"
    ReminderStatus.EXACT -> "已排程 · 精準鬧鐘（省電模式仍有限制）"
    ReminderStatus.APPROXIMATE -> "已排程 · 非精準，可能延遲"
    ReminderStatus.BLOCKED_NOTIFICATION -> "通知未開啟，尚未排程"
    ReminderStatus.DISABLED -> "已停用"
    ReminderStatus.PAUSED_COMPLETED -> "事項已完成，提醒暫停"
    ReminderStatus.EXPIRED -> "已過期，不再補發"
    ReminderStatus.DELIVERED -> "已送出"
    ReminderStatus.ERROR -> "排程或發送失敗，請檢查權限並重新恢復排程"
}
