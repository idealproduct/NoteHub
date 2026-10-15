package com.nqutaoitfteikc.notehub.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nqutaoitfteikc.notehub.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateLabel = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.TAIWAN)
private val fullDateTime = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.TAIWAN)
private val clockTime = DateTimeFormatter.ofPattern("HH:mm", Locale.TAIWAN)

@Composable
fun CalendarScreen(state: HubState, onAdd: (Long) -> Unit, onEdit: (String) -> Unit, onComplete: (String, Boolean) -> Unit, onReminders: (String) -> Unit, onPhotos: (String) -> Unit) {
    var viewName by rememberSaveable { mutableStateOf(CalendarView.MONTH.name) }
    val view = CalendarView.valueOf(viewName)
    var selectedDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val date = LocalDate.ofEpochDay(selectedDay)
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var completionName by rememberSaveable { mutableStateOf(CompletionFilter.ALL.name) }
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) zone = ZoneId.systemDefault() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val context = pickerContext()
    val filtered = remember(state.notes, query, category, completionName) {
        CalendarRules.filter(state.notes, query, category, CompletionFilter.valueOf(completionName))
    }
    val categories = remember(state.notes, category) { (state.notes.map { it.category } + listOfNotNull(category)).distinct().sorted() }
    val todos = filtered.filter { it.kind == ScheduleKind.TODO }.sortedWith(compareBy<Note> { it.completed }.thenByDescending { it.createdAt })
    fun changePeriod(direction: Long) {
        selectedDay = when (view) {
            CalendarView.MONTH -> date.plusMonths(direction)
            CalendarView.WEEK -> date.plusWeeks(direction)
            else -> date.plusDays(direction)
        }.toEpochDay()
    }
    LazyColumn(Modifier.fillMaxSize().testTag("calendar-content"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("我的行事曆", style = MaterialTheme.typography.headlineMedium)
                Button(enabled = !state.loading && !state.saving, onClick = { onAdd(selectedDay) }, modifier = Modifier.testTag("add-note")) { Text("新增事項") }
            }
        }
        item {
            OutlinedTextField(query, { query = it }, label = { Text("搜尋標題、描述或分類") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("search"), trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清除") } })
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("全部分類") })
                categories.forEach { value -> FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value.ifEmpty { "未分類" }) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompletionFilter.entries.forEach { value -> FilterChip(selected = completionName == value.name, onClick = { completionName = value.name },
                    label = { Text(when (value) { CompletionFilter.ALL -> "全部狀態"; CompletionFilter.PENDING -> "未完成"; CompletionFilter.COMPLETED -> "已完成" }) }) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CalendarView.entries.forEach { value -> FilterChip(selected = view == value, modifier = Modifier.weight(1f).testTag("view-${value.name}"),
                    onClick = { viewName = value.name }, label = { Text(when (value) { CalendarView.MONTH -> "月"; CalendarView.WEEK -> "週"; CalendarView.DAY -> "日"; CalendarView.LIST -> "清單" }) }) }
            }
        }
        if (state.loading) item { CircularProgressIndicator() }
        else {
            if (view != CalendarView.LIST) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { changePeriod(-1) }) { Text("上一頁") }
                    TextButton(onClick = {
                        DatePickerDialog(context, { _, y, m, d -> selectedDay = LocalDate.of(y, m + 1, d).toEpochDay() }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
                            setTitle("跳至日期")
                            setButton(android.content.DialogInterface.BUTTON_POSITIVE, "確定", this)
                            setButton(android.content.DialogInterface.BUTTON_NEGATIVE, "取消", this)
                        }.show()
                    }, modifier = Modifier.testTag("selected-date")) { Text(date.toString()) }
                    TextButton(onClick = { changePeriod(1) }) { Text("下一頁") }
                    TextButton(onClick = { selectedDay = LocalDate.now().toEpochDay() }) { Text("今天") }
                }
            }
            item { Text(if (view == CalendarView.LIST) "所有日期 · 共 ${filtered.size} 筆" else "以 ${zone.id} 顯示 · 搜尋與篩選套用於目前視圖", style = MaterialTheme.typography.bodySmall) }
            when (view) {
                CalendarView.MONTH -> {
                    item { MonthGrid(date, filtered, zone) { selectedDay = it.toEpochDay() } }
                    item { Text(date.format(dateLabel), style = MaterialTheme.typography.titleLarge) }
                    val daily = CalendarRules.onDay(filtered, date, zone)
                    if (daily.isEmpty()) item { Text("這一天沒有符合條件的事項") }
                    noteCards(daily, "month", state.saving, zone, onEdit, onComplete, onReminders, onPhotos)
                }
                CalendarView.WEEK -> {
                    val start = CalendarRules.weekStart(date)
                    item { Text("${start} ～ ${start.plusDays(6)}", style = MaterialTheme.typography.titleMedium) }
                    repeat(7) { offset ->
                        val day = start.plusDays(offset.toLong())
                        item {
                            TextButton(onClick = { selectedDay = day.toEpochDay(); viewName = CalendarView.DAY.name }, modifier = Modifier.testTag("week-$day")) {
                                Text(day.format(dateLabel), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        val daily = CalendarRules.onDay(filtered, day, zone)
                        if (daily.isEmpty()) item { Text("沒有事項", style = MaterialTheme.typography.bodySmall) }
                        noteCards(daily, "week-$day", state.saving, zone, onEdit, onComplete, onReminders, onPhotos)
                    }
                }
                CalendarView.DAY -> {
                    item { Text(date.format(dateLabel), style = MaterialTheme.typography.titleLarge) }
                    val daily = CalendarRules.onDay(filtered, date, zone)
                    item { Text("全天", style = MaterialTheme.typography.titleMedium) }
                    val allDay = daily.filter { it.kind == ScheduleKind.ALL_DAY }
                    if (allDay.isEmpty()) item { Text("沒有全天事項") }
                    noteCards(allDay, "day-all", state.saving, zone, onEdit, onComplete, onReminders, onPhotos)
                    val timed = daily.filter { it.kind == ScheduleKind.TIMED }
                    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                    repeat(24) { hour ->
                        item {
                            HorizontalDivider()
                            Text("%02d:00".format(Locale.ROOT, hour), style = MaterialTheme.typography.labelLarge)
                        }
                        noteCards(timed.filter { Instant.ofEpochMilli(maxOf(requireNotNull(it.startMillis), dayStart)).atZone(zone).hour == hour }, "hour-$hour", state.saving, zone, onEdit, onComplete, onReminders, onPhotos)
                    }
                }
                CalendarView.LIST -> {
                    val scheduled = filtered.filter { it.kind != ScheduleKind.TODO }.sortedWith(compareBy<Note> {
                        if (it.kind == ScheduleKind.ALL_DAY) LocalDate.ofEpochDay(requireNotNull(it.startDay)).atStartOfDay(zone).toInstant().toEpochMilli()
                        else requireNotNull(it.startMillis)
                    }.thenBy { it.title })
                    item { Text("有日期的事項", style = MaterialTheme.typography.titleLarge) }
                    if (scheduled.isEmpty()) item { Text("沒有符合條件的有日期事項") }
                    noteCards(scheduled, "list", state.saving, zone, onEdit, onComplete, onReminders, onPhotos)
                }
            }
            item { Text("無時間待辦（不受日期限制）", style = MaterialTheme.typography.titleLarge) }
            if (todos.isEmpty()) item { Text("沒有符合條件的無時間待辦") }
            noteCards(todos, "todo", state.saving, zone, onEdit, onComplete, onReminders, onPhotos)
        }
    }
}

@Composable
private fun MonthGrid(date: LocalDate, notes: List<Note>, zone: ZoneId, select: (LocalDate) -> Unit) {
    val first = date.withDayOfMonth(1)
    val start = CalendarRules.weekStart(first)
    val cells = ((first.dayOfWeek.value - 1 + first.lengthOfMonth() + 6) / 7) * 7
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${date.year} 年 ${date.monthValue} 月", style = MaterialTheme.typography.titleLarge)
        Row { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, modifier = Modifier.weight(1f).padding(8.dp)) } }
        repeat(cells / 7) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { weekday ->
                    val day = start.plusDays((week * 7 + weekday).toLong())
                    val count = notes.count { CalendarRules.occursOn(it, day, zone) }
                    Surface(color = if (day == date) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f).heightIn(min = 60.dp).testTag("day-$day")
                            .semantics { contentDescription = "$day，$count 筆事項" }.clickable { select(day) }, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                            Text(day.dayOfMonth.toString(), color = if (day.month == date.month) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                            if (count > 0) Text("$count 筆", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.noteCards(notes: List<Note>, prefix: String, saving: Boolean, zone: ZoneId, edit: (String) -> Unit, complete: (String, Boolean) -> Unit, reminders: (String) -> Unit, photos: (String) -> Unit) {
    items(notes, key = { "$prefix-${it.id}" }) { note -> NoteCard(note, saving, zone, edit, complete, reminders, photos) }
}

@Composable
private fun NoteCard(note: Note, saving: Boolean, zone: ZoneId, edit: (String) -> Unit, complete: (String, Boolean) -> Unit, reminders: (String) -> Unit, photos: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("note-${note.id}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Checkbox(checked = note.completed, enabled = !saving, onCheckedChange = { complete(note.id, it) },
                    modifier = Modifier.semantics { contentDescription = "${if (note.completed) "標記未完成" else "標記完成"}：${note.title}" })
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium, textDecoration = if (note.completed) TextDecoration.LineThrough else null)
                    Text(note.category.ifEmpty { "未分類" }, style = MaterialTheme.typography.labelMedium)
                }
                TextButton(enabled = !saving, onClick = { edit(note.id) }, modifier = Modifier.semantics { contentDescription = "編輯：${note.title}" }) { Text("編輯") }
            }
            TextButton(enabled = !saving, onClick = { reminders(note.id) }, modifier = Modifier.testTag("open-reminders-${note.id}")) { Text("管理提醒") }
            if (note.photoCapturedAt != null) TextButton(enabled = !saving, onClick = { photos(note.id) }, modifier = Modifier.testTag("open-photos-${note.id}")) { Text("查看照片") }
            Text(scheduleLabel(note, zone), style = MaterialTheme.typography.bodySmall)
            if (note.description.isNotEmpty()) Text(note.description, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

fun scheduleLabel(note: Note, zone: ZoneId): String = when (note.kind) {
    ScheduleKind.TODO -> "無指定時間"
    ScheduleKind.ALL_DAY -> {
        val start = LocalDate.ofEpochDay(requireNotNull(note.startDay))
        val end = LocalDate.ofEpochDay(requireNotNull(note.endDayExclusive)).minusDays(1)
        if (start == end) "$start · 全天" else "$start ～ $end · 全天"
    }
    ScheduleKind.TIMED -> if (note.photoCapturedAt != null) "拍攝：${captureLabel(note.photoCapturedAt)}" else {
        val start = Instant.ofEpochMilli(requireNotNull(note.startMillis)).atZone(zone)
        val end = Instant.ofEpochMilli(requireNotNull(note.endMillis)).atZone(zone)
        "${start.format(fullDateTime)} ～ ${if (start.toLocalDate() == end.toLocalDate()) end.format(clockTime) else end.format(fullDateTime)}"
    }
}


