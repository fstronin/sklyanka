package ru.stohastika.sklyanka.feature.meds.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.PrimaryButtonKind
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.QuietButtonKind
import ru.stohastika.sklyanka.ui.motion.MotionPhase
import ru.stohastika.sklyanka.ui.motion.motionTiming
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Шторка подтверждения удаления лекарства (ОВ-124, §9.3).
 *
 * Не экран: подтверждается одна запись, и предмет назван в заголовке. Полотно выезжает
 * снизу (`SheetEnter`/`SheetExit`), затемнение фона и закрытие по системной «Назад»
 * и тапу вне даёт диалоговое окно — своего слоя затемнения дизайн-система не заводит.
 *
 * Основная кнопка — вид `Danger`: заливка опасности живёт только на подтверждении,
 * а не на постоянной кнопке карточки, иначе удаление читалось бы как главное действие.
 */
@Composable
internal fun DeleteMedicationSheet(medicationName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val enter = motionTiming(MotionPhase.SheetEnter)
    val exit = motionTiming(MotionPhase.SheetExit)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
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
            ) {
                DeleteMedicationPanel(
                    medicationName = medicationName,
                    onDismiss = onDismiss,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}

/**
 * Полотно шторки: заголовок с названием, предупреждение и два действия.
 *
 * Вынесено из [DeleteMedicationSheet] ради читаемости: слой анимации и слой содержания
 * отвечают на разные вопросы, и путать их в одной функции незачем.
 */
@Composable
private fun DeleteMedicationPanel(medicationName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .navigationBarsPadding()
            .padding(SklyankaSpacing.Section)
            .testTag(TAG_DELETE_SHEET),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        Text(
            text = stringResource(R.string.card_delete_sheet_title, medicationName),
            style = SklyankaType.Subtitle,
            color = colors.text,
            modifier = Modifier.testTag(TAG_DELETE_SHEET_TITLE),
        )
        Text(
            text = stringResource(R.string.card_delete_sheet_text),
            style = SklyankaType.BodyDefault,
            color = colors.text,
            modifier = Modifier.testTag(TAG_DELETE_SHEET_TEXT),
        )
        PrimaryButton(
            text = stringResource(R.string.card_delete),
            onClick = onConfirm,
            kind = PrimaryButtonKind.Danger,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_DELETE_CONFIRM),
        )
        QuietButton(
            text = stringResource(UiR.string.action_cancel),
            onClick = onDismiss,
            kind = QuietButtonKind.Muted,
            modifier = Modifier.testTag(TAG_DELETE_SHEET_CANCEL),
        )
    }
}

const val TAG_DELETE_SHEET = "med_card_delete_sheet"
const val TAG_DELETE_SHEET_TITLE = "med_card_delete_sheet_title"
const val TAG_DELETE_SHEET_TEXT = "med_card_delete_sheet_text"
const val TAG_DELETE_CONFIRM = "med_card_delete_confirm"
const val TAG_DELETE_SHEET_CANCEL = "med_card_delete_sheet_cancel"
