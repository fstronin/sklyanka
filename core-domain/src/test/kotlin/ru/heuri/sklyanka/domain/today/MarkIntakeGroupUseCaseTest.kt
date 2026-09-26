package ru.heuri.sklyanka.domain.today

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.intake.MarkIntakeUseCase
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Profile
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import ru.heuri.sklyanka.domain.schedule.FakeIntakeRepository
import ru.heuri.sklyanka.domain.schedule.FakeMedicationRepository
import ru.heuri.sklyanka.domain.schedule.FakeProfileRepository
import ru.heuri.sklyanka.domain.schedule.RecordingOperationLog
import ru.heuri.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal

/**
 * Групповая отметка одновременных приёмов (решение ОВ-32).
 *
 * Два обязательства проверяются здесь: отдельная запись на каждый приём группы и
 * списание остатка по каждому лекарству. Третье — «частичного результата не бывает» —
 * проверяется откатом.
 */
class MarkIntakeGroupUseCaseTest {

    private val clock = FixedTimeSource(Today.moment(Today.at(14, 5)), Today.ZONE)
    private val profiles = FakeProfileRepository()
    private val medications = FakeMedicationRepository(clock)
    private val log = RecordingOperationLog()

    private val magnesium = Today.medication("m-magnesium", dose = "2", stockLeft = "9")
    private val panangin = Today.medication("m-panangin", dose = "1", stockLeft = "20")
    private val omez = Today.medication("m-omez", dose = "1", stockLeft = null)

    init {
        profiles.put(Profile(id = Today.PROFILE, name = "self", color = 0, isSelf = true, mirrorReminders = false))
        medications.putAll(listOf(magnesium, panangin, omez))
    }

    @Test
    fun `the whole group is marked by one operation`() = runTest {
        val intakes = storeWithThreePending()
        val useCase = useCaseOver(intakes, intakes)

        val outcome = useCase(groupIds(), IntakeSource.PHONE)

        assertEquals(GroupMarkOutcome.Applied(marked = 3, alreadyMarked = 0), outcome)
        assertTrue(groupIds().all { intakes.stored.getValue(it).status == IntakeStatus.TAKEN })
    }

    @Test
    fun `every intake of the group keeps its own record and the same moment`() = runTest {
        val intakes = storeWithThreePending()
        val useCase = useCaseOver(intakes, intakes)

        useCase(groupIds(), IntakeSource.PHONE)

        val moments = groupIds().map { intakes.stored.getValue(it).actedAt }
        assertEquals(3, moments.size)
        assertEquals(setOf(Today.moment(Today.at(14, 5))), moments.toSet())
    }

    @Test
    fun `the stock is written off for every medication of the group`() = runTest {
        val intakes = storeWithThreePending()
        val useCase = useCaseOver(intakes, intakes)

        useCase(groupIds(), IntakeSource.PHONE)

        assertEquals(BigDecimal("7"), medications.stored.getValue(magnesium.id).stockLeft)
        assertEquals(BigDecimal("19"), medications.stored.getValue(panangin.id).stockLeft)
        assertNull(medications.stored.getValue(omez.id).stockLeft)
    }

    @Test
    fun `an already marked intake is skipped silently and its stock is untouched`() = runTest {
        val intakes = storeWithThreePending()
        intakes.put(
            intakes.stored.getValue("i-panangin").copy(
                status = IntakeStatus.SKIPPED,
                actedAt = Today.moment(Today.at(14, 1)),
                source = IntakeSource.PHONE,
            ),
        )
        val useCase = useCaseOver(intakes, intakes)

        val outcome = useCase(groupIds(), IntakeSource.PHONE)

        assertEquals(GroupMarkOutcome.Applied(marked = 2, alreadyMarked = 1), outcome)
        assertEquals(IntakeStatus.SKIPPED, intakes.stored.getValue("i-panangin").status)
        assertEquals(BigDecimal("20"), medications.stored.getValue(panangin.id).stockLeft)
    }

    @Test
    fun `a failing write leaves no intake marked and no stock touched`() = runTest {
        val intakes = storeWithThreePending()
        val failing = FailingOnMark(intakes, failingIntakeId = "i-omez")
        val useCase = useCaseOver(repository = failing, store = intakes)

        val outcome = useCase(groupIds(), IntakeSource.PHONE)

        assertInstanceOf(GroupMarkOutcome.Rejected::class.java, outcome)
        assertTrue(groupIds().all { intakes.stored.getValue(it).status == IntakeStatus.PENDING })
        assertEquals(BigDecimal("9"), medications.stored.getValue(magnesium.id).stockLeft)
        assertEquals(BigDecimal("20"), medications.stored.getValue(panangin.id).stockLeft)
    }

    @Test
    fun `the group operation leaves its own trace in the log`() = runTest {
        val intakes = storeWithThreePending()
        val useCase = useCaseOver(intakes, intakes)

        useCase(groupIds(), IntakeSource.PHONE)

        val record = log.records.single { it.event == OpEvent.INTAKE_GROUP_MARKED }
        assertEquals("3", record.after)
        assertEquals("marked=3,already=0", record.detail)
    }

    private fun groupIds() = listOf("i-magnesium", "i-panangin", "i-omez")

    private fun storeWithThreePending(): FakeIntakeRepository {
        val store = FakeIntakeRepository(clock, medications)
        listOf(magnesium, panangin, omez).forEach { medication ->
            store.put(pending(medication.id))
        }
        return store
    }

    private fun pending(medicationId: String): Intake =
        Today.intake(id = "i-${medicationId.removePrefix("m-")}", medicationId = medicationId, at = Today.at(14, 0))

    private fun useCaseOver(repository: IntakeRepository, store: FakeIntakeRepository) = MarkIntakeGroupUseCase(
        markIntake = MarkIntakeUseCase(repository, medications, profiles, clock),
        transactor = SnapshotTransactor(store, medications),
        time = clock,
        log = log,
    )
}
