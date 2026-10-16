package com.nqutaoitfteikc.notehub.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(state: HubState, onAdd: (Long) -> Unit, onOpen: (String) -> Unit, onComplete: (String, Boolean) -> Unit) {
    var viewName by rememberSaveable { mutableStateOf(CalendarView.MONTH.name) }
    val view = CalendarView.valueOf(viewName)
    BackHandler(enabled = view == CalendarView.DAY) { viewName = CalendarView.MONTH.name }
    var selectedDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val date = LocalDate.ofEpochDay(selectedDay)
    var query by rememberSaveable { mutableStateOf("") }
    var scopeName by rememberSaveable { mutableStateOf(SearchScope.ALL.name) }
    val scope = SearchScope.valueOf(scopeName)
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
    val searched = filtered.filter { TimelineRules.inScope(it, date, scope, zone) }
    val showingSearch = query.isNotBlank()
    val todos = (if (showingSearch) searched else filtered).filter { it.kind == ScheduleKind.TODO }.sortedWith(compareBy<Note> { it.completed }.thenByDescending { it.createdAt })
    fun changePeriod(direction: Long) {
        if (showingSearch && scope != SearchScope.ALL) {
            selectedDay = when (scope) {
                SearchScope.YEAR -> date.plusYears(direction)
                SearchScope.MONTH -> date.plusMonths(direction)
                else -> date.plusDays(direction)
            }.toEpochDay()
            return
        }
        selectedDay = when (view) {
            CalendarView.MONTH -> date.plusMonths(direction)
            CalendarView.WEEK -> date.plusWeeks(direction)
            else -> date.plusDays(direction)
        }.toEpochDay()
    }
    LazyColumn(Modifier.fillMaxSize().testTag("calendar-content"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("NOTEHUB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("我的行事曆", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Button(enabled = !state.loading && !state.saving, onClick = { onAdd(selectedDay) }, modifier = Modifier.testTag("add-note")) { Text("新增事項") }
            }
        }
        item {
            OutlinedTextField(query, { query = it }, label = { Text("搜尋標題、描述或分類") }, singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().testTag("search"), trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清除") } })
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
            val controlWidth = if (compact) maxWidth else (maxWidth - 16.dp) / 3
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarDropdown("分類", category?.ifEmpty { "未分類" } ?: "全部分類", "category-dropdown", Modifier.width(controlWidth)) { close ->
                    DropdownMenuItem(text = { Text("全部分類") }, onClick = { category = null; close() })
                    categories.forEach { value ->
                        DropdownMenuItem(text = { Text(value.ifEmpty { "未分類" }) }, onClick = { category = value; close() })
                    }
                }
                CalendarDropdown("狀態", completionLabel(CompletionFilter.valueOf(completionName)), "status-dropdown", Modifier.width(controlWidth)) { close ->
                    CompletionFilter.entries.forEach { value ->
                        DropdownMenuItem(text = { Text(completionLabel(value)) }, onClick = { completionName = value.name; close() })
                    }
                }
                CalendarDropdown("顯示方式", viewLabel(view), "view-dropdown", Modifier.width(controlWidth)) { close ->
                    CalendarView.entries.forEach { value ->
                        DropdownMenuItem(modifier = Modifier.testTag("view-${value.name}"), text = { Text(viewLabel(value)) },
                            onClick = { viewName = value.name; close() })
                    }
                }
            }
        }
        }
        item {
            CalendarDropdown("搜尋範圍", scopeLabel(scope), "search-scope", Modifier.fillMaxWidth()) { close ->
                SearchScope.entries.forEach { value -> DropdownMenuItem(text = { Text(scopeLabel(value)) },
                    modifier = Modifier.testTag("scope-${value.name}"), onClick = { scopeName = value.name; close() }) }
            }
            if (scope != SearchScope.ALL) Text("以 ${when (scope) { SearchScope.DAY -> date.toString(); SearchScope.MONTH -> "${date.year} 年 ${date.monthValue} 月"; else -> "${date.year} 年" }} 搜尋；無日期待辦請選全部",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.loading) item { CircularProgressIndicator() }
        else {
            if (view != CalendarView.LIST || (showingSearch && scope != SearchScope.ALL)) item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { changePeriod(-1) }, modifier = Modifier.semantics { contentDescription = "上一頁" }) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                    TextButton(onClick = {
                        DatePickerDialog(context, { _, y, m, d -> selectedDay = LocalDate.of(y, m + 1, d).toEpochDay() }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
                            setTitle("跳至日期")
                            setButton(android.content.DialogInterface.BUTTON_POSITIVE, "確定", this)
                            setButton(android.content.DialogInterface.BUTTON_NEGATIVE, "取消", this)
                        }.show()
                    }, modifier = Modifier.testTag("selected-date")) { Text(date.toString(), fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = { changePeriod(1) }, modifier = Modifier.semantics { contentDescription = "下一頁" }) { Text("›", style = MaterialTheme.typography.headlineSmall) }
                    TextButton(onClick = { selectedDay = LocalDate.now().toEpochDay() }) { Text("今天") }
                }
            }
            item { Text(if (view == CalendarView.LIST) "所有日期 · 共 ${filtered.size} 筆" else "${zone.id} · 搜尋與篩選已套用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (showingSearch) {
                item { Text("搜尋結果 · ${searched.size} 筆", style = MaterialTheme.typography.titleLarge) }
                val results = searched.filter { it.kind != ScheduleKind.TODO }.sortedWith(compareBy<Note> { it.startMillis ?: LocalDate.ofEpochDay(requireNotNull(it.startDay)).atStartOfDay(zone).toInstant().toEpochMilli() }.thenBy { it.title })
                if (searched.isEmpty()) item { Text("沒有符合搜尋條件的事項") }
                noteCards(results, "search", state.saving, zone, onOpen, onComplete)
            } else when (view) {
                CalendarView.MONTH -> {
                    item { MonthGrid(date, filtered, zone) { selectedDay = it.toEpochDay(); viewName = CalendarView.DAY.name } }
                    item { Text(date.format(dateLabel), style = MaterialTheme.typography.titleLarge) }
                    val daily = CalendarRules.onDay(filtered, date, zone)
                    if (daily.isEmpty()) item { Text("這一天沒有符合條件的事項") }
                    noteCards(daily, "month", state.saving, zone, onOpen, onComplete)
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
                        noteCards(daily, "week-$day", state.saving, zone, onOpen, onComplete)
                    }
                }
                CalendarView.DAY -> {
                    item { TextButton(onClick = { viewName = CalendarView.MONTH.name }, modifier = Modifier.testTag("back-to-month")) { Text("‹ 返回月曆") } }
                    item { Text(date.format(dateLabel), style = MaterialTheme.typography.titleLarge) }
                    val daily = CalendarRules.onDay(filtered, date, zone)
                    item { Text("全天", style = MaterialTheme.typography.titleMedium) }
                    val allDay = daily.filter { it.kind == ScheduleKind.ALL_DAY }
                    if (allDay.isEmpty()) item { Text("沒有全天事項") }
                    noteCards(allDay, "day-all", state.saving, zone, onOpen, onComplete)
                    val timed = daily.filter { it.kind == ScheduleKind.TIMED }
                    item { DayTimeline(date, timed, zone, onOpen) }
                    item { Text("當日行程", style = MaterialTheme.typography.titleLarge) }
                    if (timed.isEmpty()) item { Text(if (daily.isEmpty()) "這一天沒有符合條件的事項" else "沒有指定時間的行程") }
                    noteCards(timed, "day-timed", state.saving, zone, onOpen, onComplete)
                }
                CalendarView.LIST -> {
                    val scheduled = filtered.filter { it.kind != ScheduleKind.TODO }.sortedWith(compareBy<Note> {
                        if (it.kind == ScheduleKind.ALL_DAY) LocalDate.ofEpochDay(requireNotNull(it.startDay)).atStartOfDay(zone).toInstant().toEpochMilli()
                        else requireNotNull(it.startMillis)
                    }.thenBy { it.title })
                    item { Text("有日期的事項", style = MaterialTheme.typography.titleLarge) }
                    if (scheduled.isEmpty()) item { Text("沒有符合條件的有日期事項") }
                    noteCards(scheduled, "list", state.saving, zone, onOpen, onComplete)
                }
            }
            item { Text("無時間待辦（不受日期限制）", style = MaterialTheme.typography.titleLarge) }
            if (todos.isEmpty()) item { Text("沒有符合條件的無時間待辦") }
            noteCards(todos, "todo", state.saving, zone, onOpen, onComplete)
        }
    }
}

@Composable
private fun MonthGrid(date: LocalDate, notes: List<Note>, zone: ZoneId, select: (LocalDate) -> Unit) {
    val first = date.withDayOfMonth(1)
    val start = CalendarRules.weekStart(first)
    val cells = ((first.dayOfWeek.value - 1 + first.lengthOfMonth() + 6) / 7) * 7
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${date.year} 年 ${date.monthValue} 月", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        Row { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, modifier = Modifier.weight(1f).padding(vertical = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        repeat(cells / 7) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { weekday ->
                    val day = start.plusDays((week * 7 + weekday).toLong())
                    val count = notes.count { CalendarRules.occursOn(it, day, zone) }
                    Surface(color = if (day == date) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f).heightIn(min = 54.dp).testTag("day-$day")
                            .semantics { contentDescription = "$day，$count 筆事項" }.clickable { select(day) }, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day.dayOfMonth.toString(), color = when { day == LocalDate.now() -> MaterialTheme.colorScheme.primary; day.month != date.month -> MaterialTheme.colorScheme.outline; else -> MaterialTheme.colorScheme.onSurface }, fontWeight = if (day == date || day == LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                            if (count > 0) Text("$count 筆", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

}

private fun LazyListScope.noteCards(notes: List<Note>, prefix: String, saving: Boolean, zone: ZoneId, open: (String) -> Unit, complete: (String, Boolean) -> Unit) {
    items(notes, key = { "$prefix-${it.id}" }) { note -> NoteCard(note, saving, zone, open, complete) }
}

@Composable
private fun NoteCard(note: Note, saving: Boolean, zone: ZoneId, open: (String) -> Unit, complete: (String, Boolean) -> Unit) {
    Card(onClick = { open(note.id) }, modifier = Modifier.fillMaxWidth().testTag("note-${note.id}")
        .semantics { contentDescription = "開啟詳細資訊：${note.title}" }, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Checkbox(checked = note.completed, enabled = !saving, onCheckedChange = { complete(note.id, it) },
                    modifier = Modifier.semantics { contentDescription = "${if (note.completed) "標記未完成" else "標記完成"}：${note.title}" })
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium, textDecoration = if (note.completed) TextDecoration.LineThrough else null)
                    Text(note.category.ifEmpty { "未分類" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(scheduleLabel(note, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (note.description.isNotEmpty()) Text(note.description, maxLines = 3, overflow = TextOverflow.Ellipsis)
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



private fun completionLabel(value: CompletionFilter) = when (value) {
    CompletionFilter.ALL -> "全部狀態"
    CompletionFilter.PENDING -> "未完成"
    CompletionFilter.COMPLETED -> "已完成"
}

private fun viewLabel(value: CalendarView) = when (value) {
    CalendarView.MONTH -> "月"
    CalendarView.WEEK -> "週"
    CalendarView.DAY -> "日"
    CalendarView.LIST -> "清單"
}

@Composable
private fun CalendarDropdown(label: String, value: String, tag: String, modifier: Modifier = Modifier,
    options: @Composable ColumnScope.(() -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag(tag)
            .semantics { contentDescription = "$label：$value，開啟選單" },
            color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(value, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge)
                    Text("⌄", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp)) { options { expanded = false } }
    }
}

private fun scopeLabel(value: SearchScope) = when (value) {
    SearchScope.ALL -> "全部"
    SearchScope.DAY -> "選定日"
    SearchScope.MONTH -> "選定月"
    SearchScope.YEAR -> "選定年"
}
