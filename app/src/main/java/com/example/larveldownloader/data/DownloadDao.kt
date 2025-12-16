package com.example.larveldownloader.data

import androidx.room.*
import com.example.larveldownloader.model.DownloadItem
import com.example.larveldownloader.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>
    
    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadItem>>
    
    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadItem?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadItem): Long
    
    @Update
    suspend fun update(item: DownloadItem)
    
    @Delete
    suspend fun delete(item: DownloadItem)
    
    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus)
    
    @Query("UPDATE downloads SET downloadedSize = :downloadedSize WHERE id = :id")
    suspend fun updateProgress(id: Long, downloadedSize: Long)
    
    @Query("UPDATE downloads SET downloadedSize = :downloadedSize, status = :status WHERE id = :id")
    suspend fun updateProgressAndStatus(id: Long, downloadedSize: Long, status: DownloadStatus)
    
    @Query("UPDATE downloads SET filePath = :filePath WHERE id = :id")
    suspend fun updateFilePath(id: Long, filePath: String)
    
    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteByStatus(status: DownloadStatus)
}
