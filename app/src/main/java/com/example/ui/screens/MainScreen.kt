package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ToolType
import com.example.ui.components.ApiKeyConfigDialog
import com.example.ui.components.LandingView
import com.example.ui.components.MessageBubble
import com.example.ui.components.NandiComposer
import com.example.ui.components.NandiHeader
import com.example.ui.components.NandiSidebar
import com.example.ui.viewmodel.NandiViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: NandiViewModel,
    modifier: Modifier = Modifier
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConvId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val selectedTool by viewModel.selectedTool.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val apiKeyOverride by viewModel.apiKeyOverride.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val generatedImageResult by viewModel.generatedImageResult.collectAsStateWithLifecycle()

    var showApiKeyDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Auto-scroll chat to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NandiSidebar(
                    conversations = conversations,
                    activeConversationId = activeConvId,
                    selectedTool = selectedTool,
                    onSelectTool = { tool ->
                        viewModel.selectTool(tool)
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        viewModel.startNewChat()
                        scope.launch { drawerState.close() }
                    },
                    onSelectConversation = { conv ->
                        viewModel.selectConversation(conv)
                        scope.launch { drawerState.close() }
                    },
                    onRenameConversation = { id, title ->
                        viewModel.renameConversation(id, title)
                    },
                    onDeleteConversation = { id ->
                        viewModel.deleteConversation(id)
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                NandiHeader(
                    selectedModel = selectedModel,
                    hasApiKey = viewModel.hasApiKey,
                    isDarkMode = isDarkMode,
                    onMenuClick = {
                        scope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    },
                    onModelSelected = { model ->
                        viewModel.setSelectedModel(model)
                    },
                    onApiKeyClick = {
                        showApiKeyDialog = true
                    },
                    onThemeToggle = {
                        viewModel.toggleDarkMode()
                    }
                )
            },
            bottomBar = {
                // Bottom Composer (Always available for Chat or Active Conversation)
                if (activeConvId != null || selectedTool == ToolType.CHAT || selectedTool == ToolType.IMAGE_ANALYSIS || selectedTool == ToolType.FILE_ANALYSIS || selectedTool == ToolType.SUMMARIZER) {
                    NandiComposer(
                        isGenerating = isGenerating,
                        selectedTool = selectedTool,
                        onSendMessage = { text, imageUris, fileUris ->
                            viewModel.sendMessage(text, imageUris, fileUris)
                        },
                        onStopGeneration = {
                            viewModel.stopGeneration()
                        }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (activeConvId != null && messages.isNotEmpty()) {
                    // Chat Screen View
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            MessageBubble(
                                message = msg,
                                onRegenerate = {
                                    val lastUserMsg = messages.lastOrNull { it.sender == com.example.domain.model.ChatSender.USER }
                                    if (lastUserMsg != null) {
                                        viewModel.sendMessage(lastUserMsg.text, emptyList(), emptyList())
                                    }
                                },
                                onEditAndResend = { text ->
                                    viewModel.sendMessage(text, emptyList(), emptyList())
                                }
                            )
                        }
                    }
                } else {
                    // Tool Stage View when no active chat selected
                    when (selectedTool) {
                        ToolType.IMAGE_GEN -> {
                            ImageGeneratorScreen(
                                isGenerating = isGenerating,
                                generatedImageResult = generatedImageResult,
                                onGenerateImage = { options ->
                                    viewModel.generateImage(options)
                                }
                            )
                        }
                        ToolType.CHART_GEN -> {
                            ChartGeneratorScreen(
                                onGenerateChartFromPrompt = { prompt ->
                                    viewModel.sendMessage(prompt, emptyList(), emptyList())
                                }
                            )
                        }
                        ToolType.CODE_ASSISTANT -> {
                            CodeAssistantScreen(
                                onRunCodeAction = { action, lang, text ->
                                    val formattedPrompt = "$action $lang code for:\n$text"
                                    viewModel.sendMessage(formattedPrompt, emptyList(), emptyList())
                                }
                            )
                        }
                        else -> {
                            LandingView(
                                onPromptClick = { prompt, tool ->
                                    viewModel.selectTool(tool)
                                    viewModel.sendMessage(prompt, emptyList(), emptyList())
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showApiKeyDialog) {
        ApiKeyConfigDialog(
            currentApiKey = apiKeyOverride,
            onDismiss = { showApiKeyDialog = false },
            onSaveApiKey = { key ->
                viewModel.setApiKeyOverride(key)
            }
        )
    }
}
