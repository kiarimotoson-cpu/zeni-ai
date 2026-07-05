package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "responseMimeType") val responseMimeType: String? = null,
    @Json(name = "temperature") val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class ZenyParsedAction(
    @Json(name = "conversationalResponse") val conversationalResponse: String,
    @Json(name = "action") val action: String = "NONE", // "CREATE_EVENT", "CREATE_TASK", "CREATE_WORKFLOW", "DELETE_EVENT", "SEARCH", "NONE"
    @Json(name = "eventToCreate") val eventToCreate: ParsedEvent? = null,
    @Json(name = "taskToCreate") val taskToCreate: ParsedTask? = null,
    @Json(name = "workflowToCreate") val workflowToCreate: ParsedWorkflow? = null,
    @Json(name = "searchQuery") val searchQuery: String? = null
)

@JsonClass(generateAdapter = true)
data class ParsedEvent(
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String = "",
    @Json(name = "category") val category: String = "General", // Work, Personal, Meeting, Fitness, Social
    @Json(name = "daysFromNow") val daysFromNow: Int = 0, // 0 = today, 1 = tomorrow, etc.
    @Json(name = "hour") val hour: Int = 9,
    @Json(name = "minute") val minute: Int = 0,
    @Json(name = "durationMinutes") val durationMinutes: Int = 60
)

@JsonClass(generateAdapter = true)
data class ParsedTask(
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String = "",
    @Json(name = "triggerPhrase") val triggerPhrase: String = ""
)

@JsonClass(generateAdapter = true)
data class ParsedWorkflowStep(
    @Json(name = "type") val type: String, // "SAVE_ATTACHMENT", "CREATE_EVENT", "SEND_REPLY", "SEARCH_INFO", "NOTIFY"
    @Json(name = "parameter") val parameter: String
)

@JsonClass(generateAdapter = true)
data class ParsedWorkflow(
    @Json(name = "title") val title: String,
    @Json(name = "triggerPhrase") val triggerPhrase: String,
    @Json(name = "steps") val steps: List<ParsedWorkflowStep>
)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GeminiApi::class.java)
    }
}
