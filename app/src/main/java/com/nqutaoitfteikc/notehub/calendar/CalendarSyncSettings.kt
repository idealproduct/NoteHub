package com.nqutaoitfteikc.notehub.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.nqutaoitfteikc.notehub.domain.GoogleCalendar
import kotlinx.coroutines.flow.map

data class CalendarSyncSettings(val enabled: Boolean = false, val account: String = "",
    val calendar: GoogleCalendar = GoogleCalendar("", "", "UTC"), val hours: Int = 6,
    val status: String = "尚未同步", val lastSuccess: Long = 0, val generation: String = "")

class CalendarSyncSettingsRepository(private val store: DataStore<Preferences>) {
    private val enabled = booleanPreferencesKey("sync_enabled")
    private val account = stringPreferencesKey("sync_account")
    private val calendar = stringPreferencesKey("sync_calendar")
    private val title = stringPreferencesKey("sync_calendar_title")
    private val zone = stringPreferencesKey("sync_zone")
    private val hours = intPreferencesKey("sync_hours")
    private val status = stringPreferencesKey("sync_status")
    private val success = longPreferencesKey("sync_success")
    private val generation = stringPreferencesKey("sync_generation")
    val flow = store.data.map { p -> CalendarSyncSettings(p[enabled] ?: false, p[account].orEmpty(),
        GoogleCalendar(p[calendar].orEmpty(), p[title].orEmpty(), p[zone] ?: "UTC", "writer"),
        p[hours] ?: 6, p[status] ?: "尚未同步", p[success] ?: 0, p[generation].orEmpty()) }
    suspend fun enable(email: String, selected: GoogleCalendar, interval: Int) {
        require(email.isNotBlank() && selected.id.isNotBlank() && selected.writable) { "請選擇可寫入的行事曆" }
        require(interval in listOf(1, 6, 12, 24)) { "同步間隔不正確" }
        store.edit { p ->
            p[enabled] = true; p[account] = email; p[calendar] = selected.id; p[title] = selected.title
            p[zone] = selected.zone; p[hours] = interval; p[status] = "等待同步"; p[success] = 0
            p[generation] = java.util.UUID.randomUUID().toString()
        }
    }
    suspend fun disable() { store.edit { it[enabled] = false; it[status] = "已停用定期同步" } }
    suspend fun status(message: String, completed: Boolean = false) {
        store.edit { it[status] = message; if (completed) it[success] = System.currentTimeMillis() }
    }
}
