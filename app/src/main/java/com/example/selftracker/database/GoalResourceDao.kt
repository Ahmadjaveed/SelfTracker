package com.example.selftracker.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.selftracker.models.GoalResource

@Dao
interface GoalResourceDao {
    @Insert
    suspend fun insertResource(resource: GoalResource): Long

    @Update
    suspend fun updateResource(resource: GoalResource)

    @Delete
    suspend fun deleteResource(resource: GoalResource)

    @Query("SELECT * FROM goal_resources WHERE goalId = :goalId ORDER BY createdAt DESC")
    fun getResourcesByGoal(goalId: Long): LiveData<List<GoalResource>>

    @Query("DELETE FROM goal_resources WHERE goalId = :goalId")
    suspend fun deleteResourcesByGoal(goalId: Long)

    @Query("SELECT * FROM goal_resources WHERE stepId = :stepId ORDER BY createdAt DESC")
    fun getResourcesByStep(stepId: Long): LiveData<List<GoalResource>>

    @Query("SELECT * FROM goal_resources WHERE subStepId = :subStepId ORDER BY createdAt DESC")
    fun getResourcesBySubStep(subStepId: Long): LiveData<List<GoalResource>>
}
