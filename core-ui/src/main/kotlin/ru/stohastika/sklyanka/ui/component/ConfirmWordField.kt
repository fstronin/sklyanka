package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Подтверждение разрушительного действия словом (Р-21-смежное ПЕР-16, ПРФ-29, ПР-14; §5.7).
 *
 * Поле слова, подпись-образец и подтверждающая кнопка — **один компонент** дизайн-системы,
 * а не копия на каждом экране: у копий общие не только вид и подпись, но и правило
 * сравнения, а второе правило сравнения — это второй ответ на вопрос «подтверждено ли
 * действие». Потребителей трое — удаление профиля, подтверждение замены, шторка удаления
 * лекарства, — и отличаются они только словом и подписью кнопки.
 *
 * Подпись-образец показана видимой строкой до поля: человек копирует слово глазами,
 * а не по памяти, и сравнение идёт тем же словом, которое напечатано. Состояния ошибки
 * у поля нет: пока слово не совпало, кнопка недоступна, — второе объяснение было бы
 * вторым ответом на один вопрос (§5.7).
 *
 * @param word слово действия — ресурс экрана (у удаления и замены слова разные)
 * @param value текущее значение поля
 * @param onValueChange обработчик ввода
 * @param confirmLabel подпись подтверждающей кнопки — тоже своя у каждого потребителя
 * @param busy состояние занятости подтверждающей кнопки
 * @param surface подложка поля: на белом полотне шторки — [TextFieldSurface.Background]
 */
// Три модификатора у трёх частей (подпись, поле, кнопка) — цена того, что компонент
// один на трёх потребителей: экран ставит на них свои теги и поля, а вид остаётся общим.
@Suppress("LongParameterList")
@Composable
fun ConfirmWordField(
    word: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    surface: TextFieldSurface = TextFieldSurface.Surface,
    labelModifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    confirmModifier: Modifier = Modifier,
) {
    val matches = matchesConfirmationWord(value, word)
    val fieldDescription = stringResource(R.string.cd_confirm_word_field)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large)) {
        Text(
            text = stringResource(R.string.confirm_word_label, word),
            style = SklyankaType.BodyDefault,
            color = SklyankaTheme.colors.text,
            modifier = labelModifier.testTag(TAG_CONFIRM_WORD_LABEL),
        )
        SklyankaTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = CONFIRM_WORD_KEYBOARD,
            surface = surface,
            modifier = fieldModifier
                .semantics { contentDescription = fieldDescription }
                .testTag(TAG_CONFIRM_WORD_FIELD),
        )
        PrimaryButton(
            text = confirmLabel,
            onClick = onConfirm,
            state = when {
                busy -> ButtonState.Busy
                matches -> ButtonState.Normal
                else -> ButtonState.Disabled
            },
            kind = PrimaryButtonKind.Danger,
            modifier = confirmModifier,
        )
    }
}

/**
 * Совпадает ли введённое слово с образцом (ОВ-7, ОВ-67; §5.7).
 *
 * Краевые пробелы не считаются вводом, регистр не различается: слово набирают
 * с клавиатуры телефона, и требование точного совпадения наказывало бы за лишний
 * пробел или заглавную букву, а не защищало от случайного нажатия. Внутренние
 * пробелы значимы — «Заменить всё» не равно «Заменить».
 *
 * Образец приходит из ресурсов (НФТ-42): русского текста в этом файле нет. Правило
 * одно на все разрушительные действия, и живёт оно рядом с компонентом, который его
 * применяет: второе правило сравнения — это второй ответ на вопрос «подтверждено ли».
 */
fun matchesConfirmationWord(input: String, word: String): Boolean = input.trim().equals(word.trim(), ignoreCase = true)

/**
 * Режим ввода слова-подтверждения.
 *
 * Автозамена и автокапитализация выключены: система не «улучшает» слово, которое человек
 * копирует с образца, — подменённое автозаменой слово это уже другое слово, и подтверждение
 * не сработало бы (§5.7).
 */
private val CONFIRM_WORD_KEYBOARD = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
)

const val TAG_CONFIRM_WORD_LABEL = "confirm_word_label"
const val TAG_CONFIRM_WORD_FIELD = "confirm_word_field"
