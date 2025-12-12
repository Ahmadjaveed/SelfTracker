package com.example.selftracker.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.selftracker.models.Notification
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(notification: Notification)

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<Notification>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()
    
    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}
