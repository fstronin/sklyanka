package ru.stohastika.sklyanka.domain.profile

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.intake.IntakeTestWorld
import ru.stohastika.sklyanka.domain.intake.MarkRequest
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import java.time.LocalDateTime

/**
 * Отметка в чужом профиле (ПР-12, ПРФ-14, ПРФ-22) — негативная часть приёмки.
 *
 * Проверяется то, ради чего задача существует: отметка попадает **в чужой профиль**
 * и несёт подпись того, кто её поставил; своя отметка подписи не получает.
 */
@DisplayName("A mark made in a foreign profile is stored there and signed by the owner")
class ForeignIntakeMarkingTest {

    private val world = IntakeTestWorld()

    @Test
    fun `the owner marking a foreign intake is recorded as the actor`() = runTest {
        world.foreignProfile()
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 8, 5))

        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))

        val intake = world.stored()
        assertEquals(IntakeStatus.TAKEN, intake.status)
        assertEquals(Fixtures.PROFILE_ID, intake.actorProfileId)
    }

    @Test
    fun `a mark in the own profile carries no actor`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 8, 5))

        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.TAKEN, IntakeSource.PHONE))

        assertNull(world.stored().actorProfileId)
    }

    /** Осознанный пропуск в чужом профиле подписывается так же, как принятый приём. */
    @Test
    fun `a manual skip in a foreign profile is signed as well`() = runTest {
        world.foreignProfile()
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 9, 40))

        world.mark(MarkRequest(Fixtures.INTAKE_ID, MarkResult.SKIPPED, IntakeSource.PHONE))

        val intake = world.stored()
        assertEquals(IntakeStatus.SKIPPED, intake.status)
        assertEquals(Fixtures.PROFILE_ID, intake.actorProfileId)
    }

    /** Приём по потребности идёт тем же правилом: второго определения исполнителя нет. */
    @Test
    fun `an on-demand intake taken in a foreign profile is signed too`() = runTest {
        world.foreignProfile()
        world.useOnDemand()
        world.medications.put(world.medication().copy(profileId = Fixtures.OTHER_PROFILE_ID))
        world.moveTo(LocalDateTime.of(2026, 9, 25, 12, 0))

        world.createPrn(Fixtures.MEDICATION_ID, IntakeSource.PHONE)

        val created = world.intakes.stored.values.single()
        assertEquals(Fixtures.PROFILE_ID, created.actorProfileId)
    }
}
