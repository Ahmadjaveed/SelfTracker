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
    // Prioritizing stable 1.5 versions to avoid Quota issues with experimental models.
    private val candidateModels = listOf(
        "gemini-1.5-flash-001",
        "gemini-1.5-flash-002",
        "gemini-1.5-flash",
        "gemini-1.5-flash-latest",
        "gemini-1.5-pro-001",
        "gemini-1.5-pro",
        "gemini-pro",
        "gemini-2.0-flash-exp"
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
                  "description": "string", // Brief explanation of the step
                  "duration_value": int,
                  "duration_unit": "days", // or weeks/months
                  "substeps": [
                    {
                      "substep_name": "string",
                      "duration_value": int, 
                      "duration_unit": "days"
                    }
                  ] // optional
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

    suspend fun generateGoalIcon(goalName: String): String {
        log("generateGoalIcon called for: $goalName")
         if (apiKey.isBlank()) throw Exception("API Key is missing")
         
         val prompt = """
             Generate an Android Vector Drawable XML (API 24+) for a flat, modern, minimal icon representing: "$goalName".
             
             Constraints:
             - Viewport: 24x24
             - Style: Flat, solid fill (no gradients), modern.
             - Colors: Use mostly distinct colors like #FF6B6B (Red), #4ECDC4 (Teal), #FFE66D (Yellow), #1A535C (Dark Blue). Avoid broad transparency.
             - Output: ONLY the XML code. No markdown code fences. No explanation.
         """.trimIndent()
         
         log("Requesting Icon generation...")
         var xml = generateContentWithFallback(prompt)
         
         // Cleanup Markdown
         if (xml.contains("```xml")) {
             xml = xml.substringAfter("```xml").substringBefore("```")
         } else if (xml.contains("```")) {
            xml = xml.substringAfter("```").substringBefore("```")
         }
         
         // Ensure we only have the XML content
         val startIndex = xml.indexOf("<vector")
         val endIndex = xml.lastIndexOf("</vector>")
         
         if (startIndex != -1 && endIndex != -1) {
             xml = xml.substring(startIndex, endIndex + 9)
         }
         
         return xml.trim()
    }

    suspend fun generateMotivation(habit: String): String {
        log("generateMotivation called for: $habit")
        if (apiKey.isBlank()) throw Exception("API Key is missing")
        
        val prompt = "Write a short, punchy (max 12 words) notification reminder to do '$habit' right now. Be motivating, friendly, and urgent. Do not use quotes."
        
        var text = generateContentWithFallback(prompt)
        return text.replace("\"", "").trim()
    }

    /**
     * Tries to generate content using a list of models. Returns the first successful response.
     */
    private suspend fun generateContentWithFallback(prompt: String): String {
        var lastException: Exception? = null
        var quotaExceeded = false
        
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
                // Catch ALL exceptions to prevent crash, including Quota or Network errors
                val errorMessage = e.message ?: ""
                log("Failed with model: $modelName. Error: $errorMessage")
                
                if (errorMessage.contains("API key was reported as leaked")) {
                    logError("CRITICAL: API Key Leaked.", e)
                    throw Exception("API Key Leaked. Please update your API Key.") 
                }
                
                if (errorMessage.contains("Quota exceeded") || errorMessage.contains("429")) {
                     logError("Quota exceeded for model: $modelName", e)
                     quotaExceeded = true
                }
                
                lastException = e
                // Continue to next model
            }
        }
        
        // Prioritize Quota Error if it occurred and no models succeeded
        if (quotaExceeded) {
             throw Exception("AI Usage Limit reached. Please wait a moment and try again.")
        }
        
        val finalError = lastException?.message ?: "Unknown error"
        logError("All models failed. Last error: $finalError", lastException ?: Exception())
        
        if (finalError.contains("API Key Leaked")) {
             throw Exception("Your API Key has been flagged as leaked. Please generate a new one.")
        }
        
        // Generic fallback error
        throw Exception("AI Generation failed. Please check your connection.")
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
