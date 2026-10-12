package ru.stohastika.sklyanka.domain.onboarding

import ru.stohastika.sklyanka.domain.onboarding.port.AutostartHintReader
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingStateStore
import ru.stohastika.sklyanka.domain.onboarding.port.PermissionRequestSupport

/**
 * Хранилище состояния онбординга в памяти.
 *
 * Хранит ровно «видел» и три вендорских подтверждения — и это проверяется тестом: любое
 * состояние разрешения здесь означало бы, что разрешение где-то запомнили (НФТ-52).
 */
internal class FakeOnboardingStateStore(
    private var seen: Boolean = false,
    private var appStartupConfirmed: Boolean = false,
    private var batterySavingConfirmed: Boolean = false,
    private var recentsPinConfirmed: Boolean = false,
) : OnboardingStateStore {

    var markSeenCalls: Int = 0
        private set

    override suspend fun seen(): Boolean = seen

    override suspend fun markSeen() {
        markSeenCalls++
        seen = true
    }

    override suspend fun appStartupConfirmed(): Boolean = appStartupConfirmed

    override suspend fun setAppStartupConfirmed(confirmed: Boolean) {
        appStartupConfirmed = confirmed
    }

    override suspend fun batterySavingConfirmed(): Boolean = batterySavingConfirmed

    override suspend fun setBatterySavingConfirmed(confirmed: Boolean) {
        batterySavingConfirmed = confirmed
    }

    override suspend fun recentsPinConfirmed(): Boolean = recentsPinConfirmed

    override suspend fun setRecentsPinConfirmed(confirmed: Boolean) {
        recentsPinConfirmed = confirmed
    }
}

/** Прошивка в памяти: по умолчанию эталонный Huawei. */
internal class FakeVendorProfileReader(var vendor: VendorProfile = VendorProfile.EMUI) : VendorProfileReader {

    override fun profile(): VendorProfile = vendor
}

/** Косвенный признак энергосбережения в памяти. */
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

    /** Платформенный ответ «можно спросить» — для проверок, что домен его не переоценивает. */
    fun allowAskingFor(step: PermissionStep) {
        requestable.add(step)
    }

    /** Android 12 и старше: разрешения на уведомления не существует, запросить нечего. */
    fun beforeAndroid13() {
        requestable.remove(PermissionStep.NOTIFICATIONS)
    }
}
