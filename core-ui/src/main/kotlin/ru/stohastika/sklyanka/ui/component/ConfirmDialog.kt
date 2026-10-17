package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Диалог подтверждения о двух действиях (§4.14, Р-87 п. 1).
 *
 * Оболочка — та же, что у диалога времени и выбора значения: поверхность с рамкой и
 * радиусом `Large`, затемнение ролью `scrim` (§2.3), без Material-тени. Отличие — состав:
 * заголовок, пояснение и два действия, а не механизм выбора. Диалог общий для любого
 * подтверждения «остаться / сделать», поэтому подписи приходят параметрами, а не из
 * ресурсов системы: текст знает вызывающий, компонент отвечает только за вид.
 *
 * Действие подтверждения по умолчанию ведёт себя как опасное ([QuietButtonKind.Danger]):
 * подтверждение обычно означает необратимый шаг (сброс ввода, отказ от продолжения).
 * Отмена — тихая нейтральная, и она же исход системной «Назад» и тапа вне окна
 * ([onDismiss]): безопасный выход не делает необратимого шага.
 */
@Composable
fun ConfirmDialog(
    texts: ConfirmDialogTexts,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmKind: QuietButtonKind = QuietButtonKind.Danger,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppDialogScrim(onDismiss = onDismiss)
            ConfirmDialogSurface(texts, onConfirm, onDismiss, modifier, confirmKind)
        }
    }
}

/** Поверхность диалога: рамка и радиус оболочки выбора, состав — заголовок, пояснение, два действия. */
@Composable
private fun ConfirmDialogSurface(
    texts: ConfirmDialogTexts,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
    confirmKind: QuietButtonKind,
) {
    val colors = SklyankaTheme.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(SklyankaSpacing.Screen)
            .testTag(TAG_CONFIRM_DIALOG),
        shape = SklyankaShapes.Large,
        color = colors.surface,
        border = BorderStroke(SklyankaSizes.BorderThin, colors.lineOnSurface),
    ) {
        Column(
            modifier = Modifier.padding(SklyankaSpacing.Block),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            Text(
                text = texts.title,
                style = SklyankaType.BodyDefaultStrong,
                color = colors.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_CONFIRM_TITLE),
            )
            Text(
                text = texts.message,
                style = SklyankaType.BodyDefault,
                color = colors.textMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_CONFIRM_MESSAGE),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton(
                    text = texts.dismissLabel,
                    onClick = onDismiss,
                    kind = QuietButtonKind.Muted,
                    form = QuietButtonForm.Header,
                    modifier = Modifier.testTag(TAG_CONFIRM_DISMISS),
                )
                QuietButton(
                    text = texts.confirmLabel,
                    onClick = onConfirm,
                    kind = confirmKind,
                    form = QuietButtonForm.Header,
                    modifier = Modifier.testTag(TAG_CONFIRM_CONFIRM),
                )
            }
        }
    }
}

/** Теги диалога подтверждения: экранная проверка отличает его действия друг от друга. */
public const val TAG_CONFIRM_DIALOG: String = "confirm_dialog"
public const val TAG_CONFIRM_TITLE: String = "confirm_title"
public const val TAG_CONFIRM_MESSAGE: String = "confirm_message"
public const val TAG_CONFIRM_DISMISS: String = "confirm_dismiss"
public const val TAG_CONFIRM_CONFIRM: String = "confirm_confirm"
