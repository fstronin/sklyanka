package ru.stohastika.sklyanka.feature.history

import android.content.Context
import ru.stohastika.sklyanka.domain.history.AdherencePeriod
import ru.stohastika.sklyanka.domain.history.DayOutcome
import ru.stohastika.sklyanka.domain.history.DayState
import ru.stohastika.sklyanka.domain.history.MedicationAdherence
import ru.stohastika.sklyanka.domain.history.OnTimeShare
import ru.stohastika.sklyanka.domain.history.ReportDocument
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Состояния экрана истории для проверок и снимков.
 *
 * Числа те же, что в `docs/tasks/SKL-18/acceptance.feature`: сентябрь 2026, сегодня —
 * 30-е, «пропусков за месяц» 12, «дней без пропусков» 9, доли 97 % и 72 %, вывод про
 * вечерний приём в 20:00. Так проверка, снимок и документ приёмки говорят об одном
 * месяце, а не о трёх похожих.
 *
 * Названия лекарств латиницей: заглушки не изображают правдоподобных лекарств (НФТ-19).
 */
internal object HistoryStates {

    val SEPTEMBER: YearMonth = YearMonth.of(2026, 9)
    val TODAY: LocalDate = LocalDate.of(2026, 9, 30)
    val EVENING: LocalTime = LocalTime.of(20, 0)

    /** Обычный месяц: два лекарства, частичные дни, пропуски и явный вывод. */
    fun september(): HistoryUiState = HistoryUiState(
        month = SEPTEMBER,
        period = AdherencePeriod(
            profileId = PROFILE_ID,
            from = SEPTEMBER.atDay(1),
            to = SEPTEMBER.atEndOfMonth(),
            days = days(),
            byMedication = listOf(
                MedicationAdherence(
                    medicationId = "medication-a",
                    name = "med-a",
                    taken = 29,
                    planned = 30,
                    misses = 1,
                    archived = false,
                    low = false,
                ),
                MedicationAdherence(
                    medicationId = "medication-b",
                    name = "med-b",
                    taken = 43,
                    planned = 60,
                    misses = 17,
                    archived = false,
                    low = true,
                ),
            ),
            daysWithoutMisses = 9,
            missesInPeriod = 12,
            onTimeShare = OnTimeShare.Undefined,
            missesByTime = mapOf(EVENING to 9, LocalTime.of(8, 0) to 3),
            scheduledTimes = setOf(LocalTime.of(8, 0), EVENING),
        ),
        insightTime = EVENING,
    )

    /** Месяц без единой записи: плитки показывают прочерки, блок «По лекарствам» скрыт. */
    fun emptyMonth(): HistoryUiState = HistoryUiState(
        month = YearMonth.of(2026, 7),
        period = AdherencePeriod.empty(
            profileId = PROFILE_ID,
            from = LocalDate.of(2026, 7, 1),
            to = LocalDate.of(2026, 7, 31),
        ).copy(days = neutralDays(YearMonth.of(2026, 7))),
    )

    /** История началась 12 сентября: первые одиннадцать дней нейтральны. */
    fun shortHistory(): HistoryUiState = september().let { state ->
        state.copy(
            period = state.period.copy(
                days = state.period.days.map { day ->
                    if (day.date < LocalDate.of(2026, 9, 12)) {
                        day.copy(state = DayState.BEFORE_HISTORY)
                    } else {
                        day
                    }
                },
            ),
        )
    }

    /** Завершённый курс остаётся в разбивке месяца (ПР-11). */
    fun withArchivedCourse(): HistoryUiState = september().let { state ->
        state.copy(
            period = state.period.copy(
                byMedication = state.period.byMedication + MedicationAdherence(
                    medicationId = "medication-c",
                    name = "med-c",
                    taken = 9,
                    planned = 10,
                    misses = 1,
                    archived = true,
                    low = false,
                ),
            ),
        )
    }

    /** Чужой профиль: отметку поставил владелец телефона (ПРФ-14). */
    fun foreignProfile(): HistoryUiState = september().let { state ->
        state.copy(
            period = state.period.copy(
                byMedication = state.period.byMedication.map { row ->
                    row.copy(markedByOtherProfile = row.medicationId == "medication-a")
                },
            ),
        )
    }

    /** Открытый отчёт врачу: предупреждение, состав и три действия. */
    fun withReport(content: String = REPORT_CONTENT): HistoryUiState = september().copy(
        report = ReportUi(
            ReportDocument(fileName = "sklyanka-report-2026-09.txt", mediaType = "text/plain", content = content),
        ),
    )

    /** Подпись дня для чтения с экрана — той же сборкой, какой её собирает экран. */
    fun dayDescription(context: Context, date: LocalDate, state: Int): String =
        context.getString(R.string.cd_history_day, dayLabel(context, date), context.getString(state))

    fun todayDescription(context: Context, date: LocalDate, state: Int): String =
        context.getString(R.string.cd_history_day_today, dayLabel(context, date), context.getString(state))

    private fun dayLabel(context: Context, date: LocalDate): String = date.format(
        DateTimeFormatter.ofPattern(
            context.getString(R.string.history_date_pattern),
            context.resources.configuration.locales[0],
        ),
    )

    /**
     * Дни сентября: принятые, один частичный, один пропущенный, один без приёмов.
     *
     * Состояния расставлены по числам, а не случайно: проверки подписей ссылаются
     * на конкретные дни, и снимок обязан показывать все три цвета легенды.
     */
    private fun days(): List<DayOutcome> = SEPTEMBER.dates().map { date ->
        val state = when {
            date == TODAY -> DayState.AHEAD
            date.dayOfMonth == PARTIAL_DAY -> DayState.PARTIAL
            date.dayOfMonth == MISSED_DAY -> DayState.MISSED
            date.dayOfMonth == NO_INTAKES_DAY -> DayState.NO_INTAKES
            else -> DayState.ALL_TAKEN
        }
        DayOutcome(date = date, state = state, isToday = date == TODAY)
    }

    private fun neutralDays(month: YearMonth): List<DayOutcome> =
        month.dates().map { date -> DayOutcome(date = date, state = DayState.NO_INTAKES) }

    private fun YearMonth.dates(): List<LocalDate> = (1..lengthOfMonth()).map { day -> atDay(day) }

    const val PARTIAL_DAY = 15
    const val MISSED_DAY = 16
    const val NO_INTAKES_DAY = 17

    private const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"

    /** Заглушка документа: состав проверяется отрисовкой, а не этим текстом. */
    private const val REPORT_CONTENT = "report-content"
}
