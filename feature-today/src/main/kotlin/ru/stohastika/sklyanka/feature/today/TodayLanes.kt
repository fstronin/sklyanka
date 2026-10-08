package ru.stohastika.sklyanka.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import ru.stohastika.sklyanka.domain.today.GroupAction
import ru.stohastika.sklyanka.domain.today.IntakeItem
import ru.stohastika.sklyanka.domain.today.IntakePresentation
import ru.stohastika.sklyanka.domain.today.TimeGroup
import ru.stohastika.sklyanka.ui.component.ButtonForm
import ru.stohastika.sklyanka.ui.component.IntakeCard
import ru.stohastika.sklyanka.ui.component.IntakeCardActions
import ru.stohastika.sklyanka.ui.component.IntakeCardLabels
import ru.stohastika.sklyanka.ui.component.IntakeCardState
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Лента приёмов одного времени: разделитель, строки, подпись и батч-кнопка
 * (`Main.dc.html`, решение ОВ-32).
 *
 * Строки — единственный вид одновременных приёмов: ни групповой карточки, ни ручного
 * разворачивания «По одному» больше нет. Подпись и батч-кнопка выдаются только группе,
 * время которой наступило, и только если ожидающих приёмов не меньше двух: у будущей
 * группы батч отметил бы принятым то, что ещё не принято, а у группы с одним ожидающим
 * описывать нечего.
 */
@Composable
internal fun TimeLane(group: TimeGroup, state: TodayUiState.Content, onIntent: (TodayIntent) -> Unit) {
    val action = GroupAction.of(group).takeIf { group.isNow }
    val confirming = action != null && state.confirmingGroup == group.time
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default)) {
        LaneHeader(group)
        action?.let { GroupCaption(it.count) }
        // Строки времени не повторяют — его сказал разделитель. Исключение —
        // одновременные приёмы впереди: у них нет ни подписи, ни батча, и когда
        // принять каждый, называет его строка (`03-screens.md` §7 «Сегодня — плотный
        // день»; приёмка SKL-31 — «время в каждой строке»).
        val rowsAreDense = !group.isNow && group.items.size > 1
        val timeWidth = denseTimeColumnWidth(group.items)
        group.items.forEach { item ->
            if (rowsAreDense) DenseIntakeRow(item, onIntent, timeWidth) else IntakeRow(item, onIntent)
        }
        action?.let { batch ->
            GroupMarkButton(
                action = batch,
                confirming = confirming,
                onArm = { onIntent(TodayIntent.ArmGroupMark(group.time)) },
                onConfirm = { onIntent(TodayIntent.MarkGroupTaken(batch.markableIntakeIds)) },
                modifier = Modifier.testTag(TAG_GROUP_MARK_ALL),
            )
        }
    }
}

/** «3 приёма · отметьте по одному» — подпись группы по числу ожидающих (НФТ-44). */
@Composable
private fun GroupCaption(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = groupCaption(count),
        style = SklyankaType.Caption,
        color = SklyankaTheme.colors.textMuted,
        modifier = modifier.testTag(TAG_GROUP_CAPTION),
    )
}

/**
 * Батч-кнопка группы: контурная «Отметить …» и акцентная «Подтвердить отметку …?».
 *
 * Габарит в обоих состояниях один — 52 dp и радиус `Large` 16: у второстепенной кнопки
 * высота доводится `heightIn`, основная такова по построению. Иначе смена состояния
 * сдвигала бы строки под пальцем (`design-decisions.md:293-295`). Состояние подтверждения
 * читается и словом (видимая подпись-вопрос), и цветом (акцентная заливка) — НФТ-30/31.
 */
@Composable
private fun GroupMarkButton(
    action: GroupAction,
    confirming: Boolean,
    onArm: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (confirming) {
        action.labelForm.confirmLabel(action.count)
    } else {
        action.labelForm.label(action.count)
    }
    val button = Modifier.fillMaxWidth().heightIn(min = SklyankaSizes.PrimaryButtonHeight)
    // Тег батча стоит на обёртке: у самой кнопки он занят её видом
    // (`TAG_PRIMARY_BUTTON`/`TAG_SECONDARY_BUTTON`), а два тега на одном узле
    // не уживаются — выживает первый.
    Box(modifier = modifier) {
        if (confirming) {
            PrimaryButton(
                text = label,
                onClick = onConfirm,
                modifier = button.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            SecondaryButton(
                text = label,
                onClick = onArm,
                form = ButtonForm.Screen,
                modifier = button,
            )
        }
    }
}

/** Разделитель ленты: время слева, метка времени суток или «Сейчас» справа. */
@Composable
private fun LaneHeader(group: TimeGroup) {
    val colors = SklyankaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
        modifier = Modifier.fillMaxWidth().testTag(TAG_LANE_HEADER),
    ) {
        Text(text = group.time.timeLabel(), style = SklyankaType.LabelStrong, color = colors.text)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(SklyankaSizes.DividerThickness)
                .background(colors.line),
        )
        Text(
            text = if (group.isNow) stringResource(R.string.today_now) else group.partOfDay.label(),
            style = if (group.isNow) SklyankaType.CaptionStrong else SklyankaType.CaptionSmall,
            color = if (group.isNow) colors.accent else colors.textMuted,
        )
    }
}

/**
 * Одна строка приёма.
 *
 * Вид карточки приходит из домена, а не выводится по месту: «принято», «время
 * наступило», «время впереди» и «пропущено» — предметные состояния, и разметка их
 * не пересчитывает (`docs/v2/10-behavior.md`).
 */
@Composable
internal fun IntakeRow(item: IntakeItem, onIntent: (TodayIntent) -> Unit, modifier: Modifier = Modifier) {
    IntakeCard(
        state = item.presentation.cardState(),
        labels = IntakeCardLabels(
            medication = item.medication.name,
            dose = item.doseCaption(),
            time = item.markedAt?.timeLabel().orEmpty(),
        ),
        // Шестой признак чужого профиля (ПРФ-14): отметку в чужом профиле поставил
        // владелец телефона, и она подписана. В своём профиле подписи не бывает никогда —
        // `actor_profile_id` там пуст по построению (ИНВ-18).
        markedByYou = item.markedByYou,
        // Силуэт строки — форма и вид самого лекарства, а не нейтральная пиктограмма:
        // одно и то же лекарство узнаётся в списке, карточке и на «Сегодня» (§4.15, ОВ-94).
        form = item.medication.form,
        shape = item.medication.shape,
        actions = IntakeCardActions(
            onMark = { onIntent(TodayIntent.MarkTaken(item.id)) }.takeIf { item.isMarkable },
            onSkip = { onIntent(TodayIntent.MarkSkipped(item.id)) }.takeIf { item.isMarkable },
        ),
        modifier = modifier
            // Нажатие мимо кнопки отметки открывает карточку лекарства (`Main.dc.html`).
            .clickable { onIntent(TodayIntent.OpenMedication(item.medicationId)) },
    )
}

/**
 * Строка приёма плотного дня: время отдельной колонкой слева (`MainDense.dc.html`).
 *
 * [timeWidth] — общая ширина колонки для строк одной ленты (ОВ-117): при крупном
 * системном шрифте она растёт по значению, и все времена группы стоят на одной
 * вертикали. Карточка получает остаток ширины.
 */
@Composable
internal fun DenseIntakeRow(item: IntakeItem, onIntent: (TodayIntent) -> Unit, timeWidth: Dp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = item.intake.scheduledAt.toLocalTime().timeLabel(),
            style = SklyankaType.LabelStrong,
            color = SklyankaTheme.colors.textMuted,
            // Машинное время атомарно (ОВ-117): одна строка без переноса по символам.
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(timeWidth).testTag(TAG_DENSE_ROW_TIME),
        )
        IntakeRow(item, onIntent, Modifier.weight(1f))
    }
}

/**
 * Ширина колонки времени для ленты: самая широкая строка группы при текущем
 * системном масштабе, но не меньше цели нажатия 44 dp (ОВ-117). Считается один раз
 * на ленту, а не каждой строкой: иначе «18:00» и «20:30» встали бы по-разному.
 */
@Composable
internal fun denseTimeColumnWidth(items: List<IntakeItem>): Dp {
    val measurer = rememberTextMeasurer()
    val widest = items.maxOf { item ->
        measurer.measure(
            text = item.intake.scheduledAt.toLocalTime().timeLabel(),
            style = SklyankaType.LabelStrong,
            maxLines = 1,
            softWrap = false,
        ).size.width
    }
    return with(LocalDensity.current) { widest.toDp() }.coerceAtLeast(SklyankaSizes.MinTouchTarget)
}

/** Вид карточки дизайн-системы по предметному виду приёма. */
private fun IntakePresentation.cardState(): IntakeCardState = when (this) {
    IntakePresentation.TAKEN -> IntakeCardState.Taken
    IntakePresentation.DUE_NOW -> IntakeCardState.DueNow
    IntakePresentation.UPCOMING -> IntakeCardState.Pending
    IntakePresentation.MISSED -> IntakeCardState.Missed
}

const val TAG_DENSE_ROW_TIME = "dense_row_time"
const val TAG_LANE_HEADER = "lane_header"
const val TAG_GROUP_CAPTION = "group_caption"
const val TAG_GROUP_MARK_ALL = "group_mark_all"
