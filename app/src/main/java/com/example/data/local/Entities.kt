package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val toolType: String = "CHAT" // CHAT, IMAGE_GEN, CHART_GEN, IMAGE_ANALYSIS, FILE_ANALYSIS, CODE_ASSISTANT
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val sender: String, // USER, AI, SYSTEM
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUrisJson: String = "",
    val fileUrisJson: String = "",
    val chartDataJson: String = "",
    val codeSnippetJson: String = "",
    val modelUsed: String = "gemini-3.5-flash",
    val isError: Boolean = false
)

@Entity(tableName = "generated_images")
data class GeneratedImageEntity(
    @PrimaryKey val id: String,
    val prompt: String,
    val imagePathOrUrl: String,
    val style: String = "Photorealistic",
    val aspectRatio: String = "1:1",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "uploaded_files")
data class UploadedFileEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val extractedText: String = "",
    val fileUri: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)
