package ru.stohastika.sklyanka.acceptance.steps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.acceptance.steps.shared.ClockSteps
import ru.stohastika.sklyanka.acceptance.steps.shared.IntakeSteps
import ru.stohastika.sklyanka.acceptance.steps.shared.MedicationSteps
import ru.stohastika.sklyanka.acceptance.steps.shared.ProfileSteps
import ru.stohastika.sklyanka.acceptance.steps.task.IntakeLifecycleSteps
import ru.stohastika.sklyanka.acceptance.steps.task.StockSteps
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Общие шаги и шаги задачи в одном сценарии.
 *
 * Тест проверяет то, ради чего задача и делалась, и проверяет это без прогона Cucumber:
 * шаг из общего словаря меняет состояние, а шаг задачи видит изменение. Прогон это тоже
 * покажет — но только если сценарий действительно падал бы; именно так и не падал
 * сценарий SKL-07 про перелёт, пока перелёт происходил в чужом состоянии.
 */
class SharedStepsTest {

    private val world = AcceptanceWorld()
    private val profiles = ProfileSteps(world)
    private val clock = ClockSteps(world)
    private val medications = MedicationSteps(world)
    private val intakes = IntakeSteps(world)
    private val lifecycle = IntakeLifecycleSteps(world)
    private val stock = StockSteps(world)

    @Test
    fun `the flight declared in the dictionary is seen by the steps of the task`() {
        backgroundOfIntakeLifecycle()
        intakes.thatIntakeIsAlreadyTaken("Л", "25 сентября 2026", "08:00", "08:05")
        val zoneBefore = world.clock.zone
        val momentBefore = world.clock.now()

        clock.theDeviceChangesItsTimeZone("25 сентября 2026", "12:00", "три", "восточнее")

        assertNotEquals(zoneBefore, world.clock.zone, "the flight has to actually happen")
        assertEquals(ZoneId.ofOffset("", java.time.ZoneOffset.ofHours(6)), world.clock.zone)
        assertNotEquals(momentBefore, world.clock.now(), "the scenario moved the clock to 12:00 before flying")
        // Шаги задачи SKL-07 читают то самое состояние, которое изменил общий шаг.
        lifecycle.theMomentOfTheMarkIsUnchanged()
        lifecycle.theScheduledMomentStays("25 сентября 2026", "08:00")
        intakes.theIntakeIsInThatState("принятым")
    }

    @Test
    fun `the moment named by the background reaches the steps of the task`() {
        backgroundOfIntakeLifecycle()

        assertEquals(LocalDateTime.of(2026, 9, 25, 7, 55), world.clock.nowLocal())
    }

    @Test
    fun `a task that wrote no steps of its own gets an executable background`() {
        // Предыстория SKL-12 целиком: первые три строки — общий словарь, четвёртая —
        // шаг самой SKL-12, пятая снова общий.
        profiles.theActiveProfileIs("Я")
        medications.theProfileHasAScheduledMedicationWithADose("Я", "Магний B6", "08:00 и 20:00", "1")
        medications.theStockOfThatMedication("Магний B6", 30)
        stock.theWarningThresholdOfThatMedication("Магний B6", 5)
        clock.theCurrentMomentIs("25 сентября 2026", "07:55")

        val medication = world.medications.get("Магний B6")
        assertEquals(BigDecimal(30), medication.stockLeft, "the stock of the background")
        assertEquals(5, medication.stockThresholdDays, "the threshold of the background")
        assertEquals(2, medication.times.size, "the schedule of the background")
        assertEquals(world.profiles.get("Я").id, medication.profileId)
    }

    @Test
    fun `the mark made by a shared step is spent from the stock the task reads`() {
        backgroundOfIntakeLifecycle()
        intakes.thatIntakeIsPending("Л", "25 сентября 2026", "08:00")

        intakes.thePersonMarksTheIntake("25 сентября 2026", "08:05", "принятым")

        assertEquals(BigDecimal(29), world.medications.stockOf("Л"), "one tablet of the dose")
    }

    private fun backgroundOfIntakeLifecycle() {
        profiles.theActiveProfileIs("Я")
        medications.theProfileHasAScheduledMedicationWithADose("Я", "Л", "08:00 и 20:00", "1")
        medications.theStockOfThatMedication("Л", 30)
        lifecycle.theIntakeWindowRunsUntilTheEndOfTheDay()
        clock.theCurrentMomentIs("25 сентября 2026", "07:55")
    }
}
