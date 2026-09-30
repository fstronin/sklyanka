package ru.stohastika.sklyanka.feature.settings.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import ru.stohastika.sklyanka.ui.component.ChipState
import ru.stohastika.sklyanka.ui.component.SklyankaChip
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Карточка экрана переноса: белая поверхность с заголовком и подписью.
 *
 * Собрана из существующих токенов дизайн-системы, своих значений не вводит: радиус
 * карточки, линия и отступы — те же, что у остальных карточек приложения.
 */
@Composable
internal fun TransferCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair)) {
            Text(text = title, style = SklyankaType.BodyStrong, color = colors.text)
            Text(text = subtitle, style = SklyankaType.CaptionSmall, color = colors.textMuted)
        }
        content()
    }
}

/**
 * Один из двух режимов загрузки (ПЕР-14).
 *
 * Выбранное состояние обозначено не только цветом: рамка утолщается, а подпись становится
 * полужирной (НФТ-33). Роль `RadioButton` — для чтения с экрана: человеку, который слушает,
 * должно быть понятно, что из двух вариантов выбран один.
 */
@Composable
internal fun ModeOption(
    title: String,
    hint: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    val border = if (selected) colors.accent else colors.lineInteractive
    val width = if (selected) SklyankaSizes.BorderAccent else SklyankaSizes.BorderThin
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .minTouchTarget()
            .background(if (selected) colors.background else colors.surface, SklyankaShapes.Medium)
            .border(width, border, SklyankaShapes.Medium)
            .padding(horizontal = SklyankaSpacing.Large, vertical = SklyankaSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
    ) {
        Text(
            text = title,
            style = if (selected) SklyankaType.LabelStrong else SklyankaType.Label,
            color = colors.text,
        )
        Text(text = hint, style = SklyankaType.CaptionSmall, color = colors.textMuted)
    }
}

/**
 * Строка чипов состава.
 *
 * Чипы собираются из тех же чисел, что уходят в файл (критерий 3): набор — «N лекарств»,
 * «N в архиве», «история за N месяцев», «N профиля», «настройки». Чипа «звуки» из макета
 * здесь нет, а чип «настройки» есть — решение ОВ-66, `task.md` «Дефекты постановки».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompositionChips(
    medications: Int,
    archived: Int,
    historyMonths: Int?,
    profiles: Int,
    settingsLabel: String,
    modifier: Modifier = Modifier,
) {
    // Поток, а не два фиксированных ряда: число и длина чипов зависят от данных, и на
    // реальной ширине последний чип получал нулевую ширину и разворачивался по буквам
    // (SKL-60, дефект пилота). Чип переносится целиком.
    FlowRow(
        modifier = modifier.fillMaxWidth().testTag(TAG_COMPOSITION),
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        InfoChip(medicationsChip(medications))
        InfoChip(archivedChip(archived))
        if (historyMonths != null) InfoChip(historyChip(historyMonths))
        InfoChip(profilesChip(profiles))
        InfoChip(settingsLabel)
    }
}

@Composable
internal fun InfoChip(text: String, modifier: Modifier = Modifier) {
    SklyankaChip(
        text = text,
        modifier = modifier,
        state = ChipState.Info,
        shape = SklyankaShapes.Pill,
    )
}

/** Метка списка чипов состава — точка опоры экранных тестов. */
internal const val TAG_COMPOSITION = "transfer_composition"
