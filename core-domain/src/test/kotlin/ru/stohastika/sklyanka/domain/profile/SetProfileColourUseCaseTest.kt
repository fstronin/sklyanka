package ru.stohastika.sklyanka.domain.profile

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Fixtures

/**
 * Правка цвета профиля (ПРФ-15, ПРФ-16, ПРФ-27).
 *
 * Парный тест к `RenameProfileUseCaseTest`, и утверждения у них противоположные: имя правится
 * у любого профиля, цвет — только у чужого. Причина не в удобстве: цвет владельца нигде
 * не показывается как индикация (его роль несёт основной акцент приложения), а свободный
 * выбор акцента — платная возможность «Про» (Р-31). Поменять эти два правила местами легко,
 * и обе ошибки тихие.
 */
@DisplayName("Setting a profile colour is open for foreign profiles only and closed outside the palette")
class SetProfileColourUseCaseTest {

    private val world = ProfileTestWorld()

    @Test
    fun `a foreign profile takes a colour from the palette`() = runTest {
        world.foreign()

        val outcome = world.setColour(Fixtures.OTHER_PROFILE_ID, ProfileTestWorld.SECOND_COLOUR)

        assertEquals(ProfileTestWorld.SECOND_COLOUR, (outcome as SetProfileColourOutcome.Applied).profile.color)
        assertEquals(
            ProfileTestWorld.SECOND_COLOUR,
            requireNotNull(world.profiles.get(Fixtures.OTHER_PROFILE_ID)).color,
        )
    }

    /** ПРФ-16, ПРФ-27: цвет владельца не виден и не правится — им остаётся основной акцент. */
    @Test
    fun `the owner profile keeps its colour`() = runTest {
        val before = requireNotNull(world.profiles.get(Fixtures.PROFILE_ID)).color

        val outcome = world.setColour(Fixtures.PROFILE_ID, ProfileTestWorld.SECOND_COLOUR)

        assertEquals(SetProfileColourOutcome.OwnProfile, outcome)
        assertEquals(before, requireNotNull(world.profiles.get(Fixtures.PROFILE_ID)).color)
        assertEquals(0, world.eventsOf(OpEvent.PROFILE_COLOUR_CHANGED).size)
    }

    /** ПРФ-15: произвольный цвет мог бы совпасть с акцентом и снять защиту от чужого приёма. */
    @Test
    fun `a colour outside the palette is refused`() = runTest {
        world.foreign()

        val outcome = world.setColour(Fixtures.OTHER_PROFILE_ID, ProfileTestWorld.OUTSIDE_PALETTE_COLOUR)

        assertEquals(SetProfileColourOutcome.ColourOutsidePalette, outcome)
        assertEquals(
            ProfileTestWorld.FOREIGN_COLOUR,
            requireNotNull(world.profiles.get(Fixtures.OTHER_PROFILE_ID)).color,
        )
    }

    @Test
    fun `an unknown profile is refused before anything else`() = runTest {
        val outcome = world.setColour("00000000-0000-4000-8000-000000000000", ProfileTestWorld.SECOND_COLOUR)

        assertEquals(SetProfileColourOutcome.UnknownProfile, outcome)
        assertEquals(0, world.eventsOf(OpEvent.PROFILE_COLOUR_CHANGED).size)
    }

    /**
     * Тот же цвет — не событие (ПРФ-27).
     *
     * Форма правки зовёт сценарий при каждом сохранении чужого профиля: переименование без
     * смены цвета иначе оставляло бы в журнале пару одинаковых значений и трогало хранилище.
     */
    @Test
    fun `the same colour writes nothing and records nothing`() = runTest {
        world.foreign()

        val outcome = world.setColour(Fixtures.OTHER_PROFILE_ID, ProfileTestWorld.FOREIGN_COLOUR)

        assertEquals(SetProfileColourOutcome.Unchanged, outcome)
        assertEquals(0, world.eventsOf(OpEvent.PROFILE_COLOUR_CHANGED).size)
        assertEquals(
            ProfileTestWorld.FOREIGN_COLOUR,
            requireNotNull(world.profiles.get(Fixtures.OTHER_PROFILE_ID)).color,
        )
    }

    /** Цвет — не персональные данные: пара значений ARGB в журнале и есть разбор «стал другой цвет». */
    @Test
    fun `the journal records both argb values`() = runTest {
        world.foreign()

        world.setColour(Fixtures.OTHER_PROFILE_ID, ProfileTestWorld.SECOND_COLOUR)

        val record = world.eventsOf(OpEvent.PROFILE_COLOUR_CHANGED).single()
        assertEquals(Fixtures.OTHER_PROFILE_ID, record.profileId)
        assertEquals("#FF8F3E23", record.before)
        assertEquals("#FF1F5F6B", record.after)
    }
}
