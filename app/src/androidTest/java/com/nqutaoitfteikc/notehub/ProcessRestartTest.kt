package com.nqutaoitfteikc.notehub

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.time.LocalDate
import java.util.UUID
import java.io.File

/** Run methods in separate instrumentation invocations with the same persistenceRun
 * argument and an am force-stop between them to verify actual process restarts. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ProcessRestartTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NoteHubApplication
    private val token get() = InstrumentationRegistry.getArguments().getString("persistenceRun") ?: defaultToken
    private val container get() = app.container
    private val themeBackup get() = File(app.cacheDir, "restart-${token.hashCode()}.txt")

    @Test fun aPrepare() = runBlocking {
        themeBackup.writeText(container.observeTheme().first().name)
        val day = LocalDate.now().toEpochDay()
        container.saveNote(null, NoteDraft("$token-新增", "重啟後檢查內容", "驗收分類", ScheduleKind.ALL_DAY, day, day + 3))
        val item = container.observeNotes().first().single { it.title == "$token-新增" }
        container.saveNote(item.id, NoteDraft("$token-已編輯", "重啟後檢查內容", "驗收分類", ScheduleKind.ALL_DAY, day, day + 3))
        container.setCompleted(item.id, true)
        container.saveNote(null, NoteDraft("$token-刪除"))
        val removed = container.observeNotes().first().single { it.title == "$token-刪除" }
        container.deleteNote(removed.id)
        container.setTheme(ThemeMode.DARK)
    }

    @Test fun bVerifyAndCleanUp() = runBlocking {
        val items = container.observeNotes().first()
        val item = items.single { it.title == "$token-已編輯" }
        try {
            assertTrue(item.completed)
            assertEquals("重啟後檢查內容", item.description)
            assertEquals("驗收分類", item.category)
            assertEquals(ScheduleKind.ALL_DAY, item.kind)
            assertEquals(3L, requireNotNull(item.endDayExclusive) - requireNotNull(item.startDay))
            assertTrue(items.none { it.title == "$token-刪除" || it.title == "$token-新增" })
            assertEquals(ThemeMode.DARK, container.observeTheme().first())
        } finally {
            container.deleteNote(item.id)
            container.setTheme(ThemeMode.valueOf(themeBackup.readText()))
            themeBackup.delete()
        }
    }

    companion object { private val defaultToken = "程序驗收-${UUID.randomUUID()}" }
}
