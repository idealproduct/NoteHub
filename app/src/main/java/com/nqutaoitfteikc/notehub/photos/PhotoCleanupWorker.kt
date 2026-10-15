package com.nqutaoitfteikc.notehub.photos

import android.content.Context
import android.util.Log
import androidx.work.*
import com.nqutaoitfteikc.notehub.NoteHubApplication
import kotlinx.coroutines.CancellationException

class PhotoCleanupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        (applicationContext as NoteHubApplication).container.photos.recoverFiles()
        Result.success()
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        Log.e("NoteHubPhotos", "照片檔案清理失敗，保留佇列以供重試", e)
        if (runAttemptCount < 5) Result.retry() else Result.failure()
    }
    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("photo-file-cleanup", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<PhotoCleanupWorker>().build())
        }
    }
}
