package ru.heuri.sklyanka.domain.stock

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.model.Fixtures
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.schedule.FakeMedicationRepository
import ru.heuri.sklyanka.domain.schedule.RecordingOperationLog
import ru.heuri.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Одно предупреждение на экран «Сегодня», в контексте ровно одного профиля.
 *
 * Сценарии `@ui` про несколько сработавших порогов и `@profiles`
 * из `docs/tasks/SKL-12/acceptance.feature`.
 */
class StockWarningForProfileUseCaseTest {

    private val clock = FixedTimeSource(Instant.parse("2026-09-25T06:00:00Z"), ZoneId.of("Europe/Moscow"))
    private val medications = FakeMedicationRepository(clock)
    private val log = RecordingOperationLog()
    private val warnings = StockWarningForProfileUseCase(medications, clock, log)

    @Test
    fun `nothing fired means no plate at all`() = runTest {
        medications.put(twiceADay(id = "magnesium", stock = "30"))

        assertNull(warnings(Fixtures.PROFILE_ID).first())
    }

    @Test
    fun `the most urgent of several is shown`() = runTest {
        medications.put(twiceADay(id = "magnesium", stock = "9"))
        medications.put(onceADay(id = "calcium", stock = "2"))

        val warning = warnings(Fixtures.PROFILE_ID).first()

        assertEquals("calcium", warning?.medicationId)
        assertEquals(2, warning?.daysLeft)
    }

    @Test
    fun `a warning of another profile never leaks into this one`() = runTest {
        medications.put(twiceADay(id = "magnesium", stock = "30"))
        medications.put(onceADay(id = "mother", stock = "1").copy(profileId = Fixtures.OTHER_PROFILE_ID))

        assertNull(warnings(Fixtures.PROFILE_ID).first())
        assertEquals("mother", warnings(Fixtures.OTHER_PROFILE_ID).first()?.medicationId)
    }

    @Test
    fun `a finished course does not ask to buy more`() = runTest {
        // Курс закончился вчера: покупать нечего и незачем. Сегодняшняя дата берётся
        // из подменяемого источника времени (НФТ-48).
        medications.put(twiceADay(id = "magnesium", stock = "1").copy(endDate = LocalDate.of(2026, 9, 24)))

        assertNull(warnings(Fixtures.PROFILE_ID).first())
    }

    @Test
    fun `the last day of the course still warns`() = runTest {
        medications.put(twiceADay(id = "magnesium", stock = "1").copy(endDate = LocalDate.of(2026, 9, 25)))

        assertEquals("magnesium", warnings(Fixtures.PROFILE_ID).first()?.medicationId)
    }

    @Test
    fun `the appearance of a warning leaves a trace`() = runTest {
        medications.put(twiceADay(id = "magnesium", stock = "9"))

        warnings(Fixtures.PROFILE_ID).first()

        val record = log.records.single()
        assertEquals(OpEvent.STOCK_WARNING_RAISED, record.event)
        assertEquals("magnesium", record.medicationId)
        assertEquals("5", record.before)
        assertEquals("4", record.after)
    }

    @Test
    fun `no warning leaves no trace`() = runTest {
        medications.put(twiceADay(id = "magnesium", stock = "30"))

        warnings(Fixtures.PROFILE_ID).first()

        assertEquals(emptyList<OpEvent>(), log.events())
    }

    private fun twiceADay(id: String, stock: String): Medication = base(id, stock).copy(
        times = listOf(Fixtures.MORNING, EVENING),
    )

    private fun onceADay(id: String, stock: String): Medication = base(id, stock).copy(
        times = listOf(Fixtures.MORNING),
    )

    private fun base(id: String, stock: String): Medication = Fixtures.scheduledMedication().copy(
        id = id,
        name = id,
        doseAmount = BigDecimal.ONE,
        stockLeft = BigDecimal(stock),
        stockThresholdDays = FIVE_DAYS,
    )

    private companion object {
        val EVENING: LocalTime = LocalTime.of(20, 0)
        const val FIVE_DAYS = 5
    }
}
