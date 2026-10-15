package com.nqutaoitfteikc.notehub.calendar

import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.time.*
import javax.net.ssl.HttpsURLConnection

class CalendarApiException(val status: Int, message: String, val reason: String? = null) : Exception(message)

object CalendarApiErrors {
    fun reason(body: JSONObject): String? {
        val error = body.optJSONObject("error") ?: return null
        val reasons = mutableListOf<String>()
        listOf("errors", "details").forEach { field ->
            val rows = error.optJSONArray(field)
            if (rows != null) for (i in 0 until rows.length()) rows.optJSONObject(i)?.optString("reason")?.takeIf { it.matches(Regex("[A-Za-z0-9_]{1,80}")) }?.let(reasons::add)
        }
        return reasons.firstOrNull { it in setOf("SERVICE_DISABLED", "accessNotConfigured", "ACCESS_TOKEN_SCOPE_INSUFFICIENT", "insufficientPermissions") }
            ?: reasons.firstOrNull()
    }
    fun message(code: Int, reason: String?): String = when {
        code == 401 -> "Google 授權已失效，請重新授權"
        code == 403 && reason in setOf("SERVICE_DISABLED", "accessNotConfigured") -> "此 Google Cloud 專案尚未啟用 Calendar API，請啟用後再點「重新載入行事曆」"
        code == 403 && reason in setOf("ACCESS_TOKEN_SCOPE_INSUFFICIENT", "insufficientPermissions") -> "Google 行事曆唯讀權限不足，請重新授權並同意兩項唯讀存取"
        code == 429 || reason in setOf("rateLimitExceeded", "userRateLimitExceeded", "RATE_LIMIT_EXCEEDED") -> "Google 請求過於頻繁，請稍後再試"
        code == 403 && reason == "quotaExceeded" -> "Google Calendar 配額已用完，請稍後再試或檢查專案配額"
        code == 403 -> "Google 拒絕讀取（403），請檢查 Calendar API、唯讀授權與帳戶政策"
        code == 404 -> "行事曆已不存在或無法存取，請重新載入行事曆"
        code in 500..599 -> "Google 服務暫時無法使用，請稍後再試"
        else -> "Google Calendar 讀取失敗（$code）"
    }
}
fun interface CalendarTransport {
    suspend fun get(path: String, query: Map<String, String>, token: String): JSONObject
}
class GoogleCalendarTransport : CalendarTransport {
    override suspend fun get(path: String, query: Map<String, String>, token: String): JSONObject = withContext(Dispatchers.IO) {
        require(path.startsWith("/users/me/calendarList") || path.startsWith("/calendars/"))
        val params = query.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val connection = URL("https://www.googleapis.com/calendar/v3$path?$params").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            currentCoroutineContext().ensureActive()
            val code = connection.responseCode
            if (code != 200) {
                val reason = try {
                    connection.errorStream?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (output.size() <= 65536) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        if (output.size() > 65536) "ERROR_BODY_TOO_LARGE"
                        else CalendarApiErrors.reason(JSONObject(output.toString("UTF-8")))
                    }
                } catch (error: org.json.JSONException) { "ERROR_BODY_NOT_JSON" }
                catch (error: java.io.IOException) { "ERROR_BODY_READ_FAILED" }
                throw CalendarApiException(code, CalendarApiErrors.message(code, reason), reason)
            }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 8 * 1024 * 1024) { "Google 回應過大，請縮小日期範圍" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            currentCoroutineContext().ensureActive()
            JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}
internal fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

class GoogleCalendarApi(private val transport: CalendarTransport = GoogleCalendarTransport()) {
    private suspend fun pages(path: String, query: Map<String, String>, token: String, consume: (JSONObject) -> Unit) {
        var next: String? = null
        val seen = mutableSetOf<String>()
        repeat(100) {
            currentCoroutineContext().ensureActive()
            val page = transport.get(path, query + (next?.let { mapOf("pageToken" to it) } ?: emptyMap()), token)
            consume(page)
            next = page.optString("nextPageToken").takeIf { it.isNotBlank() }
            if (next == null) return
            check(seen.add(next!!)) { "Google 分頁重複，請重新載入" }
        }
        error("資料頁數過多，請縮小匯入範圍")
    }
    suspend fun calendars(token: String): List<GoogleCalendar> {
        val result = linkedMapOf<String, GoogleCalendar>()
        pages("/users/me/calendarList", mapOf("maxResults" to "250", "showDeleted" to "false"), token) { page ->
            val items = page.optJSONArray("items")
            if (items != null) for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                if (!item.optBoolean("deleted")) {
                    val id = item.getString("id")
                    result[id] = GoogleCalendar(id, item.optString("summaryOverride").ifBlank { item.optString("summary", "未命名行事曆") }, item.optString("timeZone", "UTC"))
                }
            }
        }
        return result.values.toList()
    }
    suspend fun preview(account: String, calendars: List<GoogleCalendar>, range: ImportRange, token: String): ImportPreview {
        require(calendars.isNotEmpty()) { "請選擇至少一個行事曆" }
        val result = linkedMapOf<Pair<String, String>, CalendarImportEvent>()
        var cancelled = 0
        calendars.forEach { calendar ->
            pages("/calendars/${encode(calendar.id)}/events", mapOf("timeMin" to range.timeMin, "timeMax" to range.timeMax,
                "singleEvents" to "true", "showDeleted" to "false", "orderBy" to "startTime", "maxResults" to "250"), token) { page ->
                val zone = page.optString("timeZone").ifBlank { calendar.zone }
                val items = page.optJSONArray("items")
                if (items != null) for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    if (item.optString("status") == "cancelled") cancelled++
                    else {
                        val event = CalendarEventParser.parse(calendar.copy(zone = zone), item)
                        result[calendar.id to event.instanceKey] = event
                        check(result.size <= 10000) { "事件超過一萬筆，請縮小範圍" }
                    }
                }
            }
        }
        return ImportPreview(account, result.values.toList(), cancelled)
    }
}

object CalendarEventParser {
    fun parse(calendar: GoogleCalendar, item: JSONObject): CalendarImportEvent {
        val id = item.getString("id")
        val start = item.getJSONObject("start")
        val end = item.getJSONObject("end")
        val original = item.optJSONObject("originalStartTime")
        val series = item.optString("recurringEventId")
        // Original occurrence identity survives a move; normal events use their immutable Google event ID.
        val key = if (series.isNotBlank() && original != null) {
            val whenOriginal = if (original.has("date")) "d:${LocalDate.parse(original.getString("date"))}"
                else "t:${instant(original, calendar.zone)}"
            "recurring:$series:$whenOriginal"
        } else "event:$id"
        val title = item.optString("summary").ifBlank { "（無標題事件）" }
        val description = listOf(item.optString("description"), item.optString("location").takeIf { it.isNotBlank() }?.let { "地點：$it" }.orEmpty())
            .filter { it.isNotBlank() }.joinToString("\n\n")
        val warnings = mutableListOf<String>()
        if (title.length > 200 || description.length > 20000 || calendar.title.length > 40) warnings += "文字超過本機欄位限制，將截短"
        val base = NoteDraft(title.take(200), description.take(20000), calendar.title.take(40))
        val draft = if (start.has("date")) {
            base.copy(kind = ScheduleKind.ALL_DAY, startDay = LocalDate.parse(start.getString("date")).toEpochDay(),
                endDayExclusive = LocalDate.parse(end.getString("date")).toEpochDay())
        } else {
            val from = instant(start, calendar.zone)
            val to = instant(end, calendar.zone)
            require(to >= from) { "Google 事件結束時間早於開始時間，請修正來源事件後再試" }
            if (to == from) warnings += "瞬間事件以 1 毫秒表示"
            base.copy(kind = ScheduleKind.TIMED, startMillis = from, endMillis = if (to == from) Math.addExact(to, 1) else to,
                zoneId = start.optString("timeZone").ifBlank { calendar.zone }.let { ZoneId.of(it).id })
        }
        return CalendarImportEvent(calendar.id, id, key, draft.validated(), warnings.takeIf { it.isNotEmpty() }?.joinToString("；"))
    }
    private fun instant(value: JSONObject, fallbackZone: String): Long {
        val dateTime = value.getString("dateTime")
        return try { OffsetDateTime.parse(dateTime).toInstant().toEpochMilli() }
        catch (_: java.time.format.DateTimeParseException) {
            val local = LocalDateTime.parse(dateTime)
            val zone = ZoneId.of(value.optString("timeZone").ifBlank { fallbackZone })
            val offsets = zone.rules.getValidOffsets(local)
            require(offsets.size == 1) { "Google 事件時間缺少明確時差，無法安全匯入" }
            local.toInstant(offsets.single()).toEpochMilli()
        }
    }
}
