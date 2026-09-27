package ru.heuri.sklyanka.domain.profile

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.model.Fixtures

/**
 * Дублирование напоминаний (ПРФ-5, ПРФ-6, НАП-11, НАП-12).
 *
 * Парный тест к `SwitchProfileUseCaseTest`: там перепланирования быть не должно, здесь оно
 * обязательно. Эти два утверждения легко поменять местами, и обе ошибки тихие — одна
 * шумит в журнале, другая оставляет человека без напоминаний до следующего якорного
 * будильника.
 */
@DisplayName("Mirroring reminders replans the alarms immediately, in both directions")
class SetMirrorRemindersUseCaseTest {

    private val world = ProfileTestWorld()

    @Test
    fun `turning mirroring on replans and records the change`() = runTest {
        world.foreign(mirrorReminders = false)

        val outcome = world.setMirror(Fixtures.OTHER_PROFILE_ID, enabled = true)

        assertEquals(MirrorRemindersOutcome.Applied(before = false, after = true), outcome)
        assertTrue(requireNotNull(world.profiles.get(Fixtures.OTHER_PROFILE_ID)).mirrorReminders)
        assertEquals(1, world.replanner.calls)
        assertEquals(1, world.eventsOf(OpEvent.PROFILE_MIRROR_CHANGED).size)
    }

    @Test
    fun `turning mirroring off replans too`() = runTest {
        world.foreign(mirrorReminders = true)

        val outcome = world.setMirror(Fixtures.OTHER_PROFILE_ID, enabled = false)

        assertEquals(MirrorRemindersOutcome.Applied(before = true, after = false), outcome)
        assertEquals(1, world.replanner.calls)
    }

    @Test
    fun `setting the same value is not an event`() = runTest {
        world.foreign(mirrorReminders = true)

        val outcome = world.setMirror(Fixtures.OTHER_PROFILE_ID, enabled = true)

        assertEquals(MirrorRemindersOutcome.Unchanged, outcome)
        assertEquals(0, world.replanner.calls)
        assertEquals(0, world.eventsOf(OpEvent.PROFILE_MIRROR_CHANGED).size)
    }

    /** ПРФ-26: для профиля владельца флаг смысла не имеет и в интерфейсе не показывается. */
    @Test
    fun `the own profile has no mirroring to set`() = runTest {
        val outcome = world.setMirror(Fixtures.PROFILE_ID, enabled = true)

        assertEquals(MirrorRemindersOutcome.OwnProfile, outcome)
        assertEquals(0, world.replanner.calls)
    }

    @Test
    fun `the journal records the values and not the profile name`() = runTest {
        world.foreign(mirrorReminders = false)

        world.setMirror(Fixtures.OTHER_PROFILE_ID, enabled = true)

        val record = world.eventsOf(OpEvent.PROFILE_MIRROR_CHANGED).single()
        assertEquals(Fixtures.OTHER_PROFILE_ID, record.profileId)
        assertEquals("false", record.before)
        assertEquals("true", record.after)
    }
}
