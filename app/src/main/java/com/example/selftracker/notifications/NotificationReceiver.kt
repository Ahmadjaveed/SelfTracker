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
            val targetName = intent.getStringExtra("TARGET_NAME") ?: intent.getStringExtra("HABIT_NAME") ?: "Task"
            val targetId = intent.getIntExtra("TARGET_ID", intent.getIntExtra("HABIT_ID", -1))
            val targetType = intent.getStringExtra("TARGET_TYPE") ?: "HABIT"

            val workRequest = OneTimeWorkRequestBuilder<AiNotificationWorker>()
                .setInputData(workDataOf(
                    "TARGET_NAME" to targetName,
                    "TARGET_ID" to targetId,
                    "TARGET_TYPE" to targetType
                ))
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        } else if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // ideally reschedule all alarms here
        }
    }
}
