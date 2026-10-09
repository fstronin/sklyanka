package ru.stohastika.sklyanka.feature.meds.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.ui.component.ConfirmWordField
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.QuietButtonKind
import ru.stohastika.sklyanka.ui.component.SklyankaBottomSheet
import ru.stohastika.sklyanka.ui.component.TextFieldSurface
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Шторка подтверждения удаления лекарства (ОВ-124, ПР-14, §5.7, §9.3).
 *
 * Собирается из [SklyankaBottomSheet], а не из диалога по месту: тогда затемнение приходит
 * ролью `scrim` (§2.3), а поведение с клавиатурой — из спецификации шторки. Собственной
 * прокрутки у шторки нет: полотно поднимается IME-инсетом целиком, поле слова
 * и подтверждающая кнопка видны целиком при поднятой клавиатуре.
 *
 * Подтверждение — **вводом слова** («Удалить»), как у удаления профиля и режима
 * «Заменить всё»: одно разрушительное действие, требующее слова, и второе, не требующее,
 * учат тому, что порог различает действия, а не последствия. Окно «Отменить» после
 * удаления при этом остаётся: слово защищает от случайного нажатия, окно отмены — от уже
 * случившегося (§5.7).
 */
@Composable
internal fun DeleteMedicationSheet(medicationName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var word by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { visible = true }
    SklyankaBottomSheet(visible = visible, onDismiss = onDismiss) {
        DeleteMedicationPanel(
            medicationName = medicationName,
            word = word,
            onWordChange = { word = it },
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
    }
}

/** Полотно шторки: название, предупреждение, поле слова и два действия. */
@Composable
private fun ColumnScope.DeleteMedicationPanel(
    medicationName: String,
    word: String,
    onWordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
        ConfirmWordField(
            word = stringResource(R.string.medication_delete_word),
            value = word,
            onValueChange = onWordChange,
            confirmLabel = stringResource(R.string.card_delete),
            onConfirm = onConfirm,
            // Поле стоит на белом полотне шторки: подложка — фон среды, иначе граница
            // `Line` на `surface` почти не видна (§4.4).
            surface = TextFieldSurface.Background,
            fieldModifier = Modifier.fillMaxWidth(),
            confirmModifier = Modifier.fillMaxWidth().testTag(TAG_DELETE_CONFIRM),
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
