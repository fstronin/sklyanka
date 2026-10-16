package ru.stohastika.sklyanka.domain.history

import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.HistoryRepository
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Разделы «Про» расширенного отчёта врачу (`docs/norm/06-design.md` §5.15, SKL-119).
 *
 * Считает тремя блоками, и у каждого своё правило «когда печатать» (Р-33): сравнение — когда
 * в предыдущем периоде есть наступившие приёмы, динамика — когда в периоде не меньше двух
 * месяцев с наступившими приёмами, паттерн — когда говорит существующее [MissedPatternRule].
 * Пустой блок — `null`, а не пустая таблица: раздела без данных в документе не бывает.
 *
 * **Второго расчёта здесь нет.** Числа приходят из [`AdherenceCalculator`] по тому же срезу
 * `HistoryRepository`, что и экран истории, — сравнение и динамика считаются той же
 * приверженностью, а не своей арифметикой по приёмам. Паттерн берётся у того же правила,
 * что печатает строку-вывод «Истории» (Р-85 п. 4): «Про» добавляет второй выход правила,
 * а не второй экземпляр его порогов.
 *
 * Сети на этом пути нет ни в одном шаге (ADR-0010): документ собирается из локальных данных.
 */
class BuildExtendedSectionsUseCase(
    private val activeProfile: ActiveProfileRepository,
    private val history: HistoryRepository,
    private val calculator: AdherenceCalculator,
    private val missedPattern: MissedPatternRule,
    private val time: TimeSource,
) {

    /**
     * @return разделы расширенного состава; пустой период даёт [ExtendedReportSections.NONE] —
     *   документ остаётся базовым и несёт одну фразу («Про»-разделов у пустого периода нет).
     */
    suspend operator fun invoke(window: HistoryWindow): ExtendedReportSections {
        val profileId = activeProfile.current()
        val today = time.today()
        val zone = time.zone()
        val slice = history.observe(profileId, window).first()
        val period = calculator.of(slice, window, today, zone)
        if (period.isEmpty) return ExtendedReportSections.NONE

        val current = summaryOf(period, window, countedIntakes(slice, zone))
        return ExtendedReportSections(
            comparison = comparisonOf(profileId, window, current, today, zone),
            dynamics = dynamicsOf(countedIntakes(slice, zone), window),
            pattern = patternOf(countedIntakes(slice, zone), period),
        )
    }

    /**
     * Сравнение с периодом такой же длины, идущим непосредственно перед отчётным.
     *
     * Длина считается сутками, а не скользящими часами: в сутках с переводом времени их
     * 23 или 25, и арифметика «минус N суток» от границы до границы теряет или приобретает
     * день (НФТ-37). Сравнивать не с чем, если в предыдущем периоде не наступило ни одного
     * приёма, — тогда раздела нет вовсе.
     */
    private suspend fun comparisonOf(
        profileId: String,
        window: HistoryWindow,
        current: PeriodSummary,
        today: LocalDate,
        zone: ZoneId,
    ): PeriodComparison? {
        val previousWindow = previousWindow(window)
        val slice = history.observe(profileId, previousWindow).first()
        val period = calculator.of(slice, previousWindow, today, zone)
        val previous = summaryOf(period, previousWindow, countedIntakes(slice, zone))
        return if (previous.due == 0) null else PeriodComparison(current = current, previous = previous)
    }

    /** Период такой же длины, идущий непосредственно перед отчётным. */
    private fun previousWindow(window: HistoryWindow): HistoryWindow {
        val days = ChronoUnit.DAYS.between(window.from, window.to) + 1
        return HistoryWindow(from = window.from.minusDays(days), to = window.from.minusDays(1))
    }

    /**
     * Динамика по месяцам периода: месяц без наступивших приёмов строки не получает — делить
     * в нём не на что. Меньше двух таких месяцев — динамики нет: динамика одного месяца
     * не динамика (Р-33).
     */
    private fun dynamicsOf(counted: List<Intake>, window: HistoryWindow): MonthDynamics? {
        val months = generateSequence(YearMonth.from(window.from)) { month ->
            month.plusMonths(1).takeIf { !it.isAfter(YearMonth.from(window.to)) }
        }.toList()
        val rows = months.mapNotNull { month ->
            val ofMonth = counted.filter { YearMonth.from(it.scheduledAt) == month }
            if (ofMonth.count { it.status != IntakeStatus.PENDING } == 0) {
                null
            } else {
                MonthAdherence(
                    month = month,
                    taken = ofMonth.count { it.status == IntakeStatus.TAKEN },
                    planned = ofMonth.size,
                )
            }
        }
        return rows.takeIf { it.size >= MINIMUM_MONTHS }?.let(::MonthDynamics)
    }

    /**
     * Паттерн пропусков тем же правилом, что строка-вывод «Истории».
     *
     * Даты берутся у приёмов этого времени: правило называет время, а перечень дат и их
     * природу документ печатает словами — так же, как печатает пропуски лекарства.
     */
    private fun patternOf(counted: List<Intake>, period: AdherencePeriod): MissedPatternBlock? {
        val time = missedPattern.dominantMissedTime(period) ?: return null
        val missed = counted.filter { it.isMiss && it.scheduledAt.toLocalTime() == time }
        return MissedPatternBlock(
            time = time,
            missedDates = missed.map { it.scheduledAt.toLocalDate() }.distinct().sorted(),
            skippedByPerson = missed.count { it.status == IntakeStatus.SKIPPED },
            missedWithoutMark = missed.count { it.status == IntakeStatus.MISSED },
        )
    }

    private fun summaryOf(period: AdherencePeriod, window: HistoryWindow, counted: List<Intake>): PeriodSummary =
        PeriodSummary(
            from = window.from,
            to = window.to,
            taken = period.byMedication.sumOf { it.taken },
            planned = period.byMedication.sumOf { it.planned },
            onTimeShare = period.onTimeShare,
            daysWithoutMisses = period.daysWithoutMisses,
            misses = period.missesInPeriod,
            due = counted.count { it.status != IntakeStatus.PENDING },
        )

    private companion object {

        /** Меньше двух месяцев с наступившими приёмами — динамики нет (Р-33). */
        const val MINIMUM_MONTHS = 2
    }
}
