package com.example.domain.model

enum class ChatSender {
    USER, AI, SYSTEM
}

enum class ToolType(val displayName: String, val iconName: String, val description: String) {
    CHAT("AI Chat", "chat", "Conversational AI for reasoning, answering questions, and streaming responses"),
    IMAGE_GEN("Image Generator", "image", "Create high-quality AI images with aspect ratios & style controls"),
    CHART_GEN("Chart Generator", "analytics", "Generate interactive Bar, Line, Pie, Scatter & Area charts"),
    IMAGE_ANALYSIS("Image Analyzer", "remove_red_eye", "Describe images, read text (OCR), analyze charts & diagrams"),
    FILE_ANALYSIS("File Analyzer", "description", "Upload PDF, TXT, CSV, JSON files for instant summary & analysis"),
    CODE_ASSISTANT("Code Assistant", "code", "Generate, debug, refactor, and explain code across 10+ languages"),
    SUMMARIZER("Summarizer", "short_text", "Condensed bullet-point summaries for articles, papers & notes"),
    TRANSLATOR("Translator", "translate", "Real-time AI translation across 50+ languages"),
    CALCULATOR("Math & Stats", "calculate", "Step-by-step calculus, algebra, statistical calculations"),
    WRITING_ASSISTANT("Writing Assistant", "edit_note", "Refine essays, emails, blog posts, and technical docs")
}

enum class ChartType {
    BAR, LINE, PIE, SCATTER, AREA
}

data class ChartPoint(
    val label: String,
    val value: Float,
    val xValue: Float = 0f
)

data class ChartSeries(
    val seriesName: String,
    val points: List<ChartPoint>,
    val colorHex: String = "#38BDF8"
)

data class ChartData(
    val title: String,
    val chartType: ChartType = ChartType.BAR,
    val xAxisLabel: String = "",
    val yAxisLabel: String = "",
    val unit: String = "",
    val series: List<ChartSeries> = emptyList(),
    val summary: String = ""
)

data class CodeSnippet(
    val language: String,
    val code: String,
    val explanation: String = ""
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val sender: ChatSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUris: List<String> = emptyList(),
    val fileUris: List<String> = emptyList(),
    val chartData: ChartData? = null,
    val codeSnippet: CodeSnippet? = null,
    val modelUsed: String = "gemini-3.5-flash",
    val isError: Boolean = false
)

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val toolType: ToolType = ToolType.CHAT
)

data class ImageGenOptions(
    val prompt: String,
    val aspectRatio: String = "1:1", // 1:1, 16:9, 9:16, 4:3
    val style: String = "Photorealistic", // Photorealistic, Digital Art, Cinematic, Anime, Minimalist
    val quality: String = "Standard"
)

data class AIProviderSettings(
    val providerName: String = "Gemini AI",
    val apiKeyOverride: String = "",
    val activeModel: String = "gemini-3.5-flash",
    val isDarkMode: Boolean = true
)
