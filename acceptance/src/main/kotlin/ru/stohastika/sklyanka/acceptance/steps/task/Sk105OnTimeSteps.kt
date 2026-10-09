package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.history.OnTimeRule
import ru.stohastika.sklyanka.domain.history.OnTimeShare
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * Шаги SKL-105: доля «принято вовремя» — числитель, знаменатель и то, что в них не входит.
 *
 * Здесь живёт предмет самой задачи: правило «вовремя» (Р-21) и состав наступивших плановых
 * приёмов (Р-27). Слова плитки и её ряд — в `feature-history` (`Sk105HistoryScreenSteps`):
 * экран показывает то, что посчитал домен, и своего счёта не заводит.
 *
 * Долю считает настоящий `AdherenceCalculator` с тем же правилом, что зарегистрировано
 * в продукте (`OnTimeRule.TwoHours`), а срез периода даёт `world.history` — тот же
 * `HistoryRepository` сценария, которым пользуются экранные проверки. Второго расчёта
 * долей в приёмке нет: шаг, посчитавший долю сам, проверял бы себя.
 *
 * Шаги заводят состояние настоящим доменом: отметка идёт `world.markExisting`, приёмы
 * «наступили и не отмечены» — ожидающимися и проходом по просроченным (`world.sweep`),
 * а не записью статуса в обход перехода.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class Sk105OnTimeSteps(private val world: AcceptanceWorld) {

    /**
     * Период, о котором сценарий спросил приверженность: его называет шаг «приложение
     * считает приверженность за …».
     *
     * Пока период не назван, меряется месяц текущего дня сценария: экранный сценарий SKL-18
     * смотрит на месяц своего «сегодня», а его шаг «человек смотрит на историю за сентябрь
     * 2026» живёт в наборе экрана (`feature-history`) и следа здесь не оставляет.
     */
    private var counted: HistoryWindow? = null

    // --- Предыстория: отметка, которая состоялась, и приёмы, которые наступили -------------

    /**
     * Осознанный пропуск как данность Предыстории: приём наступил, человек его пропустил.
     *
     * Отметка идёт доменом (`world.markExisting`), а не записью статуса: приём пропущен
     * тогда же, когда наступил, и окно приёма на этот момент ещё открыто.
     */
    @Given("^приём «([^»]+)» за (.+?), (\\d{2}:\\d{2}) пропущен осознанно в (\\d{2}:\\d{2})$")
    fun thatIntakeIsSkippedConsciously(medication: String, date: String, time: String, markedAt: String) {
        world.markExisting(
            medication = world.medicationNamed(medication),
            at = world.localMoment(date, time),
            markedAt = LocalTime.parse(markedAt),
            result = MarkResult.SKIPPED,
        )
    }

    /**
     * Доля считается за весь период, который сценарий называет отрезком или месяцем.
     *
     * Считает не шаг, а домен: здесь запоминается только период, а числа берёт расчёт
     * (Р-21, Р-27).
     */
    @When("^приложение считает приверженность за (.+)$")
    fun theApplicationCountsAdherenceFor(period: String) {
        counted = windowNamed(period)
    }

    // --- Проверки: доля вовремя ------------------------------------------------------------

    /** Числитель и знаменатель плитки: «1 из 3 наступивших приёмов». */
    @Then("^доля «принято вовремя» — (\\d+) из (\\d+) наступивш\\S* приём\\S*$")
    fun theOnTimeShareIs(onTime: Int, due: Int) = assertOnTimeShare(onTime = onTime, due = due)

    /** Тот же вопрос словами: «из двух наступивших приёмов вовремя считается один». */
    @Then("^из двух наступивших приёмов вовремя считается один$")
    fun oneOfTwoIntakesCameOnTime() = assertOnTimeShare(onTime = 1, due = 2)

    /**
     * Приёмы «по потребности» не входят в долю ни числителем, ни знаменателем (Р-27).
     *
     * Проверяется тем, что доля не меняется от изъятия этого лекарства: у приёмов
     * по потребности нет назначенного времени, и «вовремя» для них не определено.
     * У приёма с назначенным временем доля бы изменилась — и до, и после окна, —
     * поэтому неизменность и есть доказательство исключения из обоих счётчиков.
     */
    @Then("^«([^»]+)» не входит ни в числитель, ни в знаменатель доли вовремя$")
    fun thatMedicationIsOutOfTheOnTimeShare(medication: String) {
        val name = world.medicationNamed(medication)
        val medicationId = world.medications.get(name).id
        val window = countedWindow()
        val itsDueIntakes = world.intakes.of(name)
            .filter { !it.isPending && it.scheduledAt.toLocalDate() in window.from..window.to }
        assertTrue(
            itsDueIntakes.isNotEmpty(),
            "«$name» must have a due intake in the period, otherwise the check proves nothing",
        )
        val withIt = shareOf(window)
        val withoutIt = shareOf(window, excludedMedications = setOf(medicationId))
        assertTrue(
            withIt is OnTimeShare.Value,
            "the scenario must leave a measured share for the counters to speak about: $withIt",
        )
        assertEquals(
            withIt,
            withoutIt,
            "the intakes of «$name» must be out of the on-time share: numerator and denominator alike (Р-27)",
        )
    }

    // --- Предыстория: наступившие плановые приёмы ------------------------------------------

    /**
     * Наступившие плановые приёмы месяца как данность Предыстории.
     *
     * Сетка собирается из расписания: несколько приёмов в сутки по дням, которые уже
     * прошли (у сегодняшнего дня вечерний приём ещё не наступил, и делать его пропущенным
     * значило бы соврать). Лекарств заводится столько, сколько нужно сетке, чтобы вместить
     * названное число приёмов; отмеченные вовремя занимают первые `onTime` мест.
     *
     * Неотмеченные становятся пропущенными настоящим проходом по просроченным (ПР-1):
     * сценарий говорит не «человек пропустил», а «приёмы наступили».
     */
    @Given(
        "^в сентябре 2026 у профиля «([^»]+)» (\\d+) наступивш\\S+ планов\\S+ приём\\S+, " +
            "из них (\\d+) отмечен\\S* вовремя$",
    )
    fun thatProfileHasThatManyDueIntakes(profile: String, due: Int, onTime: Int) {
        assertTrue(onTime in 0..due, "the scenario cannot mark more intakes on time than came: $onTime of $due")
        val slots = septemberGrid(profile, due)
        assertEquals(due, slots.size, "the September grid must hold every due intake the scenario names")
        slots.take(onTime).forEach { (name, moment) -> world.intakes.markTaken(name, moment) }
        slots.drop(onTime).forEach { (name, moment) -> world.intakes.pending(name, moment) }
        world.sweep()
        val window = HistoryWindow.of(SEPTEMBER)
        val profileId = world.profiles.get(profile).id
        val dueInWorld = world.history.slice(profileId, window).intakes.count { !it.isPending }
        assertEquals(due, dueInWorld, "the scenario names $due due intakes, and the world has $dueInWorld")
    }

    // --- Обвязка ----------------------------------------------------------------------------

    /** Период проверок: названный сценарием, а если не назван — месяц текущего дня (см. [counted]). */
    private fun countedWindow(): HistoryWindow = counted ?: HistoryWindow.of(YearMonth.from(world.clock.today()))

    /**
     * Период из текста шага: «1–7 сентября 2026» — отрезок, «сентябрь 2026» — месяц.
     *
     * Границы без месяца и года достраивает [RussianDates], а не шаг: «1–7 сентября» —
     * та же запись, что «с 20 по 24 сентября» в соседних документах.
     */
    private fun windowNamed(period: String): HistoryWindow {
        val edges = PERIOD_DASH.split(period.trim())
        return if (edges.size == 2) {
            val dates = RussianDates.range(edges[0], edges[1], world.clock.today())
            HistoryWindow(dates.first(), dates.last())
        } else {
            HistoryWindow.of(RussianDates.month(period, world.clock.today()))
        }
    }

    /**
     * Доля «принято вовремя» по срезу периода.
     *
     * Срез берётся у мира сценария, а не собирается здесь: приёмы, лекарства и начало
     * истории обязаны прийти из одной выборки (ПР-10, ПР-11). Изъятие `excludedMedications`
     * — тот же приём, что у мягко удалённых лекарств: запись, чьё лекарство выборка
     * не принесла, в расчёт не входит вовсе.
     */
    private fun shareOf(window: HistoryWindow, excludedMedications: Set<String> = emptySet()): OnTimeShare {
        val profileId = world.profiles.get(world.profiles.active).id
        val slice = world.history.slice(profileId, window)
        val measured = if (excludedMedications.isEmpty()) {
            slice
        } else {
            slice.copy(
                intakes = slice.intakes.filterNot { it.medicationId in excludedMedications },
                medications = slice.medications.filterNot { it.id in excludedMedications },
            )
        }
        return AdherenceCalculator(OnTimeRule.TwoHours)
            .of(measured, window, world.clock.today(), world.clock.zone)
            .onTimeShare
    }

    /** Доля обязана быть посчитана: сценарий спрашивает числа, а не «мерить нечего». */
    private fun assertOnTimeShare(onTime: Int, due: Int) {
        val share = requireNotNull(shareOf(countedWindow()) as? OnTimeShare.Value) {
            "the scenario must leave a measured on-time share, and there is nothing to measure"
        }
        assertEquals(due, share.due, "the denominator counts every due scheduled intake (Р-27)")
        assertEquals(onTime, share.onTime, "the numerator counts the intakes marked inside the window (Р-21)")
    }

    /**
     * Наступившие приёмы сентября до сегодняшнего дня: пары «лекарство, момент».
     *
     * Сетки лекарств заводится столько, чтобы вместить названное число приёмов; лишние
     * места сетки сценарий не занимает — приёмов ровно столько, сколько он назвал.
     */
    private fun septemberGrid(profile: String, due: Int): List<Pair<String, LocalDateTime>> {
        val today = world.clock.today()
        val days = (1..SEPTEMBER.lengthOfMonth())
            .map { SEPTEMBER.atDay(it) }
            .filter { it.isBefore(today) }
        val perMedication = days.size * DOSE_TIMES.size
        assertTrue(perMedication > 0, "the scenario's today must have past days of September")
        val medicationCount = (due + perMedication - 1) / perMedication
        return (1..medicationCount)
            .map { index -> addMedication(profile, index) }
            .flatMap { name -> days.flatMap { date -> DOSE_TIMES.map { time -> name to date.atTime(time) } } }
            .take(due)
    }

    /** Лекарство сетки: курс с начала месяца, расписание — времена сетки. */
    private fun addMedication(profile: String, index: Int): String {
        val name = "$GRID_MEDICATION $index"
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = name,
            times = DOSE_TIMES,
            startDate = SEPTEMBER.atDay(1),
        )
        return name
    }

    /**
     * Все приёмы профиля — «по потребности»: наступивших плановых приёмов нет ни одного.
     *
     * Режим меняется у лекарств, а не у приёмов: «по потребности» — свойство лекарства
     * (ИНВ-14), и расчёт доли отбрасывает такие приёмы целиком (Р-27). Период в тексте шага
     * не используется: он называет отрезок, о котором говорит сценарий, а лекарство без
     * расписания не даёт наступивших плановых приёмов ни в одном месяце.
     */
    // Период в тексте шага — часть фразы сценария: он называет отрезок, о котором говорит
    // проверка. Приспособление не зависит от него, потому что режим меняется у лекарств
    // целиком, и наступивших плановых приёмов не остаётся ни в одном месяце.
    @Suppress("UnusedParameter")
    @Given("^все приёмы профиля «([^»]+)» в (.+?) назначены «по потребности»$")
    fun allIntakesOfTheProfileAreAsNeeded(profile: String, period: String) {
        val profileId = world.profiles.get(profile).id
        world.medications.names()
            .filter { key ->
                val medication = world.medications.get(key)
                medication.profileId == profileId && !medication.isArchived
            }
            .forEach { key ->
                world.medications.update(key) {
                    it.copy(scheduleMode = ScheduleMode.PRN, maxPerDay = PRN_MAX_PER_DAY, times = emptyList())
                }
            }
    }

    private companion object {
        val SEPTEMBER: YearMonth = YearMonth.of(2026, 9)

        /** Не чаще трёх раз в сутки — умолчание продукта для приёма «по потребности». */
        const val PRN_MAX_PER_DAY = 3

        /** Времена сетки: утро и вечер — дни сетки всегда прошедшие, и обе отметки наступили. */
        val DOSE_TIMES: List<LocalTime> = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))

        const val GRID_MEDICATION = "Наступившее лекарство"

        /** Отрезок дат в тексте шага: «1–7 сентября 2026». */
        val PERIOD_DASH = Regex("\\s*[–—-]\\s*")
    }
}
