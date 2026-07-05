package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "calendar_events")
data class CalendarEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val startTime: Long, // timestamp
    val endTime: Long,   // timestamp
    val isCompleted: Boolean = false,
    val category: String = "General" // Work, Personal, Fitness, Meeting, etc.
)

@Entity(tableName = "zeny_tasks")
data class ZenyTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val isActive: Boolean = true,
    val triggerPhrase: String = "",
    val createdTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "zeny_workflows")
data class ZenyWorkflow(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val triggerPhrase: String,
    val stepsJson: String, // Stores serialized steps (e.g., "[{\"type\":\"SAVE_ATTACHMENT\",\"param\":\"Folder Y\"},{\"type\":\"CREATE_EVENT\",\"param\":\"Z\"}]")
    val isActive: Boolean = true,
    val createdTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String, // "user" or "model"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ZenyDao {
    // Calendar Events
    @Query("SELECT * FROM calendar_events ORDER BY startTime ASC")
    fun getAllEvents(): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_events WHERE startTime >= :start AND startTime <= :end ORDER BY startTime ASC")
    fun getEventsForRange(start: Long, end: Long): Flow<List<CalendarEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEvent)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEventById(id: Int)

    @Query("UPDATE calendar_events SET isCompleted = :completed WHERE id = :id")
    suspend fun updateEventCompletion(id: Int, completed: Boolean)

    // Automation Tasks
    @Query("SELECT * FROM zeny_tasks ORDER BY createdTime DESC")
    fun getAllTasks(): Flow<List<ZenyTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ZenyTask)

    @Query("DELETE FROM zeny_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    @Query("UPDATE zeny_tasks SET isActive = :active WHERE id = :id")
    suspend fun updateTaskStatus(id: Int, active: Boolean)

    // Workflows
    @Query("SELECT * FROM zeny_workflows ORDER BY createdTime DESC")
    fun getAllWorkflows(): Flow<List<ZenyWorkflow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkflow(workflow: ZenyWorkflow)

    @Query("DELETE FROM zeny_workflows WHERE id = :id")
    suspend fun deleteWorkflowById(id: Int)

    @Query("UPDATE zeny_workflows SET isActive = :active WHERE id = :id")
    suspend fun updateWorkflowStatus(id: Int, active: Boolean)

    // Chat History
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getChatHistoryFlow(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getChatHistory(): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()
}

@Database(entities = [CalendarEvent::class, ZenyTask::class, ZenyWorkflow::class, ChatMessage::class], version = 2, exportSchema = false)
abstract class ZenyDatabase : RoomDatabase() {
    abstract fun zenyDao(): ZenyDao
}
