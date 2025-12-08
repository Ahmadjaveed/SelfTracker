package com.example.selftracker.repository

import com.example.selftracker.BuildConfig
import com.example.selftracker.models.GeneratedGoal
import com.example.selftracker.models.GeneratedStep
import com.example.selftracker.models.PlanOption
import com.example.selftracker.models.PlanOptionsResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoalGeneratorRepository {

    // List of models to try in order of preference.
    // User has access to advanced/experimental models (2.0/2.5/3.0).
    // Prioritizing 2.0 Flash as it is likely stable enough for JSON tasks.
    private val candidateModels = listOf(
        "gemini-2.0-flash",
        "gemini-2.0-flash-exp",
        "gemini-2.5-flash",
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-pro"
    )

    private val apiKey = BuildConfig.GEMINI_API_KEY

    suspend fun generatePlanOptions(goal: String): List<PlanOption> {
        log("generatePlanOptions called for: $goal")
        if (apiKey.isBlank()) throw Exception("API Key is missing")

        val prompt = """
            I want to achieve: "$goal".
            Propose 3 distinct execution strategies (e.g., "Aggressive/Fast", "Balanced/Steady", "Relaxed/Long-term").
            
            Return ONLY valid JSON matching this structure:
            {
              "options": [
                {
                  "title": "Strategy Name",
                  "description": "Brief explanation of this approach (1 sentence).",
                  "strategy": "unique_id_for_strategy" 
                }
              ]
            }
        """.trimIndent()

        log("Requesting Options Prompt: $prompt")
        
        val responseText = generateContentWithFallback(prompt)
        val parsed = parseJson<PlanOptionsResponse>(responseText)
        return parsed?.options ?: emptyList()
    }

    suspend fun generateGoal(userPrompt: String, strategy: String? = null): GeneratedGoal {
        log("generateGoal called for: $userPrompt, strategy: $strategy")
        if (apiKey.isBlank()) throw Exception("API Key is missing")

        val strategyContext = if (strategy != null) "Use the '$strategy' strategy." else ""
        val fullPrompt = """
            You are the Goal Architect. Create a structured goal plan for: "$userPrompt".
            $strategyContext
            
            IMPORTANT: Return ONLY valid JSON matching this structure. Do not include markdown formatting or explanation text.
            {
              "goal_title": "string",
              "steps": [
                {
                  "step_name": "string",
                  "duration_value": int,
                  "duration_unit": "days", // or weeks/months
                  "substeps": ["string", "string"] // optional
                }
              ]
            }
        """.trimIndent()

        log("Requesting Goal Prompt: $fullPrompt")
        
        val responseText = generateContentWithFallback(fullPrompt)
        return parseJson<GeneratedGoal>(responseText) ?: throw Exception("Failed to parse AI response")
    }

    suspend fun enhanceGoalDescription(input: String): String {
        log("enhanceGoalDescription called: $input")
        if (apiKey.isBlank()) throw Exception("API Key is missing")

        val prompt = """
            Task: Rewrite the following goal description to be more inspiring and actionable.
            Input: "$input"
            Constraint: Output ONLY the rewritten sentence. Do not add quotes. Do not say "Here is the rewritten goal".
        """.trimIndent()
        
        log("Requesting Enhance Prompt: $prompt")
        
        var text = generateContentWithFallback(prompt)
        log("Raw Enhance Response: $text")
        
        // Aggressive cleanup
        text = text.replace("\"", "") // Remove quotes
        if (text.contains(":")) {
            text = text.substringAfter(":").trim()
        }
        
        return text
    }

    /**
     * Tries to generate content using a list of models. Returns the first successful response.
     */
    private suspend fun generateContentWithFallback(prompt: String): String {
        var lastException: Exception? = null
        
        for (modelName in candidateModels) {
            try {
                log("Attempting generation with model: $modelName")
                val generativeModel = com.google.ai.client.generativeai.GenerativeModel(
                    modelName = modelName,
                    apiKey = apiKey,
                    safetySettings = listOf(
                        com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.HARASSMENT, com.google.ai.client.generativeai.type.BlockThreshold.ONLY_HIGH),
                        com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.HATE_SPEECH, com.google.ai.client.generativeai.type.BlockThreshold.ONLY_HIGH),
                        com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.SEXUALLY_EXPLICIT, com.google.ai.client.generativeai.type.BlockThreshold.ONLY_HIGH),
                        com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.DANGEROUS_CONTENT, com.google.ai.client.generativeai.type.BlockThreshold.ONLY_HIGH)
                    )
                )
                
                val response = generativeModel.generateContent(prompt)
                val text = response.text
                if (text != null) {
                    log("Success with model: $modelName")
                    return text
                } else {
                    log("Model $modelName returned null text, trying next...")
                }
            } catch (e: Exception) {
                log("Failed with model: $modelName. Error: ${e.message}")
                lastException = e
                // Continue to next model
            }
        }
        
        // If we get here, all models failed
        logError("All models failed.", lastException ?: Exception("Unknown error"))
        throw lastException ?: Exception("All AI models failed to respond. Please check API Key.")
    }

    private inline fun <reified T> parseJson(jsonString: String): T? {
        return try {
            // cleanup markdown
            var clean = jsonString.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
                
            // heuristic: find first '{' and last '}'
            val firstBrace = clean.indexOf('{')
            val lastBrace = clean.lastIndexOf('}')
            
            if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
                clean = clean.substring(firstBrace, lastBrace + 1)
            }
            
            log("Cleaned JSON for parsing: $clean")
            Gson().fromJson(clean, T::class.java)
        } catch (e: Exception) {
            logError("JSON Parse Error on: $jsonString", e)
            null
        }
    }
    
    private fun log(message: String) {
        android.util.Log.d("GoalGenerator", message)
    }

    private fun logError(message: String, e: Exception) {
        android.util.Log.e("GoalGenerator", message, e)
    }
}
