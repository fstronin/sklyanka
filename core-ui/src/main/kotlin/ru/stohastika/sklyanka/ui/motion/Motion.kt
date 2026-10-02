package ru.stohastika.sklyanka.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.ui.token.SklyankaMotion

enum class MotionPhase {
    SecondLevelEnter,
    SecondLevelExit,
    AdjacentEnter,
    AdjacentExit,
    ReminderEnter,
    ReminderExit,
    SnackbarEnter,
    SnackbarExit,
    SheetEnter,
    SheetExit,
}

data class MotionTiming(val durationMillis: Int, val easing: Easing)

fun motionTiming(phase: MotionPhase): MotionTiming = when (phase) {
    MotionPhase.SecondLevelEnter -> MotionTiming(SklyankaMotion.DurationMedium, SklyankaMotion.EasingEnter)
    MotionPhase.SecondLevelExit -> MotionTiming(SklyankaMotion.DurationMedium, SklyankaMotion.EasingExit)
    MotionPhase.AdjacentEnter -> MotionTiming(SklyankaMotion.DurationShort, SklyankaMotion.EasingEnter)
    MotionPhase.AdjacentExit -> MotionTiming(SklyankaMotion.DurationShort, SklyankaMotion.EasingExit)
    MotionPhase.ReminderEnter -> MotionTiming(SklyankaMotion.DurationMedium, SklyankaMotion.EasingEnter)
    MotionPhase.ReminderExit -> MotionTiming(SklyankaMotion.DurationShort, SklyankaMotion.EasingExit)
    MotionPhase.SnackbarEnter -> MotionTiming(SklyankaMotion.DurationShort, SklyankaMotion.EasingEnter)
    MotionPhase.SnackbarExit -> MotionTiming(SklyankaMotion.DurationFast, SklyankaMotion.EasingExit)
    MotionPhase.SheetEnter -> MotionTiming(SklyankaMotion.DurationMedium, SklyankaMotion.EasingEnter)
    MotionPhase.SheetExit -> MotionTiming(SklyankaMotion.DurationShort, SklyankaMotion.EasingExit)
}

enum class LayerMotion {
    Forward,
    Back,
    Crossfade,
}

private const val TARGET_ON_TOP = 1f
private const val TARGET_UNDER_TOP = -1f

fun AnimatedContentTransitionScope<*>.layerTransform(motion: LayerMotion): ContentTransform = when (motion) {
    LayerMotion.Forward -> ContentTransform(
        targetContentEnter = slideInHorizontally(
            animationSpec = tween(
                durationMillis = motionTiming(MotionPhase.SecondLevelEnter).durationMillis,
                easing = motionTiming(MotionPhase.SecondLevelEnter).easing,
            ),
            initialOffsetX = { width -> width },
        ),
        initialContentExit = ExitTransition.KeepUntilTransitionsFinished,
        targetContentZIndex = TARGET_ON_TOP,
    )

    LayerMotion.Back -> ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = slideOutHorizontally(
            animationSpec = tween(
                durationMillis = motionTiming(MotionPhase.SecondLevelExit).durationMillis,
                easing = motionTiming(MotionPhase.SecondLevelExit).easing,
            ),
            targetOffsetX = { width -> width },
        ),
        targetContentZIndex = TARGET_UNDER_TOP,
    )

    LayerMotion.Crossfade -> ContentTransform(
        targetContentEnter = fadeIn(
            animationSpec = tween(
                durationMillis = motionTiming(MotionPhase.AdjacentEnter).durationMillis,
                easing = motionTiming(MotionPhase.AdjacentEnter).easing,
            ),
        ),
        initialContentExit = fadeOut(
            animationSpec = tween(
                durationMillis = motionTiming(MotionPhase.AdjacentExit).durationMillis,
                easing = motionTiming(MotionPhase.AdjacentExit).easing,
            ),
        ),
        targetContentZIndex = TARGET_ON_TOP,
    )
}

@Composable
fun <S> AnimatedLayer(layer: S, motion: LayerMotion, modifier: Modifier = Modifier, content: @Composable (S) -> Unit) {
    AnimatedContent(
        targetState = layer,
        modifier = modifier,
        transitionSpec = { layerTransform(motion) },
        label = "layer",
    ) { shown ->
        content(shown)
    }
}
