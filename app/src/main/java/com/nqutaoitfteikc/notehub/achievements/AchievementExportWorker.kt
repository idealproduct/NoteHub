package com.nqutaoitfteikc.notehub.achievements

import android.content.Context
import androidx.work.*
import com.nqutaoitfteikc.notehub.NoteHubApplication

class AchievementExportWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        return Result.success() // Retired: achievements are shared explicitly, never auto-saved.
    }
}
