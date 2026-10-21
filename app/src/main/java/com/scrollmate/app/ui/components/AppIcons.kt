package com.scrollmate.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Modern line icons (24 × 24 grid, rounded strokes) for the tabs and the header.
 * The "filled" variants are used for the selected tab.
 */
object AppIcons {

    private fun icon(name: String, strokes: List<String>, fills: List<String> = emptyList(), width: Float = 1.9f): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        fills.forEach { d ->
            b.addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        }
        strokes.forEach { d ->
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0 Z"

    private const val HOME = "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"
    private const val CHAT = "M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"
    private const val CHART_FRAME = "M3 21h18"

    val Today: ImageVector = icon("Today", listOf(HOME))
    val TodayFilled: ImageVector = icon("TodayFilled", listOf(HOME), fills = listOf(HOME))

    val Coach: ImageVector = icon("Coach", listOf(CHAT, "M8.5 11.5h.01M12 11.5h.01M15.5 11.5h.01"), width = 1.9f)
    val CoachFilled: ImageVector = icon("CoachFilled", listOf(CHAT), fills = listOf(CHAT))

    val Focus: ImageVector = icon("Focus", listOf(circle(12f, 13f, 8f), "M12 9v4l2.5 2", "M9.5 2h5"))
    val FocusFilled: ImageVector = icon("FocusFilled", listOf(circle(12f, 13f, 8f), "M12 9v4l2.5 2", "M9.5 2h5"), width = 2.6f)

    val History: ImageVector = icon("History", listOf("M6 17V11", "M12 17V6", "M18 17v-4", CHART_FRAME))
    val HistoryFilled: ImageVector = icon(
        "HistoryFilled",
        listOf(CHART_FRAME),
        fills = listOf("M4.5 10.5h3v7h-3z", "M10.5 5h3v12.5h-3z", "M16.5 12h3v5.5h-3z"),
    )

    private val SLIDERS = listOf("M4 6h9", "M17 6h3", "M4 12h3", "M11 12h9", "M4 18h11", "M19 18h1")
    val Settings: ImageVector = icon("Settings", SLIDERS + listOf(circle(15f, 6f, 2f), circle(9f, 12f, 2f), circle(17f, 18f, 2f)))
    val SettingsFilled: ImageVector = icon(
        "SettingsFilled",
        SLIDERS,
        fills = listOf(circle(15f, 6f, 2.6f), circle(9f, 12f, 2.6f), circle(17f, 18f, 2.6f)),
    )

    val Bell: ImageVector = icon("Bell", listOf("M6 8a6 6 0 1 1 12 0c0 7 3 8 3 8H3s3-1 3-8", "M10.3 21a1.94 1.94 0 0 0 3.4 0"))
    val Moon: ImageVector = icon("Moon", listOf("M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"))
    val ChatSmall: ImageVector = icon("ChatSmall", listOf(CHAT))
}
