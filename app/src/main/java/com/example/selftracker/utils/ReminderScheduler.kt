package com.example.selftracker.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.selftracker.models.Habit
import com.example.selftracker.notifications.NotificationReceiver
import java.util.Calendar

object ReminderScheduler {

    fun scheduleReminder(context: Context, habit: Habit) {
        val reminderTime = habit.reminderTime ?: return
        
        // Calculate trigger time for daily recurring habit
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()
        target.timeInMillis = reminderTime
        
        val trigger = Calendar.getInstance()
        trigger.set(Calendar.HOUR_OF_DAY, target.get(Calendar.HOUR_OF_DAY))
        trigger.set(Calendar.MINUTE, target.get(Calendar.MINUTE))
        trigger.set(Calendar.SECOND, 0)
        
        if (trigger.before(now)) {
            trigger.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "com.example.selftracker.NOTIFY"
            putExtra("TARGET_NAME", habit.name)
            putExtra("TARGET_ID", habit.habitId)
            putExtra("TARGET_TYPE", "HABIT")
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            habit.habitId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarm(context, trigger.timeInMillis, pendingIntent)
    }

    fun scheduleStepReminder(context: Context, stepId: Long, name: String, reminderTime: Long) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "com.example.selftracker.NOTIFY"
            putExtra("TARGET_NAME", name)
            putExtra("TARGET_ID", stepId.toInt())
            putExtra("TARGET_TYPE", "STEP")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            stepId.toInt() * 100, // Namespace ID to avoid collision with habits
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarm(context, reminderTime, pendingIntent)
    }

    fun scheduleSubStepReminder(context: Context, subStepId: Long, name: String, reminderTime: Long) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "com.example.selftracker.NOTIFY"
            putExtra("TARGET_NAME", name)
            putExtra("TARGET_ID", subStepId.toInt())
            putExtra("TARGET_TYPE", "SUBSTEP")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            subStepId.toInt() * 1000, // Namespace ID
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarm(context, reminderTime, pendingIntent)
    }

    private fun scheduleAlarm(context: Context, timeInMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                     alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        timeInMillis,
                        pendingIntent
                    )
                } else {
                     alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                 alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelReminder(context: Context, habit: Habit) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
             action = "com.example.selftracker.NOTIFY"
        }
         val pendingIntent = PendingIntent.getBroadcast(
            context,
            habit.habitId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
