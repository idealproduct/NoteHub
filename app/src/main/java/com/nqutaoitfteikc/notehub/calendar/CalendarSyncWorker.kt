package com.nqutaoitfteikc.notehub.calendar

import android.content.Context
import androidx.work.*
import com.nqutaoitfteikc.notehub.NoteHubApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class CalendarSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = (applicationContext as NoteHubApplication).container
        return app.calendarSync.mutex.withLock {
            val config = app.syncSettings.flow.first()
            if (!config.enabled) return@withLock Result.success()
            var token: String? = null
            try {
                app.syncSettings.status("同步中…")
                val auth = app.googleIdentity.authorize(config.account, CALENDAR_SYNC_SCOPES)
                if (auth.hasResolution()) {
                    app.syncSettings.status("需要重新授權，請開啟雙向同步設定")
                    return@withLock Result.success()
                }
                token = app.googleIdentity.token(auth, CALENDAR_SYNC_SCOPES)
                val conflicts = app.calendarSync.run(config, token)
                app.syncSettings.status(if (conflicts == 0) "同步完成" else "同步完成，$conflicts 筆衝突待處理", completed = true)
                Result.success()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                android.util.Log.w("NoteHubSync", "雙向同步未完成（${e.javaClass.simpleName}）")
                if (e is CalendarApiException && e.status == 401) {
                    token?.let { expired ->
                        try { app.googleIdentity.clearExpiredToken(expired) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { android.util.Log.w("NoteHubSync", "過期授權清除失敗") }
                    }
                }
                app.syncSettings.status(when (e) {
                    is CalendarApiException -> if (e.status == 412) "同步時資料變更，請稍後重試" else e.message ?: "Google 同步失敗"
                    is java.io.IOException -> "無法連線 Google，稍後重試"
                    is IllegalArgumentException, is IllegalStateException -> e.message ?: "同步資料格式不符"
                    else -> "同步未完成，請重新登入與授權後重試"
                })
                if ((e is java.io.IOException || e is CalendarApiException && (e.status == 429 || e.status >= 500)) && runAttemptCount < 5) Result.retry()
                else Result.success() // A periodic job remains scheduled; auth failures must not loop rapidly.
            }
        }
    }
    companion object {
        private const val PERIODIC = "google-calendar-bidirectional"
        private const val IMMEDIATE = "google-calendar-sync-now"
        private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun schedule(context: Context, hours: Int) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<CalendarSyncWorker>(hours.toLong(), TimeUnit.HOURS)
                    .setConstraints(constraints()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
        fun now(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CalendarSyncWorker>().setConstraints(constraints()).build())
        }
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE)
        }
    }
}
