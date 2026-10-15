package com.nqutaoitfteikc.notehub.domain

import java.time.LocalDate
import java.time.ZoneId

data class GoogleCalendar(val id: String, val title: String, val zone: String)
data class ImportRange(val first: LocalDate, val last: LocalDate, val zone: ZoneId) {
    init { require(!last.isBefore(first) && last.toEpochDay() - first.toEpochDay() <= 366) { "日期範圍須介於 1 至 367 天" } }
    // Instant's ISO representation includes seconds; OffsetDateTime.toString() may omit :00.
    val timeMin: String get() = first.atStartOfDay(zone).toInstant().toString()
    val timeMax: String get() = last.plusDays(1).atStartOfDay(zone).toInstant().toString()
}
data class CalendarImportEvent(val calendarId: String, val eventId: String, val instanceKey: String,
    val draft: NoteDraft, val warning: String? = null, val alreadyImported: Boolean = false, val locallyDeleted: Boolean = false) {
    val canSelect: Boolean get() = !alreadyImported || locallyDeleted
}
data class ImportPreview(val account: String, val events: List<CalendarImportEvent>, val cancelled: Int)
fun ImportPreview.defaultSelection(): Set<Pair<String, String>> = events.filter { !it.alreadyImported && !it.locallyDeleted }.map { it.calendarId to it.instanceKey }.toSet()
data class ImportResult(val inserted: Int, val skipped: Int, val restored: Int = 0)
interface CalendarImportRepository {
    suspend fun markExisting(account: String, events: List<CalendarImportEvent>): List<CalendarImportEvent>
    suspend fun commit(account: String, events: List<CalendarImportEvent>): ImportResult
}
class ConfirmCalendarImport(private val repository: CalendarImportRepository) {
    suspend operator fun invoke(preview: ImportPreview, selected: Set<Pair<String, String>>): ImportResult {
        require(selected.isNotEmpty()) { "請選擇要匯入的事件" }
        return repository.commit(preview.account, preview.events.filter { (it.calendarId to it.instanceKey) in selected })
    }
}
