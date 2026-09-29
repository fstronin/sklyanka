package ru.stohastika.sklyanka.feature.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Составные части экранов лекарств, которых нет в каталоге дизайн-системы.
 *
 * Сегментный переключатель, плитка формы выпуска и ряд счётчика компонентами системы
 * не являются (`TDD.md §5.7`) и собираются из токенов. Собираются здесь, а не по месту
 * на двух экранах: одинаковый на вид элемент, набранный дважды, расходится на первой же
 * правке токена.
 */

/** Надпись над блоком: «ФОРМА ВЫПУСКА», «ВРЕМЯ ПРИЁМА». */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(InterfaceLocale.VALUE),
        style = SklyankaType.Overline,
        color = SklyankaTheme.colors.textMuted,
        modifier = modifier,
    )
}

/** Одна позиция сегментного переключателя. */
internal data class Segment(val label: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * Сегментный переключатель на две позиции.
 *
 * Выбранная вкладка отличается не только цветом: подложка становится поверхностью,
 * а подпись — полужирной (НФТ-33).
 */
@Composable
internal fun SegmentedControl(segments: List<Segment>, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.neutralSoft, SklyankaShapes.Button)
            .padding(SklyankaSpacing.Micro)
            .testTag(TAG_SEGMENTED_CONTROL),
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
    ) {
        segments.forEach { segment ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SklyankaSizes.SegmentHeight)
                    .then(
                        if (segment.selected) {
                            Modifier.background(colors.surface, SklyankaShapes.Compact)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(role = Role.Tab, onClick = segment.onClick)
                    .padding(horizontal = SklyankaSpacing.Small),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = segment.label,
                    style = if (segment.selected) SklyankaType.LabelStrong else SklyankaType.Label,
                    color = if (segment.selected) colors.text else colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Выбор из плиток: формы выпуска и связь с едой.
 *
 * Высота приходит параметром, потому что плитка формы выпуска в макете 78 dp с иконкой,
 * а кнопка связи с едой — 44 dp без неё; всё остальное у них общее.
 */
@Composable
internal fun ChoiceTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: (@Composable () -> Unit)? = null,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .background(
                if (selected) colors.accentSoft else colors.surface,
                SklyankaShapes.Large,
            )
            .border(
                if (selected) SklyankaSizes.BorderAccent else SklyankaSizes.BorderThin,
                if (selected) colors.accent else colors.line,
                SklyankaShapes.Large,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(SklyankaSpacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small, Alignment.CenterVertically),
    ) {
        glyph?.invoke()
        Text(
            text = label,
            style = if (selected) SklyankaType.CaptionStrong else SklyankaType.Caption,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Круглая кнопка с одним знаком: «Уменьшить», «Увеличить», удаление времени.
 *
 * Подпись для чтения с экрана обязательна: видимого текста у кнопки нет вовсе (НФТ-30).
 * Цель нажатия доводится до 44 dp модификатором, а не увеличением рисунка.
 */
@Composable
internal fun GlyphButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
    enabled: Boolean = true,
    glyph: @Composable () -> Unit,
) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = modifier
            .minTouchTarget()
            .then(
                if (bordered) {
                    Modifier
                        .background(colors.background, SklyankaShapes.Pill)
                        .border(SklyankaSizes.BorderThin, colors.line, SklyankaShapes.Pill)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        glyph()
    }
}

/** Карточка-группа настроек: «Остаток в упаковке», «Период приёма». */
@Composable
internal fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Large)
            .border(SklyankaSizes.BorderThin, colors.line, SklyankaShapes.Large),
    ) {
        content()
    }
}

/** Разделитель строк внутри карточки-группы. */
@Composable
internal fun RowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SklyankaSizes.DividerThickness)
            .background(SklyankaTheme.colors.line),
    )
}

/**
 * Строка «подпись — значение» внутри карточки-группы.
 *
 * Приглушённая строка («Окончание» при включённой бессрочности) отличается цветом
 * и недоступностью нажатия: «недоступно» и «выглядит недоступным» обязаны совпадать.
 */
@Composable
internal fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = SklyankaSpacing.Block, vertical = SklyankaSpacing.Large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        Text(
            text = label,
            style = SklyankaType.BodyDefault,
            color = if (enabled) colors.text else colors.textDisabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (enabled) SklyankaType.BodyDefaultStrong else SklyankaType.BodyDefault,
            color = if (enabled) colors.text else colors.textDisabled,
        )
    }
}

/**
 * Подписи ряда со счётчиком одним значением.
 *
 * Четыре строки подряд в подписи компонента путают местами на первом же вызове —
 * а две из них читает программа чтения с экрана, и перепутанные они не заметны глазом.
 */
internal data class CounterLabels(val title: String, val caption: String, val decrease: String, val increase: String)

/**
 * Ряд со счётчиком: «Приёмов в сутки» и, при режиме «по потребности», ограничение (Д-1).
 *
 * Число между кнопками имеет наименьшую ширину, иначе переход с «9» на «10» дёргает
 * весь ряд.
 */
@Composable
internal fun CounterCard(
    labels: CounterLabels,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    SectionCard(modifier = modifier.testTag(TAG_COUNTER_CARD)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SklyankaSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair),
            ) {
                Text(text = labels.title, style = SklyankaType.BodyDefaultStrong, color = colors.text)
                Text(text = labels.caption, style = SklyankaType.CaptionSmall, color = colors.textMuted)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
            ) {
                GlyphButton(contentDescription = labels.decrease, onClick = onDecrease) {
                    MinusGlyph(color = colors.textMuted)
                }
                Text(
                    text = value,
                    style = SklyankaType.BodyLarge,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(SklyankaSizes.CounterValue),
                )
                GlyphButton(contentDescription = labels.increase, onClick = onIncrease) {
                    PlusGlyph(color = colors.textMuted)
                }
            }
        }
    }
}

/** Плашка значка формы выпуска в карточке списка. */
@Composable
internal fun FormPlate(glyph: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(SklyankaSizes.IconPlate)
            .background(SklyankaTheme.colors.neutralSoft, SklyankaShapes.Button),
        contentAlignment = Alignment.Center,
    ) {
        glyph()
    }
}

/** Боковое поле экрана; вынесено, чтобы список и форма не разъезжались по краям. */
internal fun Modifier.screenPadding(): Modifier = padding(horizontal = SklyankaSpacing.Section)

/** Опоры экранных тестов. */
const val TAG_SEGMENTED_CONTROL = "meds_segmented_control"
const val TAG_COUNTER_CARD = "meds_counter_card"
