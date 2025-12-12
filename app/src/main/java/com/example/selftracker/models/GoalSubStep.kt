package com.example.selftracker.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "goal_sub_steps",
    foreignKeys = [
        ForeignKey(
            entity = GoalStep::class,
            parentColumns = ["stepId"],
            childColumns = ["stepId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GoalSubStep(
    @PrimaryKey(autoGenerate = true)
    val subStepId: Long = 0,
    val stepId: Long,
    val name: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val orderIndex: Int = 0,
    val duration: Int = 0,
    val durationUnit: String = "days",
    val reminderTime: Long? = null
)