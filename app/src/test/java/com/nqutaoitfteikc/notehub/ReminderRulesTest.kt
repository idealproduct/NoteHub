package com.nqutaoitfteikc.notehub

import com.nqutaoitfteikc.notehub.domain.*
import org.junit.Assert.*
import org.junit.Test
class ReminderRulesTest {
    private val now = 2000000L
    private val future = Reminder("1", "note", now + 1000, "v1")
    @Test fun exactAndApproximateAreExplicit() {
        assertEquals(ReminderStatus.EXACT, ReminderRules.decide(future, false, true, true, now))
        assertEquals(ReminderStatus.APPROXIMATE, ReminderRules.decide(future, false, true, false, now))
        assertEquals(ReminderStatus.BLOCKED_NOTIFICATION, ReminderRules.decide(future, false, false, true, now))
    }
    @Test fun completedDisabledAndDeliveredNeverSchedule() {
        assertEquals(ReminderStatus.PAUSED_COMPLETED, ReminderRules.decide(future, true, true, true, now))
        assertEquals(ReminderStatus.DISABLED, ReminderRules.decide(future.copy(enabled = false), false, true, true, now))
        assertEquals(ReminderStatus.DELIVERED, ReminderRules.decide(future.copy(deliveredAt = now), false, true, true, now))
    }
    @Test fun onlyRecentMissedRemindersAreRecovered() {
        assertEquals(ReminderStatus.EXACT, ReminderRules.decide(future.copy(triggerAt = now - ReminderRules.LATE_GRACE_MILLIS), false, true, true, now))
        assertEquals(ReminderStatus.EXPIRED, ReminderRules.decide(future.copy(triggerAt = now - ReminderRules.LATE_GRACE_MILLIS - 1), false, true, true, now))
    }
    @Test fun staleEarlyDuplicateDisabledAndExpiredBroadcastsAreRejected() {
        assertFalse(ReminderRules.acceptsDelivery(future, "v0", now + 1000))
        assertFalse(ReminderRules.acceptsDelivery(future, "v1", now))
        assertFalse(ReminderRules.acceptsDelivery(future.copy(deliveredAt = now), "v1", now + 1000))
        assertFalse(ReminderRules.acceptsDelivery(future.copy(enabled = false), "v1", now + 1000))
        assertFalse(ReminderRules.acceptsDelivery(future.copy(triggerAt = now - ReminderRules.LATE_GRACE_MILLIS - 1), "v1", now))
        assertTrue(ReminderRules.acceptsDelivery(future, "v1", now + 1000))
    }
}
