package ru.stohastika.sklyanka.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import ru.stohastika.sklyanka.ui.motion.MotionPhase
import ru.stohastika.sklyanka.ui.motion.MotionTiming
import ru.stohastika.sklyanka.ui.motion.motionTiming
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaMotion
import ru.stohastika.sklyanka.ui.token.SklyankaShapes

/**
 * Нижняя шторка — модальный слой действия (§4.18, ОВ-15/ОВ-125).
 *
 * Полотно снизу во всю ширину **по высоте содержимого**, а не на весь экран: пустой
 * середины не остаётся. Затемнение `DarkBackground` 32 % покрывает экран целиком,
 * включая системные панели, и перехватывает нажатия: фон под слоем недоступен.
 *
 * Ручки и приёма «потянуть вниз» нет: ручка обещает жест, которого в системе нет.
 * Закрывают шторку «Отмена», системная «Назад» и тап по затемнению — все три означают
 * отмену. Движение — §9.3, сценарии 8/9; при системном уменьшении движения Compose
 * гасит длительности сам, конечные состояния совпадают (§9.4).
 *
 * [visible] управляет появлением и исчезновением, но содержимое остаётся скомпонованным
 * на время ухода: вызывающий обязан держать последнее непустое содержимое (так же,
 * как это делал прежний экранный хост шторки).
 */
@Composable
fun SklyankaBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val enter = motionTiming(MotionPhase.SheetEnter)
    val exit = motionTiming(MotionPhase.SheetExit)
    Box(modifier = modifier.fillMaxSize().testTag(TAG_BOTTOM_SHEET)) {
        SheetScrim(visible = visible, enter = enter, exit = exit, onDismiss = onDismiss)
        SheetSurface(visible = visible, enter = enter, exit = exit, content = content)
    }
}

/** Затемнение шторки: закрывает экран целиком и перехватывает нажатия — фон под слоем недоступен. */
@Composable
private fun SheetScrim(visible: Boolean, enter: MotionTiming, exit: MotionTiming, onDismiss: () -> Unit) {
    val scrim = SklyankaTheme.colors.scrim
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(SCRIM_ENTER_MS, easing = enter.easing)),
        exit = fadeOut(tween(SCRIM_EXIT_MS, easing = exit.easing)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .testTag(TAG_BOTTOM_SHEET_SCRIM),
        )
    }
}

/** Полотно шторки: снизу по высоте содержимого, над клавиатурой и не выше статус-бара. */
@Composable
private fun SheetSurface(
    visible: Boolean,
    enter: MotionTiming,
    exit: MotionTiming,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SklyankaTheme.colors
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(enter.durationMillis, easing = enter.easing),
            initialOffsetY = { height -> height },
        ),
        exit = slideOutVertically(
            animationSpec = tween(exit.durationMillis, easing = exit.easing),
            targetOffsetY = { height -> height },
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Инсеты — на контейнере выравнивания, а не на полотне: полотно высотой
        // по содержимому встаёт нижней гранью на верх клавиатуры (или системной
        // панели), а над ним остаётся затемнённый экран (ОВ-140, §4.18, AC-34).
        // Панель навигации и клавиатура объединяются, а не складываются: при
        // открытой клавиатуре полотно поднимается на её высоту, а не на
        // «клавиатура + панель».
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            // Полотно не поднимается выше статус-бара: длинное содержимое
            // прокручивается внутри, а не уходит под вырез экрана.
            val sheetMaxHeight = maxHeight * SHEET_MAX_HEIGHT_FRACTION
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = sheetMaxHeight)
                    // Смена состава меняет высоту полотна: без анимации шторка
                    // прыгает на новую высоту одним кадром (SKL-101, дефект 1).
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = SklyankaMotion.DurationShort,
                            easing = SklyankaMotion.EasingEnter,
                        ),
                    )
                    .clip(SklyankaShapes.TopLarge)
                    .background(colors.surface)
                    .testTag(TAG_BOTTOM_SHEET_SURFACE),
                verticalArrangement = Arrangement.Top,
                content = content,
            )
        }
    }
}

const val TAG_BOTTOM_SHEET = "bottom_sheet"
const val TAG_BOTTOM_SHEET_SCRIM = "bottom_sheet_scrim"
const val TAG_BOTTOM_SHEET_SURFACE = "bottom_sheet_surface"

/** Затемнение: появление 200 ms, уход 150 ms — §9.3, сценарий 8/9. */
private const val SCRIM_ENTER_MS = 200
private const val SCRIM_EXIT_MS = 150

/** Потолок высоты полотна: полотно не поднимается выше статус-бара. */
private const val SHEET_MAX_HEIGHT_FRACTION = 0.9f
