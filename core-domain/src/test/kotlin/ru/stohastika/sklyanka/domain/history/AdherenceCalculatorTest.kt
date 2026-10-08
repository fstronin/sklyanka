package ru.stohastika.sklyanka.domain.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Расчёт приверженности за месяц: сводные числа, разбивка, архив и границы суток.
 *
 * Числа взяты из `acceptance/src/main/resources/features/SKL-18.feature`, чтобы проверка и документ
 * приёмки говорили об одном месяце, а не о двух похожих.
 */
class AdherenceCalculatorTest {

    private val calculator = AdherenceCalculator()

    @Test
    fun `the calendar holds one outcome for every day of the month`() {
        val period = calculate(Months.slice(Months.daily(SEPTEMBER_FIRST, Months.TODAY)))

        assertEquals(SEPTEMBER_DAYS, period.days.size)
        assertEquals(SEPTEMBER_FIRST, period.days.first().date)
        assertEquals(LocalDate.of(2026, 9, 30), period.days.last().date)
        assertTrue(period.days.single { it.date == Months.TODAY }.isToday)
    }

    /** Сценарий приёмки: 4 отмеченных пропуска и 8 автоматических дают 12. */
    @Test
    fun `misses count both the marks of the person and the automatic ones`() {
        val intakes = Months.daily(SEPTEMBER_FIRST, LocalDate.of(2026, 9, 4), status = IntakeStatus.SKIPPED) +
            Months.daily(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 12), status = IntakeStatus.MISSED) +
            Months.daily(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 29), status = IntakeStatus.TAKEN)

        val period = calculate(Months.slice(intakes))

        assertEquals(12, period.missesInPeriod)
    }

    /** Сценарий приёмки: девять дней, в которых принято всё. */
    @Test
    fun `days without misses count only the days where everything was taken`() {
        val taken = Months.daily(SEPTEMBER_FIRST, LocalDate.of(2026, 9, 9), status = IntakeStatus.TAKEN)
        val partly = (10..29).flatMap { day ->
            val date = LocalDate.of(2026, 9, day)
            listOf(
                Months.intake(date.atTime(Months.MORNING), IntakeStatus.TAKEN),
                Months.intake(date.atTime(Months.EVENING), IntakeStatus.MISSED),
            )
        }

        val period = calculate(Months.slice(taken + partly))

        assertEquals(9, period.daysWithoutMisses)
    }

    /**
     * Сегодняшний день в «дни без пропусков» не идёт, даже когда все его приёмы приняты.
     *
     * То же правило, что у недельной полосы карточки лекарства (ЭКР-6): сутки не
     * кончились, и исхода у них ещё нет.
     */
    @Test
    fun `today does not join the days without misses`() {
        val period = calculate(Months.slice(Months.daily(SEPTEMBER_FIRST, Months.TODAY)))

        assertEquals(SEPTEMBER_DAYS - 1, period.daysWithoutMisses)
        assertEquals(DayState.AHEAD, period.days.single { it.date == Months.TODAY }.state)
    }

    /** Сценарий приёмки: 29 из 30 — это 97 %, 43 из 60 — 72 %. */
    @Test
    fun `the breakdown carries the share of every medication of the profile`() {
        val first = Months.daily(SEPTEMBER_FIRST, LocalDate.of(2026, 9, 29), status = IntakeStatus.TAKEN) +
            Months.intake(LocalDate.of(2026, 9, 30).atTime(Months.MORNING), IntakeStatus.MISSED)
        val second = secondMedicationMonth(taken = 43)

        val period = calculate(
            Months.slice(
                intakes = first + second,
                medications = listOf(
                    Months.medication(),
                    Months.medication(id = Months.SECOND_MEDICATION_ID, name = "med-b", times = SECOND_TIMES),
                ),
            ),
        )

        val byId = period.byMedication.associateBy { it.medicationId }
        assertEquals(97, byId.getValue(Months.MEDICATION_ID).takenPercent)
        assertEquals(72, byId.getValue(Months.SECOND_MEDICATION_ID).takenPercent)
    }

    @Test
    fun `a medication far below the others is marked as low`() {
        val period = calculate(
            Months.slice(
                intakes = Months.daily(SEPTEMBER_FIRST, LocalDate.of(2026, 9, 29), status = IntakeStatus.TAKEN) +
                    secondMedicationMonth(taken = 43),
                medications = listOf(
                    Months.medication(),
                    Months.medication(id = Months.SECOND_MEDICATION_ID, name = "med-b", times = SECOND_TIMES),
                ),
            ),
        )

        val byId = period.byMedication.associateBy { it.medicationId }
        assertTrue(byId.getValue(Months.SECOND_MEDICATION_ID).low)
        assertFalse(byId.getValue(Months.MEDICATION_ID).low)
    }

    /**
     * ПР-11: архивированный курс остаётся в истории месяца наравне с активным.
     *
     * Архив придуман ровно для этого, и проверка стоит здесь, а не в разметке: экран
     * показывает то, что посчитано.
     */
    @Test
    fun `an archived course stays in the picture of the month`() {
        val archivedOn = LocalDate.of(2026, 9, 10)
        val period = calculate(
            Months.slice(
                intakes = Months.daily(
                    from = SEPTEMBER_FIRST,
                    to = archivedOn,
                    status = IntakeStatus.TAKEN,
                    medicationId = Months.ARCHIVED_MEDICATION_ID,
                ),
                medications = listOf(
                    Months.medication(
                        id = Months.ARCHIVED_MEDICATION_ID,
                        name = "med-c",
                        archivedAt = Months.instant(archivedOn.atTime(18, 0)),
                    ),
                ),
            ),
        )

        val row = period.byMedication.single()
        assertTrue(row.archived)
        assertEquals(10, row.planned)
        assertEquals(DayState.ALL_TAKEN, period.days.single { it.date == archivedOn }.state)
    }

    /**
     * ОВ-44: приёмы, назначенные после архивации, из картины выпадают целиком.
     *
     * Решение согласовано с SKL-11 (`OutcomeDenominator.UP_TO_ARCHIVING`): курс,
     * прекращённый на десятый день, не обязан отвечать за приёмы, от которых человек
     * отказался.
     */
    @Test
    fun `intakes scheduled after the archiving fall out of both numerator and denominator`() {
        val archivedOn = LocalDate.of(2026, 9, 10)
        val period = calculate(
            Months.slice(
                intakes = Months.daily(
                    from = SEPTEMBER_FIRST,
                    to = LocalDate.of(2026, 9, 20),
                    status = IntakeStatus.TAKEN,
                    medicationId = Months.ARCHIVED_MEDICATION_ID,
                ).map { intake ->
                    if (intake.scheduledAt.toLocalDate().isAfter(archivedOn)) {
                        intake.copy(status = IntakeStatus.MISSED)
                    } else {
                        intake
                    }
                },
                medications = listOf(
                    Months.medication(
                        id = Months.ARCHIVED_MEDICATION_ID,
                        archivedAt = Months.instant(archivedOn.atTime(18, 0)),
                    ),
                ),
            ),
        )

        assertEquals(10, period.byMedication.single().planned)
        assertEquals(0, period.missesInPeriod)
        assertEquals(DayState.NO_INTAKES, period.days.single { it.date == LocalDate.of(2026, 9, 15) }.state)
    }

    /** ПР-10: приём, чьё лекарство выборка не принесла, профилю не приписывается. */
    @Test
    fun `an intake without its medication in the slice is not counted`() {
        val period = calculate(
            Months.slice(
                intakes = Months.daily(SEPTEMBER_FIRST, LocalDate.of(2026, 9, 5)) +
                    Months.daily(
                        from = SEPTEMBER_FIRST,
                        to = LocalDate.of(2026, 9, 5),
                        status = IntakeStatus.MISSED,
                        medicationId = "medication-of-another-profile",
                    ),
                medications = listOf(Months.medication()),
            ),
        )

        assertEquals(1, period.byMedication.size)
        assertEquals(0, period.missesInPeriod)
    }

    @Test
    fun `a month without a single intake is empty and shows no numbers`() {
        val period = calculate(Months.slice(intakes = emptyList(), medications = emptyList(), historyStartsAt = null))

        assertTrue(period.isEmpty)
        assertEquals(0, period.missesInPeriod)
        assertEquals(0, period.daysWithoutMisses)
        assertTrue(period.byMedication.isEmpty())
        assertEquals(OnTimeShare.Undefined, period.onTimeShare)
    }

    /**
     * Приём в 23:30, отмеченный в 00:10 следующих суток, принадлежит дню назначения.
     *
     * Прямое следствие хранения локального `scheduled_at` (НФТ-34): группировка идёт
     * по назначенному времени, а не по моменту отметки.
     */
    @Test
    fun `an intake marked after midnight belongs to the day it was scheduled for`() {
        val scheduled = LocalDate.of(2026, 9, 15).atTime(23, 30)
        val period = calculate(
            Months.slice(
                intakes = listOf(
                    Months.intake(
                        at = scheduled,
                        status = IntakeStatus.TAKEN,
                        actedAt = Months.instant(LocalDate.of(2026, 9, 16).atTime(0, 10)),
                    ),
                ),
            ),
        )

        assertEquals(DayState.ALL_TAKEN, period.days.single { it.date == LocalDate.of(2026, 9, 15) }.state)
        assertEquals(DayState.NO_INTAKES, period.days.single { it.date == LocalDate.of(2026, 9, 16) }.state)
    }

    /** Приёмы на самых границах месяца остаются в своём месяце. */
    @Test
    fun `the first and the last minutes of the month belong to it`() {
        val period = calculate(
            Months.slice(
                intakes = listOf(
                    Months.intake(SEPTEMBER_FIRST.atTime(0, 5), IntakeStatus.TAKEN),
                    Months.intake(LocalDate.of(2026, 9, 29).atTime(23, 55), IntakeStatus.MISSED),
                ),
            ),
        )

        assertEquals(DayState.ALL_TAKEN, period.days.first().state)
        assertEquals(1, period.missesInPeriod)
        assertEquals(2, period.byMedication.single().planned)
    }

    /**
     * Переход на летнее время внутри периода не смещает дни.
     *
     * Группировка календарная, а не по разнице меток времени: сутки перевода длиннее
     * или короче обычных, и «минус 24 часа» теряет день (НФТ-37). Проверяется в поясе,
     * где перевод есть, — пояс профиля приходит параметром.
     */
    @Test
    fun `a daylight saving change inside the period keeps every day in its place`() {
        val zone = ZoneId.of("Europe/Berlin")
        val october = HistoryWindow.of(Months.OCTOBER)
        val transition = LocalDate.of(2026, 10, 25)
        val slice = Months.slice(
            intakes = Months.daily(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 26)),
            historyStartsAt = LocalDate.of(2026, 10, 24),
        )

        val period = calculator.of(slice, october, today = LocalDate.of(2026, 10, 31), zone = zone)

        assertEquals(DayState.ALL_TAKEN, period.days.single { it.date == transition }.state)
        assertEquals(3, period.days.count { it.state == DayState.ALL_TAKEN })
    }

    /** Строка-вывод считает пропуски по времени приёма — опора `MissedPatternRule`. */
    @Test
    fun `misses are grouped by the time of the intake`() {
        val period = calculate(
            Months.slice(
                intakes = listOf(
                    Months.intake(LocalDate.of(2026, 9, 3).atTime(Months.EVENING), IntakeStatus.MISSED),
                    Months.intake(LocalDate.of(2026, 9, 4).atTime(Months.EVENING), IntakeStatus.SKIPPED),
                    Months.intake(LocalDate.of(2026, 9, 5).atTime(Months.MORNING), IntakeStatus.MISSED),
                ),
            ),
        )

        assertEquals(mapOf(Months.EVENING to 2, Months.MORNING to 1), period.missesByTime)
        assertEquals(setOf(Months.EVENING, Months.MORNING), period.scheduledTimes)
    }

    /**
     * ОВ-4: без согласованного порога доли «принято вовремя» не существует.
     *
     * Плитка показывает прочерк, и это проектное решение, а не заглушка.
     */
    @Test
    fun `without an agreed threshold the on time share is undefined`() {
        val period = calculate(Months.slice(Months.daily(SEPTEMBER_FIRST, LocalDate.of(2026, 9, 10))))

        assertEquals(OnTimeShare.Undefined, period.onTimeShare)
    }

    /**
     * Проверка, готовая заранее: с подставленным правилом доля считается по нему.
     *
     * Включится вместе с ответом по ОВ-4 — расчёт при этом не переделывается, правило
     * приходит снаружи (`TDD.md §5.2`).
     */
    @Test
    fun `with an agreed threshold the share is counted by that rule`() {
        val twoHours = OnTimeRule.Agreed { scheduledAt, actedAt ->
            !actedAt.isAfter(scheduledAt.plusHours(2))
        }
        val date = LocalDate.of(2026, 9, 3)
        val slice = Months.slice(
            intakes = listOf(
                Months.intake(
                    at = date.atTime(Months.MORNING),
                    status = IntakeStatus.TAKEN,
                    actedAt = Months.instant(date.atTime(8, 20)),
                ),
                Months.intake(
                    at = date.plusDays(1).atTime(Months.MORNING),
                    status = IntakeStatus.TAKEN,
                    actedAt = Months.instant(date.plusDays(1).atTime(23, 40)),
                ),
            ),
        )

        val share = AdherenceCalculator(onTimeRule = twoHours)
            .of(slice, Months.WINDOW, Months.TODAY, Months.ZONE)
            .onTimeShare

        assertEquals(OnTimeShare.Value(0.5), share)
        assertEquals(50, (share as OnTimeShare.Value).percent)
    }

    /**
     * Почему доля объявлена неопределённой при выбранном окне (ОВ-4).
     *
     * Граница окна `pending → missed` выбрана «до конца суток» (`EndOfDayIntakeWindow`,
     * SKL-07). Правило «вовремя», построенное по этой границе, засчитывает **любую**
     * отметку принятым вовремя — то есть метрика совпадает с долей принятого и перестаёт
     * отличаться от приверженности. Это и есть довод в пользу [OnTimeShare.Undefined],
     * и он проверяется, а не рассказывается.
     */
    @Test
    fun `an end of day window makes on time indistinguishable from taken`() {
        val endOfDay = OnTimeRule.Agreed { scheduledAt, actedAt ->
            actedAt.toLocalDate() <= scheduledAt.toLocalDate()
        }
        val date = LocalDate.of(2026, 9, 3)
        val slice = Months.slice(
            intakes = listOf(
                Months.intake(
                    at = date.atTime(Months.MORNING),
                    status = IntakeStatus.TAKEN,
                    actedAt = Months.instant(date.atTime(23, 50)),
                ),
            ),
        )

        val period = AdherenceCalculator(onTimeRule = endOfDay).of(slice, Months.WINDOW, Months.TODAY, Months.ZONE)

        assertEquals(OnTimeShare.Value(1.0), period.onTimeShare)
        assertEquals(period.byMedication.single().takenShare, (period.onTimeShare as OnTimeShare.Value).share)
    }

    @Test
    fun `a period without a single decided intake has no on time share to show`() {
        val alwaysOnTime = OnTimeRule.Agreed { _, _ -> true }
        val slice = Months.slice(
            intakes = listOf(Months.intake(Months.TODAY.atTime(Months.EVENING), IntakeStatus.PENDING)),
        )

        val period = AdherenceCalculator(onTimeRule = alwaysOnTime)
            .of(slice, Months.WINDOW, Months.TODAY, Months.ZONE)

        assertEquals(OnTimeShare.Undefined, period.onTimeShare)
        assertNull(period.missesByTime[Months.EVENING])
    }

    private fun calculate(slice: HistorySlice) = calculator.of(slice, Months.WINDOW, Months.TODAY, Months.ZONE)

    /** Второе лекарство: два приёма в сутки весь месяц, из них [taken] принято. */
    private fun secondMedicationMonth(taken: Int) = (1..SEPTEMBER_DAYS).flatMap { day ->
        val date = LocalDate.of(2026, 9, day)
        SECOND_TIMES.map { time -> date.atTime(time) }
    }.mapIndexed { index, moment ->
        Months.intake(
            at = moment,
            status = if (index < taken) IntakeStatus.TAKEN else IntakeStatus.MISSED,
            medicationId = Months.SECOND_MEDICATION_ID,
        )
    }

    private companion object {
        val SEPTEMBER_FIRST: LocalDate = LocalDate.of(2026, 9, 1)
        const val SEPTEMBER_DAYS = 30
        val SECOND_TIMES: List<LocalTime> = listOf(Months.MORNING, Months.EVENING)
    }
}
