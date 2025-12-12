package com.example.selftracker.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.selftracker.R
import com.example.selftracker.activities.MainActivity
import com.example.selftracker.repository.GoalGeneratorRepository

class AiNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val targetName = inputData.getString("TARGET_NAME") ?: inputData.getString("HABIT_NAME") ?: return Result.failure()
        val targetId = inputData.getInt("TARGET_ID", inputData.getInt("HABIT_ID", -1))
        val targetType = inputData.getString("TARGET_TYPE") ?: "HABIT"

        // 1. Generate Motivation
        val repository = GoalGeneratorRepository()
        val message = try {
            repository.generateMotivation(targetName, targetType)
        } catch (e: Exception) {
            "Time to work on $targetName!" // Fallback
        }

        // 2. Save to Database (In-App History)
        val database = com.example.selftracker.database.SelfTrackerDatabase.getDatabase(applicationContext)
        val notification = com.example.selftracker.models.Notification(
            title = "SelfTracker: $targetName",
            message = message,
            timestamp = System.currentTimeMillis(),
            type = targetType
        )
        database.notificationDao().insert(notification)

        // 3. Show System Notification
        showNotification(targetId, targetName, message)

        return Result.success()
    }

    private fun showNotification(id: Int, title: String, message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "habit_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Habit Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                enableLights(true)
                description = "Daily reminders for your habits"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_small) // Alpha-only vector
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_notification)) // Colorful 3D icon
            .setContentTitle("SelfTracker: $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        manager.notify(id, notification)
    }
}
