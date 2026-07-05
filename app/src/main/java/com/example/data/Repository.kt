package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

class ZenyRepository(private val dao: ZenyDao) {

    // Events
    val allEvents: Flow<List<CalendarEvent>> = dao.getAllEvents()

    fun getEventsForRange(start: Long, end: Long): Flow<List<CalendarEvent>> =
        dao.getEventsForRange(start, end)

    suspend fun insertEvent(event: CalendarEvent) = dao.insertEvent(event)

    suspend fun deleteEventById(id: Int) = dao.deleteEventById(id)

    suspend fun updateEventCompletion(id: Int, completed: Boolean) =
        dao.updateEventCompletion(id, completed)

    // Tasks
    val allTasks: Flow<List<ZenyTask>> = dao.getAllTasks()

    suspend fun insertTask(task: ZenyTask) = dao.insertTask(task)

    suspend fun deleteTaskById(id: Int) = dao.deleteTaskById(id)

    suspend fun updateTaskStatus(id: Int, active: Boolean) =
        dao.updateTaskStatus(id, active)

    // Workflows
    val allWorkflows: Flow<List<ZenyWorkflow>> = dao.getAllWorkflows()

    suspend fun insertWorkflow(workflow: ZenyWorkflow) = dao.insertWorkflow(workflow)

    suspend fun deleteWorkflowById(id: Int) = dao.deleteWorkflowById(id)

    suspend fun updateWorkflowStatus(id: Int, active: Boolean) =
        dao.updateWorkflowStatus(id, active)

    // Chat
    val chatHistoryFlow: Flow<List<ChatMessage>> = dao.getChatHistoryFlow()

    suspend fun getChatHistory(): List<ChatMessage> = dao.getChatHistory()

    suspend fun insertChatMessage(message: ChatMessage) = dao.insertChatMessage(message)

    suspend fun clearChatHistory() = dao.clearChatHistory()

    companion object {
        @Volatile
        private var INSTANCE: ZenyDatabase? = null

        fun getDatabase(context: Context): ZenyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZenyDatabase::class.java,
                    "zeny_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
