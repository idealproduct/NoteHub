package com.nqutaoitfteikc.notehub

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.achievements.*
import com.nqutaoitfteikc.notehub.data.*
import com.nqutaoitfteikc.notehub.reminders.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AchievementRepositoryTest {
    @Test fun localCalendarCreationCountsButEditsRemindersAndTodosDoNot() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,NoteDatabase::class.java).build()
        val now=System.currentTimeMillis()
        val coordinator=ReminderCoordinator(db,ReminderPlatform(context),{now})
        val notes=RoomNoteRepository(db.notes(),db)
        val note=com.nqutaoitfteikc.notehub.domain.Note("fixture","事件",now,kind=com.nqutaoitfteikc.notehub.domain.ScheduleKind.ALL_DAY,startDay=20000,endDayExclusive=20001)
        try {
            notes.insert(note)
            assertEquals(1L,db.achievements().total())
            notes.update(note.copy(title="修改"))
            notes.insert(note.copy(id="todo",kind=com.nqutaoitfteikc.notehub.domain.ScheduleKind.TODO,startDay=null,endDayExclusive=null))
            repeat(3) { coordinator.save(null,note.id,now+3600000,false) }
            assertEquals(1L,db.achievements().total())
            notes.delete(note.id)
            assertEquals(1L,db.achievements().total())
            db.achievements().reset(1)
            assertNull(db.achievements().get(1)); assertEquals(1L,db.achievements().total())
            db.achievements().unlock(AchievementEntity(30,123))
            assertNotNull(db.achievements().get(30)); assertEquals(1L,db.achievements().total())
        } finally { db.close() }
    }
    @Test fun rollbackAndConcurrentTransactionsCannotIssueDuplicateAchievements() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,NoteDatabase::class.java).build()
        val recorder=AchievementRecorder(db.achievements(),MilestonePolicy(listOf(1,3,5))::contains)
        try {
            try { db.withTransaction { recorder.recordNewRecord(1); error("模擬提醒交易失敗") } } catch(_:IllegalStateException) { }
            assertNull(db.achievements().total()); assertTrue(db.achievements().all().isEmpty())
            coroutineScope { repeat(100) { launch(Dispatchers.IO) { db.withTransaction { recorder.recordNewRecord(123) } } } }
            assertEquals(100L,db.achievements().total())
            assertEquals(listOf(1L,5L,10L,30L,50L,100L),db.achievements().all().map { it.milestone })
        } finally { db.close() }
    }
    @Test fun countAndAcknowledgementSurviveDatabaseReopen() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="achievement-persistence-${java.util.UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,NoteDatabase::class.java,name).build()
        var db=open()
        try {
            val recorder=AchievementRecorder(db.achievements(),MilestonePolicy(listOf(1,3,5))::contains)
            db.withTransaction { recorder.recordNewRecord(456) }
            db.achievements().acknowledge(1)
            db.close(); db=open()
            assertEquals(1L,db.achievements().total())
            assertTrue(requireNotNull(db.achievements().get(1)).announced)
            assertEquals(456L,db.achievements().get(1)?.unlockedAt)
        } finally { db.close(); context.deleteDatabase(name) }
    }

}
