package com.nqutaoitfteikc.notehub.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nqutaoitfteikc.notehub.AppContainer
import com.nqutaoitfteikc.notehub.calendar.*
import com.nqutaoitfteikc.notehub.domain.GoogleCalendar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock

data class CalendarSyncUiState(val account: String? = null, val authorized: Boolean = false,
    val calendars: List<GoogleCalendar> = emptyList(), val selected: String? = null, val hours: Int = 6,
    val busy: Boolean = false, val resolution: PendingIntent? = null, val awaiting: Boolean = false, val error: String? = null)

class CalendarSyncViewModel(private val app: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(CalendarSyncUiState())
    val state = mutable.asStateFlow()
    val config = app.syncSettings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarSyncSettings())
    val conflicts = app.syncConflicts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val noteTitles = app.observeNotes().map { notes -> notes.associate { it.id to it.title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    private var token: String? = null
    private fun action(block: suspend () -> Unit) {
        if (state.value.busy || state.value.awaiting) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                android.util.Log.w("NoteHubSync", "同步設定操作失敗（${e.javaClass.simpleName}）")
                mutable.update { it.copy(error = when (e) {
                    is CalendarApiException -> e.message
                    is java.io.IOException -> "無法連線 Google，請確認網路後重試"
                    is IllegalArgumentException, is IllegalStateException -> e.message ?: "請確認同步設定"
                    else -> "Google 操作未完成，請重新登入及授權後重試"
                }) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun signIn(activity: Activity) = action {
        token = null
        mutable.update { it.copy(account = null, authorized = false, calendars = emptyList(), selected = null) }
        val account = app.googleIdentity.signIn(activity)
        mutable.update { it.copy(account = account) }
    }
    fun authorize() = action {
        val account = state.value.account ?: app.syncSettings.flow.first().account.takeIf { it.isNotBlank() }
            ?: error("請先登入 Google")
        mutable.update { it.copy(account = account) }
        val result = app.googleIdentity.authorize(account, CALENDAR_SYNC_SCOPES)
        if (result.hasResolution()) mutable.update { it.copy(resolution = result.pendingIntent, awaiting = true) }
        else { token = app.googleIdentity.token(result, CALENDAR_SYNC_SCOPES); load() }
    }
    private suspend fun load() {
        val calendars = GoogleCalendarApi().calendars(requireNotNull(token)).filter { it.writable }
        mutable.update { it.copy(authorized = true, calendars = calendars, selected = null) }
    }
    fun launched() { mutable.update { it.copy(resolution = null) } }
    fun resolved(code: Int, data: Intent?) {
        mutable.update { it.copy(awaiting = false) }
        action {
            check(code == Activity.RESULT_OK && data != null) { "已取消雙向同步授權" }
            token = app.googleIdentity.token(app.googleIdentity.resolve(requireNotNull(data)), CALENDAR_SYNC_SCOPES)
            load()
        }
    }
    fun launchFailed() { mutable.update { it.copy(awaiting = false, resolution = null, error = "無法開啟 Google 授權畫面，請重試") } }
    fun select(id: String) { mutable.update { it.copy(selected = id) } }
    fun hours(hours: Int) { mutable.update { it.copy(hours = hours) } }
    fun enable() = action {
        check(state.value.authorized) { "請先授權 Google 事件讀寫權限" }
        val account = requireNotNull(state.value.account)
        val calendar = state.value.calendars.single { it.id == state.value.selected }
        app.calendarSync.mutex.withLock {
            app.syncSettings.enable(account, calendar, state.value.hours)
            app.scheduleCalendarSync(state.value.hours)
            app.syncCalendarNow()
        }
    }
    fun disable() = action {
        app.calendarSync.mutex.withLock { app.syncSettings.disable(); app.cancelCalendarSync() }
    }
    fun now() = action {
        check(app.syncSettings.flow.first().enabled) { "請先啟用雙向同步" }
        app.syncCalendarNow()
    }
    fun resolve(eventId: String, useGoogle: Boolean) = action {
        app.calendarSync.mutex.withLock {
            val settings = app.syncSettings.flow.first()
            check(settings.enabled && settings.account == state.value.account) { "請使用目前同步帳號重新登入與授權" }
            app.calendarSync.resolve(settings, requireNotNull(token) { "請先授權 Google 事件讀寫權限" }, eventId, useGoogle)
            val remaining = app.syncConflicts.first().count { it.account == settings.account && it.calendarId == settings.calendar.id }
            app.syncSettings.status("衝突處理完成，剩餘 $remaining 筆")
        }
    }
}
