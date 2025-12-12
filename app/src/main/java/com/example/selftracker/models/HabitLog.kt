package com.example.selftracker.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit_logs",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["habitId"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["habitId", "date"], unique = true)]
)
data class HabitLog(
    @PrimaryKey(autoGenerate = true)
    val logId: Int = 0,
    val habitId: Int,
    val date: String,
    val isCompleted: Boolean = false,
    val actualValue: Int = 0,
    val isFrozen: Boolean = false
)