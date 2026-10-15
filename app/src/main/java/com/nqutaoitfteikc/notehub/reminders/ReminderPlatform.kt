package com.nqutaoitfteikc.notehub.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.nqutaoitfteikc.notehub.MainActivity
import com.nqutaoitfteikc.notehub.R
import com.nqutaoitfteikc.notehub.data.ReminderEntity
import com.nqutaoitfteikc.notehub.data.NoteEntity
import com.nqutaoitfteikc.notehub.domain.ReminderStatus

class ReminderPlatform(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    init {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "事項提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "記事與行事曆事項的獨立提醒"
        })
    }
    fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            notifications.areNotificationsEnabled() && notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    fun canExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun batteryUnrestricted(): Boolean = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun alarmIntent(reminder: ReminderEntity, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
            .setData(Uri.Builder().scheme("notehub-reminder").authority("alarm").appendPath(reminder.id).appendPath(reminder.revision).build()),
        flags or PendingIntent.FLAG_IMMUTABLE)

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(reminder: ReminderEntity, at: Long): ReminderStatus {
        val pending = requireNotNull(alarmIntent(reminder))
        if (canExact()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                return ReminderStatus.EXACT
            } catch (_: SecurityException) {
                // Permission can be revoked between the check and the binder call.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        return ReminderStatus.APPROXIMATE
    }
    fun cancelAlarm(reminder: ReminderEntity) {
        alarmIntent(reminder, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
    }
    fun cancelNotification(id: String) { notifications.cancel(id, 0) }

    @SuppressLint("MissingPermission")
    fun notify(reminder: ReminderEntity, note: NoteEntity): Boolean {
        if (!canNotify()) return false
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setData(Uri.Builder().scheme("notehub").authority("note").appendPath(note.id).appendPath(reminder.id).build())
            .putExtra(EXTRA_NOTE_ID, note.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("事項提醒：${note.title}")
            .setContentText(note.description.ifEmpty { "點選查看事項內容" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.description.ifEmpty { "點選查看事項內容" }))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        notifications.notify(reminder.id, 0, notification)
        return true
    }
    companion object {
        const val CHANNEL = "notehub_reminders_v1"
        const val ACTION_FIRE = "com.nqutaoitfteikc.notehub.FIRE_REMINDER"
        const val EXTRA_NOTE_ID = "open_note_id"
    }
}
