package com.nqutaoitfteikc.notehub.data

import androidx.room.*
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "notes", indices = [Index("category"), Index("startMillis"), Index("startDay")])
data class NoteEntity(
    @PrimaryKey val id: String, val title: String, val createdAt: Long,
    @ColumnInfo(defaultValue = "''") val description: String = "",
    @ColumnInfo(defaultValue = "''") val category: String = "",
    @ColumnInfo(defaultValue = "0") val completed: Boolean = false,
    @ColumnInfo(defaultValue = "'TODO'") val kind: String = "TODO",
    val startDay: Long? = null, val endDayExclusive: Long? = null,
    val startMillis: Long? = null, val endMillis: Long? = null, val zoneId: String? = null,
    val photoCapturedAt: Long? = null
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC, id ASC")
    fun observe(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun get(id: String): NoteEntity?
    @Insert suspend fun insert(note: NoteEntity)
    @Update suspend fun update(note: NoteEntity): Int
    @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE notes SET completed = :completed WHERE id = :id") suspend fun setCompleted(id: String, completed: Boolean): Int
}

@Database(entities = [NoteEntity::class, ReminderEntity::class, PhotoEntity::class, PhotoDeletionEntity::class, CalendarImportEntity::class, CalendarSyncLink::class, AchievementProgress::class, AchievementEntity::class], version = 9, exportSchema = true)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun reminders(): ReminderDao
    abstract fun photos(): PhotoDao
    abstract fun calendarImports(): CalendarImportDao
    abstract fun calendarSync(): CalendarSyncDao
    abstract fun achievements(): AchievementDao
    companion object {
        /** One production opening path, also exercised by startup regression tests. */
        fun open(context: android.content.Context, name: String = "notehub.db"): NoteDatabase =
            Room.databaseBuilder(context.applicationContext, NoteDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6,MIGRATION_6_7,MIGRATION_7_8,MIGRATION_8_9)
                .addCallback(object: RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // Assignment PRAGMAs can return a row on newer Android SQLite drivers.
                        db.query("PRAGMA secure_delete=ON").use { cursor ->
                            check(cursor.moveToFirst() && cursor.getInt(0)==1) { "無法啟用資料庫安全刪除設定" }
                        }
                    }
                }).build()

        val MIGRATION_8_9 = object : Migration(8,9) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE photos ADD COLUMN sealedLocation TEXT") }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE photos ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE photos ADD COLUMN locationAccuracy REAL")
                db.execSQL("ALTER TABLE photos ADD COLUMN locationMeasuredAt INTEGER")
                db.execSQL("ALTER TABLE photos ADD COLUMN locationMock INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN locationStatus TEXT NOT NULL DEFAULT 'NOT_RECORDED'")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS achievement_progress (id INTEGER NOT NULL, total INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("INSERT OR IGNORE INTO achievement_progress (id,total) VALUES(1,0)")
                db.execSQL("CREATE TABLE IF NOT EXISTS achievements (milestone INTEGER NOT NULL, unlockedAt INTEGER NOT NULL, announced INTEGER NOT NULL, albumUri TEXT, svgUri TEXT, exportError TEXT, exported INTEGER NOT NULL, PRIMARY KEY(milestone))")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS calendar_sync (account TEXT NOT NULL, calendarId TEXT NOT NULL, eventId TEXT NOT NULL, noteId TEXT NOT NULL, localHash TEXT NOT NULL, remoteHash TEXT NOT NULL, conflict TEXT NOT NULL, PRIMARY KEY(account, calendarId, eventId))")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS calendar_imports (account TEXT NOT NULL, calendarId TEXT NOT NULL, instanceKey TEXT NOT NULL, eventId TEXT NOT NULL, noteId TEXT NOT NULL, importedAt INTEGER NOT NULL, PRIMARY KEY(account, calendarId, instanceKey))")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN photoCapturedAt INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS photos (id TEXT NOT NULL, noteId TEXT, sessionId TEXT NOT NULL, fileName TEXT NOT NULL, capturedAt INTEGER NOT NULL, rotationDegrees INTEGER NOT NULL, zoneId TEXT NOT NULL, timeSource TEXT NOT NULL, caption TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_noteId ON photos(noteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_sessionId ON photos(sessionId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_photos_fileName ON photos(fileName)")
                db.execSQL("CREATE TABLE IF NOT EXISTS photo_deletions (fileName TEXT NOT NULL, PRIMARY KEY(fileName))")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reminders (id TEXT NOT NULL, noteId TEXT NOT NULL, triggerAt INTEGER NOT NULL, revision TEXT NOT NULL, enabled INTEGER NOT NULL, deliveredAt INTEGER, status TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_noteId ON reminders(noteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_triggerAt ON reminders(triggerAt)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN completed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN kind TEXT NOT NULL DEFAULT 'TODO'")
                db.execSQL("ALTER TABLE notes ADD COLUMN startDay INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN endDayExclusive INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN startMillis INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN endMillis INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN zoneId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_category ON notes (category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_startMillis ON notes (startMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_startDay ON notes (startDay)")
            }
        }
    }
}

internal fun NoteEntity.toDomain() = Note(id, title, createdAt, description, category, completed,
    ScheduleKind.valueOf(kind), startDay, endDayExclusive, startMillis, endMillis, zoneId, photoCapturedAt)
internal fun Note.toEntity() = NoteEntity(id, title, createdAt, description, category, completed,
    kind.name, startDay, endDayExclusive, startMillis, endMillis, zoneId, photoCapturedAt)

class RoomNoteRepository(private val dao: NoteDao, private val database: NoteDatabase? = null) : NoteRepository {
    override fun observe() = dao.observe().map { rows -> rows.map { it.toDomain() } }
    override suspend fun get(id: String) = dao.get(id)?.toDomain()
    override suspend fun insert(note: Note) {
        val db=database
        if(db==null) dao.insert(note.toEntity()) else db.withTransaction {
            dao.insert(note.toEntity())
            if(note.kind!=ScheduleKind.TODO && note.photoCapturedAt==null)
                com.nqutaoitfteikc.notehub.achievements.AchievementRecorder(db.achievements(),com.nqutaoitfteikc.notehub.achievements.MilestonePolicy(listOf(1,3,5))::contains).recordNewRecord(System.currentTimeMillis())
        }
    }
    override suspend fun update(note: Note) { check(dao.update(note.toEntity()) == 1) { "事項已不存在" } }
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun setCompleted(id: String, completed: Boolean) { check(dao.setCompleted(id, completed) == 1) { "事項已不存在" } }
}

