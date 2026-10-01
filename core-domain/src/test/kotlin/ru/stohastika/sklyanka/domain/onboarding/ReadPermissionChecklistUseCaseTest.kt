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
    private val vendor = FakeVendorProfileReader()

    private val read = ReadPermissionChecklistUseCase(
        permissions = permissions,
        store = store,
        hints = hints,
        requests = requests,
        time = time,
        vendor = vendor,
    )

    @Test
    fun `granted permissions are reported with the moment they were read`() = runTest {
        val chain = read()

        assertEquals(PermissionGrant.Granted(now), chain.notifications.permission)
        assertEquals(PermissionGrant.Granted(now), chain.exactAlarms)
    }

    @Test
    fun `the channel and the full-screen right are read separately from the permission`() = runTest {
        val chain = read()

        assertEquals(PermissionGate.GRANTED, chain.notifications.channel)
        assertEquals(PermissionGate.GRANTED, chain.notifications.fullScreenIntent)
        assertNull(chain.notifications.issue)
    }

    @Test
    fun `a disabled channel is seen on the next read without a restart`() = runTest {
        permissions.states[ReminderPermission.NOTIFICATION_CHANNEL] = PermissionState.DENIED

        val chain = read()

        assertEquals(PermissionGate.DENIED, chain.notifications.channel)
        assertFalse(chain.notifications.isResolved)
    }

    @Test
    fun `a permission revoked in system settings is seen on the next read`() = runTest {
        assertTrue(read().notifications.isResolved)

        permissions.states[ReminderPermission.NOTIFICATIONS] = PermissionState.DENIED

        // Никакого перезапуска: тот же экземпляр сценария, новое чтение системы (НФТ-52).
        assertEquals(PermissionGrant.Denied(canAsk = true), read().notifications.permission)
    }

    @Test
    fun `the vendor comes from the platform port`() = runTest {
        assertEquals(VendorProfile.EMUI, read().vendor)
        assertEquals(5, read().steps.size)

        vendor.vendor = VendorProfile.GENERIC

        assertEquals(VendorProfile.GENERIC, read().vendor)
        assertEquals(2, read().steps.size)
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

        val denied = read().notifications.permission as PermissionGrant.Denied

        assertTrue(denied.canAsk)
        assertEquals(StepAction.REQUEST_PERMISSION, actionFor(PermissionStep.NOTIFICATIONS, read().notifications))
    }

    @Test
    fun `before Android 13 the notifications step leads to system settings and not to a dialog`() = runTest {
        // Разрешения не существует, запрашивать нечего: диалог ничего бы не сделал,
        // а карточка осталась бы невыданной без объяснения (ПЛ-13).
        requests.beforeAndroid13()
        permissions.states[ReminderPermission.NOTIFICATIONS] = PermissionState.DENIED

        val state = read().notifications
        val denied = state.permission as PermissionGrant.Denied

        assertFalse(denied.canAsk)
        assertEquals(StepAction.OPEN_SYSTEM_SETTINGS, actionFor(PermissionStep.NOTIFICATIONS, state))
    }

    @Test
    fun `a channel issue leads to system settings even when the permission can be asked for`() = runTest {
        permissions.states[ReminderPermission.NOTIFICATION_CHANNEL] = PermissionState.DENIED

        // Разрешение выдано, канал чинится только на системном экране уведомлений.
        assertEquals(
            StepAction.OPEN_SYSTEM_SETTINGS,
            actionFor(PermissionStep.NOTIFICATIONS, read().notifications),
        )
    }

    @Test
    fun `a granted step offers no action at all`() = runTest {
        assertNull(actionFor(PermissionStep.NOTIFICATIONS, read().notifications))
        assertNull(actionFor(PermissionStep.EXACT_ALARMS, read().exactAlarms))
    }

    @Test
    fun `vendor steps open their system screen, and recents pin has no screen to open`() = runTest {
        val chain = read()

        // У закрепления системного экрана нет вовсе: только инструкция и переключатель.
        assertEquals(
            StepAction.OPEN_VENDOR_STEP,
            actionFor(PermissionStep.APP_STARTUP, chain.appStartup),
        )
        assertEquals(
            StepAction.OPEN_VENDOR_STEP,
            actionFor(PermissionStep.BATTERY_SAVING, chain.batterySaving),
        )
        assertNull(actionFor(PermissionStep.RECENTS_PIN, chain.recentsPin))
    }

    @Test
    fun `a vendor step keeps its action even after the person confirmed it`() = runTest {
        store.setAppStartupConfirmed(true)

        // Подтверждение человека — не проверка системы, и путь назад в настройки
        // обязан остаться открытым (НАП-20).
        assertEquals(
            StepAction.OPEN_VENDOR_STEP,
            actionFor(PermissionStep.APP_STARTUP, read().appStartup),
        )
    }

    @Test
    fun `vendor steps are unresolved by default even when everything else is in place`() = runTest {
        hints.batteryOptimizationIgnored = true

        val chain = read()

        assertFalse(chain.appStartup.isResolved, "REMINDER-20: default state of a vendor step is unresolved")
        assertFalse(chain.batterySaving.isResolved)
        assertFalse(chain.recentsPin.isResolved)
        assertFalse(chain.allResolved)
    }

    @Test
    fun `a lifted battery optimisation is shown as an indirect hint on the battery step only`() = runTest {
        hints.batteryOptimizationIgnored = true

        val chain = read()

        assertEquals(setOf(ManualStepHint.BATTERY_OPTIMIZATION_IGNORED), chain.batterySaving.hints)
        // Признак относится к предмету шага «Энергосбережение»: у запуска приложений его нет.
        assertTrue(chain.appStartup.hints.isEmpty())
        assertFalse(chain.batterySaving.isResolved)
    }

    @Test
    fun `manual confirmation is the only thing that resolves a vendor step`() = runTest {
        store.setAppStartupConfirmed(true)
        store.setBatterySavingConfirmed(true)
        store.setRecentsPinConfirmed(true)

        val chain = read()

        assertTrue(chain.appStartup.confirmedByUser)
        assertTrue(chain.batterySaving.confirmedByUser)
        assertTrue(chain.recentsPin.confirmedByUser)
        assertFalse(chain.appStartup.verifiedBySystem, "confirmed by the person is not verified by the system")
        assertTrue(chain.allResolved)
    }

    @Test
    fun `withdrawn confirmation puts a vendor step back to unresolved`() = runTest {
        store.setAppStartupConfirmed(true)
        assertTrue(read().appStartup.isResolved)

        store.setAppStartupConfirmed(false)

        assertFalse(read().appStartup.isResolved)
    }

    @Test
    fun `an unknown answer about a permission is treated as denied`() = runTest {
        // UNKNOWN заведён для вендорских настроек и сюда попасть не должен; если попал —
        // ошибка в сторону «попросим лишний раз» дешевле молчащих напоминаний.
        permissions.states[ReminderPermission.NOTIFICATIONS] = PermissionState.UNKNOWN

        assertFalse(read().notifications.isResolved)
    }

    @Test
    fun `the read never asks the system about vendor settings`() = runTest {
        read()

        // Порт разрешений о вендорских настройках не спрашивается вовсе: у него нет ответа
        // (НАП-20), а вопрос в коде означал бы, что кто-то рассчитывает на ответ.
        assertEquals(PermissionState.UNKNOWN, permissions.state(ReminderPermission.AUTOSTART))
        assertFalse(read().appStartup.verifiedBySystem)
    }
}
