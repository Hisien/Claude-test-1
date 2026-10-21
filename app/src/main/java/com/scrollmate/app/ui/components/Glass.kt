package com.scrollmate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrollmate.app.ui.theme.LocalGlass

/** Night ground with three soft light spots; everything else sits on top of it. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val g = LocalGlass.current
    Box(
        modifier
            .fillMaxSize()
            .background(g.ground)
            .drawBehind {
                val w = size.width
                val h = size.height
                val r = maxOf(w, h)
                val centres = listOf(Offset(w * 0.12f, h * 0.06f), Offset(w * 0.95f, h * 0.32f), Offset(w * 0.3f, h * 0.95f))
                g.spots.zip(centres).forEach { (color, c) ->
                    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = c, radius = r * 0.55f), radius = r * 0.55f, center = c)
                }
            },
        content = content,
    )
}

/** Translucent glass surface with a thin bright edge. */
@Composable
fun Modifier.glass(shape: Shape, strong: Boolean = false, edge: Dp = 1.dp): Modifier {
    val g = LocalGlass.current
    return this
        .clip(shape)
        .background(if (strong) g.fillStrong else g.fill)
        .border(edge, g.edge, shape)
}
