package com.nqutaoitfteikc.notehub

import com.nqutaoitfteikc.notehub.domain.*
import org.junit.Assert.*
import org.junit.Test

class ImportSelectionTest {
    @Test fun deletedCopiesAreSelectableButNotSelectedByDefault() {
        val fresh = CalendarImportEvent("calendar", "new", "new", NoteDraft("新事件"))
        val existing = fresh.copy(eventId = "existing", instanceKey = "existing", alreadyImported = true)
        val deleted = fresh.copy(eventId = "deleted", instanceKey = "deleted", alreadyImported = true, locallyDeleted = true)
        assertEquals(setOf("calendar" to "new"), ImportPreview("account", listOf(fresh, existing, deleted), 0).defaultSelection())
        assertTrue(deleted.canSelect)
        assertFalse(existing.canSelect)
    }
}
