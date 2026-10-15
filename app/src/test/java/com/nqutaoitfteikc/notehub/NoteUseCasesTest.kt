package com.nqutaoitfteikc.notehub
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
class NoteUseCasesTest {
    private class MemoryNotes : NoteRepository {
        val rows = MutableStateFlow<List<Note>>(emptyList())
        override fun observe() = rows
        override suspend fun insert(note: Note) { rows.value += note }
        override suspend fun get(id: String) = rows.value.find { it.id == id }
        override suspend fun update(note: Note) { rows.value = rows.value.map { if (it.id == note.id) note else it } }
        override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
        override suspend fun setCompleted(id: String, completed: Boolean) { update(requireNotNull(get(id)).copy(completed = completed)) }
    }
    @Test fun trimsTitleAndPreservesInjectedTimeAndId() = runTest {
        val repository = MemoryNotes()
        AddNote(repository, { 123L }, { "note-1" })("  想法  ")
        assertEquals(listOf(Note("note-1", "想法", 123L)), repository.rows.value)
    }
    @Test fun rejectsEmptyAndOverlongTitlesWithoutWriting() = runTest {
        val repository = MemoryNotes()
        for (title in listOf("  ", "字".repeat(201))) {
            try { AddNote(repository)(title); fail("應拒絕不合法標題") }
            catch (_: IllegalArgumentException) { }
        }
        assertTrue(repository.rows.value.isEmpty())
    }
    @Test fun acceptsMaximumLengthAndGeneratesIndependentIds() = runTest {
        val repository = MemoryNotes()
        val add = AddNote(repository)
        add("字".repeat(200)); add("另一筆")
        assertEquals(2, repository.rows.value.size)
        assertNotEquals(repository.rows.value[0].id, repository.rows.value[1].id)
    }
}

