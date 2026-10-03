package com.yourname.mymoney.ai

import android.content.Context
import android.util.Log
import com.yourname.mymoney.data.SecureApiKeyStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

sealed class AiEntryResult {
    data class TransactionResult(
        val type: String, // "INCOME" or "EXPENSE"
        val title: String,
        val amount: Double,
        val category: String,
        val note: String,
        val recurrence: String = "NONE"
    ) : AiEntryResult()

    data class LoanResult(
        val personName: String,
        val amount: Double,
        val type: String, // "LENT" or "BORROWED"
        val dueDateTimestamp: Long,
        val note: String
    ) : AiEntryResult()
}

data class MonthlyInsightsResult(
    val summary: String,
    val tips: List<String>
)

data class ReceiptScanResult(
    val title: String,
    val amount: Double,
    val category: String,
    val date: Long,
    val note: String
)

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val MODEL_NAME = "gemini-2.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"
    private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"
    const val API_KEY_NOT_CONFIGURED_MESSAGE =
        "Add your Gemini API key in Settings to use AI"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getStoredApiKey(context: Context): String? {
        val key = SecureApiKeyStorage.getApiKey(context)
        return if (key.isNullOrBlank()) null else key
    }

    /**
     * Tests the provided Gemini API key with a minimal request.
     */
    suspend fun testApiKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(Exception("API key cannot be empty."))
        }

        try {
            val responseText = generateContent(
                prompt = "Ping. Reply with 'OK'.",
                apiKey = trimmed,
                isJson = false
            )
            if (responseText.isNotBlank()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Gemini returned an empty response."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun parseNaturalLanguageEntry(
        context: Context,
        input: String,
        availableCategories: List<String>
    ): Result<AiEntryResult> = withContext(Dispatchers.IO) {
        val apiKey = getStoredApiKey(context)
            ?: return@withContext Result.failure(Exception(API_KEY_NOT_CONFIGURED_MESSAGE))

        val prompt = """
            You are a personal finance assistant that extracts structured financial data from natural language.
            Available categories: ${availableCategories.joinToString(", ")}.
            
            Input text: "$input"
            
            Classify whether this is:
            1. An "EXPENSE" (money spent on food, bills, shopping, travel, etc.)
            2. An "INCOME" (money earned, salary, bonus, refund, sales, gifts)
            3. A "LOAN" (money lent to someone, or borrowed from someone, e.g. "Gave Ahmed 50", "Lent Bob 20", "Borrowed 100 from Mom", "Owe Alice 30")
            
            Check if recurrence is mentioned (e.g. daily, weekly, monthly, every month, every week).
            
            Return ONLY a valid JSON object (no markdown, no backticks, no explanations) in this exact schema:
            {
              "classification": "EXPENSE" | "INCOME" | "LOAN",
              "title": string,
              "amount": number (positive float),
              "category": string (match one of available categories or "Other"),
              "note": string,
              "recurrence": "NONE" | "DAILY" | "WEEKLY" | "MONTHLY",
              "loanPersonName": string (only if LOAN, else empty string),
              "loanType": "LENT" | "BORROWED" (only if LOAN, "LENT" if I gave/lent money to someone, "BORROWED" if I borrowed from someone),
              "loanDueDateDaysFromNow": number (estimated days from now until due, default 14 if unspecified)
            }
        """.trimIndent()

        try {
            val responseText = generateContent(prompt, apiKey, isJson = true)
            val json = parseJsonObject(responseText)

            val classification = json.optString("classification", "EXPENSE").uppercase()
            val amount = json.optDouble("amount", 0.0)
            val title = json.optString("title", input).ifBlank { input }
            val category = json.optString("category", "Other").ifBlank { "Other" }
            val note = json.optString("note", "")
            val recurrence = json.optString("recurrence", "NONE").uppercase()

            if (classification == "LOAN") {
                val person = json.optString("loanPersonName", "Contact").ifBlank { "Contact" }
                val loanType = json.optString("loanType", "LENT").uppercase()
                val dueDays = json.optInt("loanDueDateDaysFromNow", 14)
                val dueDate = System.currentTimeMillis() + (dueDays.toLong() * 24 * 60 * 60 * 1000L)

                Result.success(
                    AiEntryResult.LoanResult(
                        personName = person,
                        amount = amount,
                        type = if (loanType == "BORROWED") "BORROWED" else "LENT",
                        dueDateTimestamp = dueDate,
                        note = note.ifBlank { input }
                    )
                )
            } else {
                val txType = if (classification == "INCOME") "INCOME" else "EXPENSE"
                Result.success(
                    AiEntryResult.TransactionResult(
                        type = txType,
                        title = title,
                        amount = amount,
                        category = category,
                        note = note,
                        recurrence = if (recurrence in listOf("DAILY", "WEEKLY", "MONTHLY")) recurrence else "NONE"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse natural language entry", e)
            Result.failure(e)
        }
    }

    suspend fun scanReceipt(
        context: Context,
        base64Jpeg: String,
        availableCategories: List<String>
    ): Result<ReceiptScanResult> = withContext(Dispatchers.IO) {
        val apiKey = getStoredApiKey(context)
            ?: return@withContext Result.failure(Exception(API_KEY_NOT_CONFIGURED_MESSAGE))

        val prompt = """
            You are an expert receipt OCR and transaction extractor for personal finance.
            Analyze this receipt image. Extract:
            1. Merchant name / store as title (e.g. "Trader Joe's", "Shell Oil", "Starbucks")
            2. Final total amount (number, positive float)
            3. Primary category (one of: ${availableCategories.joinToString(", ")} or "Food", "Bills", "Shopping", "Transport", "Health", "Other")
            4. Date if printed on receipt (timestamp millis), otherwise current date.
            5. Note listing 2-3 key items purchased.
            
            Return ONLY a valid JSON object (no markdown, no backticks):
            {
              "title": string,
              "amount": number,
              "category": string,
              "dateMillis": number,
              "note": string
            }
        """.trimIndent()

        try {
            val responseText = generateContent(prompt, apiKey, isJson = true, imageBase64 = base64Jpeg)
            val json = parseJsonObject(responseText)

            val title = json.optString("title", "Scanned Receipt").ifBlank { "Scanned Receipt" }
            val amount = json.optDouble("amount", 0.0)
            val category = json.optString("category", "Food").ifBlank { "Food" }
            val date = json.optLong("dateMillis", System.currentTimeMillis()).let {
                if (it <= 0) System.currentTimeMillis() else it
            }
            val note = json.optString("note", "Scanned with Gemini")

            Result.success(ReceiptScanResult(title, amount, category, date, note))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan receipt", e)
            Result.failure(e)
        }
    }

    suspend fun askFinancialQuestion(
        context: Context,
        question: String,
        financialContext: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getStoredApiKey(context)
            ?: return@withContext Result.failure(Exception(API_KEY_NOT_CONFIGURED_MESSAGE))

        val prompt = """
            You are an expert personal financial advisor for the user's MyMoney app.
            Answer the user's question accurately and helpfully using ONLY their provided financial data.
            If calculating sums or averages, be mathematically precise.
            Keep answers friendly, concise, and formatted clearly with bullet points or bold text where appropriate.
            
            USER'S FINANCIAL DATA:
            $financialContext
            
            USER'S QUESTION:
            "$question"
        """.trimIndent()

        try {
            val response = generateContent(prompt, apiKey, isJson = false)
            Result.success(response.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to answer question", e)
            Result.failure(e)
        }
    }

    suspend fun getMonthlyInsights(
        context: Context,
        financialContext: String
    ): Result<MonthlyInsightsResult> = withContext(Dispatchers.IO) {
        val apiKey = getStoredApiKey(context)
            ?: return@withContext Result.failure(Exception(API_KEY_NOT_CONFIGURED_MESSAGE))

        val prompt = """
            You are a personal finance intelligence engine.
            Analyze the user's monthly spending and cashflow data:
            $financialContext
            
            Generate:
            1. A concise 2-sentence summary of their current financial performance and spending patterns.
            2. Exactly 3 personalized, realistic, and actionable saving tips based on their actual expenses and budgets.
            
            Return ONLY a valid JSON object (no markdown, no backticks):
            {
              "summary": string,
              "tips": [string, string, string]
            }
        """.trimIndent()

        try {
            val responseText = generateContent(prompt, apiKey, isJson = true)
            val json = parseJsonObject(responseText)

            val summary = json.optString("summary", "Your finances are steady this month.")
            val tipsArray = json.optJSONArray("tips")
            val tips = mutableListOf<String>()
            if (tipsArray != null) {
                for (i in 0 until tipsArray.length()) {
                    tips.add(tipsArray.optString(i))
                }
            }
            if (tips.isEmpty()) {
                tips.addAll(
                    listOf(
                        "Set up automatic weekly savings transfers to lock in your surplus.",
                        "Track non-essential shopping to avoid impulse expenses.",
                        "Follow up on pending loan repayments to replenish your cash reserves."
                    )
                )
            }

            Result.success(MonthlyInsightsResult(summary, tips))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get monthly insights", e)
            Result.failure(e)
        }
    }

    private fun generateContent(
        prompt: String,
        apiKey: String,
        isJson: Boolean,
        imageBase64: String? = null
    ): String {
        val parts = JSONArray()
        parts.put(JSONObject().put("text", prompt))
        if (imageBase64 != null) {
            val inlineData = JSONObject()
                .put("mimeType", "image/jpeg")
                .put("data", imageBase64)
            parts.put(JSONObject().put("inlineData", inlineData))
        }

        val root = JSONObject()
        root.put("contents", JSONArray().put(JSONObject().put("parts", parts)))
        if (isJson) {
            root.put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        }

        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("x-goog-api-key", apiKey)
            .post(root.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: SocketTimeoutException) {
            throw Exception("Request timed out. Please check your internet connection and try again.")
        } catch (e: IOException) {
            throw Exception("No internet connection or server unreachable. Please check your internet connection.")
        }

        return response.use { resp ->
            val bodyString = resp.body?.string()
            if (!resp.isSuccessful) {
                throw Exception(httpErrorMessage(resp.code, bodyString))
            }
            if (bodyString.isNullOrBlank()) {
                throw Exception("Gemini returned an empty response. Please try again.")
            }
            extractCandidateText(bodyString)
        }
    }

    private fun extractCandidateText(body: String): String {
        val responseJson = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw Exception("Gemini returned an unreadable response. Please try again.")
        }

        val blockReason = responseJson.optJSONObject("promptFeedback")
            ?.optString("blockReason")
            ?.takeIf { it.isNotBlank() }
        if (blockReason != null) {
            throw Exception(
                "Gemini blocked this request for safety reasons ($blockReason). Please rephrase and try again."
            )
        }

        val candidates = responseJson.optJSONArray("candidates")
        val candidate = candidates?.optJSONObject(0)
        if (candidate == null) {
            throw Exception("Gemini returned no content. Please try again.")
        }

        val finishReason = candidate.optString("finishReason").ifBlank { "STOP" }
        if (finishReason != "STOP" && finishReason != "MAX_TOKENS") {
            throw Exception(finishReasonMessage(finishReason))
        }

        val text = StringBuilder()
        val candidateParts = candidate.optJSONObject("content")?.optJSONArray("parts")
        if (candidateParts != null) {
            for (i in 0 until candidateParts.length()) {
                val part = candidateParts.optJSONObject(i) ?: continue
                if (part.optBoolean("thought", false)) continue
                val piece = part.optString("text")
                if (piece.isNotEmpty()) {
                    text.append(piece)
                }
            }
        }

        val result = text.toString().trim()
        if (result.isEmpty()) {
            throw Exception(
                if (finishReason == "MAX_TOKENS") {
                    finishReasonMessage("MAX_TOKENS")
                } else {
                    "Gemini returned an empty response. Please try again."
                }
            )
        }
        return result
    }

    private fun finishReasonMessage(reason: String): String = when (reason) {
        "SAFETY", "IMAGE_SAFETY" ->
            "Gemini stopped for safety reasons. Please rephrase and try again."
        "RECITATION", "BLOCKLIST" ->
            "Gemini stopped because the response matched blocked or copyrighted content. Please rephrase and try again."
        "PROHIBITED_CONTENT", "IMAGE_PROHIBITED_CONTENT" ->
            "Gemini refused this request because it asked for prohibited content. Please change your request."
        "SPII" ->
            "Gemini stopped to avoid sharing sensitive personal information. Please rephrase and try again."
        "MAX_TOKENS" ->
            "Gemini ran out of output tokens before finishing. Please ask a narrower question."
        else ->
            "Gemini could not finish the response (finishReason: $reason). Please try again."
    }

    private fun httpErrorMessage(code: Int, body: String?): String {
        val apiMessage = try {
            JSONObject(body ?: "{}")
                .optJSONObject("error")
                ?.optString("message")
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
        val detail = apiMessage?.let { ": $it" } ?: ""
        return when {
            code == 400 -> "Invalid request (HTTP 400)$detail. Please verify your input or API key."
            code == 404 -> "Model not found (HTTP 404)$detail. The model may be unavailable for your API key."
            code == 401 || code == 403 -> "Invalid Gemini API key (HTTP $code). Please check or update your key in Settings."
            code == 429 -> "Gemini API quota or rate limit exceeded (HTTP 429). Please wait a moment or check your API account quota."
            code >= 500 -> "Gemini server error (HTTP $code). Please try again later."
            else -> "Gemini API error (HTTP $code)$detail"
        }
    }

    private fun parseJsonObject(raw: String): JSONObject = try {
        JSONObject(cleanJsonString(raw))
    } catch (e: JSONException) {
        throw Exception("Gemini returned an unreadable response. Please try again.")
    }

    private fun cleanJsonString(raw: String): String {
        var str = raw.trim()
        if (str.startsWith("```json")) {
            str = str.removePrefix("```json")
        }
        if (str.startsWith("```")) {
            str = str.removePrefix("```")
        }
        if (str.endsWith("```")) {
            str = str.removeSuffix("```")
        }
        return str.trim()
    }
}
