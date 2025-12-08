package com.example.selftracker.database

import androidx.room.*
import com.example.selftracker.models.HabitLog
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHabitLog(habitLog: HabitLog)

    @Update
    suspend fun updateHabitLog(habitLog: HabitLog)

    @Delete
    suspend fun deleteHabitLog(habitLog: HabitLog)

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date = :date")
    suspend fun getLogByHabitAndDate(habitId: Int, date: String): HabitLog?

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY date DESC")
    fun getLogsByHabit(habitId: Int): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE date = :date")
    fun getLogsByDate(date: String): Flow<List<HabitLog>>

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteLogsByHabit(habitId: Int)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND date = :date")
    suspend fun deleteLogByHabitAndDate(habitId: Int, date: String)

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND isCompleted = 1")
    suspend fun getCompletedCount(habitId: Int): Int

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND date BETWEEN :startDate AND :endDate AND isCompleted = 1")
    suspend fun getCompletedCountInRange(habitId: Int, startDate: String, endDate: String): Int
}