package ru.heuri.sklyanka.domain.onboarding

import ru.heuri.sklyanka.domain.onboarding.port.AutostartHintReader
import ru.heuri.sklyanka.domain.onboarding.port.OnboardingStateStore
import ru.heuri.sklyanka.domain.onboarding.port.PermissionRequestSupport

/**
 * Хранилище состояния онбординга в памяти.
 *
 * Хранит ровно два значения — и это проверяется тестом: любое третье значение здесь
 * означало бы, что состояние разрешения где-то запомнили (НФТ-52).
 */
internal class FakeOnboardingStateStore(
    private var seen: Boolean = false,
    private var autostartConfirmed: Boolean = false,
) : OnboardingStateStore {

    var markSeenCalls: Int = 0
        private set

    override suspend fun seen(): Boolean = seen

    override suspend fun markSeen() {
        markSeenCalls++
        seen = true
    }

    override suspend fun autostartConfirmed(): Boolean = autostartConfirmed

    override suspend fun setAutostartConfirmed(confirmed: Boolean) {
        autostartConfirmed = confirmed
    }
}

/** Косвенные признаки автозапуска в памяти. */
internal class FakeAutostartHintReader(var batteryOptimizationIgnored: Boolean = false) : AutostartHintReader {

    override fun batteryOptimizationIgnored(): Boolean = batteryOptimizationIgnored
}

/**
 * Что платформа даёт запросить.
 *
 * По умолчанию — как на Android 13 и новее: уведомления диалогом, точные будильники нет.
 */
internal class FakePermissionRequestSupport(
    private val requestable: MutableSet<PermissionStep> = mutableSetOf(PermissionStep.NOTIFICATIONS),
) : PermissionRequestSupport {

    override fun canRequestFromCode(step: PermissionStep): Boolean = step in requestable

    /** Android 12 и старше: разрешения на уведомления не существует, запросить нечего. */
    fun beforeAndroid13() {
        requestable.remove(PermissionStep.NOTIFICATIONS)
    }
}
