package com.nqutaoitfteikc.notehub.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nqutaoitfteikc.notehub.calendar.*
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class CalendarImportState(
    val account: String? = null, val authorized: Boolean = false, val busy: Boolean = false,
    val calendars: List<GoogleCalendar> = emptyList(), val selectedCalendars: Set<String> = emptySet(), val calendarsLoaded: Boolean = false,
    val first: String = LocalDate.now().toString(), val last: String = LocalDate.now().plusMonths(1).toString(),
    val rangeZone: String = ZoneId.systemDefault().id, val preview: ImportPreview? = null,
    val selectedEvents: Set<Pair<String, String>> = emptySet(), val result: ImportResult? = null,
    val error: String? = null, val resolution: PendingIntent? = null, val awaitingAuthorization: Boolean = false
)
class CalendarImportViewModel(private val identity: GoogleIdentity, private val api: GoogleCalendarApi,
    private val repository: CalendarImportRepository) : ViewModel() {
    private val mutable = MutableStateFlow(CalendarImportState())
    val state = mutable.asStateFlow()
    val configured get() = identity.clientId.isNotBlank()
    private var token: String? = null
    private fun launch(action: suspend () -> Unit) {
        if (state.value.busy || state.value.awaitingAuthorization) return
        mutable.update { it.copy(busy = true, error = null, result = null) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (error is CalendarApiException && com.nqutaoitfteikc.notehub.BuildConfig.DEBUG) {
                    android.util.Log.w("NoteHubGoogle", "Calendar HTTP=${error.status}, reason=${error.reason ?: "未提供"}")
                }
                if (error is androidx.credentials.exceptions.GetCredentialException && com.nqutaoitfteikc.notehub.BuildConfig.DEBUG) {
                    val diagnostic = error.message.orEmpty()
                        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[帳號略]")
                        .replace(Regex("eyJ[A-Za-z0-9_.-]+"), "[權杖略]").take(500)
                    android.util.Log.w("NoteHubGoogle", "${error.javaClass.simpleName}: $diagnostic")
                }
                if (error is CalendarApiException && error.status == 401) {
                    val expired = token
                    token = null
                    mutable.update { it.copy(authorized = false, preview = null, selectedEvents = emptySet()) }
                    if (expired != null) try { identity.clearExpiredToken(expired) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (clearError: Exception) { android.util.Log.w("CalendarImport", "清除過期授權失敗，仍需重新授權", clearError) }
                }
                mutable.update { it.copy(error = when (error) {
                    is CalendarApiException -> error.message
                    is androidx.credentials.exceptions.GetCredentialCancellationException ->
                        if (error.message.orEmpty().contains("reauth", ignoreCase = true)) "Google 帳號重新驗證失敗（代碼 16），請確認裝置日期時間、Google 帳戶登入狀態及 OAuth 設定。"
                        else "Google 登入未完成，服務回報取消。若選取帳號後發生，請確認 OAuth 套件名稱、SHA-1 與 Web client ID 設定，再重新登入。"
                    is androidx.credentials.exceptions.NoCredentialException -> "沒有可用的 Google 帳號，請確認裝置帳號與 Google Play 服務"
                    is androidx.credentials.exceptions.GetCredentialException ->
                        if (error.message.orEmpty().contains("28444")) "Google OAuth 設定錯誤（代碼 28444）。請確認 App 使用「網頁應用程式」類型的 client ID，並與 Android OAuth client 位於同一專案。"
                        else "Google 登入服務無法完成要求，請確認 Google 帳戶與 OAuth 設定後重試"
                    is java.io.IOException -> "無法連線 Google，請檢查網路後重試"
                    is java.time.DateTimeException -> "日期或時區格式不正確，請使用 YYYY-MM-DD 並確認 Google 來源時區"
                    is com.google.android.gms.common.api.ApiException -> "Google 授權失敗（代碼 ${error.statusCode}），請檢查 OAuth 套件名稱、簽章與測試帳號"
                    is org.json.JSONException -> "Google 回應格式不符，請重新載入；若持續發生，請檢查來源事件"
                    is IllegalArgumentException, is IllegalStateException -> error.message?.takeIf { text -> text.any { c -> c in '\u4e00'..'\u9fff' } } ?: "日期或 Google 事件格式不符，請檢查範圍及來源資料"
                    else -> "Google 操作失敗，請確認 OAuth 設定、Google Play 服務與授權後重試"
                }) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun signIn(activity: Activity) = launch {
        token = null
        mutable.value = CalendarImportState(busy = true)
        val email = identity.signIn(activity)
        mutable.update { it.copy(account = email) }
    }
    fun authorize() = launch {
        val email = state.value.account ?: error("請先登入 Google")
        val result = identity.authorize(email)
        if (result.hasResolution()) mutable.update { it.copy(resolution = requireNotNull(result.pendingIntent), awaitingAuthorization = true) }
        else { token = identity.token(result); mutable.update { it.copy(authorized = true) }; loadCalendars() }
    }
    fun resolutionLaunched() { mutable.update { it.copy(resolution = null) } }
    fun resolved(resultCode: Int, data: Intent?) {
        mutable.update { it.copy(awaitingAuthorization = false) }
        launch {
            check(state.value.account != null) { "登入狀態已遺失，請重新登入後授權" }
            if (resultCode != Activity.RESULT_OK || data == null) error("已取消行事曆授權")
            token = identity.token(identity.resolve(data))
            mutable.update { it.copy(authorized = true) }
            loadCalendars()
        }
    }
    fun resolutionFailed() { mutable.update { it.copy(resolution = null, awaitingAuthorization = false, error = "無法開啟 Google 授權畫面，請重試") } }
    private suspend fun loadCalendars() {
        mutable.update { it.copy(calendarsLoaded = false, calendars = emptyList(), selectedCalendars = emptySet(), preview = null, selectedEvents = emptySet()) }
        val calendars = api.calendars(requireNotNull(token))
        mutable.update { it.copy(calendars = calendars, calendarsLoaded = true) }
    }
    fun reload() = launch { loadCalendars() }
    fun range(first: String, last: String) {
        if (!state.value.busy) mutable.update { it.copy(first = first, last = last, preview = null, selectedEvents = emptySet(), result = null) }
    }
    fun selectCalendar(id: String, checked: Boolean) {
        if (!state.value.busy) mutable.update { it.copy(selectedCalendars = if (checked) it.selectedCalendars + id else it.selectedCalendars - id, preview = null, selectedEvents = emptySet(), result = null) }
    }
    fun preview() = launch {
        val state = state.value
        val range = ImportRange(LocalDate.parse(state.first), LocalDate.parse(state.last), ZoneId.of(state.rangeZone))
        mutable.update { it.copy(preview = null, selectedEvents = emptySet()) }
        val preview = api.preview(requireNotNull(state.account), state.calendars.filter { it.id in state.selectedCalendars }, range, requireNotNull(token))
        val marked = preview.copy(events = repository.markExisting(preview.account, preview.events))
        mutable.update { it.copy(preview = marked, selectedEvents = marked.defaultSelection()) }
    }
    fun selectEvent(key: Pair<String, String>, checked: Boolean) {
        if (!state.value.busy) mutable.update { it.copy(selectedEvents = if (checked) it.selectedEvents + key else it.selectedEvents - key) }
    }
    fun confirm() = launch {
        val state = state.value
        val result = ConfirmCalendarImport(repository)(requireNotNull(state.preview), state.selectedEvents)
        val marked = state.preview.copy(events = repository.markExisting(state.preview.account, state.preview.events))
        mutable.update { it.copy(result = result, preview = marked, selectedEvents = emptySet()) }
    }
    fun signOut() = launch {
        // Clear local access even if clearing the provider's account preference fails.
        token = null
        mutable.value = CalendarImportState(busy = true)
        identity.signOut()
    }
}
