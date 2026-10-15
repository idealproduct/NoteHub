package com.nqutaoitfteikc.notehub

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.calendar.*
import com.nqutaoitfteikc.notehub.data.*
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.UUID

// Explicit test fixtures, never used by the production transport or offered as live Google results.
class CalendarImportTest {
    @Test fun deletedCopyRequiresExplicitSelectionAndStalePreviewCannotOverwriteRestoredNote() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java).build()
        try {
            val repository = RoomCalendarImportRepository(db)
            val event = CalendarEventParser.parse(calendar, event())
            repository.commit("fixture", listOf(event))
            val original = db.calendarImports().forAccount("fixture").single()
            db.notes().delete(original.noteId)
            val marked = repository.markExisting("fixture", listOf(event)).single()
            assertTrue(marked.alreadyImported); assertTrue(marked.locallyDeleted); assertTrue(marked.canSelect)
            val preview = ImportPreview("fixture", listOf(marked), 0)
            assertTrue(preview.defaultSelection().isEmpty())
            assertEquals(0, repository.commit("fixture", listOf(event)).restored)
            assertTrue(db.notes().observe().first().isEmpty())
            val result = ConfirmCalendarImport(repository)(preview, setOf(event.calendarId to event.instanceKey))
            assertEquals(1, result.restored); assertEquals(0, result.inserted)
            val source = db.calendarImports().forAccount("fixture").single()
            assertNotEquals(original.noteId, source.noteId)
            val restored = requireNotNull(db.notes().get(source.noteId))
            db.notes().update(restored.copy(title = "保留重新匯入後的本機修改"))
            assertEquals(1, ConfirmCalendarImport(repository)(preview, setOf(event.calendarId to event.instanceKey)).skipped)
            assertEquals("保留重新匯入後的本機修改", db.notes().get(source.noteId)?.title)
            assertFalse(repository.markExisting("fixture", listOf(event)).single().locallyDeleted)
            db.notes().delete(source.noteId)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON notes BEGIN SELECT RAISE(ABORT, 'fixture restore failure'); END")
            try { ConfirmCalendarImport(repository)(preview, setOf(event.calendarId to event.instanceKey)); fail("應回滾來源更新") }
            catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(source.noteId, db.calendarImports().forAccount("fixture").single().noteId)
            assertTrue(db.notes().observe().first().isEmpty())
        } finally { db.close() }
    }
    @Test fun apiErrorsDistinguishDisabledServiceScopeAndRateLimitsWithoutDisplayingServerData() {
        val disabled = JSONObject("""{"error":{"errors":[{"reason":"accessNotConfigured"}],"details":[{"reason":"SERVICE_DISABLED"}]}}""")
        val disabledReason = CalendarApiErrors.reason(disabled)
        assertTrue(CalendarApiErrors.message(403, disabledReason).contains("尚未啟用"))
        val scope = JSONObject("""{"error":{"details":[{"reason":"ACCESS_TOKEN_SCOPE_INSUFFICIENT"}]}}""")
        assertTrue(CalendarApiErrors.message(403, CalendarApiErrors.reason(scope)).contains("權限不足"))
        assertEquals(CalendarApiErrors.message(429, null), CalendarApiErrors.message(403, "rateLimitExceeded"))
        assertNull(CalendarApiErrors.reason(JSONObject("""{"error":{"errors":[{"reason":"private@example.test"}]}}""")))
    }
    private val calendar = GoogleCalendar("a+b@example.test", "測試分類", "Asia/Taipei")
    private fun event(id: String = "e1") = JSONObject("""{"id":"$id","summary":"測試事件","start":{"dateTime":"2026-10-15T10:00:00+08:00"},"end":{"dateTime":"2026-10-16T11:00:00+08:00"}}""")
    @Test fun parserPreservesAllDayExclusiveEndAndTimedOffsets() {
        val allDay = JSONObject("""{"id":"all","start":{"date":"2026-10-15"},"end":{"date":"2026-10-18"}}""")
        val parsed = CalendarEventParser.parse(calendar, allDay)
        assertEquals(ScheduleKind.ALL_DAY, parsed.draft.kind)
        assertEquals(3, parsed.draft.endDayExclusive!! - parsed.draft.startDay!!)
        assertEquals("（無標題事件）", parsed.draft.title)
        val timed = CalendarEventParser.parse(calendar, event()).draft
        assertEquals(Instant.parse("2026-10-15T02:00:00Z").toEpochMilli(), timed.startMillis)
        assertEquals(25, Duration.ofMillis(timed.endMillis!! - timed.startMillis!!).toHours())
    }
    @Test fun recurringMovedInstanceKeepsStableKeyAndDifferentInstancesRemainDistinct() {
        val first = event("occurrence").put("recurringEventId", "series").put("originalStartTime", JSONObject("""{"dateTime":"2026-10-15T10:00:00+08:00"}"""))
        val moved = JSONObject(first.toString()).put("id", "newInstanceId").put("start", JSONObject("""{"dateTime":"2026-10-17T10:00:00+08:00"}""")).put("end", JSONObject("""{"dateTime":"2026-10-17T11:00:00+08:00"}"""))
        assertEquals(CalendarEventParser.parse(calendar, first).instanceKey, CalendarEventParser.parse(calendar, moved).instanceKey)
        moved.put("originalStartTime", JSONObject("""{"dateTime":"2026-10-16T10:00:00+08:00"}"""))
        assertNotEquals(CalendarEventParser.parse(calendar, first).instanceKey, CalendarEventParser.parse(calendar, moved).instanceKey)
    }
    @Test fun textTruncationAndInstantEventsAreVisibleAndInvalidDatesAreRejected() {
        val item = event().put("summary", "長".repeat(250)).put("description", "字".repeat(20001))
        item.put("end", item.getJSONObject("start"))
        val parsed = CalendarEventParser.parse(calendar.copy(title = "類".repeat(41)), item)
        assertEquals(200, parsed.draft.title.length)
        assertEquals(20000, parsed.draft.description.length)
        assertEquals(40, parsed.draft.category.length)
        assertEquals(1L, parsed.draft.endMillis!! - parsed.draft.startMillis!!)
        assertTrue(parsed.warning!!.contains("截短")); assertTrue(parsed.warning!!.contains("1 毫秒"))
        val invalid = JSONObject("""{"id":"invalid","start":{"date":"2026-10-18"},"end":{"date":"2026-10-15"}}""")
        try { CalendarEventParser.parse(calendar, invalid); fail("不得猜測非法日期") } catch (_: IllegalArgumentException) { }
    }
    @Test fun paginatedReadOnlyRequestsExpandInstancesAndDoNotLosePages() = runBlocking {
        val calls = mutableListOf<Pair<String, Map<String,String>>>()
        val api = GoogleCalendarApi(CalendarTransport { path, query, token ->
            assertEquals("fixture-token", token)
            calls += path to query
            if (path.contains("calendarList")) {
                if (query["pageToken"] == null) JSONObject("""{"items":[{"id":"a+b@example.test","summary":"測試分類","timeZone":"Asia/Taipei"}],"nextPageToken":"second"}""")
                else JSONObject("""{"items":[{"id":"other","summary":"其他","timeZone":"UTC"}]}""")
            } else {
                assertEquals("true", query["singleEvents"])
                assertEquals("false", query["showDeleted"])
                assertEquals("startTime", query["orderBy"])
                assertEquals("2026-09-30T16:00:00Z", query["timeMin"])
                assertEquals("2026-10-31T16:00:00Z", query["timeMax"])
                assertTrue(path.contains("a%2Bb%40example.test"))
                if (query["pageToken"] == null) JSONObject().put("items", org.json.JSONArray().put(event())).put("nextPageToken", "page2")
                else JSONObject().put("items", org.json.JSONArray().put(event()).put(event("e2")).put(JSONObject("""{"id":"cancelled","status":"cancelled"}""")))
            }
        })
        assertEquals(2, api.calendars("fixture-token").size)
        val preview = api.preview("fixture-account", listOf(calendar), ImportRange(LocalDate.of(2026,10,1), LocalDate.of(2026,10,31), ZoneId.of("Asia/Taipei")), "fixture-token")
        assertEquals(2, preview.events.size)
        assertEquals(1, preview.cancelled)
        assertEquals(4, calls.size)
        assertEquals("page2", calls.last().second["pageToken"])
    }
    @Test fun repeatedPageAndHttpFailureCannotReturnPartialPreview() = runBlocking {
        val range = ImportRange(LocalDate.of(2026,10,1), LocalDate.of(2026,10,31), ZoneId.of("UTC"))
        val looping = GoogleCalendarApi(CalendarTransport { _, _, _ -> JSONObject().put("items", org.json.JSONArray().put(event())).put("nextPageToken", "same") })
        try { looping.preview("fixture", listOf(calendar), range, "fixture"); fail("應拒絕重複頁") } catch (_: IllegalStateException) { }
        val failing = GoogleCalendarApi(CalendarTransport { _, query, _ ->
            if (query["pageToken"] != null) throw CalendarApiException(403, "測試拒絕")
            JSONObject().put("items", org.json.JSONArray().put(event())).put("nextPageToken", "next")
        })
        try { failing.preview("fixture", listOf(calendar), range, "fixture"); fail("不可回傳部分成功") } catch (error: CalendarApiException) { assertEquals(403, error.status) }
    }
    @Test fun transactionDedupLocalEditsDeletionAndReopenPreserveImportIdentity() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "calendar-import-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, NoteDatabase::class.java, name).build()
        try {
            var repository = RoomCalendarImportRepository(db)
            val events = listOf(CalendarEventParser.parse(calendar, event()), CalendarEventParser.parse(calendar, event("e2")))
            val result = repository.commit("account-a", events + events)
            assertEquals(2, result.inserted); assertEquals(2, result.skipped)
            val note = db.notes().observe().first().first()
            db.notes().update(note.copy(title = "本機修改"))
            assertEquals(0, repository.commit("account-a", events).inserted)
            assertEquals("本機修改", db.notes().get(note.id)?.title)
            db.notes().delete(note.id)
            db.close()
            db = Room.databaseBuilder(context, NoteDatabase::class.java, name).build()
            repository = RoomCalendarImportRepository(db)
            assertEquals(0, repository.commit("account-a", events).inserted)
            assertTrue(repository.markExisting("account-a", events).all { it.alreadyImported })
            assertEquals(1, db.notes().observe().first().size)
            assertEquals(2, repository.commit("account-b", events).inserted)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun versionFourMigrationKeepsPhotosAndAddsImportLedger() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "v4-import-${UUID.randomUUID()}.db"
        val schema = JSONObject(instrumentation.context.assets.open("com.nqutaoitfteikc.notehub.data.NoteDatabase/4.json").bufferedReader().readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.optJSONArray("indices")
                if (indexes != null) for (j in 0 until indexes.length()) db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO notes(id,title,createdAt,photoCapturedAt) VALUES('v4','照片記事',123,123)")
            db.execSQL("INSERT INTO photos VALUES('photo','v4','session','file.jpg',123,90,'Asia/Taipei','CAPTURE_STARTED','描述')")
            db.version = 4
        }
        val db = Room.databaseBuilder(context, NoteDatabase::class.java, name).addMigrations(NoteDatabase.MIGRATION_4_5).build()
        try {
            assertEquals(123L, db.notes().get("v4")?.photoCapturedAt)
            assertEquals("描述", db.photos().all().single().caption)
            assertTrue(db.calendarImports().forAccount("fixture").isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun databaseFailureRollsBackEveryNoteAndSourceKey() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java).build()
        try {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_test_note BEFORE INSERT ON notes WHEN NEW.title = '拒絕' BEGIN SELECT RAISE(ABORT, 'fixture database failure'); END")
            val first = CalendarEventParser.parse(calendar, event())
            val second = CalendarEventParser.parse(calendar, event("e2").put("summary", "拒絕"))
            try { RoomCalendarImportRepository(db).commit("fixture-account", listOf(first, second)); fail("應回滾整批匯入") }
            catch (_: android.database.sqlite.SQLiteException) { }
            assertTrue(db.notes().observe().first().isEmpty())
            assertTrue(db.calendarImports().forAccount("fixture-account").isEmpty())
        } finally { db.close() }
    }
}
