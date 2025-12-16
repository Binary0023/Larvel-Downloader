package com.example.larveldownloader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.larveldownloader.model.DownloadItem

@Database(entities = [DownloadItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun downloadDao(): DownloadDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "larvel_downloader_db"
                )
                    .fallbackToDestructiveMigration() // Clear old data on schema change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
