package com.nqutaoitfteikc.notehub.data

import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.flow.map

class RoomAchievementRepository(private val dao: AchievementDao): AchievementRepository {
    override fun observe() = dao.observe().map { rows -> rows.map { row ->
        Achievement(row.milestone,row.unlockedAt,row.announced,row.albumUri,row.svgUri,row.exportError,row.exported)
    } }
    override fun observeTotal() = dao.observeTotal().map { it ?: 0L }
    override suspend fun acknowledge(milestone: Long) = dao.acknowledge(milestone)
}
