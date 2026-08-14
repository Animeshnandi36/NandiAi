package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ChartData
import com.example.domain.model.ChartType
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartVisualizer(
    chartData: ChartData,
    modifier: Modifier = Modifier
) {
    var animationProgress by remember { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animationProgress,
        animationSpec = tween(durationMillis = 800),
        label = "chartAnimation"
    )

    LaunchedEffect(chartData) {
        animationProgress = 1f
    }

    var selectedPointLabel by remember { mutableStateOf<String?>(null) }
    var selectedPointValue by remember { mutableStateOf<Float?>(null) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (chartData.chartType) {
                            ChartType.BAR -> Icons.Default.BarChart
                            ChartType.LINE, ChartType.AREA -> Icons.Default.ShowChart
                            ChartType.PIE -> Icons.Default.PieChart
                            ChartType.SCATTER -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = chartData.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = chartData.chartType.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (selectedPointLabel != null && selectedPointValue != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Selected: $selectedPointLabel = ${selectedPointValue}${chartData.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart Canvas
            val primarySeries = chartData.series.firstOrNull()
            if (primarySeries != null && primarySeries.points.isNotEmpty()) {
                val points = primarySeries.points
                val maxValue = (points.maxOfOrNull { it.value } ?: 100f).coerceAtLeast(1f)

                when (chartData.chartType) {
                    ChartType.BAR -> BarChartCanvas(
                        points = points,
                        maxValue = maxValue,
                        unit = chartData.unit,
                        progress = animatedProgress,
                        color = parseHexColor(primarySeries.colorHex),
                        onPointClick = { label, valNum ->
                            selectedPointLabel = label
                            selectedPointValue = valNum
                        }
                    )
                    ChartType.LINE, ChartType.AREA -> LineChartCanvas(
                        points = points,
                        maxValue = maxValue,
                        isArea = chartData.chartType == ChartType.AREA,
                        progress = animatedProgress,
                        color = parseHexColor(primarySeries.colorHex),
                        onPointClick = { label, valNum ->
                            selectedPointLabel = label
                            selectedPointValue = valNum
                        }
                    )
                    ChartType.PIE -> PieChartCanvas(
                        points = points,
                        progress = animatedProgress,
                        onPointClick = { label, valNum ->
                            selectedPointLabel = label
                            selectedPointValue = valNum
                        }
                    )
                    ChartType.SCATTER -> ScatterChartCanvas(
                        points = points,
                        maxValue = maxValue,
                        progress = animatedProgress,
                        color = parseHexColor(primarySeries.colorHex),
                        onPointClick = { label, valNum ->
                            selectedPointLabel = label
                            selectedPointValue = valNum
                        }
                    )
                }
            } else {
                Text("No data points available", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend / Series labels
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chartData.series.forEach { series ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(series.colorHex))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = series.seriesName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (chartData.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = chartData.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun BarChartCanvas(
    points: List<com.example.domain.model.ChartPoint>,
    maxValue: Float,
    unit: String,
    progress: Float,
    color: Color,
    onPointClick: (String, Float) -> Unit
) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val width = size.width
        val height = size.height
        val bottomMargin = 40f
        val topMargin = 20f
        val chartHeight = height - bottomMargin - topMargin
        val barCount = points.size
        val barWidth = (width / barCount) * 0.5f
        val spacing = width / barCount

        // Draw baseline
        drawLine(
            color = axisColor,
            start = Offset(0f, height - bottomMargin),
            end = Offset(width, height - bottomMargin),
            strokeWidth = 2f
        )

        points.forEachIndexed { index, point ->
            val barHeight = (point.value / maxValue) * chartHeight * progress
            val x = index * spacing + spacing / 2 - barWidth / 2
            val y = height - bottomMargin - barHeight

            // Draw Bar
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
        }
    }

    // X-Axis Labels Row below Canvas
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        points.forEach { pt ->
            Text(
                text = pt.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onPointClick(pt.label, pt.value) }
            )
        }
    }
}

@Composable
private fun LineChartCanvas(
    points: List<com.example.domain.model.ChartPoint>,
    maxValue: Float,
    isArea: Boolean,
    progress: Float,
    color: Color,
    onPointClick: (String, Float) -> Unit
) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val width = size.width
        val height = size.height
        val bottomMargin = 40f
        val topMargin = 20f
        val chartHeight = height - bottomMargin - topMargin
        val spacing = width / (points.size - 1).coerceAtLeast(1)

        // Draw Baseline
        drawLine(
            color = axisColor,
            start = Offset(0f, height - bottomMargin),
            end = Offset(width, height - bottomMargin),
            strokeWidth = 2f
        )

        val path = Path()
        val areaPath = Path()

        points.forEachIndexed { i, pt ->
            val x = i * spacing
            val y = height - bottomMargin - ((pt.value / maxValue) * chartHeight * progress)

            if (i == 0) {
                path.moveTo(x, y)
                areaPath.moveTo(x, height - bottomMargin)
                areaPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                areaPath.lineTo(x, y)
            }

            if (i == points.size - 1) {
                areaPath.lineTo(x, height - bottomMargin)
                areaPath.close()
            }

            // Point dots
            drawCircle(
                color = color,
                radius = 6f,
                center = Offset(x, y)
            )
        }

        if (isArea) {
            drawPath(
                path = areaPath,
                color = color.copy(alpha = 0.25f)
            )
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        points.forEach { pt ->
            Text(
                text = pt.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onPointClick(pt.label, pt.value) }
            )
        }
    }
}

@Composable
private fun PieChartCanvas(
    points: List<com.example.domain.model.ChartPoint>,
    progress: Float,
    onPointClick: (String, Float) -> Unit
) {
    val totalSum = points.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
    val colors = listOf(
        Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFFC084FC),
        Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFF43F5E)
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (size.height / 2.2f) * progress
        var startAngle = -90f

        points.forEachIndexed { i, pt ->
            val sweepAngle = (pt.value / totalSum) * 360f * progress
            val sliceColor = colors[i % colors.size]

            drawArc(
                color = sliceColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun ScatterChartCanvas(
    points: List<com.example.domain.model.ChartPoint>,
    maxValue: Float,
    progress: Float,
    color: Color,
    onPointClick: (String, Float) -> Unit
) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val width = size.width
        val height = size.height
        val bottomMargin = 40f
        val chartHeight = height - bottomMargin
        val spacing = width / (points.size - 1).coerceAtLeast(1)

        drawLine(
            color = axisColor,
            start = Offset(0f, height - bottomMargin),
            end = Offset(width, height - bottomMargin),
            strokeWidth = 2f
        )

        points.forEachIndexed { i, pt ->
            val x = i * spacing
            val y = height - bottomMargin - ((pt.value / maxValue) * chartHeight * progress)

            drawCircle(
                color = color,
                radius = 10f * progress,
                center = Offset(x, y)
            )
        }
    }
}

fun parseHexColor(hex: String): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        val colorInt = if (cleanHex.length == 6) {
            android.graphics.Color.parseColor("#$cleanHex")
        } else {
            android.graphics.Color.parseColor("#38BDF8")
        }
        Color(colorInt)
    } catch (e: Exception) {
        Color(0xFF38BDF8)
    }
}
