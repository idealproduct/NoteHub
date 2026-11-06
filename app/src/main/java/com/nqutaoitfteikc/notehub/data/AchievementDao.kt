package com.nqutaoitfteikc.notehub.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Lifetime count starts with this feature; it is intentionally independent of reminder deletion. */
@Entity(tableName = "achievement_progress")
data class AchievementProgress(@PrimaryKey val id: Int = 1, val total: Long = 0)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val milestone: Long,
    val unlockedAt: Long,
    val announced: Boolean = false,
    val albumUri: String? = null,
    val svgUri: String? = null,
    val exportError: String? = null,
    val exported: Boolean = false
)

@Dao
interface AchievementDao {
    @Query("DELETE FROM achievements WHERE milestone = :milestone") suspend fun reset(milestone: Long)
    @Query("SELECT * FROM achievements ORDER BY milestone DESC") fun observe(): Flow<List<AchievementEntity>>
    @Query("SELECT * FROM achievements ORDER BY milestone ASC") suspend fun all(): List<AchievementEntity>
    @Query("SELECT * FROM achievements WHERE milestone = :milestone") suspend fun get(milestone: Long): AchievementEntity?
    @Query("SELECT total FROM achievement_progress WHERE id = 1") suspend fun total(): Long?
    @Query("SELECT total FROM achievement_progress WHERE id = 1") fun observeTotal(): Flow<Long?>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun initialize(progress: AchievementProgress)
    @Query("UPDATE achievement_progress SET total = :total WHERE id = 1") suspend fun setTotal(total: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun unlock(achievement: AchievementEntity): Long
    @Query("UPDATE achievements SET announced = 1 WHERE milestone = :milestone") suspend fun acknowledge(milestone: Long)
    @Query("UPDATE achievements SET albumUri = :uri, exported = 0 WHERE milestone = :milestone") suspend fun album(milestone: Long, uri: String)
    @Query("UPDATE achievements SET svgUri = :uri, exported = 0 WHERE milestone = :milestone") suspend fun svg(milestone: Long, uri: String)
    @Query("UPDATE achievements SET exported = 1, exportError = NULL WHERE milestone = :milestone") suspend fun exported(milestone: Long)
    @Query("UPDATE achievements SET exportError = :message WHERE milestone = :milestone") suspend fun exportError(milestone: Long, message: String?)
}
