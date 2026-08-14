package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.NandiDatabase
import com.example.data.remote.AIProviderRepository
import com.example.data.remote.GeminiContent
import com.example.data.remote.GeminiPart
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatSender
import com.example.domain.model.CodeSnippet
import com.example.domain.model.Conversation
import com.example.domain.model.ImageGenOptions
import com.example.domain.model.ToolType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class NandiViewModel(application: Application) : AndroidViewModel(application) {

    private val db = NandiDatabase.getDatabase(application)
    private val dao = db.nandiDao()
    private val repository = AIProviderRepository(application)

    // State
    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _selectedTool = MutableStateFlow(ToolType.CHAT)
    val selectedTool: StateFlow<ToolType> = _selectedTool.asStateFlow()

    private val _selectedModel = MutableStateFlow("gemini-3.5-flash")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _apiKeyOverride = MutableStateFlow("")
    val apiKeyOverride: StateFlow<String> = _apiKeyOverride.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _generatedImageResult = MutableStateFlow<String?>(null)
    val generatedImageResult: StateFlow<String?> = _generatedImageResult.asStateFlow()

    private var activeGenerationJob: Job? = null

    // Conversations Flow
    val conversations: StateFlow<List<Conversation>> = dao.getAllConversations()
        .map { list ->
            list.map { entity ->
                Conversation(
                    id = entity.id,
                    title = entity.title,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    isPinned = entity.isPinned,
                    toolType = try { ToolType.valueOf(entity.toolType) } catch (e: Exception) { ToolType.CHAT }
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Active Messages Flow
    val messages: StateFlow<List<ChatMessage>> = combine(
        _activeConversationId,
        dao.getAllConversations()
    ) { convId, _ -> convId }
        .map { convId ->
            if (convId == null) emptyList()
            else {
                dao.getMessagesListForConversation(convId).map { entity ->
                    ChatMessage(
                        id = entity.id,
                        conversationId = entity.conversationId,
                        sender = try { ChatSender.valueOf(entity.sender) } catch (e: Exception) { ChatSender.AI },
                        text = entity.text,
                        timestamp = entity.timestamp,
                        imageUris = if (entity.imageUrisJson.isNotBlank()) entity.imageUrisJson.split(",") else emptyList(),
                        fileUris = if (entity.fileUrisJson.isNotBlank()) entity.fileUrisJson.split(",") else emptyList(),
                        chartData = repository.parseChartDataFromResponse(entity.text),
                        codeSnippet = repository.parseCodeSnippetFromResponse(entity.text),
                        modelUsed = entity.modelUsed,
                        isError = entity.isError
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val hasApiKey: Boolean
        get() = repository.getEffectiveApiKey(_apiKeyOverride.value).isNotBlank()

    fun selectTool(tool: ToolType) {
        _selectedTool.value = tool
        _activeConversationId.value = null
    }

    fun selectConversation(conversation: Conversation) {
        _activeConversationId.value = conversation.id
        _selectedTool.value = conversation.toolType
    }

    fun startNewChat() {
        _activeConversationId.value = null
        _generatedImageResult.value = null
    }

    fun setApiKeyOverride(key: String) {
        _apiKeyOverride.value = key
    }

    fun setSelectedModel(model: String) {
        _selectedModel.value = model
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun sendMessage(promptText: String, imageUris: List<Uri>, fileUris: List<Uri>) {
        if (promptText.isBlank() && imageUris.isEmpty() && fileUris.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val tool = _selectedTool.value
            var convId = _activeConversationId.value

            // Create Conversation if not existing
            if (convId == null) {
                convId = UUID.randomUUID().toString()
                val title = if (promptText.isNotBlank()) {
                    if (promptText.length > 30) promptText.take(30) + "..." else promptText
                } else "New ${tool.displayName}"

                val newConv = ConversationEntity(
                    id = convId,
                    title = title,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    toolType = tool.name
                )
                dao.insertConversation(newConv)
                _activeConversationId.value = convId
            }

            // Extract file contents if attached
            val fileTexts = fileUris.mapNotNull { uri ->
                try {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(stream))
                    reader.use { it.readText().take(5000) } // Limit file text size
                } catch (e: Exception) {
                    null
                }
            }

            // 1. Insert User Message
            val userMsgId = UUID.randomUUID().toString()
            val imageUriStrings = imageUris.map { it.toString() }.joinToString(",")
            val fileUriStrings = fileUris.map { it.toString() }.joinToString(",")

            val userMessage = MessageEntity(
                id = userMsgId,
                conversationId = convId,
                sender = ChatSender.USER.name,
                text = promptText,
                imageUrisJson = imageUriStrings,
                fileUrisJson = fileUriStrings,
                timestamp = System.currentTimeMillis()
            )
            dao.insertMessage(userMessage)

            // 2. Prepare AI Streaming Placeholder
            val aiMsgId = UUID.randomUUID().toString()
            val initialAiMsg = MessageEntity(
                id = aiMsgId,
                conversationId = convId,
                sender = ChatSender.AI.name,
                text = "...",
                modelUsed = _selectedModel.value,
                timestamp = System.currentTimeMillis()
            )
            dao.insertMessage(initialAiMsg)

            _isGenerating.value = true

            // Gather history
            val existingMsgs = dao.getMessagesListForConversation(convId)
            val historyContents = existingMsgs.dropLast(2).map { msg ->
                GeminiContent(
                    role = if (msg.sender == ChatSender.USER.name) "user" else "model",
                    parts = listOf(GeminiPart(text = msg.text))
                )
            }

            // Execute AI Stream
            activeGenerationJob = launch(Dispatchers.IO) {
                val fullResponse = StringBuilder()

                repository.generateResponseStream(
                    prompt = promptText,
                    conversationHistory = historyContents,
                    toolType = tool,
                    selectedModel = _selectedModel.value,
                    imageUris = imageUris.map { it.toString() },
                    fileContents = fileTexts,
                    customApiKey = _apiKeyOverride.value
                ).collectLatest { chunk ->
                    if (chunk.startsWith("API_KEY_MISSING_ERROR") || chunk.startsWith("ERROR:")) {
                        val errorEntity = MessageEntity(
                            id = aiMsgId,
                            conversationId = convId,
                            sender = ChatSender.AI.name,
                            text = chunk,
                            isError = true,
                            timestamp = System.currentTimeMillis()
                        )
                        dao.insertMessage(errorEntity)
                        _isGenerating.value = false
                        return@collectLatest
                    }

                    fullResponse.append(chunk)
                    val updatedAiMsg = MessageEntity(
                        id = aiMsgId,
                        conversationId = convId,
                        sender = ChatSender.AI.name,
                        text = fullResponse.toString(),
                        modelUsed = _selectedModel.value,
                        timestamp = System.currentTimeMillis()
                    )
                    dao.insertMessage(updatedAiMsg)
                }

                _isGenerating.value = false
            }
        }
    }

    fun generateImage(options: ImageGenOptions) {
        _isGenerating.value = true
        _generatedImageResult.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.generateImage(options, _apiKeyOverride.value)
            _generatedImageResult.value = result
            _isGenerating.value = false
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        _isGenerating.value = false
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val conv = dao.getConversationById(id)
            if (conv != null) {
                dao.updateConversation(conv.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteConversation(id)
            dao.deleteMessagesForConversation(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = null
            }
        }
    }
}
