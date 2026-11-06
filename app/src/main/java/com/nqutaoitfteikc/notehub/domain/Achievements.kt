package com.nqutaoitfteikc.notehub.domain

import kotlinx.coroutines.flow.Flow

data class Achievement(
    val milestone: Long, val unlockedAt: Long, val announced: Boolean = false,
    val albumUri: String? = null, val svgUri: String? = null,
    val exportError: String? = null, val exported: Boolean = false
)
interface AchievementRepository {
    fun observe(): Flow<List<Achievement>>
    fun observeTotal(): Flow<Long>
    suspend fun acknowledge(milestone: Long)
}
class ObserveAchievements(private val repository: AchievementRepository) { operator fun invoke() = repository.observe() }
class ObserveAchievementTotal(private val repository: AchievementRepository) { operator fun invoke() = repository.observeTotal() }
class AcknowledgeAchievement(private val repository: AchievementRepository) { suspend operator fun invoke(milestone: Long) = repository.acknowledge(milestone) }
