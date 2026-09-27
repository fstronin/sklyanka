package ru.heuri.sklyanka.domain.profile

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.model.Fixtures

/**
 * Переключение профиля (ПРФ-17…ПРФ-21).
 *
 * Главный тест здесь — тот, что не проверяет переключение вовсе: смена активного профиля
 * не имеет права менять набор напоминаний (НАП-12, ПРФ-6). Это утверждение путается местами
 * с обратным («смена дублирования обязана перепланировать») ровно потому, что оба звучат
 * похоже; поэтому оба проверяются числом вызовов.
 */
@DisplayName("Switching the active profile never touches the alarms")
class SwitchProfileUseCaseTest {

    private val world = ProfileTestWorld()

    @Test
    fun `switching to a foreign profile records the reason`() = runTest {
        world.foreign()

        val outcome = world.switch(Fixtures.OTHER_PROFILE_ID, SwitchReason.USER_SETTINGS)

        assertEquals(
            SwitchOutcome.Switched(
                from = Fixtures.PROFILE_ID,
                to = ProfileContext.of(requireNotNull(world.profiles.get(Fixtures.OTHER_PROFILE_ID))),
            ),
            outcome,
        )
        assertEquals(Fixtures.OTHER_PROFILE_ID, world.active.value)
        assertEquals(listOf(SwitchReason.USER_SETTINGS), world.active.reasons)
    }

    @Test
    fun `switching the profile schedules and cancels nothing`() = runTest {
        world.foreign()

        world.switch(Fixtures.OTHER_PROFILE_ID, SwitchReason.USER_SETTINGS)
        world.switch(Fixtures.PROFILE_ID, SwitchReason.USER_SETTINGS)

        assertEquals(0, world.replanner.calls)
    }

    @Test
    fun `choosing the profile that is already active is not an event`() = runTest {
        val outcome = world.switch(Fixtures.PROFILE_ID, SwitchReason.USER_SETTINGS)

        assertEquals(SwitchOutcome.AlreadyActive, outcome)
        assertEquals(emptyList<SwitchReason>(), world.active.reasons)
    }

    @Test
    fun `an unknown profile does not become active`() = runTest {
        val outcome = world.switch(MISSING_ID, SwitchReason.USER_SETTINGS)

        assertEquals(SwitchOutcome.UnknownProfile, outcome)
        assertEquals(Fixtures.PROFILE_ID, world.active.value)
    }

    private companion object {
        const val MISSING_ID = "ffffffff-0000-4000-8000-000000000000"
    }
}
