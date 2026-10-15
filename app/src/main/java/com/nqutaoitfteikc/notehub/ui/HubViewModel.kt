package com.nqutaoitfteikc.notehub.ui
import android.util.Log
import androidx.lifecycle.*
import com.nqutaoitfteikc.notehub.AppContainer
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HubState(val notes: List<Note> = emptyList(), val theme: ThemeMode = ThemeMode.SYSTEM, val loading: Boolean = true, val saving: Boolean = false, val error: String? = null, val reminders: List<Reminder> = emptyList(),
    val photos: List<Photo> = emptyList(), val pendingPhotoCleanup: Int = 0, val capturingSession: String? = null)
class HubViewModel(private val container: AppContainer) : ViewModel() {
    val calendarImportFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CalendarImportViewModel(
            container.googleIdentity, com.nqutaoitfteikc.notehub.calendar.GoogleCalendarApi(), container.calendarImports) as T
    }
    private var captureToken: String? = null
    private var captureTimeout: kotlinx.coroutines.Job? = null
    private val mutableState = MutableStateFlow(HubState())
    val state = mutableState.asStateFlow()
    init {
        viewModelScope.launch {
            container.observePhotos().catch { report(it, "無法讀取照片資料，請重新開啟 App 再試") }
                .collect { photos -> mutableState.update { it.copy(photos = photos) } }
        }
        viewModelScope.launch {
            container.photos.pendingCleanupCount().catch { report(it, "無法讀取照片清理狀態") }
                .collect { count -> mutableState.update { it.copy(pendingPhotoCleanup = count) } }
        }
        viewModelScope.launch {
            container.observeReminders().catch { report(it, "無法讀取提醒，請重新開啟 App 再試") }
                .collect { reminders -> mutableState.update { it.copy(reminders = reminders) } }
        }
        viewModelScope.launch {
            container.observeNotes().catch { report(it, "無法讀取記事，請重新開啟 App 再試") }.collect { notes -> mutableState.update { it.copy(notes = notes, loading = false) } }
        }
        viewModelScope.launch {
            container.observeTheme().catch { report(it, "無法讀取外觀設定，暫時使用系統主題") }.collect { theme -> mutableState.update { it.copy(theme = theme) } }
        }
    }
    val photoFiles get() = container.photos.files
    val photoCaptureExecutor get() = container.photoCaptureExecutor
    fun beginCapture(sessionId: String): String? {
        if (state.value.capturingSession != null || state.value.saving) return null
        val token = java.util.UUID.randomUUID().toString()
        captureToken = token
        mutableState.update { it.copy(capturingSession = sessionId, error = null) }
        captureTimeout = viewModelScope.launch {
            kotlinx.coroutines.delay(30000)
            failCapture(token, java.io.IOException("拍攝逾時"), "相機拍攝逾時，請重新拍攝")
        }
        return token
    }
    fun failCapture(token: String, error: Throwable, message: String = "拍照失敗，請檢查相機權限與裝置空間後重試") {
        if (captureToken != token) return
        finishCapture(token)
        report(error, message)
    }
    private fun finishCapture(token: String) {
        if (captureToken == token) {
            captureTimeout?.cancel()
            captureToken = null
            mutableState.update { it.copy(capturingSession = null) }
        }
    }
    fun storeCapture(token: String, sessionId: String, image: CapturedImage) {
        if (captureToken != token) return
        viewModelScope.launch {
            try { container.storePhoto(sessionId, image) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e, "照片未能保存，請確認裝置空間後重新拍攝") }
            finally { finishCapture(token) }
        }
    }
    fun savePhotoNote(sessionId: String, targetId: String?, title: String, description: String, category: String, onSaved: (String) -> Unit) = mutate("無法儲存照片記事，已拍攝的照片仍保留在草稿中") {
        onSaved(container.savePhotoNote(sessionId, targetId, title, description, category))
    }
    fun removePhoto(id: String) = mutate("無法移除照片，請稍後重試") { container.removePhoto(id) }
    fun discardPhotoDraft(session: String, onDone: () -> Unit) = mutate("無法捨棄拍攝草稿，請稍後重試") { container.discardPhotoDraft(session); onDone() }
    fun photoCaption(id: String, caption: String, onSaved: () -> Unit) = mutate("無法儲存照片說明") { container.setPhotoCaption(id, caption); onSaved() }
    fun recoverPhotoFiles() {
        viewModelScope.launch {
            try { container.photos.recoverFiles() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e, "照片清理尚未完成，請稍後再試"); container.schedulePhotoCleanup() }
        }
    }
    val reminderPlatform get() = container.reminders.platform
    fun restoreReminders() {
        viewModelScope.launch {
            try { container.restoreReminders() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e, "無法恢復提醒排程，請稍後重試") }
        }
    }
    fun saveReminder(id: String?, noteId: String, at: Long, enabled: Boolean, onSaved: () -> Unit) = mutate("無法儲存提醒，請稍後重試") {
        container.saveReminder(id, noteId, at, enabled)
        onSaved()
    }
    fun deleteReminder(id: String) = mutate("無法移除提醒，請稍後重試") { container.deleteReminder(id) }
    fun add(title: String, onSaved: () -> Unit) {
        if (state.value.saving) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { container.addNote(title); onSaved() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { report(e, e.message ?: "標題格式不正確") }
            catch (e: Exception) { report(e, "儲存失敗，請確認裝置可用空間後重試") }
            finally { mutableState.update { it.copy(saving = false) } }
        }
    }
    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            try { container.setTheme(mode) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e, "無法儲存外觀設定，請重試") }
        }
    }
    fun save(id: String?, draft: NoteDraft, onSaved: () -> Unit) = mutate("無法儲存事項，請確認裝置空間後重試") {
        container.saveNote(id, draft)
        onSaved()
    }
    fun delete(id: String, onDeleted: () -> Unit) = mutate("無法刪除事項，請重試") {
        container.deleteNote(id)
        onDeleted()
    }
    fun complete(id: String, completed: Boolean) = mutate("無法修改完成狀態，請重試") {
        container.setCompleted(id, completed)
    }
    fun clearError() { mutableState.update { it.copy(error = null) } }
    private fun mutate(message: String, action: suspend () -> Unit) {
        if (state.value.saving) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { report(e, e.message ?: message) }
            catch (e: Exception) { report(e, message) }
            finally { mutableState.update { it.copy(saving = false) } }
        }
    }
    private fun report(error: Throwable, message: String) {
        if (error is CancellationException) throw error
        Log.e("NoteHub", message, error)
        mutableState.update { it.copy(error = message, loading = false) }
    }
    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(HubViewModel::class.java))
                return HubViewModel(container) as T
            }
        }
    }
}


