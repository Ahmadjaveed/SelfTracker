package com.example.selftracker.repository

import com.example.selftracker.BuildConfig
import com.example.selftracker.models.GeneratedGoal
import com.example.selftracker.models.GeneratedStep
import com.example.selftracker.models.PlanOption
import com.example.selftracker.models.PlanOptionsResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class GoalGeneratorRepository {

    // List of models to try in order of preference.
    // Update: Removed specific versions that were 404ing. Kept generic aliases.
    // List of models to try in order of preference.
    // List of models to try in order of preference.
    private val candidateModels = listOf(
        "gemini-2.5-flash",       // Priority 1
        "gemini-2.5-flash-lite",  // Priority 2
        "gemini-2.0-flash-exp",   // Priority 3
        "gemini-1.5-flash"        // Backup
    )

    private val apiKey = BuildConfig.GEMINI_API_KEY

    suspend fun generatePlanOptions(goal: String): List<PlanOption> {
        log("generatePlanOptions called for: $goal")
        if (apiKey.isBlank()) {
            logError("API Key missing, using local fallback.", Exception("Missing API Key"))
            return generateLocalPlanOptions(goal)
        }

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
        
        return try {
            val responseText = generateContentWithFallback(prompt)
            val parsed = parseJson<PlanOptionsResponse>(responseText)
            parsed?.options ?: generateLocalPlanOptions(goal)
        } catch (e: Exception) {
            logError("AI Generation Failed for Options, using fallback.", e)
            generateLocalPlanOptions(goal)
        }
    }

    suspend fun generateGoal(userPrompt: String, strategy: String? = null): GeneratedGoal {
        log("generateGoal called for: $userPrompt, strategy: $strategy")
        // Note: We don't throw immediately on missing key, we try fallback.
        
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
        
        return try {
            if (apiKey.isBlank()) throw Exception("API Key is missing")
            val responseText = generateContentWithFallback(fullPrompt)
            parseJson<GeneratedGoal>(responseText) ?: generateLocalGoal(userPrompt)
        } catch (e: Exception) {
             logError("AI Generation Failed for Goal, using fallback.", e)
             generateLocalGoal(userPrompt)
        }
    }

    suspend fun enhanceGoalDescription(input: String): String {
        log("enhanceGoalDescription called: $input")
        
        return try {
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
            text
        } catch (e: Exception) {
            logError("Enhance failed, returning input.", e)
            input
        }
    }

    suspend fun generateGoalIcon(goalName: String): String {
        log("generateGoalIcon called for: $goalName")
         
         return try {
             if (apiKey.isBlank()) throw Exception("API Key is missing")
             val prompt = """
                 Generate a modern, flat SVG icon for: "$goalName".
                 
                 Constraints:
                 - Format: SVG 1.1
                 - Style: Minimalist, flat, solid colors (no gradients).
                 - Viewport: 24x24
                 - Colors: Use mostly distinct colors like #FF6B6B (Red), #4ECDC4 (Teal), #FFE66D (Yellow), #1A535C (Dark Blue).
                 - Output: ONLY the pure SVG code starting with <svg> and ending with </svg>. No markdown.
             """.trimIndent()
             
             log("Requesting Icon generation...")
             var svg = generateContentWithFallback(prompt)
             
             // Cleanup Markdown
             if (svg.contains("```svg")) {
                 svg = svg.substringAfter("```svg").substringBefore("```")
             } else if (svg.contains("```xml")) {
                 svg = svg.substringAfter("```xml").substringBefore("```")
             } else if (svg.contains("```")) {
                svg = svg.substringAfter("```").substringBefore("```")
             }
             
             // Ensure we only have the SVG content
             val startIndex = svg.indexOf("<svg")
             val endIndex = svg.lastIndexOf("</svg>")
             
             if (startIndex != -1 && endIndex != -1) {
                 svg = svg.substring(startIndex, endIndex + 6)
             }
    
             svg.trim()
         } catch (e: Exception) {
             logError("Icon generation failed, using local fallback.", e)
             generateLocalIcon()
         }
    }

    suspend fun generateMotivation(habit: String): String {
        log("generateMotivation called for: $habit")
        
        return try {
            if (apiKey.isBlank()) throw Exception("API Key is missing")
            val prompt = "Write a short, punchy (max 12 words) notification reminder to do '$habit' right now. Be motivating, friendly, and urgent. Do not use quotes."
            
            var text = generateContentWithFallback(prompt)
            text.replace("\"", "").trim()
        } catch (e: Exception) {
            "Time to work on $habit! You got this."
        }
    }

    private val openRouterApiKey = "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"
    private val client = okhttp3.OkHttpClient()

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
        
        // Use Fallback if Gemini failed
        if (quotaExceeded || lastException != null) {
            log("Gemini failed/quota exceeded. Switch to OpenRouter Fallback.")
            try {
                return generateWithOpenRouter(prompt)
            } catch (e: Exception) {
                logError("OpenRouter Fallback failed too.", e)
            }
        }
        
        val finalError = lastException?.message ?: "Unknown error"
        logError("All models failed. Last error: $finalError", lastException ?: Exception())
        
        if (finalError.contains("API Key Leaked")) {
             throw Exception("Your API Key has been flagged as leaked. Please generate a new one.")
        }
        
        // Generic fallback error
        throw Exception("AI Generation failed. Please check your connection.")
    }
    private suspend fun generateWithOpenRouter(prompt: String): String {
        val fallbackModels = listOf(
            "google/gemini-2.0-flash-exp:free",
            "google/gemini-2.0-flash-thinking-exp:free",
            "meta-llama/llama-3.1-8b-instruct:free",
            "mistralai/mistral-7b-instruct:free",
            "microsoft/phi-3-medium-128k-instruct:free"
        )

        var lastError: Exception? = null

        for (model in fallbackModels) {
            try {
                log("Fallback: Attempting OpenRouter with model: $model")
                return withContext(Dispatchers.IO) {
                    val json = org.json.JSONObject()
                    json.put("model", model) 
                    
                    val messages = org.json.JSONArray()
                    val message = org.json.JSONObject()
                    message.put("role", "user")
                    message.put("content", prompt)
                    messages.put(message)
                    
                    json.put("messages", messages)
                    
                    // Add existing headers + potentially more if needed
                    val requestBuilder = okhttp3.Request.Builder()
                        .url("https://openrouter.ai/api/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $openRouterApiKey")
                        .addHeader("HTTP-Referer", "http://localhost") 
                        .addHeader("X-Title", "SelfTracker")
                    
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = json.toString().toRequestBody(mediaType)
                    
                    val request = requestBuilder.post(body).build()
                        
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string()
                            throw Exception("OpenRouter Error: ${response.code} - $errorBody")
                        }
                        
                        val responseBody = response.body?.string() ?: throw Exception("Empty response from OpenRouter")
                        val responseJson = org.json.JSONObject(responseBody)
                        
                        // Check for error in JSON body even if 200 OK (some APIs do this, though OpenRouter usually uses status codes)
                        if (responseJson.has("error")) {
                             val errObj = responseJson.getJSONObject("error")
                             throw Exception("OpenRouter API Error: ${errObj.optString("message")}")
                        }

                        val choices = responseJson.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val firstChoice = choices.getJSONObject(0)
                            val messageObj = firstChoice.getJSONObject("message")
                            return@use messageObj.getString("content")
                        } else {
                            throw Exception("No content in OpenRouter response")
                        }
                    }
                }
            } catch (e: Exception) {
                log("Fallback model $model failed: ${e.message}")
                lastError = e
                // Continue to next fallback model
            }
        }
        
        throw lastError ?: Exception("All OpenRouter fallback models failed.")
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

    // --- Local Fail-Safe Methods ---

    private fun generateLocalPlanOptions(goal: String): List<PlanOption> {
        return listOf(
            PlanOption("Steady Paced", "A balanced approach to achieving '$goal' consistently.", "balanced"),
            PlanOption("Aggressive Sprint", "Fast-tracked plan to hit '$goal' as quickly as possible.", "fast"),
            PlanOption("Relaxed & Steady", "A low-pressure way to integrate '$goal' into your life.", "relaxed")
        )
    }

    private fun generateLocalGoal(prompt: String): GeneratedGoal {
        // Create a generic roadmap
        val steps = listOf(
            GeneratedStep(
                stepName = "Phase 1: Getting Started",
                description = "Initial preparation and setting up the foundation for $prompt.",
                durationValue = 5,
                durationUnit = "days",
                substeps = listOf(
                    com.example.selftracker.models.GeneratedSubStep(name = "Research and gather resources", durationValue = 2, durationUnit = "days"),
                    com.example.selftracker.models.GeneratedSubStep(name = "Set specific milestones", durationValue = 1, durationUnit = "days"),
                    com.example.selftracker.models.GeneratedSubStep(name = "Execute first small action", durationValue = 2, durationUnit = "days")
                )
            ),
            GeneratedStep(
                stepName = "Phase 2: Core Execution",
                description = "Main work period to develop the habit or skill.",
                durationValue = 2,
                durationUnit = "weeks",
                substeps = listOf(
                    com.example.selftracker.models.GeneratedSubStep(name = "Daily practice/work session", durationValue = 1, durationUnit = "days"),
                    com.example.selftracker.models.GeneratedSubStep(name = "Weekly review of progress", durationValue = 1, durationUnit = "days")
                )
            ),
             GeneratedStep(
                stepName = "Phase 3: Refinement",
                description = "Polishing and ensuring long-term success.",
                durationValue = 1,
                durationUnit = "weeks",
                substeps = emptyList()
            )
        )
        return GeneratedGoal(
            goalTitle = prompt.replaceFirstChar { it.uppercase() },
            steps = steps
        )
    }

    private fun generateLocalIcon(): String {
        // A simple generic target/bullseye SVG
        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#4ECDC4" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10" />
              <circle cx="12" cy="12" r="6" />
              <circle cx="12" cy="12" r="2" />
            </svg>
        """.trimIndent()
    }
}

