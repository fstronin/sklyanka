package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import ru.stohastika.sklyanka.ui.motion.MotionPhase
import ru.stohastika.sklyanka.ui.motion.motionTiming

@Composable
internal fun ReminderFade(
    visible: Boolean,
    closing: Boolean,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) entered = true }
    val enterTiming = motionTiming(MotionPhase.ReminderEnter)
    val exitTiming = motionTiming(MotionPhase.ReminderExit)
    val alpha by animateFloatAsState(
        targetValue = if (entered && !closing) 1f else 0f,
        animationSpec = if (closing) {
            tween(exitTiming.durationMillis, easing = exitTiming.easing)
        } else {
            tween(enterTiming.durationMillis, easing = enterTiming.easing)
        },
        finishedListener = { if (closing) onClosed() },
        label = "reminderFade",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    ) {
        content()
    }
}
