package com.scrollmate.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scrollmate.app.R
import com.scrollmate.app.ui.theme.ZoneColors
import com.scrollmate.core.coach.HealthCoach
import com.scrollmate.core.coach.ScrollAlternative
import com.scrollmate.core.coach.SleepTopic
import kotlinx.coroutines.delay

/** Animated 4-7-8 breathing circle. */
@Composable
fun BreathingGuide(modifier: Modifier = Modifier) {
    var phase by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(0.6f) }
    val (inhale, hold, exhale) = HealthCoach.BREATHING_PHASES_SECONDS
    LaunchedEffect(Unit) {
        while (true) {
            phase = 0
            scale.animateTo(1f, tween(inhale * 1000, easing = LinearEasing))
            phase = 1
            delay(hold * 1000L)
            phase = 2
            scale.animateTo(0.6f, tween(exhale * 1000, easing = LinearEasing))
        }
    }
    val label = stringResource(
        when (phase) {
            0 -> R.string.breathing_in
            1 -> R.string.breathing_hold
            else -> R.string.breathing_out
        },
    )
    val description = stringResource(R.string.cd_breathing, label)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(3.dp, ZoneColors.Safe, CircleShape)
                    .semantics { contentDescription = description },
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@StringRes
fun alternativeTitle(a: ScrollAlternative): Int = when (a) {
    ScrollAlternative.BREATHING_478 -> R.string.alt_breathing
    ScrollAlternative.SQUATS -> R.string.alt_squats
    ScrollAlternative.WATER -> R.string.alt_water
    ScrollAlternative.STRETCH -> R.string.alt_stretch
    ScrollAlternative.WINDOW -> R.string.alt_window
}

@StringRes
private fun alternativeHow(a: ScrollAlternative): Int = when (a) {
    ScrollAlternative.BREATHING_478 -> R.string.alt_breathing_how
    ScrollAlternative.SQUATS -> R.string.alt_squats_how
    ScrollAlternative.WATER -> R.string.alt_water_how
    ScrollAlternative.STRETCH -> R.string.alt_stretch_how
    ScrollAlternative.WINDOW -> R.string.alt_window_how
}

/** "Statt zu scrollen": quick alternatives with short instructions; breathing is guided. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlternativesCard(suggested: ScrollAlternative, selected: ScrollAlternative?, onSelect: (ScrollAlternative?) -> Unit, modifier: Modifier = Modifier) {
    SectionCard(stringResource(R.string.alternatives_title), modifier) {
        Text(stringResource(R.string.alternatives_desc), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.alternatives_suggested, stringResource(alternativeTitle(suggested))),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScrollAlternative.values().forEach { a ->
                AssistChip(
                    onClick = { onSelect(if (selected == a) null else a) },
                    label = { Text(stringResource(alternativeTitle(a))) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (selected != null) {
            HorizontalDivider()
            Text(stringResource(alternativeTitle(selected)), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(alternativeHow(selected)), style = MaterialTheme.typography.bodyLarge)
            if (selected == ScrollAlternative.BREATHING_478) {
                var breathing by rememberSaveable { mutableStateOf(false) }
                if (breathing) BreathingGuide()
                OutlinedButton(onClick = { breathing = !breathing }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (breathing) R.string.action_stop_breathing else R.string.action_start_breathing))
                }
            }
        }
    }
}

/** Interactive sleep coach: tap a question, get a short, practical answer. */
@Composable
fun SleepCoachCard(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf<SleepTopic?>(null) }
    SectionCard(stringResource(R.string.sleep_title), modifier) {
        Text(stringResource(R.string.sleep_desc), style = MaterialTheme.typography.bodyMedium)
        SleepTopic.values().forEach { topic ->
            val (q, a) = when (topic) {
                SleepTopic.WHY_WORSE_SLEEP -> R.string.sleep_q_why to R.string.sleep_a_why
                SleepTopic.FALL_ASLEEP -> R.string.sleep_q_fall_asleep to R.string.sleep_a_fall_asleep
                SleepTopic.RACING_THOUGHTS -> R.string.sleep_q_thoughts to R.string.sleep_a_thoughts
                SleepTopic.NIGHT_WAKING -> R.string.sleep_q_night to R.string.sleep_a_night
            }
            TextButton(onClick = { open = if (open == topic) null else topic }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(q), modifier = Modifier.fillMaxWidth())
            }
            if (open == topic) {
                Text(
                    stringResource(a),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Text(stringResource(R.string.sleep_disclaimer), style = MaterialTheme.typography.bodySmall)
    }
}
