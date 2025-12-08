package com.example.selftracker.repository

import com.example.selftracker.BuildConfig
import com.example.selftracker.models.GeneratedGoal
import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting

class GoalGeneratorRepository {

    // Using "gemini-1.5-pro" as usually the standard latest pro model available via SDK.
    // User requested "Gemini 3 Pro", if that is available it can be swapped here.
    // For now defaulting to a known valid model string or "gemini-1.5-pro-latest"
    // Reverting to 1.5-pro as 1.5-flash caused "Unexpected Response" errors for user
    private val modelName = "gemini-1.5-pro" 
    
    private val apiKey = BuildConfig.GEMINI_API_KEY

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
             safetySettings = listOf(
                SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
                SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.ONLY_HIGH),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.ONLY_HIGH)
            )
        )
    }

    suspend fun generatePlanOptions(goal: String): List<com.example.selftracker.models.PlanOption> {
        log("generatePlanOptions called for: $goal")
        if (apiKey.isBlank()) throw Exception("API Key is missing")

        return withContext(Dispatchers.IO) {
            try {
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
                val response = generativeModel.generateContent(prompt)
                val text = response.text ?: throw Exception("Empty response")
                log("Raw Options Response: $text")

                val parsed = parseJson<com.example.selftracker.models.PlanOptionsResponse>(text)
                parsed?.options ?: emptyList()
            } catch (e: Exception) {
                logError("Failed to generate options", e)
                throw e
            }
        }
    }

    suspend fun generateGoal(userPrompt: String, strategy: String? = null): GeneratedGoal {
        log("generateGoal called for: $userPrompt, strategy: $strategy")
        if (apiKey.isBlank()) throw Exception("API Key is missing")

        return withContext(Dispatchers.IO) {
            try {
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
                val response = generativeModel.generateContent(fullPrompt)
                val responseText = response.text ?: throw Exception("Empty response from AI")
                log("Raw Goal Response: $responseText")

                parseJson<GeneratedGoal>(responseText) ?: throw Exception("Failed to parse AI response")
            } catch (e: Exception) {
                logError("Error generating goal", e)
                throw Exception("AI Error: ${e.message}", e)
            }
        }
    }

    suspend fun enhanceGoalDescription(input: String): String {
        log("enhanceGoalDescription called: $input")
        if (apiKey.isBlank()) throw Exception("API Key is missing")

        return withContext(Dispatchers.IO) {
            try {
                // Highly specific prompt to avoid "Sure!" or "Here is..."
                val prompt = """
                    Task: Rewrite the following goal description to be more inspiring and actionable.
                    Input: "$input"
                    Constraint: Output ONLY the rewritten sentence. Do not add quotes. Do not say "Here is the rewritten goal".
                """.trimIndent()
                
                log("Requesting Enhance Prompt: $prompt")
                val response = generativeModel.generateContent(prompt)
                var text = response.text?.trim() ?: throw Exception("Empty response from AI")
                log("Raw Enhance Response: $text")
                
                // Aggressive cleanup
                text = text.replace("\"", "") // Remove quotes
                if (text.contains(":")) {
                    text = text.substringAfter(":").trim()
                }
                
                text
            } catch (e: Exception) {
                logError("Error enhancing", e)
                throw Exception("AI Error: ${e.message}", e)
            }
        }
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
