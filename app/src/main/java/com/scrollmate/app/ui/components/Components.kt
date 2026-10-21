package com.scrollmate.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrollmate.app.R
import com.scrollmate.core.model.DataSourceState
import java.time.Duration

/** Always-visible indicator of the data source: Live-Daten, Berechtigung fehlt or Demo-Daten. */
@Composable
fun StatusChip(state: DataSourceState, modifier: Modifier = Modifier) {
    val (label, icon, container) = when (state) {
        DataSourceState.LIVE -> Triple(
            stringResource(R.string.status_live), Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primaryContainer,
        )
        DataSourceState.PERMISSION_MISSING -> Triple(
            stringResource(R.string.status_permission_missing), Icons.Filled.Info, MaterialTheme.colorScheme.tertiaryContainer,
        )
        DataSourceState.DEMO -> Triple(
            stringResource(R.string.status_demo), Icons.Filled.Star, MaterialTheme.colorScheme.secondaryContainer,
        )
    }
    val description = stringResource(R.string.cd_status_chip, label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .glass(RoundedCornerShape(999.dp))
            .background(container.copy(alpha = 0.35f))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Shown on every screen that displays demo numbers. */
@Composable
fun DemoNotice(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().glass(MaterialTheme.shapes.medium).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.demo_notice), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    stroke: Dp = 14.dp,
    center: @Composable () -> Unit,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
            val inset = stroke.toPx() / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = s)
            if (fraction > 0f) {
                drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft = topLeft, size = arcSize, style = s)
            }
        }
        center()
    }
}

/** Glass card with a coloured edge and soft glow, used for the limit status (safe / warning / exceeded). */
@Composable
fun GlowCard(
    glow: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 24.dp, shape = shape, ambientColor = glow, spotColor = glow)
            .glass(shape, strong = true)
            .border(BorderStroke(1.5.dp, glow.copy(alpha = 0.55f)), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .glass(MaterialTheme.shapes.large)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        content()
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.state_loading)
    Box(modifier.fillMaxSize().semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.state_error), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_retry))
        }
    }
}

/** Permission state: offers both ways forward, never forces one. */
@Composable
fun PermissionCard(onGrant: () -> Unit, onUseDemo: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(stringResource(R.string.permission_card_title), modifier) {
        Text(stringResource(R.string.permission_card_body), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_grant_access)) }
        OutlinedButton(onClick = onUseDemo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_use_demo)) }
    }
}

/** Accessible -/+ control with 48 dp touch targets. */
@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    val decrease = stringResource(R.string.cd_decrease, label)
    val increase = stringResource(R.string.cd_increase, label)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        FilledTonalIconButton(
            onClick = onMinus,
            enabled = minusEnabled,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = decrease },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(
            onClick = onPlus,
            enabled = plusEnabled,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = increase },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
fun InfoRow(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier.widthIn(max = 600.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** "1 Std. 32 Min." / "45 Min." */
@Composable
fun durationText(d: Duration): String {
    val total = d.toMinutes().coerceAtLeast(0)
    val h = total / 60
    val m = total % 60
    return if (h > 0) stringResource(R.string.duration_h_min, h.toInt(), m.toInt())
    else stringResource(R.string.duration_min, m.toInt())
}
