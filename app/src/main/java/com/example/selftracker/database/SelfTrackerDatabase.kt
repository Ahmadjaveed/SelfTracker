package com.example.selftracker.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.selftracker.models.Goal
import com.example.selftracker.models.GoalStep
import com.example.selftracker.models.GoalSubStep
import com.example.selftracker.models.Habit
import com.example.selftracker.models.HabitLog

@Database(
    entities = [Habit::class, HabitLog::class, Goal::class, GoalStep::class, GoalSubStep::class],
    version = 1,  // Start fresh with version 1
    exportSchema = false
)
abstract class SelfTrackerDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun goalDao(): GoalDao
    abstract fun goalStepDao(): GoalStepDao
    abstract fun goalSubStepDao(): GoalSubStepDao

    companion object {
        @Volatile
        private var INSTANCE: SelfTrackerDatabase? = null

        fun getDatabase(context: Context): SelfTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SelfTrackerDatabase::class.java,
                    "self_tracker_database"
                )
                    .fallbackToDestructiveMigration() // This will handle schema changes by recreating database
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}