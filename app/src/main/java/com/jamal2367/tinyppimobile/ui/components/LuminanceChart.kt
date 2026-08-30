package com.jamal2367.tinyppimobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jamal2367.tinyppimobile.ui.theme.ChartAverage
import com.jamal2367.tinyppimobile.ui.theme.ChartPeak
import com.jamal2367.tinyppimobile.util.Formatters
import kotlin.math.max

/** One line of the chart: what it is called, what colour it is, and its points. */
data class ChartSeries(
    val label: String,
    val color: Color,
    /** Seconds since the title started, paired with the reading at that moment. */
    val points: List<Pair<Double, Double>>,
)

/**
 * The scene luminance of the playing title, over time.
 *
 * Two lines rather than a band: the peak is what a grade is judged by and the
 * average is what a room actually sees, and the distance between them is the
 * reading - a film that sits at 100 nits and spikes to 4000 looks nothing like
 * one that holds 800 throughout, and a filled band would show both as a shape.
 *
 * Drawn on a canvas rather than assembled out of composables. There are up to
 * 3600 samples behind this and they move once a second; a composable per point
 * would be a recomposition of thousands of nodes for a line that moved by one
 * pixel.
 */
@Composable
fun LuminanceChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    /** The window the chart covers, in seconds, or null for everything it holds. */
    windowSeconds: Int? = null,
    height: Int = 160,
) {
    val points = series.flatMap { it.points }
    if (points.isEmpty()) return

    val newest = points.maxOf { it.first }
    val oldest = windowSeconds?.let { max(0.0, newest - it) } ?: points.minOf { it.first }
    // A chart of one moment has no width to draw across; a second of span is
    // what keeps the first sample of a title from dividing by zero.
    val span = max(1.0, newest - oldest)

    val visible = series.map { line ->
        line.copy(points = line.points.filter { it.first >= oldest })
    }
    val ceiling = visible.flatMap { it.points }.maxOfOrNull { it.second } ?: return
    // Rounded up to something a reader can name rather than to the highest
    // sample: a top of 3847 nits makes every line touch the frame, and says
    // less than a top of 4000 with the line just under it.
    val top = niceCeiling(ceiling)

    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = Formatters.nits(top).orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .padding(vertical = 4.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(height.dp)) {
                drawGrid(grid)
                visible.forEach { line ->
                    drawSeries(line, oldest = oldest, span = span, top = top)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = Formatters.elapsed(oldest),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Formatters.elapsed(newest),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            visible.forEach { line ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusDot(color = line.color, size = 8)
                    Text(
                        text = line.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The colours the two standard lines are drawn in. */
object ChartColors {
    val peak = ChartPeak
    val average = ChartAverage
}

/** Three lines across, which is enough to read a height off and few enough to ignore. */
private fun DrawScope.drawGrid(color: Color) {
    for (step in 1..3) {
        val y = size.height * step / 4f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
    }
}

private fun DrawScope.drawSeries(
    series: ChartSeries,
    oldest: Double,
    span: Double,
    top: Double,
) {
    if (series.points.size < 2) return

    val path = Path()
    series.points.forEachIndexed { index, (at, value) ->
        val x = ((at - oldest) / span).toFloat().coerceIn(0f, 1f) * size.width
        val y = size.height - (value / top).toFloat().coerceIn(0f, 1f) * size.height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    drawPath(
        path = path,
        color = series.color,
        style = Stroke(width = 2.5f),
    )
}

/**
 * A top for the axis that reads as a number rather than as a measurement.
 *
 * The steps are the ones a grade is actually talked about in - 100, 400, 1000,
 * 4000, 10000 nits - so the frame of the chart says something about the film
 * before a single line is looked at.
 */
private fun niceCeiling(highest: Double): Double {
    val steps = listOf(100.0, 200.0, 400.0, 600.0, 1000.0, 2000.0, 4000.0, 10000.0)
    return steps.firstOrNull { it >= highest } ?: highest
}
