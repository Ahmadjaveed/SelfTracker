package com.example.selftracker.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.selftracker.workers.AiNotificationWorker

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.selftracker.NOTIFY") {
            val habitName = intent.getStringExtra("HABIT_NAME") ?: "Habit"
            val habitId = intent.getIntExtra("HABIT_ID", -1)

            val workRequest = OneTimeWorkRequestBuilder<AiNotificationWorker>()
                .setInputData(workDataOf(
                    "HABIT_NAME" to habitName,
                    "HABIT_ID" to habitId
                ))
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        } else if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // ideally reschedule all alarms here
        }
    }
}
