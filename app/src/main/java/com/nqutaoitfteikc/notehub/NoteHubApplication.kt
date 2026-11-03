package com.nqutaoitfteikc.notehub
import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.nqutaoitfteikc.notehub.data.*
import com.nqutaoitfteikc.notehub.domain.*
import com.nqutaoitfteikc.notehub.reminders.*
import com.nqutaoitfteikc.notehub.photos.*
import java.io.File
private val Context.settingsStore by preferencesDataStore(name = "settings")
class AppContainer(private val context: Context) {
    private val database = NoteDatabase.open(context)
    private val achievementRepository = RoomAchievementRepository(database.achievements())
    val observeAchievements = ObserveAchievements(achievementRepository)
    val observeAchievementTotal = ObserveAchievementTotal(achievementRepository)
    val acknowledgeAchievement = AcknowledgeAchievement(achievementRepository)
    val achievementExporter = com.nqutaoitfteikc.notehub.achievements.AchievementExporter(context, database.achievements())
    fun exportAchievements() = androidx.work.WorkManager.getInstance(context).cancelUniqueWork("achievement-export")
    suspend fun testAchievement(milestone: Long, reset: Boolean) {
        check(BuildConfig.DEBUG)
        require(com.nqutaoitfteikc.notehub.achievements.MilestonePolicy(listOf(1,3,5)).contains(milestone))
        if(reset) database.achievements().reset(milestone)
        else database.achievements().unlock(AchievementEntity(milestone,System.currentTimeMillis()))
    }
    private val notes = RoomNoteRepository(database.notes(),database)
    private val settings = LocalSettingsRepository(context.settingsStore)
    val observeNotes = ObserveNotes(notes)
    val addNote = AddNote(notes)
    val calendarImports = RoomCalendarImportRepository(database)
    val googleIdentity = com.nqutaoitfteikc.notehub.calendar.GoogleIdentity(context, BuildConfig.GOOGLE_WEB_CLIENT_ID)
    val reminders = ReminderCoordinator(database, ReminderPlatform(context))
    val observeReminders = ObserveReminders(reminders)
    val saveReminder = SaveReminder(reminders)
    val deleteReminder = DeleteReminder(reminders)
    val restoreReminders = RestoreReminders(reminders)
    val photoCaptureExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val photoVault=com.nqutaoitfteikc.notehub.privacy.LocalVault()
    val photos = RoomPhotoRepository(database, PhotoFiles(File(context.filesDir, "photos"),photoVault),photoVault) { PhotoCleanupWorker.enqueue(context) }
    val photoExporter = PhotoExporter(context, photos.files)
    private val noteEffects = object : NoteEffects {
        override suspend fun change(noteId: String, action: suspend () -> Unit) = reminders.change(noteId, action)
        override suspend fun delete(noteId: String, action: suspend () -> Unit) = reminders.delete(noteId) { photos.deleteNoteInTransaction(noteId, action) }
    }
    val syncSettings = com.nqutaoitfteikc.notehub.calendar.CalendarSyncSettingsRepository(context.settingsStore)
    val calendarSync = com.nqutaoitfteikc.notehub.calendar.CalendarSyncEngine(database, noteEffects)
    val syncConflicts get() = database.calendarSync().conflicts()
    fun scheduleCalendarSync(hours: Int) = com.nqutaoitfteikc.notehub.calendar.CalendarSyncWorker.schedule(context, hours)
    fun syncCalendarNow() = com.nqutaoitfteikc.notehub.calendar.CalendarSyncWorker.now(context)
    fun cancelCalendarSync() = com.nqutaoitfteikc.notehub.calendar.CalendarSyncWorker.cancel(context)
    fun schedulePhotoCleanup() = PhotoCleanupWorker.enqueue(context)
    val observePhotoMap = ObservePhotoMap(photos, notes)
    val observePhotos = ObservePhotos(photos)
    val storePhoto = StorePhoto(photos)
    val savePhotoNote = SavePhotoNote(photos, reminders)
    val removePhoto = RemovePhoto(photos)
    val discardPhotoDraft = DiscardPhotoDraft(photos)
    val setPhotoCaption = SetPhotoCaption(photos)
    val saveNote = SaveNote(notes, effects = noteEffects)
    val deleteNote = DeleteNote(notes, noteEffects)
    val setCompleted = SetCompleted(notes, noteEffects)
    val observeTheme = ObserveTheme(settings)
    val setTheme = SetTheme(settings)
    val observeFontScale get() = settings.fontScale
    suspend fun setFontScale(scale: Float) = settings.setFontScale(scale)
}
class NoteHubApplication : Application() {
    val container by lazy { AppContainer(applicationContext) }
}






