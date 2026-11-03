package com.nqutaoitfteikc.notehub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nqutaoitfteikc.notehub.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
internal fun DayTimeline(day: LocalDate, notes: List<Note>, zone: ZoneId, open: (String) -> Unit) {
    val blocks = remember(notes, day, zone) { TimelineRules.blocks(notes, day, zone) }
    val start = day.atStartOfDay(zone)
    val end = day.plusDays(1).atStartOfDay(zone)
    val minutes = Duration.between(start, end).toMinutes()
    val density = LocalDensity.current
    val factor = density.fontScale.coerceAtLeast(1f)
    val minuteHeight = 1.5f * factor
    val axisWidth = (58f * factor).dp
    val scroll = rememberScrollState()
    LaunchedEffect(day, zone) {
        val minute = blocks.minOfOrNull { it.startMinute } ?: 480.0
        scroll.scrollTo(with(density) { (minute.toFloat() * minuteHeight).dp.roundToPx() })
    }
    Text("時間表 · 可上下捲動；重疊較多時可左右捲動", style = MaterialTheme.typography.bodySmall)
    BoxWithConstraints(Modifier.fillMaxWidth().height(520.dp).testTag("day-timeline")) {
        val areaWidth = maxOf(maxWidth - axisWidth, (120f * factor).dp * (blocks.maxOfOrNull { it.columns } ?: 1))
        Box(Modifier.fillMaxSize().verticalScroll(scroll).horizontalScroll(rememberScrollState())) {
            Box(Modifier.width(areaWidth + axisWidth).height((minutes * minuteHeight + 24f * factor).dp)) {
                var tick = start
                while (!tick.isAfter(end)) {
                    val offset = Duration.between(start, tick).toMinutes().toFloat() * minuteHeight
                    val label = if (tick == end) "24:00" else tick.format(DateTimeFormatter.ofPattern("HH:mm"))
                    Row(Modifier.offset(y = offset.dp).fillMaxWidth()) {
                        Text(if (minutes != 1440L && tick != end) "$label\n${tick.offset}" else label,
                            modifier = Modifier.width(axisWidth), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(Modifier.padding(top = 8.dp).testTag("timeline-tick-${Duration.between(start, tick).toMinutes()}"), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    tick = tick.plusHours(1)
                }
                blocks.forEach { block ->
                    val width = areaWidth / block.columns
                    val height = ((block.endMinute - block.startMinute) * minuteHeight).toFloat().dp.coerceAtLeast(2.dp)
                    val continuation = listOfNotNull(if (block.continuesBefore) "由前日延續" else null,
                        if (block.continuesAfter) "延續至次日" else null).joinToString(" · ")
                    val clock = DateTimeFormatter.ofPattern("HH:mm")
                    val from = if (block.continuesBefore) "00:00" else Instant.ofEpochMilli(block.note.startMillis!!).atZone(zone).format(clock)
                    val until = if (block.endMinute == minutes.toDouble()) "24:00" else Instant.ofEpochMilli(block.note.endMillis!!).atZone(zone).format(clock)
                    Surface(onClick = { open(block.note.id) },
                        modifier = Modifier.offset(x = axisWidth + width * block.column, y = (block.startMinute * minuteHeight).toFloat().dp + 8.dp)
                            .width(width - 4.dp).height(height).testTag("timeline-${block.note.id}")
                            .semantics { contentDescription = "${block.note.title}，$from 至 $until，$continuation，開啟詳細資訊" },
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (block.note.completed) 0.09f else 0.18f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))) {
                        Column(Modifier.padding(6.dp).clearAndSetSemantics { }) {
                            Text(block.note.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                            if (height >= 48.dp) Text("$from–$until", style = MaterialTheme.typography.labelSmall)
                            if (continuation.isNotEmpty() && height >= 76.dp) Text(continuation, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
