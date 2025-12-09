package com.example.selftracker.database

import androidx.room.*
import com.example.selftracker.models.Habit
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits")
    fun getAllHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE habitId = :id")
    suspend fun getHabitByIdSync(id: Int): Habit?

    @Insert
    suspend fun insertHabit(habit: Habit): Long

    @Update
    suspend fun updateHabit(habit: Habit)

    @Delete
    suspend fun deleteHabit(habit: Habit)

    @Query("UPDATE habits SET currentStreak = :streak WHERE habitId = :habitId")
    suspend fun updateCurrentStreak(habitId: Int, streak: Int)

    @Query("UPDATE habits SET bestStreak = :streak WHERE habitId = :habitId")
    suspend fun updateBestStreak(habitId: Int, streak: Int)

    @Query("UPDATE habits SET lastCompletedDate = :date WHERE habitId = :habitId")
    suspend fun updateLastCompletedDate(habitId: Int, date: String)
}