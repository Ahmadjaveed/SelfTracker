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
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = "com.example.selftracker.NOTIFY"
            putExtra("HABIT_NAME", habit.name)
            putExtra("HABIT_ID", habit.habitId)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            habit.habitId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate trigger time
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()
        target.timeInMillis = reminderTime
        
        // We only care about Hour/Minute from reminderTime
        val targetHour = target.get(Calendar.HOUR_OF_DAY)
        val targetMinute = target.get(Calendar.MINUTE)
        
        val trigger = Calendar.getInstance()
        trigger.set(Calendar.HOUR_OF_DAY, targetHour)
        trigger.set(Calendar.MINUTE, targetMinute)
        trigger.set(Calendar.SECOND, 0)
        
        if (trigger.before(now)) {
            trigger.add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                     alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        trigger.timeInMillis,
                        pendingIntent
                    )
                } else {
                     alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        trigger.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                 alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    trigger.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Permission not granted
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
