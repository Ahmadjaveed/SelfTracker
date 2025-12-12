package com.example.selftracker.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "goal_steps",
    foreignKeys = [
        ForeignKey(
            entity = Goal::class,
            parentColumns = ["goalId"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GoalStep(
    @PrimaryKey(autoGenerate = true)
    val stepId: Long = 0,
    val goalId: Long,
    val name: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val orderIndex: Int = 0,
    val duration: Int = 0,
    val durationUnit: String = "days",
    val reminderTime: Long? = null
)