package com.example.selftracker.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.selftracker.database.SelfTrackerDatabase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class InactivityWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = SelfTrackerDatabase.getDatabase(applicationContext)
        val habitDao = database.habitDao()
        val habitLogDao = database.habitLogDao()

        // fetch all habits
        val allHabits = habitDao.getAllHabits().first()

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
        val currentMonth = today.format(monthFormatter)
        val thresholdDate = today.minusDays(3) // 3 days of inactivity

        for (habit in allHabits) {
            // 1. Monthly Reset Logic
            if (habit.lastFreezeResetDate != currentMonth) {
                // It's a new month, reset freezes
                val updatedHabit = habit.copy(
                    monthlyFreezeCount = 2,
                    lastFreezeResetDate = currentMonth
                )
                habitDao.updateHabit(updatedHabit)
                // Continue with updated habit object
                // Note: In a real app we'd reload, but for logic below 'habit' vs 'updatedHabit' matters for count
                // Let's just use updated values locally if needed, or rely on next run. 
                // But for the freeze logic below, we should use the NEW count.
            }

            val logs = habitLogDao.getLogsByHabit(habit.habitId).first()

            // 2. Streak Freeze Logic (Run daily)
            // Check if Yesterday was missed
            val yesterdayLog = logs.find { it.date == yesterday.format(formatter) }
            
            if (yesterdayLog == null) {
                // Missed yesterday! Check if we can freeze.
                // We need to check if the user HAS a streak to save? 
                // Usually freeze only matters if currentStreak > 0 (or was > 0 before yesterday).
                // If habit.currentStreak > 0, and we missed yesterday, the streak is technically at risk.
                // Actually, if we missed yesterday, currentStreak might not have been updated to 0 yet depending on logic.
                // But usually streak calculation is dynamic or on-habit-update.
                // If we want to SAVE it, we should insert a frozen log.
                
                // Let's assume we always try to save if there's a freeze available, 
                // unless the user has effectively 0 streak anyway. 
                // But simplified: If missed yesterday AND freeze > 0 -> Consume Freeze.
                
                // Effective freeze count (use updated if we just reset)
                val currentFreezeCount = if (habit.lastFreezeResetDate != currentMonth) 2 else habit.monthlyFreezeCount
                
                if (currentFreezeCount > 0) {
                     // Check if we ALREADY froze it (race condition or multiple worker runs)
                     // Log check returned null, so we haven't frozen it yet.
                     
                     // Consume Freeze
                     val frozenLog = com.example.selftracker.models.HabitLog(
                         habitId = habit.habitId,
                         date = yesterday.format(formatter),
                         isCompleted = false,
                         isFrozen = true,
                         actualValue = 0
                     )
                     habitLogDao.insertHabitLog(frozenLog)
                     
                     val habitToUpdate = if (habit.lastFreezeResetDate != currentMonth) {
                         habit.copy(monthlyFreezeCount = 1, lastFreezeResetDate = currentMonth)
                     } else {
                         habit.copy(monthlyFreezeCount = currentFreezeCount - 1)
                     }
                     habitDao.updateHabit(habitToUpdate)
                     
                     // Send "Streak Saved" Notification
                     val inputData = workDataOf(
                        "TARGET_NAME" to habit.name,
                        "TARGET_ID" to habit.habitId,
                        "TARGET_TYPE" to "STREAK_FREEZE"
                    )
                    val request = OneTimeWorkRequestBuilder<AiNotificationWorker>()
                        .setInputData(inputData)
                        .build()
                    WorkManager.getInstance(applicationContext).enqueue(request)
                    
                    continue // Skip inactivity check if we just froze (it counts as activity/saved)
                }
            }
            
            // 3. Inactivity Check (Existing Logic)
            val hasRecentLog = logs.any { 
                try {
                    val logDate = LocalDate.parse(it.date, formatter)
                    !logDate.isBefore(thresholdDate)
                } catch (e: Exception) {
                    false
                }
            }

            if (!hasRecentLog) {
                // Trigger AI Notification
                val inputData = workDataOf(
                    "TARGET_NAME" to habit.name,
                    "TARGET_ID" to habit.habitId,
                    "TARGET_TYPE" to "INACTIVITY"
                )

                val request = OneTimeWorkRequestBuilder<AiNotificationWorker>()
                    .setInputData(inputData)
                    .build()

                WorkManager.getInstance(applicationContext).enqueue(request)
                
                break 
            }
        }

        return Result.success()
    }
}
