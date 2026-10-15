package com.nqutaoitfteikc.notehub.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.nqutaoitfteikc.notehub.NoteHubApplication
import kotlinx.coroutines.*

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderPlatform.ACTION_FIRE || intent.data?.scheme != "notehub-reminder") return
        val parts = intent.data?.pathSegments ?: return
        if (parts.size != 2) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8000) { (context.applicationContext as NoteHubApplication).container.reminders.deliver(parts[0], parts[1]) }
            } catch (e: Exception) {
                Log.e("NoteHubReminders", "提醒處理失敗，安排恢復排程", e)
                ReminderRestoreWorker.enqueue(context)
            } finally { pending.finish() }
        }
    }
}
class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        Log.i("NoteHubReminders", "恢復提醒排程：${intent.action}")
        // WorkManager is only a durable retry for reconciliation, never the reminder timer.
        ReminderRestoreWorker.enqueue(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(8000) { (context.applicationContext as NoteHubApplication).container.reminders.restore() } }
            catch (e: Exception) { Log.e("NoteHubReminders", "立即恢復未完成，將由背景工作重試", e) }
            finally { pending.finish() }
        }
    }
    companion object {
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED) +
            if (android.os.Build.VERSION.SDK_INT >= 31) setOf(android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) else emptySet()
    }
}
class ReminderRestoreWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        (applicationContext as NoteHubApplication).container.reminders.restore()
        Result.success()
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        Log.e("NoteHubReminders", "背景恢復失敗", e)
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("restore-reminders", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ReminderRestoreWorker>().build())
        }
    }
}
