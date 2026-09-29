package ru.stohastika.sklyanka.domain.onboarding

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.reminder.FakePermissionStateReader
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant

@DisplayName("The chain state is read from the system on every show, never from a stored flag")
class ReadPermissionChecklistUseCaseTest {

    private val now: Instant = Instant.parse("2026-09-25T10:00:00Z")
    private val time = FixedTimeSource(now)
    private val permissions = FakePermissionStateReader()
    private val store = FakeOnboardingStateStore()
    private val hints = FakeAutostartHintReader()
    private val requests = FakePermissionRequestSupport()

    private val read = ReadPermissionChecklistUseCase(
        permissions = permissions,
        store = store,
        hints = hints,
        requests = requests,
        time = time,
    )

    @Test
    fun `granted permissions are reported with the moment they were read`() = runTest {
        val chain = read()

        assertEquals(PermissionGrant.Granted(now), chain.notifications)
        assertEquals(PermissionGrant.Granted(now), chain.exactAlarms)
    }

    @Test
    fun `a permission revoked in system settings is seen on the next read`() = runTest {
        assertTrue(read().notifications.isResolved)

        permissions.states[ReminderPermission.NOTIFICATIONS] = PermissionState.DENIED

        // Никакого перезапуска: тот же экземпляр сценария, новое чтение системы (НФТ-52).
        assertEquals(PermissionGrant.Denied(canAsk = true), read().notifications)
    }

    @Test
    fun `exact alarms cannot be asked for from code and the state says so`() = runTest {
        permissions.states[ReminderPermission.EXACT_ALARMS] = PermissionState.DENIED

        val denied = read().exactAlarms as PermissionGrant.Denied

        // ПЛ-14: право выдают на экране особого доступа, системного диалога с отказом нет.
        assertFalse(denied.canAsk)
        assertEquals(StepAction.OPEN_SYSTEM_SETTINGS, actionFor(PermissionStep.EXACT_ALARMS, denied))
    }

    @Test
    fun `notifications are asked for with a system dialog`() = runTest {
        permissions.states[ReminderPermission.NOTIFICATIONS] = PermissionState.DENIED

        val denied = read().notifications as PermissionGrant.Denied

        assertTrue(denied.canAsk)
        assertEquals(StepAction.REQUEST_PERMISSION, actionFor(PermissionStep.NOTIFICATIONS, denied))
    }

    @Test
    fun `before Android 13 the notifications step leads to system settings and not to a dialog`() = runTest {
        // Разрешения не существует, запрашивать нечего: диалог ничего бы не сделал,
        // а карточка осталась бы невыданной без объяснения (ПЛ-13).
        requests.beforeAndroid13()
        permissions.states[ReminderPermission.NOTIFICATIONS] = PermissionState.DENIED

        val denied = read().notifications as PermissionGrant.Denied

        assertFalse(denied.canAsk)
        assertEquals(StepAction.OPEN_SYSTEM_SETTINGS, actionFor(PermissionStep.NOTIFICATIONS, denied))
    }

    @Test
    fun `a granted step offers no action at all`() = runTest {
        assertNull(actionFor(PermissionStep.NOTIFICATIONS, read().notifications))
        assertNull(actionFor(PermissionStep.EXACT_ALARMS, read().exactAlarms))
    }

    @Test
    fun `the autostart step keeps its action even after the person confirmed it`() = runTest {
        store.setAutostartConfirmed(true)

        // Подтверждение человека — не проверка системы, и путь назад в настройки
        // обязан остаться открытым (НАП-20).
        assertEquals(StepAction.OPEN_AUTOSTART_SETTINGS, actionFor(PermissionStep.AUTOSTART, read().autostart))
    }

    @Test
    fun `the autostart step is unresolved by default even when everything else is in place`() = runTest {
        hints.batteryOptimizationIgnored = true

        val chain = read()

        assertFalse(chain.autostart.isResolved, "REMINDER-20: default state of the third step is unresolved")
        assertFalse(chain.autostart.verifiedBySystem)
        assertFalse(chain.allResolved)
    }

    @Test
    fun `a lifted battery optimisation is shown as an indirect hint and nothing more`() = runTest {
        hints.batteryOptimizationIgnored = true

        val autostart = read().autostart

        assertEquals(setOf(AutostartHint.BATTERY_OPTIMIZATION_IGNORED), autostart.hints)
        assertFalse(autostart.isResolved)
    }

    @Test
    fun `manual confirmation is the only thing that resolves the autostart step`() = runTest {
        store.setAutostartConfirmed(true)

        val chain = read()

        assertTrue(chain.autostart.confirmedByUser)
        assertTrue(chain.autostart.isResolved)
        assertFalse(chain.autostart.verifiedBySystem, "confirmed by the person is not verified by the system")
        assertTrue(chain.allResolved)
    }

    @Test
    fun `withdrawn confirmation puts the autostart step back to unresolved`() = runTest {
        store.setAutostartConfirmed(true)
        assertTrue(read().autostart.isResolved)

        store.setAutostartConfirmed(false)

        assertFalse(read().autostart.isResolved)
    }

    @Test
    fun `an unknown answer about an Android permission is treated as denied`() = runTest {
        // UNKNOWN заведён для автозапуска и сюда попасть не должен; если попал — ошибка
        // в сторону «попросим лишний раз» дешевле молчащих напоминаний.
        permissions.states[ReminderPermission.NOTIFICATIONS] = PermissionState.UNKNOWN

        assertFalse(read().notifications.isResolved)
    }

    @Test
    fun `the read never asks the system about the autostart state`() = runTest {
        read()

        // Порт разрешений о шаге автозапуска не спрашивается вовсе: у него нет ответа
        // (НАП-20), а вопрос в коде означал бы, что кто-то рассчитывает на ответ.
        assertEquals(PermissionState.UNKNOWN, permissions.state(ReminderPermission.AUTOSTART))
        assertFalse(read().autostart.verifiedBySystem)
    }
}
