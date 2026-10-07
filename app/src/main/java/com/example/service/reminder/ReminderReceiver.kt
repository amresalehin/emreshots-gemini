package com.amresalehin.emreshots.service.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.amresalehin.emreshots.R

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return

        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val text = intent.getStringExtra(EXTRA_REMINDER_TEXT).orEmpty()
        val title = intent.getStringExtra(EXTRA_REMINDER_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.reminder_notification_title)

        ensureNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text.ifBlank { context.getString(R.string.reminder_notification_title) })
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(reminderId), notification)
    }

    companion object {
        const val ACTION_FIRE = "com.amresalehin.emreshots.action.REMINDER_FIRE"
        private const val CHANNEL_ID = "media_reminders"
        private const val EXTRA_REMINDER_ID = "reminder_id"
        private const val EXTRA_REMINDER_TITLE = "reminder_title"
        private const val EXTRA_REMINDER_TEXT = "reminder_text"

        fun schedule(context: Context, reminderId: String, timeMs: Long, title: String, text: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = pendingIntent(context, reminderId, title, text)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeMs,
                pendingIntent
            )
        }

        fun cancel(context: Context, reminderId: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent(context, reminderId, null, null))
        }

        private fun pendingIntent(
            context: Context,
            reminderId: String,
            title: String?,
            text: String?
        ): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_FIRE
                putExtra(EXTRA_REMINDER_ID, reminderId)
                title?.let { putExtra(EXTRA_REMINDER_TITLE, it) }
                text?.let { putExtra(EXTRA_REMINDER_TEXT, it) }
            }
            return PendingIntent.getBroadcast(
                context,
                reminderRequestCode(reminderId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun reminderRequestCode(reminderId: String): Int =
            reminderId.hashCode() and 0x7fffffff

        private fun notificationId(reminderId: String): Int =
            reminderRequestCode(reminderId)

        private fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.reminder_notification_channel)
                }
            )
        }
    }
}
