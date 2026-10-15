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
    private val database = Room.databaseBuilder(context, NoteDatabase::class.java, "notehub.db").addMigrations(NoteDatabase.MIGRATION_1_2, NoteDatabase.MIGRATION_2_3, NoteDatabase.MIGRATION_3_4, NoteDatabase.MIGRATION_4_5).build()
    private val notes = RoomNoteRepository(database.notes())
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
    val photos = RoomPhotoRepository(database, PhotoFiles(File(context.filesDir, "photos"))) { PhotoCleanupWorker.enqueue(context) }
    private val noteEffects = object : NoteEffects {
        override suspend fun change(noteId: String, action: suspend () -> Unit) = reminders.change(noteId, action)
        override suspend fun delete(noteId: String, action: suspend () -> Unit) = reminders.delete(noteId) { photos.deleteNoteInTransaction(noteId, action) }
    }
    fun schedulePhotoCleanup() = PhotoCleanupWorker.enqueue(context)
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
}
class NoteHubApplication : Application() {
    val container by lazy { AppContainer(applicationContext) }
}






