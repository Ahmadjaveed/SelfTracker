package com.example.selftracker.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.selftracker.models.GoalStep

@Dao
interface GoalStepDao {
    @Insert
    suspend fun insertGoalStep(goalStep: GoalStep): Long

    @Query("SELECT * FROM goal_steps WHERE goalId = :goalId ORDER BY orderIndex")
    fun getStepsByGoal(goalId: Long): LiveData<List<GoalStep>>

    @Query("SELECT * FROM goal_steps ORDER BY orderIndex")
    fun getAllSteps(): LiveData<List<GoalStep>>

    @Update
    suspend fun updateGoalStep(goalStep: GoalStep)

    @Delete
    suspend fun deleteGoalStep(goalStep: GoalStep)

    @Query("DELETE FROM goal_steps WHERE goalId = :goalId")
    suspend fun deleteStepsByGoal(goalId: Long)

    @Query("SELECT * FROM goal_steps WHERE stepId = :stepId")
    suspend fun getStepById(stepId: Long): GoalStep?
}