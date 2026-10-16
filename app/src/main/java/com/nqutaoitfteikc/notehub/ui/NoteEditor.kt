package com.nqutaoitfteikc.notehub.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import com.nqutaoitfteikc.notehub.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private val dateInputFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
private val timeInputFormat = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)

@Composable
fun NoteEditor(note: Note?, initialDay: Long, saving: Boolean, onBack: () -> Unit,
               onSave: (NoteDraft) -> Unit, onDelete: () -> Unit, categories: List<String>) {
    val zone = remember(note?.id) { ZoneId.of(note?.zoneId ?: ZoneId.systemDefault().id) }
    val initialStart = remember(note?.id) { note?.startMillis?.let { Instant.ofEpochMilli(it).atZone(zone) } }
    val initialEnd = remember(note?.id) { note?.endMillis?.let { Instant.ofEpochMilli(it).atZone(zone) } }
    val date = remember(note?.id) { LocalDate.ofEpochDay(initialDay) }
    var title by rememberSaveable(note?.id) { mutableStateOf(note?.title ?: "") }
    var description by rememberSaveable(note?.id) { mutableStateOf(note?.description ?: "") }
    var category by rememberSaveable(note?.id) { mutableStateOf(note?.category ?: "") }
    var kindName by rememberSaveable(note?.id) { mutableStateOf((note?.kind ?: ScheduleKind.TODO).name) }
    val kind = ScheduleKind.valueOf(kindName)
    var startDate by rememberSaveable(note?.id) { mutableStateOf((initialStart?.toLocalDate() ?: note?.startDay?.let(LocalDate::ofEpochDay) ?: date).toString()) }
    var endDate by rememberSaveable(note?.id) { mutableStateOf((initialEnd?.toLocalDate() ?: note?.endDayExclusive?.let { LocalDate.ofEpochDay(it).minusDays(1) } ?: date).toString()) }
    var startTime by rememberSaveable(note?.id) { mutableStateOf(initialStart?.toLocalTime()?.format(timeInputFormat) ?: "09:00") }
    var endTime by rememberSaveable(note?.id) { mutableStateOf(initialEnd?.toLocalTime()?.format(timeInputFormat) ?: "10:00") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    fun back() { if (!saving) { if (dirty) discard = true else onBack() } }
    BackHandler { back() }

    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("捨棄未儲存的修改？") },
        text = { Text("返回後，這次尚未儲存的內容將不會保留。") },
        confirmButton = { TextButton(onClick = onBack) { Text("捨棄修改") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("繼續編輯") } })
    if (delete) AlertDialog(onDismissRequest = { if (!saving) delete = false }, title = { Text("刪除此事項？") },
        text = { Text("「${note?.title}」將從本機刪除，無法復原。") },
        confirmButton = { TextButton(enabled = !saving, onClick = { delete = false; onDelete() }) { Text("確認刪除") } },
        dismissButton = { TextButton(enabled = !saving, onClick = { delete = false }) { Text("取消") } })

    fun saveDraft() {
            error = null
            try {
                val draft = if (note?.photoCapturedAt != null) NoteDraft(title, description, category) else when (kind) {
                    ScheduleKind.TODO -> NoteDraft(title, description, category)
                    ScheduleKind.ALL_DAY -> NoteDraft(title, description, category, kind,
                        LocalDate.parse(startDate, dateInputFormat).toEpochDay(), LocalDate.parse(endDate, dateInputFormat).plusDays(1).toEpochDay())
                    ScheduleKind.TIMED -> NoteDraft(title, description, category, kind,
                        startMillis = CalendarRules.resolveLocal(LocalDate.parse(startDate, dateInputFormat), LocalTime.parse(startTime, timeInputFormat), zone),
                        endMillis = CalendarRules.resolveLocal(LocalDate.parse(endDate, dateInputFormat), LocalTime.parse(endTime, timeInputFormat), zone), zoneId = zone.id)
                }.validated()
                onSave(draft)
            } catch (_: DateTimeException) { error = "日期或時間格式不正確，請使用 yyyy-MM-dd 與 HH:mm，或使用選擇按鈕" }
            catch (e: IllegalArgumentException) { error = e.message ?: "請檢查輸入內容" }

    }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
    val compact = maxHeight < 360.dp
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = !saving, onClick = { back() }) { Text("返回") }
            if (!compact) Text(if (note == null) "新增事項" else "編輯事項", modifier = Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.titleLarge)
            if (note != null) TextButton(enabled = !saving, onClick = { delete = true }) { Text("刪除") }
            if (compact) Button(enabled = !saving, onClick = ::saveDraft, modifier = Modifier.testTag("editor-save")) { Text("儲存") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = if (compact) 4.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it; dirty = true }, label = { Text("事項標題") }, singleLine = true,
                enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("editor-title"))
            OutlinedTextField(description, { description = it; dirty = true }, label = { Text("描述") }, minLines = if (compact) 1 else 2,
                enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("editor-description"))
            OutlinedTextField(category, { category = it; dirty = true }, label = { Text("分類（可自行輸入）") }, singleLine = true,
                enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("editor-category"))
            if (categories.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { value -> SuggestionChip(onClick = { category = value; dirty = true }, label = { Text(value) }, enabled = !saving) }
            }
            if (note?.photoCapturedAt != null) {
                Text("照片記事時間：${captureLabel(note.photoCapturedAt)}")
                Text("拍攝時間會永久保留，編輯描述不會改變。")
            } else {
            Text("時間安排", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleKind.entries.forEach { value -> FilterChip(selected = kind == value, enabled = !saving,
                    onClick = { kindName = value.name; dirty = true }, label = { Text(value.label()) }) }
            }
            if (kind == ScheduleKind.TODO) Text("不指定日期與時間，會列在「無時間待辦」區。")
            else {
                DateInput("開始日期", startDate, !saving) { startDate = it; dirty = true }
                DateInput(if (kind == ScheduleKind.ALL_DAY) "結束日期（含當日）" else "結束日期", endDate, !saving) { endDate = it; dirty = true }
                if (kind == ScheduleKind.TIMED) {
                    TimeInput("開始時間", startTime, !saving) { startTime = it; dirty = true }
                    TimeInput("結束時間", endTime, !saving) { endTime = it; dirty = true }
                    Text("事件時區：${zone.id}。行事曆依裝置目前時區顯示。")
                    Text("若日光節約時間造成同一時刻出現兩次，採第一次出現的時間。", style = MaterialTheme.typography.bodySmall)
                } else Text("全天事件包含開始與結束日期，不會因時區切換而改變日期。")
            }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp).testTag("editor-error")) }
        if (!compact) Button(enabled = !saving, modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("editor-save"), onClick = ::saveDraft) { Text(if (saving) "儲存中…" else "儲存事項") }
    }
}

}

fun ScheduleKind.label() = when (this) { ScheduleKind.TODO -> "無時間待辦"; ScheduleKind.ALL_DAY -> "全天"; ScheduleKind.TIMED -> "指定時間" }

@Composable
internal fun DateInput(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val context = pickerContext()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled, modifier = Modifier.weight(1f).testTag(if (label.startsWith("開始")) "start-date" else "end-date"))
        TextButton(enabled = enabled, onClick = {
            val date = runCatching { LocalDate.parse(value, dateInputFormat) }.getOrDefault(LocalDate.now())
            DatePickerDialog(context, { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
                setTitle(label)
                setButton(android.content.DialogInterface.BUTTON_POSITIVE, "確定", this)
                setButton(android.content.DialogInterface.BUTTON_NEGATIVE, "取消", this)
            }.show()
        }) { Text("選擇日期") }
    }
}

@Composable
internal fun TimeInput(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val context = pickerContext()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled, modifier = Modifier.weight(1f).testTag(if (label.startsWith("開始")) "start-time" else "end-time"))
        TextButton(enabled = enabled, onClick = {
            val time = runCatching { LocalTime.parse(value, timeInputFormat) }.getOrDefault(LocalTime.of(9, 0))
            TimePickerDialog(context, { _, hour, minute -> onChange(LocalTime.of(hour, minute).format(timeInputFormat)) }, time.hour, time.minute, true).apply {
                setTitle(label)
                setButton(android.content.DialogInterface.BUTTON_POSITIVE, "確定", this)
                setButton(android.content.DialogInterface.BUTTON_NEGATIVE, "取消", this)
            }.show()
        }) { Text("選擇時間") }
    }
}


