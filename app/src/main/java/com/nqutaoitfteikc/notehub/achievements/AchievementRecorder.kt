package com.nqutaoitfteikc.notehub.achievements

import com.nqutaoitfteikc.notehub.data.*

/** Must be invoked inside the very same Room transaction as the new local note insertion. */
class AchievementRecorder(private val dao: AchievementDao, private val isMilestone: (Long)->Boolean) {
    suspend fun recordNewRecord(now: Long): Boolean {
        dao.initialize(AchievementProgress())
        val previous=requireNotNull(dao.total())
        check(previous>=0 && previous<Long.MAX_VALUE) { "紀錄累計次數已達可儲存上限" }
        val total=previous+1
        dao.setTotal(total)
        return isMilestone(total) && dao.unlock(AchievementEntity(total,now)) != -1L
    }
}
