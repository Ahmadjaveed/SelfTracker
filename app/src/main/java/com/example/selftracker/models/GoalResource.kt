package com.example.selftracker.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(tableName = "goal_resources",
    foreignKeys = [
        androidx.room.ForeignKey(entity = Goal::class, parentColumns = ["goalId"], childColumns = ["goalId"], onDelete = androidx.room.ForeignKey.CASCADE)
    ],
    indices = [androidx.room.Index("goalId"), androidx.room.Index("stepId"), androidx.room.Index("subStepId")]
)
data class GoalResource(
    @PrimaryKey(autoGenerate = true) val resourceId: Long = 0,
    val goalId: Long,
    val stepId: Long? = null, // New: Link to Step
    val subStepId: Long? = null, // New: Link to SubStep
    val title: String,
    val url: String,
    val resourceType: String, // VIDEO, ARTICLE, IMAGE, LINK
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
