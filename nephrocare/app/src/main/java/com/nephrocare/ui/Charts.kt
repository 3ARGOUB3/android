package com.nephrocare.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nephrocare.R
import com.nephrocare.core.fmt

data class ChartSeries(val label: String, val color: Color, val points: List<Pair<Long, Double>>)

data class RefLine(val value: Double, val color: Color, val label: String = "")

private fun DrawScope.text(text: String, x: Float, y: Float, color: Color, sizePx: Float, align: Paint.Align = Paint.Align.LEFT) {
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        textSize = sizePx
        textAlign = align
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

/** A time-based line chart with optional dashed reference lines. Time always runs left to right. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    refLines: List<RefLine> = emptyList(),
    height: Dp = 220.dp,
    decimals: Int = 1,
) {
    val language = currentLanguage()
    val all = series.flatMap { it.points }
    if (all.size < 2) {
        EmptyState(stringResource(R.string.chart_not_enough), modifier)
        return
    }
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val labelPx = 11.dp.toPx()
            val left = 44.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 22.dp.toPx()
            val minX = all.minOf { it.first }
            val maxX = all.maxOf { it.first }.let { if (it == minX) minX + 1 else it }
            val values = all.map { it.second } + refLines.map { it.value }
            var minY = values.min()
            var maxY = values.max()
            if (minY == maxY) { minY -= 1; maxY += 1 }
            val pad = (maxY - minY) * 0.1
            minY -= pad; maxY += pad
            fun x(t: Long) = left + (right - left) * ((t - minX).toFloat() / (maxX - minX).toFloat())
            fun y(v: Double) = bottom - (bottom - top) * ((v - minY) / (maxY - minY)).toFloat()

            for (i in 0..4) {
                val v = minY + (maxY - minY) * i / 4
                val yy = y(v)
                drawLine(gridColor, Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
                text(fmt(v, decimals), left - 6.dp.toPx(), yy + labelPx / 3, axisColor, labelPx, Paint.Align.RIGHT)
            }
            text(Fmt.shortDate(minX, language), left, size.height - 4.dp.toPx(), axisColor, labelPx, Paint.Align.LEFT)
            text(Fmt.shortDate(maxX, language), right, size.height - 4.dp.toPx(), axisColor, labelPx, Paint.Align.RIGHT)

            refLines.forEach { ref ->
                val yy = y(ref.value)
                drawLine(
                    ref.color, Offset(left, yy), Offset(right, yy), strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                )
            }
            series.forEach { s ->
                val pts = s.points.sortedBy { it.first }
                if (pts.isEmpty()) return@forEach
                val path = Path()
                pts.forEachIndexed { i, (t, v) ->
                    if (i == 0) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v))
                }
                drawPath(path, s.color, style = Stroke(width = 3.dp.toPx()))
                pts.forEach { (t, v) -> drawCircle(s.color, radius = 3.5.dp.toPx(), center = Offset(x(t), y(v))) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            series.forEach { LegendDot(it.color, it.label) }
            refLines.filter { it.label.isNotEmpty() }.forEach { LegendDot(it.color, it.label) }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** Simple vertical bars with a label under each and an optional target line. */
@Composable
fun BarChart(
    bars: List<Pair<String, Double>>,
    color: Color,
    modifier: Modifier = Modifier,
    target: Double? = null,
    targetColor: Color = Danger,
    height: Dp = 180.dp,
) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (bars.isEmpty()) return@Canvas
        val labelPx = 11.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val top = 14.dp.toPx()
        val maxV = (bars.maxOf { it.second }.coerceAtLeast(target ?: 0.0)).coerceAtLeast(1.0) * 1.1
        val slot = size.width / bars.size
        bars.forEachIndexed { i, (label, v) ->
            val h = ((bottom - top) * (v / maxV)).toFloat()
            val x = i * slot + slot * 0.2f
            drawRoundRect(
                color, topLeft = Offset(x, bottom - h), size = Size(slot * 0.6f, h),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            text(label, i * slot + slot / 2, size.height - 3.dp.toPx(), axisColor, labelPx, Paint.Align.CENTER)
            if (v > 0) text(fmt(v, 0), i * slot + slot / 2, bottom - h - 3.dp.toPx(), axisColor, labelPx * 0.9f, Paint.Align.CENTER)
        }
        if (target != null) {
            val yy = bottom - ((bottom - top) * (target / maxV)).toFloat()
            drawLine(targetColor, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        }
    }
}
