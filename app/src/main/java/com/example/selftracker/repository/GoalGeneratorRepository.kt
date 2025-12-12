package com.example.selftracker.repository

import com.example.selftracker.BuildConfig
import com.example.selftracker.models.GeneratedGoal
import com.example.selftracker.models.GeneratedResource
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
    // List of models to try in order of preference.
    companion object {
        // List of models to try in order of preference.
        // Static to persist across instances so we remember failures.
        private val candidateModels = listOf(
            "gemini-2.0-flash-exp",
            "gemini-1.5-flash", 
            "gemini-1.5-flash-8b",
            "gemini-1.5-pro-002",
            "gemini-1.5-flash-latest",
            "gemini-1.5-pro"
        )
        
        // Track failed models to skip them in future requests for performance
        private val failedModels = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    }

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
            // If parsed is null, we throw to signal AI failure, do NOT use local fallback
            parsed?.options ?: throw Exception("AI returned empty options")
        } catch (e: Exception) {
            logError("AI Generation Failed for Options", e)
            throw e // Propagate error to UI so user knows AI failed
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
                  "description": "string",
                  "duration_value": 0,
                  "duration_unit": "days",
                  "substeps": [
                    {
                      "substep_name": "string",
                      "duration_value": 0, 
                      "duration_unit": "days",
                      "resources": [ {"title": "string", "url": "string", "type": "string"} ]
                    }
                  ], 
                  "resources": [
                    {
                      "title": "string",
                      "url": "string",
                      "type": "string"
                    }
                  ]
                }
              ],
              "resources": [
                {
                  "title": "string",
                  "url": "string",
                  "type": "string"
                }
              ]
            }
        """.trimIndent()

        log("Requesting Goal Prompt: $fullPrompt")
        
        return try {
            if (apiKey.isBlank()) throw Exception("API Key is missing")
            val responseText = generateContentWithFallback(fullPrompt)
            // If parse fails, throw exception, do NOT use local fallback
            parseJson<GeneratedGoal>(responseText) ?: throw Exception("AI returned invalid goal JSON")
        } catch (e: Exception) {
             logError("AI Generation Failed for Goal", e)
             // Propagate error to UI
             throw e
        }
    }

    private fun downloadUrlContent(url: String): String? {
        return try {
            val request = okhttp3.Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logError("Error downloading content from $url", e)
            null
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

    suspend fun generateGoalIcon(goalName: String, description: String = ""): String {
        log("generateGoalIcon called for: $goalName")
        
        // 1. Devicon Keyword Match (Priority 1 - Deterministic & High Quality for Tech)
        val devIconUrl = findDeviconUrl(goalName)
        if (devIconUrl != null) {
            log("Found Devicon URL: $devIconUrl. Downloading content...")
            // Download the SVG content to return it as a raw string
            // This allows GoalsFragment to save it locally and render specifically with AndroidSVG
            // avoiding Glide's issues with SVG URLs.
            val svgContent = downloadUrlContent(devIconUrl)
            if (svgContent != null) {
                return svgContent
            } else {
                 logError("Failed to download Devicon content", Exception("Download failed"))
            }
        }

        // 1.5. Direct CDN Fallback (Priority 1.5 - Fast & High Quality)
        // Try to guess the slug directly (e.g. "Google" -> "google") from SimpleIcons/Devicon
        val cdnIconUrl = findDirectCdnIcon(goalName)
        if (cdnIconUrl != null) {
            log("Found Direct CDN Icon: $cdnIconUrl")
            // Download content immediately to cache/save it as SVG
            val svgContent = downloadUrlContent(cdnIconUrl)
            if (svgContent != null) return svgContent
        }

        // 2. AI Smart Domain Prediction (Priority 2) - REMOVED strictly per user request (was using Clearbit)
        // We skip trying to blindly guess domains to avoid blurry raster images.

        // 3. Fallback to Standard Scraping (Name-based) - REMOVED (was using Clearbit Autocomplete)
         
        // 4. Last Resort: Local Generic Icon
        return generateLocalIcon()
    }

    private fun findDeviconUrl(query: String): String? {
        val lowerQuery = query.lowercase()
        // Simple token matching
        val tokens = lowerQuery.split(" ", "-", "_", ".")
        
        for (token in tokens) {
            if (DevIconMap.icons.containsKey(token)) {
                val iconName = DevIconMap.icons[token]
                // Construct URL: https://cdn.jsdelivr.net/gh/devicons/devicon/icons/[KEYWORD]/[KEYWORD]-original.svg
                return "https://cdn.jsdelivr.net/gh/devicons/devicon/icons/$iconName/$iconName-original.svg"
            }
        }
        return null
    }

    private suspend fun predictDomainWithAI(name: String, description: String = ""): String? {
        log("predictDomainWithAI called for: $name")
        if (apiKey.isBlank()) return null

        val prompt = """
            Identify the single most relevant OFFICIAL website domain for the goal: "$name".
            Context: "$description".
            
            Rules:
            1. Return ONLY the domain string (e.g., "python.org", "figma.com", "nike.com").
            2. If it's a generic activity (e.g. "Drink Water") with no specific brand/tool, return "null".
            3. Prefer the official product/organization page.
            4. Do NOT return full URLs, just the domain.
            
            Examples:
            "Learn to Code Python" -> "python.org"
            "Use Notion for notes" -> "notion.so"
            "Morning Jog" -> "null"
        """.trimIndent()
        
        return try {
            val responseText = generateWithOpenRouter(prompt)
            val domain = responseText.trim().lowercase()
            if (domain.contains("null", ignoreCase = true) || !domain.contains(".")) {
                null
            } else {
                domain.replace("https://", "")
                      .replace("http://", "")
                      .replace("/", "")
                      .removePrefix("www.")
            }
        } catch (e: Exception) {
            logError("AI Domain Prediction Error", e)
            null
        }
    }

    suspend fun scrapeLogoUrl(query: String): String? {
        // Use Clearbit Autocomplete API to find brand logo

    // Use Clearbit Autocomplete API to find brand logo
    val cleanQuery = query.trim()
        
        suspend fun fetch(q: String): String? {
             return withContext(Dispatchers.IO) {
                 try {
                     val request = okhttp3.Request.Builder()
                        .url("https://autocomplete.clearbit.com/v1/companies/suggest?query=${java.net.URLEncoder.encode(q, "UTF-8")}")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .build()
                     
                     client.newCall(request).execute().use { response ->
                         android.util.Log.e("LogoScraper", "Response: ${response.code} for $q")
                         if (response.isSuccessful) {
                             val json = response.body?.string()
                             android.util.Log.e("LogoScraper", "JSON: $json")
                             
                             if (!json.isNullOrEmpty()) {
                                 val array = org.json.JSONArray(json)
                                 if (array.length() > 0) {
                                     val firstMatch = array.getJSONObject(0)
                                     val meta = firstMatch
                                     val logo = meta.optString("logo")
                                     val domain = meta.optString("domain")
                                     
                                     // Priority 1: Try High-Res (512px) Clearbit URL from Domain
                                     if (domain.isNotEmpty() && domain != "null") {
                                         val highResUrl = "https://logo.clearbit.com/$domain?size=512"
                                         var highResWorks = false
                                         try {
                                             val hrRequest = okhttp3.Request.Builder().url(highResUrl).head().build()
                                             client.newCall(hrRequest).execute().use { 
                                                 if (it.isSuccessful) highResWorks = true 
                                             }
                                         } catch (e: Exception) { 
                                              android.util.Log.e("LogoScraper", "High-res check failed", e)
                                         }
                                         
                                         if (highResWorks) {
                                             android.util.Log.e("LogoScraper", "Found High-Res Logo (Clearbit): $highResUrl")
                                             return@use highResUrl
                                         }
                                     }

                                     // Priority 2: Return standard logo from JSON (usually 128px)
                                     if (logo.isNotEmpty() && logo != "null") {
                                          android.util.Log.e("LogoScraper", "Found Logo (Standard): $logo")
                                          return@use logo
                                     }
                                     
                                     // Priority 3: Fallback to Google High-Res
                                     // Only reached if High-Res Clearbit failed AND no standard logo was provided.
                                     if (domain.isNotEmpty() && domain != "null") {
                                         val favUrl = "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=http://$domain&size=512"
                                         android.util.Log.e("LogoScraper", "Found Domain (Google Fallback): $domain -> $favUrl")
                                         return@use favUrl
                                     }
                                 } else {
                                     android.util.Log.e("LogoScraper", "Empty Array")
                                 }
                             } else {
                                 android.util.Log.e("LogoScraper", "Empty JSON")
                             }
                         } else {
                             android.util.Log.e("LogoScraper", "Request Failed: ${response.message}")
                         }
                         null
                     }
                 } catch (e: Exception) {
                     logError("Clearbit scraping error for $q", e)
                     null
                 }
            }
        }
        
        // 1. Try exact query
        var result = fetch(cleanQuery)
        if (result != null) return result
        
        // 2. Try cleaning common verbs (Learn, Master, Study, Practice)
        val verbs = listOf("Learn", "Master", "Study", "Practice", "Become", "Get")
        var modifiedQuery = cleanQuery
        var changed = false
        for (verb in verbs) {
            if (modifiedQuery.startsWith("$verb ", ignoreCase = true)) {
                modifiedQuery = modifiedQuery.substring(verb.length + 1)
                changed = true
            }
        }
        
        if (changed && modifiedQuery.isNotEmpty()) {
            log("Retrying scraping with cleaned query: $modifiedQuery")
            result = fetch(modifiedQuery)
            if (result != null) return result
        }

        // 3. BLIND GUESS FALLBACK
        // If "Python" gives nothing, assume "python.com" and try getting logo from Clearbit directly
        // https://logo.clearbit.com/{domain}
        val guessDomain = modifiedQuery.replace(" ", "").lowercase() + ".com"
        val guessUrl = "https://logo.clearbit.com/$guessDomain?size=512"
        log("Attempting blind guess: $guessUrl")
        
        // We verify if this URL exists by doing a HEAD request (optional, or just return it and let Glide handle 404)
        // Since we have strict logging, let's verify it quickly
         return withContext(Dispatchers.IO) {
            try {
                val headRequest = okhttp3.Request.Builder().url(guessUrl).head().build()
                client.newCall(headRequest).execute().use { 
                    if (it.isSuccessful) guessUrl else null 
                }
            } catch (e: Exception) { null }
        }
    }

    private suspend fun findDirectCdnIcon(query: String): String? {
        val slug = query.lowercase().replace(" ", "").replace(".", "dot").replace("++", "plusplus").replace("#", "sharp")
        // Common mappings
        val finalSlug = when(slug) {
             "c++" -> "cplusplus"
             "c#" -> "csharp"
             "nodejs" -> "nodedotjs"
             else -> slug
        }
        
        // 1. Try Simple Icons (very comprehensive for brands)
        // https://cdn.jsdelivr.net/npm/simple-icons@v14/icons/[slug].svg
        val simpleIconUrl = "https://cdn.jsdelivr.net/npm/simple-icons@v14/icons/$finalSlug.svg"
        
        // 2. Try Devicon (great for tech)
        // https://cdn.jsdelivr.net/gh/devicons/devicon/icons/[slug]/[slug]-original.svg
        val devIconUrl = "https://cdn.jsdelivr.net/gh/devicons/devicon/icons/$finalSlug/$finalSlug-original.svg"
        
        return withContext(Dispatchers.IO) {
            try {
                // 1. Try Devicon (Best for tech, colored)
                // https://cdn.jsdelivr.net/gh/devicons/devicon/icons/[slug]/[slug]-original.svg
                val devIconUrl = "https://cdn.jsdelivr.net/gh/devicons/devicon/icons/$finalSlug/$finalSlug-original.svg"
                val request1 = okhttp3.Request.Builder().url(devIconUrl).head().build()
                val exists1 = client.newCall(request1).execute().use { it.isSuccessful }
                if (exists1) return@withContext devIconUrl

                // 2. Try Vector Logo Zone (Best for brands, colored)
                // https://www.vectorlogo.zone/logos/[slug]/[slug]-icon.svg
                val vectorLogoUrl = "https://www.vectorlogo.zone/logos/$finalSlug/$finalSlug-icon.svg"
                val request2 = okhttp3.Request.Builder().url(vectorLogoUrl).head().build()
                val exists2 = client.newCall(request2).execute().use { it.isSuccessful }
                if (exists2) return@withContext vectorLogoUrl
                
                // 2b. Try Vector Logo Zone (Official variant)
                val vectorLogoUrl2 = "https://www.vectorlogo.zone/logos/$finalSlug/$finalSlug-official.svg"
                val request2b = okhttp3.Request.Builder().url(vectorLogoUrl2).head().build()
                val exists2b = client.newCall(request2b).execute().use { it.isSuccessful }
                if (exists2b) return@withContext vectorLogoUrl2

                // 3. Try FlagCDN (for Country Codes e.g. "US", "FR", "IN")
                if (finalSlug.length == 2) {
                     val flagUrl = "https://flagcdn.com/${finalSlug}.svg"
                     val requestFlag = okhttp3.Request.Builder().url(flagUrl).head().build()
                     val existsFlag = client.newCall(requestFlag).execute().use { it.isSuccessful }
                     if (existsFlag) return@withContext flagUrl
                }

                // 4. Try Simple Icons (Great coverage but Monochrome/Black)
                // https://cdn.jsdelivr.net/npm/simple-icons@v14/icons/[slug].svg
                val simpleIconUrl = "https://cdn.jsdelivr.net/npm/simple-icons@v14/icons/$finalSlug.svg"
                val request4 = okhttp3.Request.Builder().url(simpleIconUrl).head().build()
                val exists4 = client.newCall(request4).execute().use { it.isSuccessful }
                if (exists4) return@withContext simpleIconUrl
                
                null
            } catch (e: Exception) {
               null
            }
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

    private val openRouterApiKeys = listOf(
        BuildConfig.OPENROUTER_API_KEY_1, // Primary
        BuildConfig.OPENROUTER_API_KEY_2  // Fallback
    )
    private val client = okhttp3.OkHttpClient()

    /**
     * Tries to generate content using a list of models. Returns the first successful response.
     */
    private suspend fun generateContentWithFallback(prompt: String): String {
        var lastException: Exception? = null
        var quotaExceeded = false
        
        for (modelName in candidateModels) {
            if (failedModels.contains(modelName)) {
                log("Skipping known failed model: $modelName")
                continue
            }

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
                
                if (errorMessage.contains("Quota exceeded") || errorMessage.contains("429") || errorMessage.contains("404")) {
                     logError("Marking model as failed: $modelName", e)
                     failedModels.add(modelName)
                }
                
                lastException = e
                // Continue to next Gemini model
            }
        }
        
        // If all Gemini models failed, try OpenRouter Fallback
        logError("All Gemini models failed. Attempting OpenRouter Fallback.", lastException ?: Exception("Unknown error"))
        
        try {
            return generateWithOpenRouter(prompt)
        } catch (e: Exception) {
             logError("OpenRouter Fallback also failed.", e)
             throw e // Throw to trigger local hardcoded fallback in the caller
        }
    }

    private suspend fun generateWithGoogle(modelName: String, prompt: String): String {
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
            throw Exception("Model $modelName returned null text.")
        }
    }

    private suspend fun generateWithOpenRouter(prompt: String): String {
        val fallbackModels = listOf(
            // Tier 1: High quality free models (Try these first)
            "google/gemini-2.0-flash-exp:free",
            "meta-llama/llama-3.2-3b-instruct:free", // Fast & reliable
            "liquid/lfm-40b:free", // High quality, often available
            
            // Tier 2: Larger experimental models (Good but maybe rate limited)
            "meta-llama/llama-3.3-70b-instruct:free",
            "nvidia/nemotron-nano-12b-v2-vl:free",
            "nex-agi/deepseek-v3.1-nex-n1:free",
            "allenai/olmo-3-32b-think:free",
            
            // Tier 3: Solid backups
            "mistralai/mistral-7b-instruct:free",
            "nousresearch/hermes-3-llama-3.1-405b:free"
        )

        var lastError: Exception? = null
        val badKeys = mutableSetOf<Int>() // Keys that are definitely dead (401/402)

        for (model in fallbackModels) {
            // Try all valid keys for this model
            var attemptsForModel = 0
            for (i in openRouterApiKeys.indices) {
                if (badKeys.contains(i)) continue // Skip permanently failed keys

                val currentApiKey = openRouterApiKeys[i]
                try {
                    log("Fallback: Attempting OpenRouter with model: $model (KeyIdx: $i)")
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
                            .addHeader("Authorization", "Bearer $currentApiKey")
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
                            
                            // Check for error in JSON body even if 200 OK
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
                    val msg = e.message ?: ""
                    lastError = e
                    attemptsForModel++

                    // Analyze error type
                    if (msg.contains("401") || msg.contains("402") || msg.contains("Quota", ignoreCase = true)) {
                        log("Key $i is DEAD (Quota/Auth). Adding to badKeys. ($msg)")
                        badKeys.add(i)
                    } else if (msg.contains("429")) {
                        log("Key $i Rate Limited on model $model. Trying next key...")
                        // Do not add to badKeys, just try next key for this model.
                    } else {
                        // 404, 500, etc. -> Model issue. 
                        log("Model $model failed with non-key error: $msg. Skipping model.")
                        break // Stop trying keys for this model, move to next model
                    }
                }
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

    private fun generateLocalGoal(prompt: String, errorReason: String? = null): GeneratedGoal {
        // Debugging: If error exists, append to title (temporary for diagnosis)
        val debugTitleSuffix = if (errorReason != null) " [Error: ${errorReason.take(20)}...]" else ""
        
        // Create a generic roadmap
        val steps = listOf(
            GeneratedStep(
                stepName = "Phase 1: Foundation",
                description = "Initial preparation and setting up the foundation for $prompt.",
                durationValue = 5,
                durationUnit = "days",
                resources = listOf(
                    GeneratedResource("Getting Started Guide", "https://en.wikipedia.org/wiki/${prompt.replace(" ", "_")}", "ARTICLE")
                ),
                substeps = listOf(
                    com.example.selftracker.models.GeneratedSubStep(
                        name = "Research and gather resources", 
                        durationValue = 2, 
                        durationUnit = "days",
                        resources = listOf(GeneratedResource("Research Tips", "https://www.wikihow.com/Research", "ARTICLE"))
                    ),
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
                    com.example.selftracker.models.GeneratedSubStep(name = "Daily practice session", durationValue = 1, durationUnit = "days"),
                    com.example.selftracker.models.GeneratedSubStep(name = "Weekly review", durationValue = 1, durationUnit = "days")
                )
            ),
             GeneratedStep(
                stepName = "Phase 3: Mastery",
                description = "Polishing and ensuring long-term success.",
                durationValue = 1,
                durationUnit = "weeks",
                substeps = emptyList()
            )
        )
        val resources = listOf(
            com.example.selftracker.models.GeneratedResource("Comprehensive Guide", "https://en.wikipedia.org/wiki/Special:Search?search=${prompt.replace(" ", "+")}", "ARTICLE"),
            // Use a valid Atomic Habits video as safe fallback
            com.example.selftracker.models.GeneratedResource("The Power of Tiny Gains", "https://www.youtube.com/watch?v=t_jHrUE5IOk", "VIDEO")
        )
        return GeneratedGoal(
            // Use the original clean prompt (first part of input) as title if we fell back
            goalTitle = prompt.substringBefore("."), 
            steps = steps,
            resources = resources
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
