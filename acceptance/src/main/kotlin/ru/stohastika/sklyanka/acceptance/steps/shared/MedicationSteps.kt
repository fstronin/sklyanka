package ru.stohastika.sklyanka.acceptance.steps.shared

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Общий словарь: лекарство, его расписание, доза и остаток упаковки.
 *
 * Строку «у профиля «П» есть лекарство «Л» …» пишет всякая задача, которой нужен человек
 * с лекарством, а строку «остаток упаковки «Л» — N таблеток» — девять задач. Объявлены
 * они по одному разу и заводят лекарство в одном и том же состоянии сценария.
 *
 * Семейств строк несколько, потому что задачи называют лекарство по-разному: одна через
 * расписание и курс, другая через дозу одного приёма, третья через режим «по потребности».
 * Одно объявление на все формы означало бы выражение, ловящее заодно чужие строки, —
 * для общего прогона это хуже, чем четыре узких.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class MedicationSteps(private val world: AcceptanceWorld) {

    @Given(
        "^у профиля «(.+?)» есть лекарство «(.+?)» с (?:приёмом в|приёмами в|расписанием) " +
            "(\\d{2}:\\d{2}(?:(?:, | и )\\d{2}:\\d{2})*)(?: и ((?:бессрочным )?курсом.*))?$",
    )
    fun theProfileHasAScheduledMedication(profile: String, medication: String, times: String, course: String?) {
        add(profile, medication, RussianDates.times(times))
        course?.let { applyCourse(medication, it) }
    }

    @Given(
        "^у профиля «(.+?)» есть лекарство «(.+?)» с (?:приёмом|приёмами) в " +
            "(\\d{2}:\\d{2}(?:(?:, | и )\\d{2}:\\d{2})*) по (\\S+) таблетк\\S+$",
    )
    fun theProfileHasAScheduledMedicationWithADose(profile: String, medication: String, times: String, dose: String) {
        add(profile, medication, RussianDates.times(times))
        world.medications.update(world.medicationNamed(medication)) { it.copy(doseAmount = RussianDates.amount(dose)) }
    }

    /**
     * Ежедневный курс с дозой у названного профиля — Предыстория сценариев отмены (SKL-30).
     *
     * От [theProfileHasAScheduledMedication] отличается дозой, от задачи SKL-29 — профилем:
     * приёмы те же, но сценарий называет и профиль, и дозу, из которой считается возврат.
     */
    @Given(
        "^у профиля «(.+?)» есть лекарство «(.+?)» с приёмом в (\\d{2}:\\d{2}) " +
            "ежедневно и дозой (\\S+) таблетк\\S*$",
    )
    fun theProfileHasADailyMedicationWithDose(profile: String, medication: String, time: String, dose: String) {
        add(profile, medication, listOf(LocalTime.parse(time)))
        world.medications.update(world.medicationNamed(medication)) { it.copy(doseAmount = RussianDates.amount(dose)) }
    }

    /**
     * Ежедневный курс с дозой у активного профиля — Предыстория сценариев удаления
     * пробного напоминания (SKL-32).
     */
    @Given("^у профиля есть лекарство «([^»]+)» с приёмом в (\\d{2}:\\d{2}) ежедневно, доза (\\S+) таблетк\\S*$")
    fun theActiveProfileHasADailyMedicationWithDose(medication: String, time: String, dose: String) {
        add(world.profiles.active, medication, listOf(LocalTime.parse(time)))
        world.medications.update(world.medicationNamed(medication)) { it.copy(doseAmount = RussianDates.amount(dose)) }
    }

    /**
     * Ежедневный курс без дозы у активного профиля — Предыстория самопроверки доставки
     * (SKL-86).
     *
     * Доза сценарию проверки не нужна: тестовое напоминание не показывает состав приёма,
     * и в Предыстории важны только лекарство и его время.
     */
    @Given("^у профиля есть лекарство «([^»]+)» с приёмом ежедневно в (\\d{2}:\\d{2})$")
    fun theActiveProfileHasADailyMedication(medication: String, time: String) {
        add(world.profiles.active, medication, listOf(LocalTime.parse(time)))
    }

    @Given("^у профиля «(.+?)» есть лекарство «(.+?)» с приёмом в (\\d{2}:\\d{2}) и без учёта остатка$")
    fun theProfileHasAMedicationWithoutStockTracking(profile: String, medication: String, time: String) {
        add(profile, medication, listOf(LocalTime.parse(time)))
        world.medications.update(medication) { it.copy(stockLeft = null) }
    }

    @Given(
        "^у профиля «(.+?)» есть лекарство «(.+?)» по потребности не чаще (\\d+) раз в сутки" +
            "(?:, по (\\S+) таблетке)?$",
    )
    fun theProfileHasAnOnDemandMedication(profile: String, medication: String, limit: Int, dose: String?) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            mode = ScheduleMode.PRN,
            maxPerDay = limit,
        )
        dose?.let { amount ->
            world.medications.update(medication) { it.copy(doseAmount = RussianDates.amount(amount)) }
        }
    }

    /**
     * Строка про остаток встречается и в Предыстории, и в проверке — дословно одна и та же.
     * До первого действия сценария она задаёт остаток, после — проверяет его.
     */
    @Given("^остаток упаковки «(.+?)» — (\\d+) таблет\\S+$")
    fun theStockOfThatMedication(medication: String, amount: Int) {
        if (world.acted) {
            assertEquals(
                BigDecimal(amount),
                world.medications.stockOf(world.medicationNamed(medication)),
                "stock of $medication",
            )
        } else {
            world.medications.update(world.medicationNamed(medication)) { it.copy(stockLeft = BigDecimal(amount)) }
        }
    }

    /** Тот же остаток, но в проверке действия: «нажатие не списало лишнего». */
    @Then("^остаток упаковки «([^»]+)» остаётся (\\d+) таблет\\S+$")
    fun theStockOfThatMedicationStays(medication: String, amount: Int) {
        assertEquals(
            BigDecimal(amount),
            world.medications.stockOf(world.medicationNamed(medication)),
            "stock of $medication",
        )
    }

    @Given("^доза одного приёма «(.+?)» — (\\S+) таблетк\\S+$")
    fun theDoseOfOneIntakeIs(medication: String, dose: String) {
        // Доза приходит и цифрой, и русской дробью «0,5» — таблица примеров SKL-12.
        world.medications.update(world.medicationNamed(medication)) { it.copy(doseAmount = RussianDates.amount(dose)) }
    }

    @Then("^учёт остатка «(.+?)» остаётся невключённым$")
    fun theStockOfThatMedicationStaysUntracked(medication: String) {
        assertNull(world.medications.stockOf(world.medicationNamed(medication)), "stock of $medication")
    }

    // --- Вспомогательное ------------------------------------------------------------------

    private fun add(profile: String, medication: String, times: List<LocalTime>) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            times = times,
        )
    }

    private fun applyCourse(medication: String, course: String) {
        when {
            course.startsWith(OPEN_ENDED_COURSE) -> {
                val start = course.removePrefix(OPEN_ENDED_COURSE).removePrefix(" с ").trim()
                world.medications.update(medication) {
                    it.copy(
                        startDate = if (start.isEmpty()) world.clock.today() else world.dateOf(start),
                        endDate = null,
                    )
                }
            }

            course.startsWith(BOUNDED_COURSE) -> {
                val bounds = course.removePrefix(BOUNDED_COURSE).split(" по ")
                val end = world.dateOf(bounds[1].trim())
                world.medications.update(medication) {
                    it.copy(startDate = RussianDates.date(bounds[0].trim(), end), endDate = end)
                }
            }

            else -> error("unknown course description: $course")
        }
    }

    private companion object {
        const val OPEN_ENDED_COURSE = "бессрочным курсом"
        const val BOUNDED_COURSE = "курсом с "
    }
}
