package com.example.selftracker.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.selftracker.models.GoalSubStep

@Dao
interface GoalSubStepDao {
    @Insert
    suspend fun insertGoalSubStep(goalSubStep: GoalSubStep): Long

    @Query("SELECT * FROM goal_sub_steps WHERE stepId = :stepId ORDER BY orderIndex")
    fun getSubStepsByStep(stepId: Long): LiveData<List<GoalSubStep>>

    @Query("SELECT goal_sub_steps.* FROM goal_sub_steps INNER JOIN goal_steps ON goal_sub_steps.stepId = goal_steps.stepId WHERE goal_steps.goalId = :goalId")
    fun getSubStepsByGoal(goalId: Long): LiveData<List<GoalSubStep>>

    @Update
    suspend fun updateGoalSubStep(goalSubStep: GoalSubStep)

    @Delete
    suspend fun deleteGoalSubStep(goalSubStep: GoalSubStep)

    @Query("DELETE FROM goal_sub_steps WHERE stepId = :stepId")
    suspend fun deleteSubStepsByStep(stepId: Long)
}