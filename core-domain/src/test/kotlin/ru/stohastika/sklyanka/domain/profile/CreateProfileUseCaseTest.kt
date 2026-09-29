package ru.stohastika.sklyanka.domain.profile

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.log.OpEvent

/**
 * Заведение профиля (ПРФ-9, ПРФ-15, `ОВ-3`).
 *
 * Экрана добавления профиля нет, и сценарий существует ради двух вещей: правило «цвет
 * только из закрытого набора» обязано работать до появления экрана, а `mirror_reminders`
 * обязан задаваться явно — значение по умолчанию решает заказчик, и подставлять его
 * молча нельзя.
 */
@DisplayName("Creating a profile enforces the closed colour palette and an explicit mirroring choice")
class CreateProfileUseCaseTest {

    private val world = ProfileTestWorld()

    @Test
    fun `a profile is created with its own identifier and without the owner flag`() = runTest {
        val outcome = world.create(name = "mother", color = ProfileTestWorld.FOREIGN_COLOUR, mirrorReminders = true)

        assertTrue(outcome is CreateProfileOutcome.Created)
        val created = (outcome as CreateProfileOutcome.Created).profile
        val stored = requireNotNull(world.profiles.get(created.id))
        assertFalse(stored.isSelf)
        assertTrue(stored.mirrorReminders)
        assertEquals(created.id, stored.id)
    }

    /** ПРФ-15: цвет, неотличимый от основного акцента, ломает защиту, поэтому его нет в наборе. */
    @Test
    fun `a colour outside the palette is refused`() = runTest {
        val outcome = world.create(
            name = "mother",
            color = ProfileTestWorld.OUTSIDE_PALETTE_COLOUR,
            mirrorReminders = true,
        )

        assertEquals(CreateProfileOutcome.ColourOutsidePalette, outcome)
        assertEquals(1, world.profiles.stored.size)
    }

    @Test
    fun `a blank name is refused`() = runTest {
        val outcome = world.create(name = "   ", color = ProfileTestWorld.FOREIGN_COLOUR, mirrorReminders = false)

        assertEquals(CreateProfileOutcome.BlankName, outcome)
        assertEquals(1, world.profiles.stored.size)
    }

    @Test
    fun `the name is stored trimmed`() = runTest {
        val outcome = world.create(
            name = "  mother  ",
            color = ProfileTestWorld.FOREIGN_COLOUR,
            mirrorReminders = false,
        )

        val created = (outcome as CreateProfileOutcome.Created).profile
        assertEquals("mother", requireNotNull(world.profiles.get(created.id)).name)
    }

    /** ПРФ-6: у нового профиля появятся приёмы, и состав будильников обязан быть пересобран. */
    @Test
    fun `creating a profile replans the alarms`() = runTest {
        world.create(name = "mother", color = ProfileTestWorld.FOREIGN_COLOUR, mirrorReminders = true)

        assertEquals(1, world.replanner.calls)
    }

    /** НФТ-19: в журнале идентификатор и выбор дублирования, имени профиля там нет. */
    @Test
    fun `the journal records the identifier and the mirroring choice only`() = runTest {
        world.create(name = "mother", color = ProfileTestWorld.FOREIGN_COLOUR, mirrorReminders = true)

        val record = world.eventsOf(OpEvent.PROFILE_CREATED).single()
        assertEquals("true", record.after)
        assertNull(record.medicationId)
        assertTrue(world.log.records.none { it.before == "mother" || it.after == "mother" })
    }
}
