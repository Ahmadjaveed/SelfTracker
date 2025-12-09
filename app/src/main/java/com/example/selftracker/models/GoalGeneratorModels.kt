package com.example.selftracker.models

import com.google.gson.annotations.SerializedName

data class GeneratedGoal(
    @SerializedName("goal_title") val goalTitle: String,
    @SerializedName("steps") val steps: List<GeneratedStep>
)

data class GeneratedStep(
    @SerializedName("step_name") val stepName: String,
    @SerializedName("description") val description: String,
    @SerializedName("duration_value") val durationValue: Int,
    @SerializedName("duration_unit") val durationUnit: String,
    @SerializedName("substeps") val substeps: List<GeneratedSubStep>? = null
)

data class GeneratedSubStep(
    @SerializedName("substep_name") val name: String,
    @SerializedName("duration_value") val durationValue: Int,
    @SerializedName("duration_unit") val durationUnit: String
)

data class PlanOption(
    val title: String,
    val description: String,
    val strategy: String // e.g. "aggressive", "balanced"
)

data class PlanOptionsResponse(
    val options: List<PlanOption>
)
