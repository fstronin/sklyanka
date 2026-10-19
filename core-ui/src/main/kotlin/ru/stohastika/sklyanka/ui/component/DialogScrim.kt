package ru.stohastika.sklyanka.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.DialogWindowProvider
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaMotion

/**
 * Затемнение модального слоя приложения (§2.3).
 *
 * Затемнение окна диалога рисует прошивка, и это чёрная подложка: на светлой тёплой теме
 * она читается обрывом экрана, а не затемнением (V-4), и системе цветов не принадлежит.
 * Поэтому затемнение рисует приложение — один слой ролью `scrim` поверх всего окна, —
 * а платформенное затемнение выключается: двух затемнений под одним слоем не бывает.
 *
 * Слой закрывает окно целиком и перехватывает нажатия: фон под слоем недоступен
 * (тап вне слоя закрывает модальный слой — тем же движением, что и раньше).
 *
 * Роль у всех модальных слоёв одна, и доля у неё одна: значение и непрозрачность живут
 * в роли ([ru.stohastika.sklyanka.ui.token.SklyankaColors.scrim]), а не по месту.
 */
@Composable
fun AppDialogScrim(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    DisposableEffect(view) {
        // Окно диалога — чужое: своё затемнение включает приложение, поэтому у окна
        // выключаются и затемнение, и фон темы. Без этого под слоем осталась бы чёрная
        // подложка прошивки, а прозрачности окна не хватило бы, чтобы её перекрыть.
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            window.setDimAmount(PLATFORM_DIM_NONE)
            // Фон окна не заводится вовсе — видно то, что под окном: экран приложения.
            // Своего значения цвета дизайн-система здесь не подставляет (НФТ-47).
            window.setBackgroundDrawable(null)
        }
        onDispose { }
    }
    // Движение — по сценариям шторки и диалога: появление 200 ms, уход 150 ms (§2.3, §8.2),
    // то есть DurationShort / DurationFast шкалы движения.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(SklyankaMotion.DurationShort, easing = SklyankaMotion.EasingEnter)),
        exit = fadeOut(tween(SklyankaMotion.DurationFast, easing = SklyankaMotion.EasingExit)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SklyankaTheme.colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .testTag(TAG_DIALOG_SCRIM),
        )
    }
}

/** Платформенное затемнение выключено: его роль играет [AppDialogScrim]. */
private const val PLATFORM_DIM_NONE = 0f

/** Тег затемнения приложения: им проверяется, что подложка — роль системы, а не прошивки. */
const val TAG_DIALOG_SCRIM = "dialog_scrim"
