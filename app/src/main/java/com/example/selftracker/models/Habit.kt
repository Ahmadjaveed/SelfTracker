package com.example.selftracker.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val habitId: Int = 0,
    val name: String,
    val targetValue: Int,
    val unit: String,
    val scheduleType: String,
    val fixedTime: String? = null,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastCompletedDate: String? = null,
    val reminderTime: Long? = null,
    val description: String = "",
    val iconPath: String? = null,
    val monthlyFreezeCount: Int = 2,
    val lastFreezeResetDate: String? = null
)