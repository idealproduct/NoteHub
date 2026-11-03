package com.nqutaoitfteikc.notehub.calendar

import com.nqutaoitfteikc.notehub.domain.*
import org.json.JSONObject
import java.time.*
import java.security.MessageDigest

interface CalendarSyncRemote {
    suspend fun list(calendar: GoogleCalendar, token: String): List<JSONObject>
    suspend fun get(calendar: String, id: String, token: String): JSONObject?
    suspend fun create(calendar: String, id: String, note: Note, token: String): JSONObject
    suspend fun update(calendar: String, remote: JSONObject, note: Note, token: String): JSONObject
    suspend fun delete(calendar: String, remote: JSONObject, token: String)
}

object CalendarSyncProjection {
    fun body(note: Note): JSONObject {
        require(note.photoCapturedAt==null) { "照片記事僅存本機，不提供行事曆同步" }
        require(note.kind != ScheduleKind.TODO) { "沒有日期的待辦不支援 Google 行事曆同步" }
        val start = JSONObject(); val end = JSONObject()
        if (note.kind == ScheduleKind.ALL_DAY) {
            start.put("date", LocalDate.ofEpochDay(requireNotNull(note.startDay)).toString()).put("dateTime", JSONObject.NULL).put("timeZone", JSONObject.NULL)
            end.put("date", LocalDate.ofEpochDay(requireNotNull(note.endDayExclusive)).toString()).put("dateTime", JSONObject.NULL).put("timeZone", JSONObject.NULL)
        } else {
            // Calendar works at second precision; a photo remains immutable locally at millisecond precision.
            val from = Math.floorDiv(requireNotNull(note.startMillis), 1000) * 1000
            val until = maxOf(from + 1000, Math.floorDiv(requireNotNull(note.endMillis), 1000) * 1000)
            start.put("dateTime", Instant.ofEpochMilli(from).toString()).put("date", JSONObject.NULL).put("timeZone", note.zoneId ?: "UTC")
            end.put("dateTime", Instant.ofEpochMilli(until).toString()).put("date", JSONObject.NULL).put("timeZone", note.zoneId ?: "UTC")
        }
        return JSONObject().put("summary", note.title).put("description", note.description).put("start", start).put("end", end)
    }
    fun draft(calendar: GoogleCalendar, remote: JSONObject): NoteDraft {
        require(remote.optString("summary").length <= 200 && remote.optString("description").length <= 20000) { "Google 文字超過本機限制，請先縮短來源文字" }
        return CalendarEventParser.parse(calendar, remote).draft.copy(description = remote.optString("description")).validated()
    }
    fun hash(note: Note): String = fingerprint(NoteDraft(note.title, note.description, kind = note.kind,
        startDay = note.startDay, endDayExclusive = note.endDayExclusive, startMillis = note.startMillis, endMillis = note.endMillis, zoneId = note.zoneId))
    fun fingerprint(draft: NoteDraft): String {
        val from = draft.startMillis?.let { Math.floorDiv(it, 1000) }
        val to = draft.endMillis?.let { maxOf(requireNotNull(from) + 1, Math.floorDiv(it, 1000)) }
        val fields = listOf(draft.title, draft.description, draft.kind.name, draft.startDay, draft.endDayExclusive, from, to)
        return digest(fields.joinToString("") { "${it.toString().length}:${it}" })
    }
    fun eventId(account: String, calendar: String, note: String) = "nh" + digest("${account.length}:$account${calendar.length}:$calendar$note")
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
}

class GoogleCalendarSyncApi(private val transport: GoogleCalendarTransport = GoogleCalendarTransport()) : CalendarSyncRemote {
    private fun path(calendar: String) = "/calendars/${encode(calendar)}/events"
    override suspend fun list(calendar: GoogleCalendar, token: String): List<JSONObject> {
        val today = LocalDate.now(ZoneId.of(calendar.zone))
        val range = ImportRange(today.minusDays(30), today.plusDays(335), ZoneId.of(calendar.zone))
        val result = mutableListOf<JSONObject>()
        var next: String? = null
        val seen = mutableSetOf<String>()
        repeat(100) {
            val query = mutableMapOf("singleEvents" to "true", "showDeleted" to "true", "maxResults" to "250", "timeMin" to range.timeMin, "timeMax" to range.timeMax)
            next?.let { query["pageToken"] = it }
            val page = transport.get(path(calendar.id), query, token)
            page.optJSONArray("items")?.let { rows -> for (i in 0 until rows.length()) result += rows.getJSONObject(i) }
            check(result.size <= 10000) { "同步事件超過一萬筆，請改用較小的行事曆" }
            next = page.optString("nextPageToken").takeIf { it.isNotBlank() }
            if (next == null) return result
            check(seen.add(requireNotNull(next))) { "Google 分頁重複，請稍後重試" }
        }
        error("同步資料頁數過多")
    }
    override suspend fun get(calendar: String, id: String, token: String): JSONObject? = try {
        transport.get("${path(calendar)}/${encode(id)}", emptyMap(), token).takeUnless { it.optString("status") == "cancelled" }
    } catch (e: CalendarApiException) { if (e.status in setOf(404, 410)) null else throw e }
    override suspend fun create(calendar: String, id: String, note: Note, token: String): JSONObject =
        transport.request("POST", path(calendar), mapOf("sendUpdates" to "none"), token, CalendarSyncProjection.body(note).put("id", id))
    override suspend fun update(calendar: String, remote: JSONObject, note: Note, token: String): JSONObject =
        transport.request("PATCH", "${path(calendar)}/${encode(remote.getString("id"))}", mapOf("sendUpdates" to "none"), token,
            CalendarSyncProjection.body(note), remote.getString("etag"))
    override suspend fun delete(calendar: String, remote: JSONObject, token: String) {
        transport.request("DELETE", "${path(calendar)}/${encode(remote.getString("id"))}", mapOf("sendUpdates" to "none"), token, etag = remote.getString("etag"))
    }
}
