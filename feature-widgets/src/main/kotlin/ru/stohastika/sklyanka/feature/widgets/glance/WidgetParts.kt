package ru.stohastika.sklyanka.feature.widgets.glance

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import ru.stohastika.sklyanka.domain.history.DayState
import ru.stohastika.sklyanka.domain.today.IntakePresentation
import ru.stohastika.sklyanka.domain.widget.WidgetDaySummary
import ru.stohastika.sklyanka.domain.widget.WidgetIntake
import ru.stohastika.sklyanka.domain.widget.WidgetProfileIndication
import ru.stohastika.sklyanka.feature.widgets.R
import ru.stohastika.sklyanka.ui.component.AdherenceDayState
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.format.TextStyle as JavaTextStyle

/**
 * Плита виджета и её части (§5.11).
 *
 * Собрано из одних и тех же кусков для трёх типов: строка приёма, шапка, счётчик и кружок
 * дня у «Сегодня», «Недели» и «по лекарству» — одни и те же. Второй редакции строки
 * не заводится: разойдясь, типы начнут выглядеть по-разному в одном продукте.
 *
 * Рамки и обводки рисуются наложенными плитами: у Glance 1.1 модификатора границы нет
 * вовсе, и «рамка 1 dp» — это подложка нужного цвета под плитой с радиусом на ступень
 * меньше. Обводка кружка и кнопки отметки — та же подложка.
 */
@Composable
internal fun Plate(
    profile: WidgetProfileIndication?,
    accent: ColorProvider,
    onClick: Action,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(WidgetLook.line)
            .cornerRadius(SklyankaShapes.CardRadius),
    ) {
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .background(WidgetLook.surface)
                .cornerRadius(SklyankaShapes.CardRadius - SklyankaSizes.BorderThin)
                .clickable(onClick),
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.Top,
                horizontalAlignment = Alignment.Horizontal.Start,
            ) {
                if (profile != null) ProfileStripe(accent)
                Column(
                    modifier = GlanceModifier.fillMaxWidth().padding(
                        start = SklyankaSizes.WidgetPlatePaddingHorizontal,
                        top = if (profile == null) {
                            SklyankaSizes.WidgetPlatePaddingVertical
                        } else {
                            // Полоса профиля занимает шесть точек верхнего поля, и высота
                            // ступени от индикации не меняется: ступень задаёт хост (§5.11).
                            SklyankaSizes.ProfileStripeHeight
                        },
                        end = SklyankaSizes.WidgetPlatePaddingHorizontal,
                        bottom = SklyankaSizes.WidgetPlatePaddingVertical,
                    ),
                    verticalAlignment = Alignment.Vertical.Top,
                    horizontalAlignment = Alignment.Horizontal.Start,
                ) {
                    content()
                }
            }
        }
    }
}

/** Полоса чужого профиля по верхней грани плиты (ПР-24, ПРФ-12). */
@Composable
private fun ProfileStripe(accent: ColorProvider) {
    Box(
        modifier = GlanceModifier.fillMaxWidth()
            .height(SklyankaSizes.ProfileStripeHeight)
            .background(accent),
        content = {},
    )
}

/**
 * Шапка: слева предмет, справа — надпись профиля у чужого (§5.11).
 *
 * Надпись читается той же индикацией, что в шапке экрана: имя профиля и его цвет,
 * без слов «чужой» и «подопечный» (ПР-24).
 */
@Composable
internal fun PlateHeader(subject: String, profileLabel: String?, accent: ColorProvider) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(SklyankaSizes.WidgetHeader),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = subject,
            modifier = GlanceModifier.defaultWeight(),
            style = WidgetLook.style(SklyankaType.BodyDefaultStrong, WidgetLook.text),
            maxLines = 1,
        )
        if (profileLabel != null) {
            Text(
                text = profileLabel,
                modifier = GlanceModifier.padding(start = SklyankaSpacing.Small),
                style = WidgetLook.style(SklyankaType.Overline, accent),
                maxLines = 1,
            )
        }
    }
}

/**
 * Счётчик: число приёмов словами.
 *
 * Он есть только у ступеней, где помещается: в компактной одна строка содержания важнее
 * счётчика (§5.11). Числитель считает только завершившиеся сутки у недели (ЭКР-6)
 * и только принятые у дня.
 */
@Composable
internal fun PlateCounter(text: String) {
    Text(
        text = text,
        modifier = GlanceModifier.padding(top = SklyankaSpacing.Micro),
        style = WidgetLook.style(SklyankaType.CaptionSmall, WidgetLook.textMuted),
        maxLines = 1,
    )
}

/** Тихая строка состояния вместо содержимого (§5.11): «На сегодня приёмов нет» и подобные. */
@Composable
internal fun PlateQuietLine(text: String) {
    Text(
        text = text,
        modifier = GlanceModifier.padding(top = SklyankaSpacing.Small),
        style = WidgetLook.style(SklyankaType.BodyDefault, WidgetLook.textMuted),
        maxLines = 2,
    )
}

/**
 * Строки приёма — только непринятые (§5.11).
 *
 * Порядок: сверху наступившие и будущие по времени, ниже прошедшие без отметки, тоже
 * по времени. Принятые строк не занимают — их число называет счётчик; день целиком
 * со всеми исходами остаётся в приложении.
 */
@Composable
internal fun ColumnScope.IntakeRows(
    items: List<WidgetIntake>,
    accent: ColorProvider,
    resources: Resources,
    mark: (String) -> Action,
) {
    val drawn = rows(items)
    if (drawn.isEmpty()) return
    Column(modifier = GlanceModifier.fillMaxWidth().padding(top = SklyankaSpacing.Small)) {
        drawn.forEachIndexed { index, intake ->
            if (index > 0) RowDivider()
            IntakeRow(intake, accent, resources, mark)
        }
    }
}

@Composable
private fun IntakeRow(intake: WidgetIntake, accent: ColorProvider, resources: Resources, mark: (String) -> Action) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(SklyankaSizes.WidgetRow),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        TimeColumn(intake, accent, resources)
        Spacer(modifier = GlanceModifier.width(SklyankaSpacing.Medium))
        Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = intake.medicationName,
                style = WidgetLook.style(
                    SklyankaType.BodyDefaultStrong,
                    if (intake.presentation == IntakePresentation.MISSED) WidgetLook.textMuted else WidgetLook.text,
                ),
                maxLines = 1,
            )
            Text(
                text = WidgetText.dose(resources, intake.dose),
                style = WidgetLook.style(SklyankaType.Caption, WidgetLook.textMuted),
                maxLines = 1,
            )
        }
        Spacer(modifier = GlanceModifier.width(SklyankaSpacing.Medium))
        when (intake.presentation) {
            IntakePresentation.DUE_NOW -> MarkButton(
                action = mark(intake.intakeId),
                description = WidgetText.markIntake(resources, intake.medicationName),
                filled = true,
                accent = accent,
            )

            IntakePresentation.UPCOMING -> MarkButton(
                action = mark(intake.intakeId),
                description = WidgetText.markIntake(resources, intake.medicationName),
                filled = false,
                accent = accent,
            )

            // Прошедшее без отметки: кнопок нет, состояние названо словом — как у карточки
            // `Missed` в приложении (§5.6), потому что цвет и форма там уже не помогают.
            IntakePresentation.MISSED -> Text(
                text = WidgetText.missed(resources),
                style = WidgetLook.style(SklyankaType.CaptionStrong, WidgetLook.textMuted),
                maxLines = 1,
            )

            IntakePresentation.TAKEN -> Unit
        }
    }
}

/** Колонка времени: «14:00» и слово «Сейчас» у наступившего приёма (§5.11). */
@Composable
private fun TimeColumn(intake: WidgetIntake, accent: ColorProvider, resources: Resources) {
    Column(
        modifier = GlanceModifier.width(SklyankaSizes.WidgetTimeColumn),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        Text(
            text = intake.time.text(),
            style = WidgetLook.style(SklyankaType.LabelStrong, WidgetLook.text),
            maxLines = 1,
        )
        if (intake.presentation == IntakePresentation.DUE_NOW) {
            Text(
                text = WidgetText.now(resources),
                style = WidgetLook.style(SklyankaType.CaptionStrong, accent),
                maxLines = 1,
            )
        }
    }
}

/**
 * Круглая кнопка отметки `MarkButton` 44 dp (§5.6, §5.11).
 *
 * Залитая — единственная заливка плиты, и значит «отметить сейчас»; контурная — приём
 * ещё впереди. Галочка — рисунок из ресурса: глиф дизайн-системы нарисован Compose-холстом,
 * а у Glance холста нет, и рисунок приезжает тем же контуром отдельным вектором.
 */
@Composable
private fun MarkButton(action: Action, description: String, filled: Boolean, accent: ColorProvider) {
    val frame = if (filled) accent else WidgetLook.lineInteractive
    val fill = if (filled) accent else WidgetLook.surface
    val glyph = if (filled) WidgetLook.onAccent else WidgetLook.textTertiary
    Box(
        modifier = GlanceModifier.size(SklyankaSizes.MarkButton)
            .background(frame)
            .cornerRadius(SklyankaShapes.PillRadius)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            // Контурная кнопка — обводка 1,5 dp: подложка цвета обводки под заливкой.
            modifier = GlanceModifier.size(
                if (filled) SklyankaSizes.MarkButton else SklyankaSizes.MarkButton - SklyankaSizes.BorderAccent * 2,
            ).background(fill).cornerRadius(SklyankaShapes.PillRadius),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_check),
                contentDescription = description,
                modifier = GlanceModifier.size(SklyankaSizes.MarkButtonGlyph),
                colorFilter = androidx.glance.ColorFilter.tint(glyph),
            )
        }
    }
}

/** Разделитель между строками приёма — во всю внутреннюю ширину плиты (§5.11). */
@Composable
private fun RowDivider() {
    Box(
        modifier = GlanceModifier.fillMaxWidth()
            .height(SklyankaSizes.BorderThin)
            .background(WidgetLook.line),
        content = {},
    )
}

/**
 * Полоса недели — словарь §5.2 без второй редакции (§5.11).
 *
 * Семь столбиков: метка дня, зазор и кружок `AdherenceDay` 26 dp. Состояния дня берутся
 * у домена ([DayState]) и переводятся в состояния компонента дизайн-системы
 * ([AdherenceDayState]) — второго набора кружков у виджета нет. Легенды и чисел
 * под кружками нет: полоса отвечает на вопрос «как прошли дни», а не «сколько именно».
 */
@Composable
internal fun WeekStrip(days: List<WidgetDaySummary>, resources: Resources, zone: ZoneId) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = SklyankaSpacing.Small),
        verticalAlignment = Alignment.Vertical.Top,
    ) {
        days.forEachIndexed { index, day ->
            if (index > 0) Spacer(modifier = GlanceModifier.width(SklyankaSpacing.Small))
            DayColumn(day, resources, zone)
        }
    }
}

/**
 * Столбик дня: метка, зазор и кружок.
 *
 * Столбик читается одним узлом: «Пятница, всё принято» — состояние дня обязано быть
 * различимо без восприятия цвета (НФТ-33, §6.2).
 */
@Composable
private fun RowScope.DayColumn(day: WidgetDaySummary, resources: Resources, zone: ZoneId) {
    val state = day.adherenceState()
    Column(
        modifier = GlanceModifier.defaultWeight().semantics {
            contentDescription = WidgetText.dayLabel(resources, day.date, day.state, zone)
        },
        verticalAlignment = Alignment.Vertical.Top,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = day.date.weekdayShort(resources),
            style = WidgetLook.style(
                if (state == AdherenceDayState.Today) SklyankaType.MicroStrong else SklyankaType.Micro,
                if (state == AdherenceDayState.Today) WidgetLook.text else WidgetLook.textMuted,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(SklyankaSpacing.Small))
        DayMark(state)
    }
}

/**
 * Кружок дня: заливка ролью, полый кружок «сегодня» и точка «приёмов не было» (§5.2).
 *
 * Второго размера кружка и второго набора состояний виджет не заводит: человек, видевший
 * блок «Последние 7 дней» в карточке лекарства, узнаёт полосу на домашнем экране.
 */
@Composable
private fun DayMark(state: AdherenceDayState) {
    when (state) {
        AdherenceDayState.Taken -> FilledCircle(WidgetLook.dayRole(AdherenceDayState.Taken))
        AdherenceDayState.Partial -> FilledCircle(WidgetLook.dayRole(AdherenceDayState.Partial))
        AdherenceDayState.Missed -> FilledCircle(WidgetLook.dayRole(AdherenceDayState.Missed))

        // Полый кружок «сегодня»: обводка 2 dp ролью `missed`, собранная подложкой.
        AdherenceDayState.Today -> Box(
            modifier = GlanceModifier.size(SklyankaSizes.AdherenceDay)
                .background(WidgetLook.ring)
                .cornerRadius(SklyankaShapes.PillRadius),
            contentAlignment = Alignment.Center,
        ) {
            FilledCircle(
                color = WidgetLook.surface,
                size = SklyankaSizes.AdherenceDay - SklyankaSizes.AdherenceRing * 2,
            )
        }

        AdherenceDayState.Empty -> Box(
            modifier = GlanceModifier.size(SklyankaSizes.AdherenceDay),
            contentAlignment = Alignment.Center,
        ) {
            FilledCircle(
                color = WidgetLook.dot,
                size = SklyankaSizes.AdherenceLegendDot,
            )
        }
    }
}

@Composable
private fun FilledCircle(color: ColorProvider, size: androidx.compose.ui.unit.Dp = SklyankaSizes.AdherenceDay) {
    Box(
        modifier = GlanceModifier.size(size).background(color).cornerRadius(SklyankaShapes.PillRadius),
        content = {},
    )
}

/** Состояние дня домена в словаре компонента дизайн-системы (§5.2): один переход, один словарь. */
internal fun WidgetDaySummary.adherenceState(): AdherenceDayState = when (state) {
    DayState.ALL_TAKEN -> AdherenceDayState.Taken
    DayState.PARTIAL -> AdherenceDayState.Partial
    DayState.MISSED -> AdherenceDayState.Missed
    DayState.AHEAD -> AdherenceDayState.Today
    DayState.NO_INTAKES, DayState.BEFORE_HISTORY -> AdherenceDayState.Empty
}

/** «Сб» — короткое имя дня по языку интерфейса, а не по языку процесса. */
internal fun java.time.LocalDate.weekdayShort(resources: Resources): String {
    val locale = resources.configuration.locales[0] ?: Locale.getDefault()
    val name = dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale)
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

/** «14:00». Машинное время плиты: атомарная строка, одинаковая во всех шести языках (§6.3). */
internal fun LocalTime.text(): String = format(TIME_FORMAT)

/**
 * Непринятые приёмы плиты в порядке нормы: наступившие и будущие по времени, затем
 * прошедшие без отметки. Принятые не попадают: их число называет счётчик (§5.11).
 */
internal fun rows(items: List<WidgetIntake>): List<WidgetIntake> {
    val expected = items.filter { it.markable }.sortedBy { it.time }
    val missed = items.filter { it.presentation == IntakePresentation.MISSED }.sortedBy { it.time }
    return expected + missed
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
