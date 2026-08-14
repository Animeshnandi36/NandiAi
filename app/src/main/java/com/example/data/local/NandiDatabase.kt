package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        GeneratedImageEntity::class,
        UploadedFileEntity::class,
        UserSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NandiDatabase : RoomDatabase() {
    abstract fun nandiDao(): NandiDao

    companion object {
        @Volatile
        private var INSTANCE: NandiDatabase? = null

        fun getDatabase(context: Context): NandiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NandiDatabase::class.java,
                    "nandi_ai_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
