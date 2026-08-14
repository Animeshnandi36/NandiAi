package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.domain.model.ChartData
import com.example.domain.model.ChartPoint
import com.example.domain.model.ChartSeries
import com.example.domain.model.ChartType
import com.example.domain.model.CodeSnippet
import com.example.domain.model.ImageGenOptions
import com.example.domain.model.ToolType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

class AIProviderRepository(private val context: Context) {

    // Retrieve active API key: BuildConfig -> Custom Setting
    fun getEffectiveApiKey(customKey: String?): String {
        if (!customKey.isNullOrBlank()) return customKey.trim()
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
            return buildConfigKey.trim()
        }
        return ""
    }

    suspend fun generateResponseStream(
        prompt: String,
        conversationHistory: List<GeminiContent>,
        toolType: ToolType,
        selectedModel: String,
        imageUris: List<String> = emptyList(),
        fileContents: List<String> = emptyList(),
        customApiKey: String? = null
    ): Flow<String> = flow {
        val apiKey = getEffectiveApiKey(customApiKey)
        if (apiKey.isBlank()) {
            emit("API_KEY_MISSING_ERROR: Gemini API Key is not configured. Please tap Settings in top right to enter your API key or configure it in AI Studio Secrets.")
            return@flow
        }

        val modelToUse = when (selectedModel) {
            "gemini-flash" -> "gemini-3.5-flash"
            "gemini-pro" -> "gemini-3.1-pro-preview"
            "gemini-lite" -> "gemini-3.1-flash-lite-preview"
            else -> if (selectedModel.isNotBlank()) selectedModel else "gemini-3.5-flash"
        }

        // Build System Instruction
        val systemText = buildString {
            append("You are NandiAI, an advanced, highly capable, versatile AI platform created and developed by Animesh Nandi in 2026. ")
            append("Always provide helpful, precise, clear responses with elegant formatting. ")
            when (toolType) {
                ToolType.CHAT -> append("Provide natural, informative conversation with markdown formatting and clear bullet points where appropriate. ")
                ToolType.CODE_ASSISTANT -> append("Provide high-quality code snippets wrapped in markdown code blocks with syntax highlighting. Offer concise explanations and optimization hints. ")
                ToolType.CHART_GEN -> append("You are in Chart Generator mode. Analyze user data and always output a JSON block inside ```json chart_data ... ``` at the end of your response specifying the chart details so NandiAI can render an interactive visual chart. Format JSON as: {\"title\": \"Title\", \"type\": \"BAR/LINE/PIE/SCATTER/AREA\", \"xAxisLabel\": \"X\", \"yAxisLabel\": \"Y\", \"unit\": \"$\", \"summary\": \"Brief insight\", \"series\": [{\"name\": \"Series 1\", \"color\": \"#38BDF8\", \"points\": [{\"label\": \"Jan\", \"value\": 100}]}]} ")
                ToolType.IMAGE_ANALYSIS -> append("Provide detailed description, text extraction (OCR), object detection, and visual analysis of the provided image(s). ")
                ToolType.FILE_ANALYSIS -> append("Summarize key findings, answer queries, and extract actionable insight from the uploaded documents/files. ")
                ToolType.SUMMARIZER -> append("Provide a structured summary with Executive Overview, Key Highlights, and Action Items. ")
                ToolType.TRANSLATOR -> append("Provide accurate, natural translations with linguistic nuances explained if requested. ")
                ToolType.CALCULATOR -> append("Show step-by-step mathematical reasoning, formulas used, and final result clearly boxed. ")
                ToolType.WRITING_ASSISTANT -> append("Refine text for clarity, tone, conciseness, and polish. Offer 2 style variations. ")
                ToolType.IMAGE_GEN -> append("Provide creative prompt expansion and image styling concepts. ")
            }
        }

        val systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemText)))

        // Build User Parts
        val partsList = mutableListOf<GeminiPart>()

        // 1. Image parts if attached
        for (uriString in imageUris) {
            val base64Data = convertUriToBase64(Uri.parse(uriString))
            if (base64Data != null) {
                partsList.add(GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Data)))
            }
        }

        // 2. File content text if attached
        if (fileContents.isNotEmpty()) {
            val combinedFiles = fileContents.joinToString("\n\n--- ATTACHED FILE CONTENT ---\n")
            partsList.add(GeminiPart(text = "Attached Documents:\n$combinedFiles"))
        }

        // 3. User prompt
        partsList.add(GeminiPart(text = prompt))

        val currentTurn = GeminiContent(role = "user", parts = partsList)
        val allContents = conversationHistory + currentTurn

        val request = GeminiRequest(
            contents = allContents,
            systemInstruction = systemInstruction
        )

        try {
            val responseBody = GeminiClient.apiService.generateContentStream(modelToUse, apiKey, request)
            val inputStream = responseBody.byteStream()
            val reader = inputStream.bufferedReader()

            var line: String?
            var fullBuffer = StringBuilder()

            while (withContext(Dispatchers.IO) { reader.readLine() }.also { line = it } != null) {
                val currentLine = line ?: break
                if (currentLine.isBlank()) continue

                val jsonLine = if (currentLine.startsWith("data: ")) currentLine.removePrefix("data: ") else currentLine
                if (jsonLine.trim() == "[DONE]") break

                try {
                    val jsonObj = JSONObject(jsonLine)
                    val candidates = jsonObj.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCand = candidates.getJSONObject(0)
                        val contentObj = firstCand.optJSONObject("content")
                        if (contentObj != null) {
                            val partsArr = contentObj.optJSONArray("parts")
                            if (partsArr != null) {
                                for (i in 0 until partsArr.length()) {
                                    val partObj = partsArr.getJSONObject(i)
                                    val textChunk = partObj.optString("text", "")
                                    if (textChunk.isNotEmpty()) {
                                        fullBuffer.append(textChunk)
                                        emit(textChunk)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore non-json or partial lines
                }
            }
            if (fullBuffer.isEmpty()) {
                // Fallback non-streaming call if stream was empty
                val directResponse = GeminiClient.apiService.generateContent(modelToUse, apiKey, request)
                val textResult = directResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: directResponse.error?.message ?: "No response received from NandiAI."
                emit(textResult)
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.message ?: "Unknown error"
            if (errorMsg.contains("400") || errorMsg.contains("API_KEY_INVALID") || errorMsg.contains("403")) {
                emit("ERROR: Invalid Gemini API key or permission error ($errorMsg). Please check your API key in Settings.")
            } else if (errorMsg.contains("429")) {
                emit("ERROR: API Rate limit exceeded. Please wait a few seconds and try again.")
            } else {
                emit("ERROR: Unable to connect to NandiAI server. Details: $errorMsg")
            }
        }
    }.flowOn(Dispatchers.IO)

    // Dedicated Image Generation Call
    suspend fun generateImage(
        options: ImageGenOptions,
        customApiKey: String? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey(customApiKey)
        if (apiKey.isBlank()) {
            return@withContext "ERROR: Gemini API Key is missing. Please configure it in Settings."
        }

        val promptText = buildString {
            append("Create a high-resolution, vivid, visually striking image of: ")
            append(options.prompt)
            append(". Style: ").append(options.style)
            append(". Aspect ratio: ").append(options.aspectRatio)
            append(". Premium quality, high detail.")
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = promptText)))
            ),
            generationConfig = GeminiGenerationConfig(
                responseModalities = listOf("TEXT", "IMAGE"),
                imageConfig = GeminiImageConfig(aspectRatio = options.aspectRatio, imageSize = "1K")
            )
        )

        try {
            val response = GeminiClient.apiService.generateContent("gemini-2.5-flash-image", apiKey, request)
            val parts = response.candidates?.firstOrNull()?.content?.parts
            if (parts != null) {
                for (part in parts) {
                    if (part.inlineData != null && part.inlineData.data.isNotEmpty()) {
                        return@withContext "data:${part.inlineData.mimeType};base64,${part.inlineData.data}"
                    }
                }
            }
            // Return error if no inline image was returned
            val err = response.error?.message
            return@withContext if (err != null) "ERROR: Image generation failed: $err" else "ERROR: Image model returned no image output for prompt."
        } catch (e: Exception) {
            return@withContext "ERROR: Image generation failed: ${e.localizedMessage}"
        }
    }

    private fun convertUriToBase64(uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    // Parse chart data if present in text response
    fun parseChartDataFromResponse(text: String): ChartData? {
        try {
            val jsonStart = text.indexOf("```json chart_data")
            val rawJson = if (jsonStart != -1) {
                val contentAfter = text.substring(jsonStart + 18)
                val jsonEnd = contentAfter.indexOf("```")
                if (jsonEnd != -1) contentAfter.substring(0, jsonEnd).trim() else contentAfter.trim()
            } else if (text.contains("\"title\"") && text.contains("\"series\"")) {
                val start = text.indexOf("{")
                val end = text.lastIndexOf("}")
                if (start != -1 && end > start) text.substring(start, end + 1) else null
            } else null

            if (rawJson != null) {
                val jsonObj = JSONObject(rawJson)
                val title = jsonObj.optString("title", "Generated Chart")
                val typeStr = jsonObj.optString("type", "BAR").uppercase()
                val chartType = when (typeStr) {
                    "LINE" -> ChartType.LINE
                    "PIE" -> ChartType.PIE
                    "SCATTER" -> ChartType.SCATTER
                    "AREA" -> ChartType.AREA
                    else -> ChartType.BAR
                }
                val xAxis = jsonObj.optString("xAxisLabel", "")
                val yAxis = jsonObj.optString("yAxisLabel", "")
                val unit = jsonObj.optString("unit", "")
                val summary = jsonObj.optString("summary", "")

                val seriesList = mutableListOf<ChartSeries>()
                val seriesArr = jsonObj.optJSONArray("series")
                if (seriesArr != null) {
                    for (i in 0 until seriesArr.length()) {
                        val sObj = seriesArr.getJSONObject(i)
                        val sName = sObj.optString("name", "Series ${i + 1}")
                        val sColor = sObj.optString("color", if (i == 0) "#38BDF8" else "#818CF8")
                        val pointsList = mutableListOf<ChartPoint>()
                        val ptsArr = sObj.optJSONArray("points")
                        if (ptsArr != null) {
                            for (j in 0 until ptsArr.length()) {
                                val pObj = ptsArr.getJSONObject(j)
                                val label = pObj.optString("label", "P${j + 1}")
                                val valNum = pObj.optDouble("value", 0.0).toFloat()
                                val xNum = pObj.optDouble("xValue", j.toDouble()).toFloat()
                                pointsList.add(ChartPoint(label, valNum, xNum))
                            }
                        }
                        seriesList.add(ChartSeries(sName, pointsList, sColor))
                    }
                }
                if (seriesList.isNotEmpty()) {
                    return ChartData(title, chartType, xAxis, yAxis, unit, seriesList, summary)
                }
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
        return null
    }

    // Parse Code Snippet if present in text response
    fun parseCodeSnippetFromResponse(text: String): CodeSnippet? {
        val startIdx = text.indexOf("```")
        if (startIdx != -1) {
            val afterStart = text.substring(startIdx + 3)
            val langEnd = afterStart.indexOf("\n")
            if (langEnd != -1) {
                val lang = afterStart.substring(0, langEnd).trim()
                val codeEnd = afterStart.indexOf("```")
                if (codeEnd != -1) {
                    val codeContent = afterStart.substring(langEnd + 1, codeEnd).trim()
                    if (codeContent.isNotBlank()) {
                        return CodeSnippet(
                            language = if (lang.isNotBlank() && !lang.startsWith("{")) lang else "code",
                            code = codeContent,
                            explanation = ""
                        )
                    }
                }
            }
        }
        return null
    }
}
private fun String?.isNotBlank(): Boolean = this != null && this.trim().isNotEmpty()
private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
