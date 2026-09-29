package ru.stohastika.sklyanka.acceptance.world

import ru.stohastika.sklyanka.domain.onboarding.AutostartOpenResult
import ru.stohastika.sklyanka.domain.onboarding.OnboardingOutcome
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionChecklist
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.port.AutostartHintReader
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingLog
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingStateStore
import ru.stohastika.sklyanka.domain.onboarding.port.PermissionRequestSupport

/**
 * Состояние онбординга сценария (SKL-14): сохранённые факты, подсказки и журнал.
 *
 * Хранится ровно то, что продукт хранит в `DataStore` (`TDD.md §4`): «человек видел экран»
 * и «человек подтвердил автозапуск». Состояний разрешений здесь нет и быть не может —
 * они читаются из системы портом [ru.stohastika.sklyanka.acceptance.world.ScenarioSystemState]
 * при каждом показе (НФТ-52).
 *
 * Косвенный признак «оптимизация батареи снята» — данность Предыстории: на JVM его
 * неоткуда взять, а без него не проверить, что третий шаг не зеленеет от подсказок (НАП-20).
 *
 * Журнал онбординга пишется в память: сценарии о нём не судят, но вызовы настоящие,
 * и заглушка не молчит о факте — она его записывает.
 */
class ScenarioOnboarding :
    OnboardingStateStore,
    AutostartHintReader,
    PermissionRequestSupport,
    OnboardingLog {

    private var seen: Boolean = false
    private var confirmed: Boolean = false
    private var batteryIgnored: Boolean = false

    /** Записи журнала онбординга: короткие коды событий, без медицинских данных (НФТ-19). */
    val records: MutableList<String> = mutableListOf()

    override suspend fun seen(): Boolean = seen

    override suspend fun markSeen() {
        seen = true
    }

    override suspend fun autostartConfirmed(): Boolean = confirmed

    override suspend fun setAutostartConfirmed(confirmed: Boolean) {
        this.confirmed = confirmed
    }

    override fun batteryOptimizationIgnored(): Boolean = batteryIgnored

    /** Телефон не применяет к приложению оптимизацию батареи — данность Предыстории (НАП-20). */
    fun setBatteryOptimizationIgnored(ignored: Boolean) {
        batteryIgnored = ignored
    }

    /**
     * Что из цепочки система позволяет запросить диалогом.
     *
     * Как на Android 13 и новее (ПЛ-13, ПЛ-14): уведомления — да, точные будильники — нет,
     * их выдают на экране особого доступа. Ответ — свойство платформы, а не счётчик отказов.
     */
    override fun canRequestFromCode(step: PermissionStep): Boolean = step == PermissionStep.NOTIFICATIONS

    // --- Журнал ------------------------------------------------------------------------------

    override fun screenOpened(source: OnboardingSource, checklist: PermissionChecklist) {
        records += "opened:$source:${checklist.unresolvedCount}"
    }

    override fun permissionRequested(step: PermissionStep) {
        records += "requested:$step"
    }

    override fun permissionResult(step: PermissionStep, granted: Boolean) {
        records += "result:$step:$granted"
    }

    override fun autostartSettingsOpened(result: AutostartOpenResult) {
        records += "autostart:$result"
    }

    override fun autostartConfirmedByUser(confirmed: Boolean) {
        records += "confirmed:$confirmed"
    }

    override fun onboardingLeft(outcome: OnboardingOutcome, checklist: PermissionChecklist) {
        records += "left:$outcome:${checklist.unresolvedCount}"
    }

    override fun revocationDetected(steps: List<PermissionStep>) {
        records += "revoked:${steps.joinToString(",")}"
    }
}
