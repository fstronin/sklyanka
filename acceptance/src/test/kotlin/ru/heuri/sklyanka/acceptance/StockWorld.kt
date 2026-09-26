package ru.heuri.sklyanka.acceptance

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.stock.RefillStockUseCase
import ru.heuri.sklyanka.domain.stock.StockForecast
import ru.heuri.sklyanka.domain.stock.StockPanel
import ru.heuri.sklyanka.domain.stock.StockWarning
import ru.heuri.sklyanka.domain.stock.StockWarningForProfileUseCase
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Состояние сценария приёмки SKL-12 — остаток упаковки и предупреждение о покупке.
 *
 * Собственного хранилища лекарств здесь нет намеренно: остаток и доза — поля лекарства,
 * их ставят шаги SKL-07, и второй экземпляр тех же данных означал бы, что приёмка
 * проверяет не то состояние, которое меняет отметка приёма. Мир надстраивается над
 * [IntakeLifecycleWorld] и добавляет к нему ровно то, чего в SKL-07 не было: порог,
 * прогноз запаса, предупреждение и пополнение.
 *
 * Прогоняются настоящие доменные правила — [StockWarning], [StockForecast],
 * [StockPanel], [RefillStockUseCase] и [StockWarningForProfileUseCase].
 */
internal class StockWorld(
    private val intakes: IntakeLifecycleWorld,
    /**
     * Мир генерации приёмов (SKL-06).
     *
     * Один сценарий SKL-12 задаёт предысторию его шагом («приёмы … отмечены принятыми»)
     * и его же шагом проверяет, что пополнение историю не тронуло. Лекарство для этого
     * обязано быть известно обоим мирам, поэтому оно отражается сюда.
     */
    private val planning: PlanningWorld,
) {

    /** Профиль, открытый на телефоне: предупреждения показываются только по его лекарствам (ПР-10). */
    var activeProfile: String = SELF_PROFILE
        private set

    // --- Предыстория -----------------------------------------------------------------------

    /**
     * Порог предупреждения о покупке.
     *
     * Лекарство заводится по первому упоминанию, если сценарий не завёл его шагом SKL-07:
     * шаги Предыстории разных задач стоят вперемежку, и порядок между ними не гарантирован.
     */
    fun threshold(name: String, days: Int?) {
        ensureKnown(name)
        intakes.update(name) { it.copy(stockThresholdDays = days) }
        mirrorToPlanning(name)
    }

    fun stock(name: String, units: BigDecimal) {
        ensureKnown(name)
        intakes.update(name) { it.copy(stockLeft = units) }
    }

    /**
     * Лекарство по расписанию, заданное числом приёмов в сутки.
     *
     * Сценарий говорит «два приёма в сутки», а не «в 08:00 и 20:00»: важно их число,
     * потому что суточный расход считается именно по нему. Времена раскладываются
     * с утра с равным шагом, лишь бы их было ровно столько.
     */
    fun addScheduled(profileName: String, name: String, perDay: Int, dose: BigDecimal) {
        intakes.addMedication(
            profileName = profileName,
            name = name,
            times = (0 until perDay).map { MORNING.plusHours(HOURS_BETWEEN * it) },
        )
        intakes.update(name) { it.copy(doseAmount = dose) }
        mirrorToPlanning(name)
    }

    fun addOnDemand(profileName: String, name: String, limit: Int, dose: BigDecimal) {
        intakes.addMedication(
            profileName = profileName,
            name = name,
            mode = ScheduleMode.PRN,
            maxPerDay = limit,
        )
        intakes.update(name) { it.copy(doseAmount = dose) }
    }

    // --- Действия --------------------------------------------------------------------------

    fun openDay(date: LocalDate) {
        intakes.rememberState()
        moveToDate(date)
    }

    fun switchTo(profileName: String) {
        intakes.profile(profileName)
        activeProfile = profileName
    }

    /**
     * Пополнение остатка человеком.
     *
     * Снимок приёмов берётся до действия: «пополнение не меняет историю приёмов» —
     * это утверждение о том, чего действие не сделало, и проверить его можно только
     * сравнением с состоянием до.
     */
    fun refill(name: String, units: BigDecimal, at: LocalDateTime, asAction: Boolean = true) {
        if (asAction) {
            // Снимок приёмов до действия берут оба мира: «пополнение не меняет историю
            // приёмов» — утверждение о том, чего действие не сделало, и проверяется оно
            // сравнением с состоянием до (шаг проверки принадлежит приёмке SKL-06).
            intakes.rememberState()
            planning.rememberState()
        }
        intakes.moveTo(at)
        val useCase = RefillStockUseCase(intakes.medicationStore, intakes.log)
        runBlocking { useCase(intakes.medication(name).id, units) }
    }

    // --- Проверки --------------------------------------------------------------------------

    fun forecast(name: String): StockForecast = StockForecast.of(medication(name))

    fun panel(name: String): StockPanel = StockPanel.of(medication(name))

    /** Предупреждение конкретного лекарства — без учёта того, чьё оно самое срочное. */
    fun warningOf(name: String): StockWarning? = StockWarning.of(medication(name))

    /** Карточка лекарства, открытая последней: шаги «в нём …» говорят о ней. */
    var openedCard: String = ""

    /** Все сработавшие пороги профиля — плашка показывает из них ровно одно. */
    fun firedOf(profileName: String): List<StockWarning> = trackedOf(profileName).mapNotNull { StockWarning.of(it) }

    /** То единственное предупреждение, которое видно на «Сегодня» у активного профиля. */
    fun warningOnToday(): StockWarning? {
        val useCase = StockWarningForProfileUseCase(intakes.medicationStore, intakes.clock, intakes.log)
        return runBlocking { useCase(intakes.profile(activeProfile).id).first() }
    }

    /** Плашка показывает одно предупреждение или ни одного — списка на экране не бывает. */
    fun plateOnToday(): List<StockWarning> = listOfNotNull(warningOnToday())

    fun medication(name: String): Medication = intakes.medication(name)

    fun stockOf(name: String): BigDecimal? = intakes.stockOf(name)

    /**
     * Лекарства профиля, у которых остаток действительно ведётся.
     *
     * Список берётся из того же хранилища, что видит домен, а не из имён, упомянутых
     * в шагах: «других лекарств с учётом остатка нет» — утверждение обо всём профиле,
     * и проверять его по списку упоминаний значит проверять сам сценарий.
     */
    fun trackedOf(profileName: String): List<Medication> {
        val profileId = intakes.profile(profileName).id
        return runBlocking { intakes.medicationStore.observeActive(profileId).first() }
            .filter { it.stockLeft != null }
    }

    // --- Вспомогательное ---------------------------------------------------------------------

    private fun ensureKnown(name: String) {
        if (!intakes.hasMedication(name)) {
            intakes.addMedication(profileName = activeProfile, name = name, times = listOf(MORNING))
        }
    }

    /**
     * Отражение лекарства в мир генерации приёмов.
     *
     * Шаги SKL-06 ищут лекарство в своём хранилище и падают, не найдя его. Предыстория
     * SKL-12 заводит лекарство шагом SKL-07, то есть в другом мире, — отражение закрывает
     * этот зазор, не объявляя чужих шагов заново.
     */
    private fun mirrorToPlanning(name: String) {
        if (planning.hasMedication(name)) return
        val source = intakes.medication(name)
        planning.addMedication(
            profileName = activeProfile,
            name = name,
            times = source.times,
            startDate = source.startDate,
            endDate = source.endDate,
            mode = source.scheduleMode,
            maxPerDay = source.maxPerDay,
        )
    }

    private fun moveToDate(date: LocalDate) {
        intakes.moveTo(date.atTime(intakes.clock.nowLocal().toLocalTime()))
    }

    private companion object {
        const val SELF_PROFILE = "Я"
        val MORNING: LocalTime = LocalTime.of(8, 0)

        /** Шаг между приёмами суток: сценарию важно их число, а не расстановка. */
        const val HOURS_BETWEEN = 4L
    }
}
