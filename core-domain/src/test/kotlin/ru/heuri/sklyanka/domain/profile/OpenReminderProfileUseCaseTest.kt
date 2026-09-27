package ru.heuri.sklyanka.domain.profile

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.model.Fixtures

/**
 * Напоминание диктует профиль (ПРФ-8, ПРФ-20, ПРФ-21).
 *
 * Без этого правила человек, нажавший «Принято» на карточке маминого приёма из своего
 * профиля, совершил бы ровно ту ошибку, от которой защищает вся задача. Тихого возврата
 * после закрытия карточки нет — и это тоже проверяется, потому что «вежливый» возврат
 * создаёт состояние, о котором человек не знает.
 */
@DisplayName("A reminder opens in the profile of its own intake and does not roll back")
class OpenReminderProfileUseCaseTest {

    private val world = ProfileTestWorld()

    @Test
    fun `a foreign reminder switches the active profile to the profile of the intake`() = runTest {
        val foreign = world.foreign()
        val medicationId = world.foreignMedication()

        val context = world.openReminder(medicationId)

        assertEquals(ProfileContext.of(foreign), context)
        assertEquals(foreign.id, world.active.value)
        assertEquals(listOf(SwitchReason.REMINDER_OPENED), world.active.reasons)
    }

    @Test
    fun `after the reminder the application stays in that profile`() = runTest {
        world.foreign()
        val medicationId = world.foreignMedication()

        world.openReminder(medicationId)
        // Карточка закрыта, отметка поставлена — ничего больше не происходит.

        assertEquals(Fixtures.OTHER_PROFILE_ID, world.active.value)
        assertEquals(1, world.active.reasons.size)
    }

    @Test
    fun `a reminder of the own profile changes nothing`() = runTest {
        val medicationId = world.foreignMedication(profileId = Fixtures.PROFILE_ID)

        val context = world.openReminder(medicationId)

        assertEquals(Fixtures.PROFILE_ID, context?.id)
        assertEquals(emptyList<SwitchReason>(), world.active.reasons)
    }

    @Test
    fun `a reminder whose medication is gone switches nothing`() = runTest {
        assertNull(world.openReminder(ProfileTestWorld.FOREIGN_MEDICATION_ID))
        assertEquals(Fixtures.PROFILE_ID, world.active.value)
    }
}
