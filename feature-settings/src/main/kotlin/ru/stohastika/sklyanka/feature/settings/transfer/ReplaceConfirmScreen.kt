package ru.stohastika.sklyanka.feature.settings.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.transfer.TransferCounts
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.matchesConfirmationWord
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.ButtonState
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.PrimaryButtonKind
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderKind
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.component.SklyankaTextField
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Подтверждение режима «Заменить всё» (ПЕР-16 п. 2, критерий 10).
 *
 * Отдельный экран, а не тост и не второе нажатие подряд: действие необратимо, и
 * предупреждение обязано перечислить стираемое **в числах**. Числа стоят двумя колонками
 * «сейчас на устройстве» и «будет из файла» — так видно не только что стирается, но и что
 * приходит взамен, а это и есть вопрос, который человек себе задаёт.
 *
 * Подтверждающая кнопка стоит **не на позиции** кнопки «Выбрать файл» предыдущего экрана:
 * иначе замена случается вторым нажатием подряд по той же точке. Кнопка — видом
 * `Danger` (ОВ-67): опасность не подменяется цветом профиля.
 *
 * Подтверждение — словом: образец приходит ресурсом, сравнение — без учёта регистра
 * и краевых пробелов. Пока слово не введено верно, кнопка недоступна; неверный ввод
 * ошибкой поля не объявляется (ОВ-38 к этому полю не применяется) — поле остаётся
 * обычным, и человеку нечего «исправлять».
 *
 * Экрана в макетах нет; собран из существующих компонентов дизайн-системы (ОВ-67).
 */
// Параметров восемь: числа с двух сторон, имя страховочной копии, слово и его обработчик,
// два действия и модификатор. Свернуть их в контейнер можно, но контейнер здесь — тот же
// список под другим именем, и он спрятал бы, что именно экран показывает. У `SklyankaTextField`
// порог тот же и снят так же.
@Suppress("LongParameterList")
@Composable
fun ReplaceConfirmScreen(
    device: TransferCounts,
    file: TransferCounts,
    safetyCopyName: String,
    confirmationWord: String,
    onConfirmationWordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    val word = stringResource(R.string.transfer_replace_word)
    val wordMatches = matchesConfirmationWord(confirmationWord, word)
    Column(modifier = modifier.fillMaxSize().testTag(TAG_REPLACE_CONFIRM)) {
        ScreenHeader(
            labels = ScreenHeaderLabels(title = stringResource(R.string.transfer_replace_title)),
            kind = ScreenHeaderKind.Nested,
            onBack = onCancel,
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SklyankaSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
        ) {
            Banner(
                text = stringResource(R.string.transfer_replace_warning),
                modifier = Modifier.testTag(TAG_REPLACE_WARNING),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Section)) {
                CountsColumn(
                    title = stringResource(R.string.transfer_replace_now),
                    counts = device,
                    modifier = Modifier.weight(1f).testTag(TAG_COUNTS_NOW),
                )
                CountsColumn(
                    title = stringResource(R.string.transfer_replace_after),
                    counts = file,
                    modifier = Modifier.weight(1f).testTag(TAG_COUNTS_AFTER),
                )
            }
            // Человеку сказано, где лежит страховочная копия (ПЕР-16 п. 3, критерий 10).
            Text(
                text = stringResource(R.string.transfer_replace_safety_copy, safetyCopyName),
                style = SklyankaType.CaptionSmall,
                color = colors.textMuted,
                modifier = Modifier.testTag(TAG_SAFETY_COPY),
            )
            ConfirmationWordBlock(
                word = word,
                value = confirmationWord,
                onValueChange = onConfirmationWordChange,
            )
            // Отступ перед подтверждающей кнопкой: она не должна оказаться под пальцем
            // там, где на прошлом экране была кнопка «Выбрать файл».
            Spacer(modifier = Modifier.padding(top = SklyankaSpacing.Section))
            SecondaryButton(
                text = stringResource(R.string.transfer_replace_cancel),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(R.string.transfer_replace_confirm),
                onClick = onConfirm,
                state = if (wordMatches) ButtonState.Normal else ButtonState.Disabled,
                kind = PrimaryButtonKind.Danger,
                modifier = Modifier.fillMaxWidth().testTag(TAG_REPLACE_CONFIRM_ACTION),
            )
        }
    }
}

/**
 * Подпись-образец и поле слова-подтверждения (ОВ-7/ОВ-67).
 *
 * Поле не переводится в состояние ошибки никогда: неверный ввод виден недоступной
 * кнопкой, а не красной рамкой (ОВ-38 к этому полю не применяется) — человеку нечего
 * «исправлять», и подписи под полем не заводится.
 */
@Composable
private fun ConfirmationWordBlock(word: String, value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro)) {
        Text(
            text = stringResource(R.string.transfer_replace_word_label, word),
            style = SklyankaType.BodyDefault,
            color = SklyankaTheme.colors.text,
            modifier = Modifier.testTag(TAG_REPLACE_WORD_LABEL),
        )
        SklyankaTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().testTag(TAG_REPLACE_WORD_FIELD),
        )
    }
}

/** Колонка чисел: лекарства, архив, профили, история — то, что перечисляет ПЕР-16. */
@Composable
private fun CountsColumn(title: String, counts: TransferCounts, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro)) {
        Text(text = title, style = SklyankaType.Overline, color = colors.textMuted)
        Text(text = medicationsChip(counts.medications), style = SklyankaType.BodyDefault, color = colors.text)
        Text(text = archivedChip(counts.archived), style = SklyankaType.BodyDefault, color = colors.text)
        Text(text = profilesChip(counts.profiles), style = SklyankaType.BodyDefault, color = colors.text)
        Text(text = intakesCount(counts.intakes), style = SklyankaType.BodyDefault, color = colors.text)
        val from = counts.historyFrom
        Text(
            text = if (from == null) {
                stringResource(R.string.transfer_replace_history_empty)
            } else {
                stringResource(R.string.transfer_replace_history_from, from.fullDate())
            },
            style = SklyankaType.CaptionSmall,
            color = colors.textMuted,
        )
    }
}

const val TAG_REPLACE_CONFIRM = "transfer_replace_confirm"
const val TAG_REPLACE_CONFIRM_ACTION = "transfer_replace_confirm_action"
const val TAG_REPLACE_WORD_LABEL = "transfer_replace_word_label"
const val TAG_REPLACE_WORD_FIELD = "transfer_replace_word_field"
const val TAG_REPLACE_WARNING = "transfer_replace_warning"
const val TAG_COUNTS_NOW = "transfer_counts_now"
const val TAG_COUNTS_AFTER = "transfer_counts_after"
const val TAG_SAFETY_COPY = "transfer_safety_copy"
