package com.example.selftracker.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.selftracker.models.Goal

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun getAllGoals(): LiveData<List<Goal>>

    @Query("SELECT * FROM goals WHERE goalId = :id")
    suspend fun getGoalById(id: Long): Goal?

    @Insert
    suspend fun insertGoal(goal: Goal): Long

    @Update
    suspend fun updateGoal(goal: Goal)

    @Delete
    suspend fun deleteGoal(goal: Goal)

    @Query("""
        SELECT g.*, 
               COUNT(DISTINCT s.stepId) as totalSteps,
               COUNT(DISTINCT CASE WHEN s.isCompleted = 1 THEN s.stepId END) as completedSteps
        FROM goals g
        LEFT JOIN goal_steps s ON g.goalId = s.goalId
        GROUP BY g.goalId
        ORDER BY g.createdAt DESC
    """)
    fun getAllGoalsWithProgress(): LiveData<List<GoalWithProgress>>
}

data class GoalWithProgress(
    @Embedded
    val goal: Goal,
    val totalSteps: Int,
    val completedSteps: Int
)