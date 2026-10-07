package com.amresalehin.emreshots.service.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amresalehin.emreshots.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val now = System.currentTimeMillis()
                database.screenshotDao()
                    .getAllScreenshotsSync()
                    .asSequence()
                    .filter { it.reminderTime != null && it.reminderTime > now }
                    .forEach { item ->
                        ReminderReceiver.schedule(
                            context,
                            item.id,
                            item.reminderTime!!,
                            context.getString(com.amresalehin.emreshots.R.string.reminder_notification_title),
                            item.reminderText.orEmpty()
                        )
                    }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
