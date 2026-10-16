package ru.stohastika.sklyanka.domain.profile

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Fixtures

/**
 * Правка имени профиля (ПРФ-27, Р-84 п. 5).
 *
 * Главное утверждение — отсутствие проверки владельца: имя правится у любого профиля,
 * и у владельца это единственное, что вообще правится. Тест «владелец переименовывается»
 * стоит первым именно поэтому: он ловит соблазн дописать сюда `isSelf` по аналогии
 * с цветом и дублированием, где такая проверка обязательна.
 */
@DisplayName("Renaming a profile touches any profile, the owner included, and keeps the name out of the journal")
class RenameProfileUseCaseTest {

    private val world = ProfileTestWorld()

    @Test
    fun `the owner profile is renamed like any other`() = runTest {
        val outcome = world.rename(Fixtures.PROFILE_ID, "me")

        assertEquals("me", (outcome as RenameProfileOutcome.Applied).profile.name)
        assertEquals("me", requireNotNull(world.profiles.get(Fixtures.PROFILE_ID)).name)
    }

    @Test
    fun `a foreign profile is renamed`() = runTest {
        world.foreign()

        val outcome = world.rename(Fixtures.OTHER_PROFILE_ID, "mama")

        assertEquals("mama", (outcome as RenameProfileOutcome.Applied).profile.name)
        assertEquals("mama", requireNotNull(world.profiles.get(Fixtures.OTHER_PROFILE_ID)).name)
    }

    @Test
    fun `a blank name is refused and nothing is written`() = runTest {
        world.foreign()

        val outcome = world.rename(Fixtures.OTHER_PROFILE_ID, "   ")

        assertEquals(RenameProfileOutcome.BlankName, outcome)
        assertEquals("mother", requireNotNull(world.profiles.get(Fixtures.OTHER_PROFILE_ID)).name)
        assertEquals(0, world.eventsOf(OpEvent.PROFILE_RENAMED).size)
    }

    @Test
    fun `the name is stored trimmed`() = runTest {
        world.foreign()

        world.rename(Fixtures.OTHER_PROFILE_ID, "  mama  ")

        assertEquals("mama", requireNotNull(world.profiles.get(Fixtures.OTHER_PROFILE_ID)).name)
    }

    @Test
    fun `an unknown profile is refused`() = runTest {
        val outcome = world.rename("00000000-0000-4000-8000-000000000000", "mama")

        assertEquals(RenameProfileOutcome.UnknownProfile, outcome)
        assertEquals(0, world.eventsOf(OpEvent.PROFILE_RENAMED).size)
    }

    /** НФТ-19: в журнале идентификатор и факт правки, а имени профиля там нет. */
    @Test
    fun `the journal records the fact and never the name`() = runTest {
        world.foreign()

        world.rename(Fixtures.OTHER_PROFILE_ID, "mama")

        val record = world.eventsOf(OpEvent.PROFILE_RENAMED).single()
        assertEquals(Fixtures.OTHER_PROFILE_ID, record.profileId)
        assertNull(record.before)
        assertNull(record.after)
        assertTrue(world.log.records.none { it.before == "mother" || it.after == "mother" })
        assertTrue(world.log.records.none { it.before == "mama" || it.after == "mama" })
    }
}
