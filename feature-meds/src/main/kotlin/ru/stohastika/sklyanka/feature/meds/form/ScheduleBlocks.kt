package ru.stohastika.sklyanka.feature.meds.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import ru.stohastika.sklyanka.domain.medication.DraftField
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import ru.stohastika.sklyanka.feature.meds.ChoiceTile
import ru.stohastika.sklyanka.feature.meds.ClockGlyph
import ru.stohastika.sklyanka.feature.meds.CounterCard
import ru.stohastika.sklyanka.feature.meds.CounterLabels
import ru.stohastika.sklyanka.feature.meds.CrossGlyph
import ru.stohastika.sklyanka.feature.meds.GlyphButton
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.SectionCard
import ru.stohastika.sklyanka.feature.meds.SectionLabel
import ru.stohastika.sklyanka.feature.meds.Segment
import ru.stohastika.sklyanka.feature.meds.SegmentedControl
import ru.stohastika.sklyanka.feature.meds.ValueRow
import ru.stohastika.sklyanka.feature.meds.amountOf
import ru.stohastika.sklyanka.feature.meds.fieldWord
import ru.stohastika.sklyanka.feature.meds.formatted
import ru.stohastika.sklyanka.feature.meds.fullWeekdayName
import ru.stohastika.sklyanka.feature.meds.label
import ru.stohastika.sklyanka.feature.meds.message
import ru.stohastika.sklyanka.feature.meds.shortWeekdayName
import ru.stohastika.sklyanka.feature.meds.stageDaysWord
import ru.stohastika.sklyanka.ui.component.ChipState
import ru.stohastika.sklyanka.ui.component.ListPickerDialog
import ru.stohastika.sklyanka.ui.component.SklyankaChip
import ru.stohastika.sklyanka.ui.component.SklyankaNumberField
import ru.stohastika.sklyanka.ui.component.SklyankaTextField
import ru.stohastika.sklyanka.ui.modifier.clickableShape
import ru.stohastika.sklyanka.ui.modifier.dashedBorder
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Режим приёма, расписание либо ограничение, связь с едой.
 *
 * Переключатель режима стоит **выше** блоков расписания — отступление от макета по Д-2,
 * зафиксированное в `TDD.md §5.6`: именно режим решает, нужны ли время и счётчик.
 */

/** «Режим приёма» — сегментный переключатель и пояснение к «по потребности». */
@Composable
internal fun ModeBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    FormBlock(stringResource(R.string.form_label_mode), null, anchors) {
        SegmentedControl(
            segments = listOf(
                Segment(
                    label = stringResource(R.string.form_mode_scheduled),
                    selected = state.draft.scheduleMode == ScheduleMode.SCHEDULED,
                    onClick = { onIntent(MedFormIntent.ModeChanged(ScheduleMode.SCHEDULED)) },
                ),
                Segment(
                    label = stringResource(R.string.form_mode_as_needed),
                    selected = state.draft.scheduleMode == ScheduleMode.PRN,
                    onClick = { onIntent(MedFormIntent.ModeChanged(ScheduleMode.PRN)) },
                ),
            ),
            modifier = Modifier.testTag(TAG_MODE_SWITCH),
        )
        BlockHint(stringResource(R.string.form_mode_as_needed_hint))
    }
}

/**
 * «Приёмов в сутки» и «Время приёма» либо ограничение «не чаще N раз в сутки».
 *
 * Дефект постановки Д-1: поля ограничения в макете нет вовсе, хотя ограничение
 * показывается и в списке лекарств, и на экране «Сегодня». Решение задачи: блок
 * «Приёмов в сутки» при режиме «по потребности» не скрывается, а меняет смысл и подпись.
 * Это сохраняет вёрстку макета и не добавляет седьмого блока в и без того длинную форму.
 */
@Composable
internal fun ScheduleBlock(
    state: MedFormUiState,
    onIntent: (MedFormIntent) -> Unit,
    anchors: FormAnchors,
    onPickTime: (Int, LocalTime) -> Unit,
) {
    if (state.applicability.maxPerDayVisible) {
        MaxPerDayBlock(state, onIntent, anchors)
    } else {
        IntakesPerDayBlock(state, onIntent, anchors)
        TimesBlock(state, onIntent, anchors, onPickTime)
    }
}

/**
 * Строка вида расписания и параметры выбранного вида (`docs/norm/06-design.md` §5.10).
 *
 * Вид выбирается **строкой**, а не третьим сегментом режима: ряд «Режим приёма» отвечает
 * на один вопрос — нужно ли время, — а вид спрашивает про другое (по какому правилу
 * порождаются дни). Порядок списка — порядок нормы, а не цены (ПР-15): «Каждый день»,
 * «По дням недели», «Через несколько дней», «Циклами», «По этапам».
 *
 * Параметры стоят сразу под строкой; чужие не показываются вовсе — вид у курса один
 * (ИНВ-25). Ниже идут неизменённые «Приёмов в сутки» и «Время приёма» ([ScheduleBlock]).
 */
@Composable
internal fun ScheduleKindBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    var pickerOpen by remember { mutableStateOf(false) }
    // Блок существует только при «по расписанию»: у «по потребности» вида не бывает
    // (ИНВ-25, ПР-7), и строка вида не показывается вовсе (§5.10). Собранный вид при этом
    // сохраняется в черновике и вернётся при возвращении к «по расписанию».
    FormBlock(null, DraftField.SCHEDULE, anchors) {
        SectionCard(modifier = Modifier.testTag(TAG_SCHEDULE_CARD)) {
            ValueRow(
                label = stringResource(R.string.form_label_schedule),
                value = state.shownScheduleKind.label(),
                onClick = { pickerOpen = true },
                modifier = Modifier.testTag(TAG_SCHEDULE_ROW),
            )
            ScheduleParams(state, onIntent)
        }
        state.violationOf(DraftField.SCHEDULE)?.let { ViolationText(it.message()) }
    }
    if (pickerOpen) {
        ListPickerDialog(
            title = stringResource(R.string.form_label_schedule),
            options = ScheduleKind.entries,
            selected = state.shownScheduleKind,
            label = { kind -> kind.label() },
            // Метки «Про» у вариантов нет: продукт бесплатный (Р-86), а вид расписания —
            // предметное свойство курса, а не признак подписки.
            onSelect = { kind ->
                onIntent(MedFormIntent.ScheduleKindChanged(kind))
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
            modifier = Modifier.testTag(TAG_SCHEDULE_PICKER),
        )
    }
}

/** Параметры выбранного вида (§5.10); у ежедневного их нет вовсе (ИНВ-23). */
@Composable
private fun ScheduleParams(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit) {
    when (state.scheduleKind) {
        ScheduleKind.DAILY -> Unit
        ScheduleKind.WEEKDAYS -> WeekdayParams(state, onIntent)
        ScheduleKind.INTERVAL -> IntervalParams(state, onIntent)
        ScheduleKind.CYCLES -> CycleParams(state, onIntent)
        ScheduleKind.STAGES -> StageParams(state, onIntent)
    }
}

/**
 * Ряд семи дней недели и нейтральная подпись под ним (§5.10).
 *
 * Подпись чипа — короткая форма интерфейса, имя для чтения с экрана — полная: семь дней
 * в шести локалях платформа склоняет и сокращает сама, а семь комплектов в каталогах
 * разошлись бы с календарём. Ни приглушения, ни состояния недоступности у дней нет
 * (П-7, Р-79): расписание доступно всем (Р-86), и второй день выбирается так же, как первый.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayParams(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit) {
    val selected = (state.draft.schedule as? ScheduleRule.Weekdays)?.days.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Block)
            .padding(bottom = SklyankaSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        SectionLabel(stringResource(R.string.form_label_weekdays))
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag(TAG_WEEKDAYS_ROW),
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
        ) {
            DayOfWeek.entries.forEach { day ->
                SklyankaChip(
                    text = day.shortWeekdayName(),
                    state = if (day in selected) ChipState.Selected else ChipState.Unselected,
                    // Выбор множественный, и роль объявлена явно (НФТ-30, §5.10): чип
                    // всегда был `RadioButton`, а ряд дней выбирает несколько значений.
                    role = Role.Checkbox,
                    contentDescription = day.fullWeekdayName(),
                    onClick = { onIntent(MedFormIntent.WeekdayToggled(day)) },
                )
            }
        }
        // «Больше одного дня» — нейтральная подпись под рядом, без метки «Про» (Р-86):
        // она называет то, что выбирают, а не то, за что платят.
        Text(
            text = stringResource(R.string.form_weekdays_pro_note),
            style = SklyankaType.CaptionSmall,
            color = SklyankaTheme.colors.textMuted,
            modifier = Modifier.testTag(TAG_WEEKDAYS_NOTE),
        )
    }
}

/** «Интервал, дней» и пояснение отсчёта (ПР-17). */
@Composable
private fun IntervalParams(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Block)
            .padding(bottom = SklyankaSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        NumberRow(
            label = stringResource(R.string.form_interval_row),
            value = state.inputs.interval,
            onValueChange = { onIntent(MedFormIntent.IntervalChanged(it)) },
            tag = TAG_INTERVAL_FIELD,
        )
        BlockHint(stringResource(R.string.form_schedule_count_from_start))
        // «Один день — это ежедневное расписание»: не отказ, а предложение базового вида
        // (ПР-15, §10). Вид при этом уже вырожден в ежедневный, и метки «Про» у него нет.
        if (state.inputs.interval.trim().toIntOrNull() == ONE_DAY_INTERVAL) {
            BlockHint(
                text = stringResource(R.string.error_interval_one_day),
                modifier = Modifier.testTag(TAG_INTERVAL_ONE_DAY),
            )
        }
    }
}

/** «Дней приёма подряд» и «Дней перерыва» (ПР-18). */
@Composable
private fun CycleParams(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Block)
            .padding(bottom = SklyankaSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        NumberRow(
            label = stringResource(R.string.form_cycle_on_row),
            value = state.inputs.cycleOn,
            onValueChange = { onIntent(MedFormIntent.CycleOnChanged(it)) },
            tag = TAG_CYCLE_ON_FIELD,
        )
        NumberRow(
            label = stringResource(R.string.form_cycle_off_row),
            value = state.inputs.cycleOff,
            onValueChange = { onIntent(MedFormIntent.CycleOffChanged(it)) },
            tag = TAG_CYCLE_OFF_FIELD,
        )
        BlockHint(stringResource(R.string.form_schedule_count_from_start))
    }
}

/** Список этапов карточками, «Добавить этап» и два пояснения (ПР-19, ИНВ-24). */
@Composable
private fun StageParams(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit) {
    val inputs = state.inputs
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Block)
            .padding(bottom = SklyankaSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        inputs.stageDoses.forEachIndexed { index, _ ->
            StageCard(state = state, index = index, onIntent = onIntent)
        }
        DashedAddButton(
            text = stringResource(R.string.form_stage_add),
            onClick = { onIntent(MedFormIntent.StageAdded) },
            modifier = Modifier.testTag(TAG_STAGE_ADD),
        )
        BlockHint(stringResource(R.string.form_stage_dose_note))
        BlockHint(stringResource(R.string.form_stage_hint))
    }
}

/**
 * Карточка этапа: та же сборка, что строка времени (§5.10).
 *
 * Состояние читается из черновика: номер, доза, длительность и признак удаления — всё
 * это поля одного этапа, и передавать их по одному значило бы заводить восемь параметров
 * на один смысл. Видимой подписи у полей нет: они называют себя словами единиц внутри,
 * полные имена для чтения с экрана — `cd_stage_dose` и `cd_stage_days`.
 */
@Composable
private fun StageCard(state: MedFormUiState, index: Int, onIntent: (MedFormIntent) -> Unit) {
    val colors = SklyankaTheme.colors
    val dose = state.inputs.stageDoses.getOrElse(index) { "" }
    val days = state.inputs.stageDays.getOrElse(index) { "" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Medium)
            .border(SklyankaSizes.BorderThin, colors.line, SklyankaShapes.Medium)
            .padding(horizontal = SklyankaSpacing.Large, vertical = SklyankaSpacing.Small)
            .testTag(TAG_STAGE_CARD),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        StageHeader(
            number = index + FIRST_STAGE_NUMBER,
            // Последний этап не удаляется: курс без этапов не собрать (ИНВ-23, §5.10).
            canDelete = state.inputs.stageDoses.size > 1,
            onDelete = { onIntent(MedFormIntent.StageRemoved(index)) },
        )
        StageFields(
            number = index + FIRST_STAGE_NUMBER,
            dose = dose,
            days = days,
            doseUnitWord = state.applicability.doseUnit.fieldWord(amountOf(dose)),
            onDoseChange = { onIntent(MedFormIntent.StageDoseChanged(index, it)) },
            onDaysChange = { onIntent(MedFormIntent.StageDaysChanged(index, it)) },
        )
    }
}

/** Заголовок карточки этапа: «Этап N» и крест удаления, когда этапов два и больше. */
@Composable
private fun StageHeader(number: Int, canDelete: Boolean, onDelete: () -> Unit) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.form_stage_title, number),
            style = SklyankaType.BodyDefaultStrong,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        if (canDelete) {
            GlyphButton(
                contentDescription = stringResource(R.string.cd_stage_delete, number),
                onClick = onDelete,
                bordered = false,
            ) {
                CrossGlyph(color = colors.iconFaint)
            }
        }
    }
}

/**
 * Два числовых поля этапа: доза со словом единицы дозы и длительность со словом дней.
 *
 * Рядом поля стоят, пока половина строки умещает содержимое каждого из них, и встают
 * столбиком, когда хотя бы одному половины не хватает. Порог — **измеренный**, а не
 * `fontScale` (§5.10, §5.1 п. 2): поле несёт слово единицы внутри себя, и длина слова
 * растёт не только с масштабом системы, но и с локалью — немецкое или испанское слово
 * длиннее русского при том же шрифте. Свойства «влезает ли содержимое» у масштаба нет,
 * а у ширины — есть (§6.3, НФТ-31).
 *
 * Мера — внутренняя ширина поля (`maxIntrinsicWidth`): набранное число плюс слово единицы
 * вместе с полем рамки, то есть ровно то, что поле показывает. Критерий берётся по худшему
 * из двух полей, а не по сумме: половины равны, и режет текст ровно то поле, которому
 * своей половины не хватило. `measure` вызывается по разу на поле — при окончательной
 * укладке, — как в `StackingEqualButtons`.
 *
 * Раскладка нарисована внутри, а не вынесена в свою функцию: файл держит двадцать
 * функций — предел detekt (`TooManyFunctions`), и отдельная функция стала бы двадцать
 * первой.
 */
@Composable
private fun StageFields(
    number: Int,
    dose: String,
    days: String,
    doseUnitWord: String,
    onDoseChange: (String) -> Unit,
    onDaysChange: (String) -> Unit,
) {
    val doseLabel = stringResource(R.string.cd_stage_dose, number)
    val daysLabel = stringResource(R.string.cd_stage_days, number)
    val doseField: @Composable () -> Unit = {
        SklyankaTextField(
            value = dose,
            onValueChange = onDoseChange,
            unitLabel = doseUnitWord,
            numeric = true,
            modifier = Modifier
                .semantics { contentDescription = doseLabel }
                .testTag(TAG_STAGE_DOSE),
        )
    }
    val daysField: @Composable () -> Unit = {
        SklyankaTextField(
            value = days,
            onValueChange = onDaysChange,
            unitLabel = stageDaysWord(days.trim().toIntOrNull()),
            numeric = true,
            modifier = Modifier
                .semantics { contentDescription = daysLabel }
                .testTag(TAG_STAGE_DAYS),
        )
    }
    Layout(
        contents = listOf(doseField, daysField),
        modifier = Modifier.fillMaxWidth(),
    ) { (doseMeasurables, daysMeasurables), constraints ->
        val gapPx = SklyankaSpacing.Small.roundToPx()
        val doseMeasurable = doseMeasurables.first()
        val daysMeasurable = daysMeasurables.first()
        val doseWidth = doseMeasurable.maxIntrinsicWidth(0)
        val daysWidth = daysMeasurable.maxIntrinsicWidth(0)
        // Ширина приходит неограниченной (горизонтальная прокрутка): тогда ряд занимает
        // нужное содержимому, а не бесконечность точек.
        val available = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            doseWidth + gapPx + daysWidth
        }
        val stacked = maxOf(doseWidth, daysWidth) * 2 + gapPx > available
        val half = ((available - gapPx) / 2).coerceAtLeast(0)
        val width = if (stacked) available else half
        val dosePlaceable = doseMeasurable.measure(Constraints.fixedWidth(width))
        val daysPlaceable = daysMeasurable.measure(Constraints.fixedWidth(width))
        val height = if (stacked) {
            dosePlaceable.height + gapPx + daysPlaceable.height
        } else {
            maxOf(dosePlaceable.height, daysPlaceable.height)
        }
        layout(available, height) {
            dosePlaceable.place(0, 0)
            if (stacked) {
                daysPlaceable.place(0, dosePlaceable.height + gapPx)
            } else {
                daysPlaceable.place(dosePlaceable.width + gapPx, 0)
            }
        }
    }
}

/** Строка «подпись — числовое поле»: интервал и длины цикла устроены одинаково (§5.10). */
@Composable
private fun NumberRow(label: String, value: String, onValueChange: (String) -> Unit, tag: String) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        Text(
            text = label,
            style = SklyankaType.BodyDefault,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        SklyankaNumberField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(SklyankaSizes.StockField).testTag(tag),
        )
    }
}

/** Интервал в один день — вырожденный: это ежедневное расписание (ПР-15). */
private const val ONE_DAY_INTERVAL = 1

/** Нумерация этапов начинается с единицы: «Этап 1», а не «Этап 0» (§5.10). */
private const val FIRST_STAGE_NUMBER = 1

/** Счётчик приёмов: производная длины списка времён, а не хранимое значение (`TDD.md §9.2`). */
@Composable
private fun IntakesPerDayBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    FormBlock(null, null, anchors) {
        CounterCard(
            labels = CounterLabels(
                title = stringResource(R.string.form_label_intakes_per_day),
                caption = stringResource(R.string.form_caption_times_below),
                decrease = stringResource(R.string.cd_decrease),
                increase = stringResource(R.string.cd_increase),
            ),
            value = state.draft.intakesPerDay.toString(),
            onDecrease = { onIntent(MedFormIntent.TimeRemovedFromEnd) },
            onIncrease = { onIntent(MedFormIntent.TimeAdded) },
        )
    }
}

/** Ограничение «не чаще N раз в сутки» (Д-1): тот же ряд счётчика, другая подпись. */
@Composable
private fun MaxPerDayBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    FormBlock(null, DraftField.MAX_PER_DAY, anchors) {
        CounterCard(
            labels = CounterLabels(
                title = stringResource(R.string.form_label_max_per_day),
                caption = stringResource(R.string.form_caption_max_per_day),
                decrease = stringResource(R.string.cd_decrease),
                increase = stringResource(R.string.cd_increase),
            ),
            value = (state.draft.maxPerDay ?: 0).toString(),
            onDecrease = { onIntent(MedFormIntent.MaxPerDayDecreased) },
            onIncrease = { onIntent(MedFormIntent.MaxPerDayIncreased) },
            modifier = Modifier.testTag(TAG_MAX_PER_DAY),
        )
        state.violationOf(DraftField.MAX_PER_DAY)?.let { ViolationText(it.message()) }
    }
}

/** «Время приёма» — строки времени и пунктирная кнопка «Добавить время». */
@Composable
private fun TimesBlock(
    state: MedFormUiState,
    onIntent: (MedFormIntent) -> Unit,
    anchors: FormAnchors,
    onPickTime: (Int, LocalTime) -> Unit,
) {
    FormBlock(stringResource(R.string.form_label_times), DraftField.TIMES, anchors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
            modifier = Modifier.testTag(TAG_TIMES_BLOCK),
        ) {
            state.draft.times.forEachIndexed { index, time ->
                TimeRow(
                    time = time,
                    onPick = { onPickTime(index, time) },
                    onDelete = { onIntent(MedFormIntent.TimeRemoved(index)) },
                )
            }
            DashedAddButton(
                text = stringResource(R.string.form_add_time),
                onClick = { onIntent(MedFormIntent.TimeAdded) },
                modifier = Modifier.testTag(TAG_ADD_TIME),
            )
            state.violationOf(DraftField.TIMES)?.let { ViolationText(it.message()) }
        }
    }
}

@Composable
private fun TimeRow(time: LocalTime, onPick: () -> Unit, onDelete: () -> Unit) {
    val colors = SklyankaTheme.colors
    val formatted = time.formatted()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Medium)
            .border(SklyankaSizes.BorderThin, colors.line, SklyankaShapes.Medium)
            .clickableShape(SklyankaShapes.Medium, role = Role.Button, onClick = onPick)
            .padding(horizontal = SklyankaSpacing.Large, vertical = SklyankaSpacing.Small)
            .testTag(TAG_TIME_ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        ClockGlyph(color = colors.iconFaint)
        Text(
            text = formatted,
            style = SklyankaType.BodyLarge,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        GlyphButton(
            contentDescription = stringResource(R.string.cd_delete_time, formatted),
            onClick = onDelete,
            bordered = false,
        ) {
            CrossGlyph(color = colors.iconFaint)
        }
    }
}

/**
 * Пунктирная кнопка добавления во всю ширину.
 *
 * Та же сборка у «Добавить время» и «Добавить этап» (§5.10): второй вид кнопки завёл бы
 * второе место, где правится её высота, цвет и подпись.
 */
@Composable
private fun DashedAddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SklyankaSizes.SecondaryButtonHeight)
            .dashedBorder(SklyankaSizes.BorderThin, colors.lineInteractive, SklyankaShapes.MediumRadius)
            .clickableShape(SklyankaShapes.Medium, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = SklyankaType.Label, color = colors.accent)
    }
}

/**
 * «Связь с едой» — четыре кнопки, которые при нехватке ширины встают по две.
 *
 * Высота — минимум цели нажатия, а не жёсткий размер: переведённая подпись («Before food»,
 * «Тамаққа дейін») длиннее русской и переносится на вторую строку. Ряд растёт по самой
 * высокой плитке (IntrinsicSize.Min), плитки выравниваются по ней — §6.3 дизайн-системы
 * требует роста блока, а не обрезки текста.
 *
 * Число колонок решает измеренная ширина самой длинной подписи, а не `fontScale`: на
 * 360 dp при 130 % слово «Неважно» в четырёх равных колонках не помещается, и Compose
 * рвёт его по буквам. Тогда блок раскладывается 2×2; если не помещаются и две колонки —
 * в столбик. Порог по фактической ширине не врёт на длинных локалях, как и порог
 * компактного заголовка (§6.3, SKL-136).
 */
@Composable
internal fun FoodRelationBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    FormBlock(stringResource(R.string.form_label_food_relation), null, anchors) {
        val labels = FoodRelation.entries.map { it.label() }
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val minTileWidth = remember(labels, density, textMeasurer) {
            with(density) {
                // Режется по буквам только слово, которое не влезает целиком; перенос
                // по пробелам («До еды») — нормальный и высоту плитки растит.
                val widestWord = labels
                    .flatMap { label -> label.split(' ') }
                    .maxOf { word -> textMeasurer.measure(text = word, style = SklyankaType.CaptionStrong).size.width }
                widestWord.toDp() + SklyankaSpacing.Small * 2 + SklyankaSpacing.Hair
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= minTileWidth * FOOD_COLUMNS_MAX + SklyankaSpacing.Small * (FOOD_COLUMNS_MAX - 1) -> {
                    FOOD_COLUMNS_MAX
                }
                maxWidth >= minTileWidth * 2 + SklyankaSpacing.Small -> 2
                else -> 1
            }
            Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
                FoodRelation.entries.chunked(columns).forEach { rowRelations ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
                    ) {
                        rowRelations.forEach { relation ->
                            ChoiceTile(
                                label = relation.label(),
                                selected = state.draft.foodRelation == relation,
                                onClick = { onIntent(MedFormIntent.FoodRelationChanged(relation)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .defaultMinSize(minHeight = SklyankaSizes.MinTouchTarget)
                                    .testTag(TAG_FOOD_RELATION),
                            )
                        }
                        // Последний ряд может быть короче: пустые доли не дают плиткам
                        // растянуться на всю ширину и сломать сетку.
                        repeat(columns - rowRelations.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** Четыре варианта связи с едой в одной строке, пока подписи помещаются. */
private const val FOOD_COLUMNS_MAX = 4

/** Сообщение об отказе под блоком, у которого нет собственного поля ввода. */
@Composable
internal fun ViolationText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SklyankaType.CaptionSmall,
        color = SklyankaTheme.colors.warning,
        modifier = modifier.testTag(TAG_VIOLATION),
    )
}

const val TAG_MODE_SWITCH = "form_mode"
const val TAG_MAX_PER_DAY = "form_max_per_day"
const val TAG_TIMES_BLOCK = "form_times"
const val TAG_TIME_ROW = "form_time_row"
const val TAG_ADD_TIME = "form_add_time"
const val TAG_FOOD_RELATION = "form_food_relation"
const val TAG_VIOLATION = "form_violation"

/** Строка вида расписания и её список (§5.10). */
const val TAG_SCHEDULE_CARD = "form_schedule_card"
const val TAG_SCHEDULE_ROW = "form_schedule_row"
const val TAG_SCHEDULE_PICKER = "form_schedule_picker"

/** Параметры видов: ряд дней и подпись под ним, интервал, длины цикла, карточки этапов. */
const val TAG_WEEKDAYS_ROW = "form_weekdays"
const val TAG_WEEKDAYS_NOTE = "form_weekdays_note"
const val TAG_INTERVAL_FIELD = "form_interval"
const val TAG_INTERVAL_ONE_DAY = "form_interval_one_day"
const val TAG_CYCLE_ON_FIELD = "form_cycle_on"
const val TAG_CYCLE_OFF_FIELD = "form_cycle_off"
const val TAG_STAGE_CARD = "form_stage_card"
const val TAG_STAGE_DOSE = "form_stage_dose"
const val TAG_STAGE_DAYS = "form_stage_days"
const val TAG_STAGE_ADD = "form_stage_add"
