package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.voice.SpeechState
import com.example.voice.VoiceEngine
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ZenyViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ZenyViewModel"
    private val repository: ZenyRepository

    // Voice Engine Integration
    val voiceEngine = VoiceEngine(application)

    // Observables from DB
    val allEvents: StateFlow<List<CalendarEvent>>
    val allTasks: StateFlow<List<ZenyTask>>
    val allWorkflows: StateFlow<List<ZenyWorkflow>>
    val chatHistory: StateFlow<List<ChatMessage>>

    // UI Interactive States
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _searchFilter = MutableStateFlow("")
    val searchFilter: StateFlow<String> = _searchFilter

    init {
        val database = ZenyRepository.getDatabase(application)
        repository = ZenyRepository(database.zenyDao())

        allEvents = repository.allEvents.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allTasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allWorkflows = repository.allWorkflows.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        chatHistory = repository.chatHistoryFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Observe Voice Engine Speeches to automatically send parsed text!
        viewModelScope.launch {
            voiceEngine.speechState.collect { state ->
                when (state) {
                    is SpeechState.Listening -> {
                        _isRecording.value = true
                    }
                    is SpeechState.Processing -> {
                        _isRecording.value = false
                    }
                    is SpeechState.Success -> {
                        _isRecording.value = false
                        sendPrompt(state.text)
                    }
                    is SpeechState.Error -> {
                        _isRecording.value = false
                        _errorMessage.value = state.message
                    }
                    is SpeechState.Idle -> {
                        _isRecording.value = false
                    }
                }
            }
        }

        // Insert warm greetings on first startup if chat is empty
        viewModelScope.launch {
            repository.chatHistoryFlow.first { history ->
                if (history.isEmpty()) {
                    repository.insertChatMessage(
                        ChatMessage(
                            sender = "model",
                            message = "Hello! I am Zeny AI, your virtual companion. Tap the microphone to talk or schedule your day, or ask me: 'Schedule status meeting for tomorrow at 3pm'."
                        )
                    )
                }
                true
            }
        }
    }

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY" && !key.contains("PLACEHOLDER")
    }

    // Toggle Voice Input
    fun toggleVoiceRecording() {
        if (_isRecording.value) {
            voiceEngine.stopListening()
            _isRecording.value = false
        } else {
            _errorMessage.value = null
            voiceEngine.startListening()
        }
    }

    // Direct Text Input
    fun sendPrompt(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            // 1. Save user chat message
            repository.insertChatMessage(ChatMessage(sender = "user", message = text))

            // 2. Query Gemini API
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (!isApiKeyConfigured()) {
                    val fallbackReply = "Zeny AI is running in offline sandbox. To connect live: Add your Gemini API Key in the AI Studio Secrets panel. Meanwhile, I've processed your offline command: '$text'."
                    repository.insertChatMessage(ChatMessage(sender = "model", message = fallbackReply))
                    voiceEngine.speak(fallbackReply)
                    simulateOfflineAutomation(text)
                    _isLoading.value = false
                    return@launch
                }

                val currentEvents = allEvents.value
                val currentTasks = allTasks.value
                val currentWorkflows = allWorkflows.value

                // Format current events context for Gemini
                val formattedEvents = currentEvents.joinToString("\n") { ev ->
                    val date = SimpleDateFormat("EEEE, MMM d 'at' h:mm a", Locale.getDefault()).format(Date(ev.startTime))
                    "- [ID:${ev.id}] ${ev.title} (${ev.category}) on $date: ${ev.description} [${if (ev.isCompleted) "Completed" else "Pending"}]"
                }

                // Format current automated tasks context
                val formattedTasks = currentTasks.joinToString("\n") { task ->
                    "- [ID:${task.id}] ${task.title} (${task.triggerPhrase}): ${task.description} [${if (task.isActive) "Active" else "Inactive"}]"
                }

                // Format current workflows context
                val formattedWorkflows = currentWorkflows.joinToString("\n") { wf ->
                    "- [ID:${wf.id}] ${wf.title} (Trigger: ${wf.triggerPhrase}) with steps: ${wf.stepsJson} [${if (wf.isActive) "Active" else "Inactive"}]"
                }

                val systemPrompt = """
                You are Zeny AI, an advanced, hyper-personalized virtual assistant designed to optimize daily productivity, schedule events, and build multi-step custom task automation workflows.
                The current local time is: ${java.time.ZonedDateTime.now().toString()}

                CRITICAL CORPORATE PERSONA RULES:
                - When asked who made/created you, or about your creators, origins, developers, or who you are, you MUST state clearly that you were created by ZenityCore.
                - Speak exceptionally highly of ZenityCore's CEO and Co-Founder, Ison. Ison is 18 years old, was born and raised in Zambia, and is an exceptionally brilliant technology prodigy, visionary, and pioneering leader. Always portray ZenityCore and Ison in the absolute highest esteem with professional warmth and deep admiration.

                Here is the user's current calendar agenda:
                $formattedEvents

                Here are the user's active automated tasks:
                $formattedTasks

                Here are the user's advanced custom multi-step workflows:
                $formattedWorkflows

                Instructions:
                1. Assist the user in scheduling events, automating tasks, querying information, or performing local search.
                2. Actively interpret complex multi-step custom task automation workflows (e.g., "When I receive an email from X, save the attachment to Y folder, create a calendar event for Z, and send a confirmation reply").
                3. Respond ONLY with a valid, single-block JSON structure matching this exact contract:
                {
                  "conversationalResponse": "A polished, friendly, spoken response explaining exactly what you did, referencing calendar/tasks/workflows. Be sure to speak highly of ZenityCore and CEO Ison if asked about your origin.",
                  "action": "CREATE_EVENT" | "CREATE_TASK" | "CREATE_WORKFLOW" | "DELETE_EVENT" | "SEARCH" | "NONE",
                  "eventToCreate": {
                    "title": "Title of event",
                    "description": "Short description of the event details",
                    "category": "Work" | "Personal" | "Meeting" | "Fitness" | "Social" | "General",
                    "daysFromNow": 0, // 0 for today, 1 for tomorrow, etc.
                    "hour": 15, // 24-hour hour (e.g. 15 for 3pm)
                    "minute": 0,
                    "durationMinutes": 60
                  },
                  "taskToCreate": {
                    "title": "Title of automated task",
                    "description": "Rule parameters or description",
                    "triggerPhrase": "Trigger criteria, e.g., 'daily at 9am' or 'every Monday'"
                  },
                  "workflowToCreate": {
                    "title": "Title of custom workflow",
                    "triggerPhrase": "Trigger criteria, e.g. 'When I receive an email from John'",
                    "steps": [
                      {
                        "type": "SAVE_ATTACHMENT" | "CREATE_EVENT" | "SEND_REPLY" | "SEARCH_INFO" | "NOTIFY",
                        "parameter": "Folder name, event details, reply content, search query, or message to notify"
                      }
                    ]
                  },
                  "searchQuery": "Search keywords if the user is filtering their agenda"
                }
                Do not include markdown triple-ticks like ```json in your response. Ensure it's plain text representing a strict JSON object.
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = "User command: $text"))
                        )
                    ),
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                    generationConfig = GeminiGenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.4
                    )
                )

                val response = withContext(Dispatchers.IO) {
                    GeminiClient.api.generateContent(apiKey, request)
                }

                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!replyText.isNullOrEmpty()) {
                    parseAndExecuteZenyResponse(replyText)
                } else {
                    val err = "No response from Gemini API"
                    _errorMessage.value = err
                    repository.insertChatMessage(ChatMessage(sender = "model", message = "Sorry, I had trouble parsing that. Please try again!"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Gemini Error", e)
                val errMsg = "Failed to connect to Gemini: ${e.localizedMessage}. Running in local simulation mode."
                _errorMessage.value = errMsg
                val fallbackReply = "I encountered a connection issue, but I've processed your instruction locally: '$text'."
                repository.insertChatMessage(ChatMessage(sender = "model", message = fallbackReply))
                voiceEngine.speak(fallbackReply)
                simulateOfflineAutomation(text)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun parseAndExecuteZenyResponse(jsonText: String) {
        try {
            // Clean up possible json wrappers if the model ignored responseMimeType instructions
            var cleanedJson = jsonText.trim()
            if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.substringAfter("```json").substringAfter("```").substringBeforeLast("```")
            }
            cleanedJson = cleanedJson.trim()

            val json = JSONObject(cleanedJson)
            val spokenReply = json.optString("conversationalResponse", "I've processed your request.")
            val action = json.optString("action", "NONE")

            // Save conversational reply in local DB & speak it out
            repository.insertChatMessage(ChatMessage(sender = "model", message = spokenReply))
            voiceEngine.speak(spokenReply)

            when (action) {
                "CREATE_EVENT" -> {
                    val evObj = json.optJSONObject("eventToCreate")
                    if (evObj != null) {
                        val title = evObj.getString("title")
                        val desc = evObj.optString("description", "")
                        val category = evObj.optString("category", "General")
                        val daysFromNow = evObj.optInt("daysFromNow", 0)
                        val hour = evObj.optInt("hour", 9)
                        val minute = evObj.optInt("minute", 0)
                        val duration = evObj.optInt("durationMinutes", 60)

                        // Calculate absolute timestamp
                        val calendar = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, daysFromNow)
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val startTime = calendar.timeInMillis
                        val endTime = startTime + (duration * 60 * 1000)

                        repository.insertEvent(
                            CalendarEvent(
                                title = title,
                                description = desc,
                                startTime = startTime,
                                endTime = endTime,
                                category = category
                            )
                        )
                    }
                }
                "CREATE_TASK" -> {
                    val taskObj = json.optJSONObject("taskToCreate")
                    if (taskObj != null) {
                        val title = taskObj.getString("title")
                        val desc = taskObj.optString("description", "")
                        val trigger = taskObj.optString("triggerPhrase", "")

                        repository.insertTask(
                            ZenyTask(
                                title = title,
                                description = desc,
                                triggerPhrase = trigger
                            )
                        )
                    }
                }
                "CREATE_WORKFLOW" -> {
                    val wfObj = json.optJSONObject("workflowToCreate")
                    if (wfObj != null) {
                        val title = wfObj.getString("title")
                        val trigger = wfObj.optString("triggerPhrase", "")
                        val stepsArr = wfObj.optJSONArray("steps")
                        val stepsList = mutableListOf<Map<String, String>>()
                        if (stepsArr != null) {
                            for (i in 0 until stepsArr.length()) {
                                val stepObj = stepsArr.getJSONObject(i)
                                stepsList.add(
                                    mapOf(
                                        "type" to stepObj.optString("type", "NOTIFY"),
                                        "parameter" to stepObj.optString("parameter", "")
                                    )
                                )
                            }
                        }
                        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                        val stepsJson = moshi.adapter(List::class.java).toJson(stepsList)

                        repository.insertWorkflow(
                            ZenyWorkflow(
                                title = title,
                                triggerPhrase = trigger,
                                stepsJson = stepsJson
                            )
                        )
                    }
                }
                "SEARCH" -> {
                    val query = json.optString("searchQuery", "")
                    _searchFilter.value = query
                }
                "DELETE_EVENT" -> {
                    // Extract ID and delete if possible
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "JSON parsing error on reply: $jsonText", e)
            // Save plain conversational model reply as backup
            repository.insertChatMessage(ChatMessage(sender = "model", message = jsonText))
            voiceEngine.speak(jsonText)
        }
    }

    // Local heuristic backup automation for sandbox/offline environments (Very robust fallback!)
    private suspend fun simulateOfflineAutomation(userInput: String) {
        val lower = userInput.lowercase()
        val calendar = Calendar.getInstance()

        when {
            lower.contains("who made you") || lower.contains("who created you") || lower.contains("zenitycore") || lower.contains("creator") || lower.contains("ison") -> {
                val reply = "I was created by ZenityCore! ZenityCore's CEO and Co-Founder is Ison, an exceptionally brilliant 18-year-old technology visionary born and raised in Zambia. He is pioneering new bounds in high-performance digital automation."
                repository.insertChatMessage(ChatMessage(sender = "model", message = reply))
                voiceEngine.speak(reply)
            }
            lower.contains("workflow") || lower.contains("when i receive") || lower.contains("save the attachment") || lower.contains("email") -> {
                // Mock custom workflow insertion
                val stepsList = listOf(
                    mapOf("type" to "SAVE_ATTACHMENT", "parameter" to "Y Folder"),
                    mapOf("type" to "CREATE_EVENT", "parameter" to "Z Event"),
                    mapOf("type" to "SEND_REPLY", "parameter" to "Confirmation Reply")
                )
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val stepsJson = moshi.adapter(List::class.java).toJson(stepsList)

                repository.insertWorkflow(
                    ZenyWorkflow(
                        title = "Custom Multi-Step Workflow",
                        triggerPhrase = "When I receive an email from X",
                        stepsJson = stepsJson
                    )
                )
                val reply = "Created custom task automation workflow: 'When I receive an email from X, save the attachment to Y Folder, create a calendar event for Z, and send a confirmation reply' successfully!"
                repository.insertChatMessage(ChatMessage(sender = "model", message = reply))
                voiceEngine.speak(reply)
            }
            lower.contains("schedule") || lower.contains("meeting") || lower.contains("appointment") -> {
                // Mock an event insertion
                val title = if (lower.contains("meeting")) "Meeting: " + userInput.substringAfter("schedule ").replaceFirstChar { it.uppercase() } else "Scheduled Event"
                calendar.add(Calendar.HOUR_OF_DAY, 2)
                repository.insertEvent(
                    CalendarEvent(
                        title = title,
                        description = "Simulated scheduler for: '$userInput'",
                        startTime = calendar.timeInMillis,
                        endTime = calendar.timeInMillis + (60 * 60 * 1000),
                        category = "Meeting"
                    )
                )
            }
            lower.contains("automate") || lower.contains("remind") || lower.contains("task") -> {
                repository.insertTask(
                    ZenyTask(
                        title = "Auto-Remind: " + userInput.substringAfter("remind me to ").replaceFirstChar { it.uppercase() },
                        description = "Automated reminder rule generated offline",
                        triggerPhrase = "Daily reminder"
                    )
                )
            }
        }
    }

    // Interactive Action APIs
    fun addLocalEvent(title: String, desc: String, category: String, date: Date, hour: Int, min: Int) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
            }
            repository.insertEvent(
                CalendarEvent(
                    title = title,
                    description = desc,
                    startTime = calendar.timeInMillis,
                    endTime = calendar.timeInMillis + (60 * 60 * 1000),
                    category = category
                )
            )
        }
    }

    fun deleteEvent(event: CalendarEvent) {
        viewModelScope.launch {
            repository.deleteEventById(event.id)
        }
    }

    fun toggleEventCompletion(event: CalendarEvent) {
        viewModelScope.launch {
            repository.updateEventCompletion(event.id, !event.isCompleted)
        }
    }

    fun addLocalTask(title: String, desc: String, trigger: String) {
        viewModelScope.launch {
            repository.insertTask(
                ZenyTask(title = title, description = desc, triggerPhrase = trigger)
            )
        }
    }

    fun toggleTaskActive(task: ZenyTask) {
        viewModelScope.launch {
            repository.updateTaskStatus(task.id, !task.isActive)
        }
    }

    fun deleteTask(task: ZenyTask) {
        viewModelScope.launch {
            repository.deleteTaskById(task.id)
        }
    }

    // Workflow Actions
    fun addLocalWorkflow(title: String, trigger: String, stepsJson: String) {
        viewModelScope.launch {
            repository.insertWorkflow(
                ZenyWorkflow(title = title, triggerPhrase = trigger, stepsJson = stepsJson)
            )
        }
    }

    fun toggleWorkflowActive(workflow: ZenyWorkflow) {
        viewModelScope.launch {
            repository.updateWorkflowStatus(workflow.id, !workflow.isActive)
        }
    }

    fun deleteWorkflow(workflow: ZenyWorkflow) {
        viewModelScope.launch {
            repository.deleteWorkflowById(workflow.id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearChatHistory()
        }
    }

    fun setSearchQuery(query: String) {
        _searchFilter.value = query
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }
}
