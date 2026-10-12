package ru.stohastika.sklyanka.acceptance.world

import ru.stohastika.sklyanka.domain.onboarding.OnboardingOutcome
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionChecklist
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.StepOpenResult
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import ru.stohastika.sklyanka.domain.onboarding.VendorProfileReader
import ru.stohastika.sklyanka.domain.onboarding.port.AutostartHintReader
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingLog
import ru.stohastika.sklyanka.domain.onboarding.port.OnboardingStateStore
import ru.stohastika.sklyanka.domain.onboarding.port.PermissionRequestSupport

/**
 * Состояние онбординга сценария (SKL-14, SKL-84): сохранённые факты, подсказки, вендор и журнал.
 *
 * Хранится ровно то, что продукт хранит в `DataStore` (`TDD.md §4`): «человек видел экран»
 * и три подтверждения вендорских шагов. Состояний разрешений здесь нет и быть не может —
 * они читаются из системы портом [ru.stohastika.sklyanka.acceptance.world.ScenarioSystemState]
 * при каждом показе (НФТ-52).
 *
 * Вендор — данность Предыстории: на JVM `Build.MANUFACTURER` неоткуда взять, а сценарии
 * проверяют и полную цепочку на Huawei, и два общих шага на остальных телефонах.
 *
 * Косвенный признак «оптимизация батареи снята» — тоже данность Предыстории: без него
 * не проверить, что вендорский шаг не зеленеет от подсказок (НАП-20).
 *
 * Журнал онбординга пишется в память: сценарии о нём не судят, но вызовы настоящие,
 * и заглушка не молчит о факте — она его записывает.
 */
@Suppress("TooManyFunctions")
class ScenarioOnboarding :
    OnboardingStateStore,
    AutostartHintReader,
    PermissionRequestSupport,
    VendorProfileReader,
    OnboardingLog {

    private var seen: Boolean = false
    private var appStartup: Boolean = false
    private var batterySaving: Boolean = false
    private var recentsPin: Boolean = false
    private var batteryIgnored: Boolean = false

    /** Прошивка сценария: по умолчанию эталонный Huawei. */
    var vendor: VendorProfile = VendorProfile.EMUI

    /**
     * Что платформа сообщает о прошивке: строки `Build` и наличие менеджера Huawei (SKL-112).
     *
     * Данность сценариев классификации: на JVM настоящего `Build.MANUFACTURER` взять неоткуда,
     * и проверяется чистая функция классификатора, а не чтение системы.
     */
    var vendorFacts: VendorFacts? = null

    /** Профиль, определённый классификатором: результат хода «приложение определяет профиль». */
    var detectedVendor: VendorProfile? = null

    /**
     * Какое из двух прав точных будильников объявляет сборка (Р-59).
     *
     * Объявление — данность Предыстории, и шаг «Точные будильники» обязан следовать
     * за ответом системы при любом из двух ответов (ПЛ-14, ПЛ-15, НАП-16).
     */
    var declaredExactAlarm: DeclaredExactAlarm = DeclaredExactAlarm.PRIMARY

    /** Записи журнала онбординга: короткие коды событий, без медицинских данных (НФТ-19). */
    val records: MutableList<String> = mutableListOf()

    override suspend fun seen(): Boolean = seen

    override suspend fun markSeen() {
        seen = true
    }

    override suspend fun appStartupConfirmed(): Boolean = appStartup

    override suspend fun setAppStartupConfirmed(confirmed: Boolean) {
        appStartup = confirmed
    }

    override suspend fun batterySavingConfirmed(): Boolean = batterySaving

    override suspend fun setBatterySavingConfirmed(confirmed: Boolean) {
        batterySaving = confirmed
    }

    override suspend fun recentsPinConfirmed(): Boolean = recentsPin

    override suspend fun setRecentsPinConfirmed(confirmed: Boolean) {
        recentsPin = confirmed
    }

    override fun profile(): VendorProfile = vendor

    override fun batteryOptimizationIgnored(): Boolean = batteryIgnored

    /** Телефон не применяет к приложению оптимизацию батареи — данность Предыстории (НАП-20). */
    fun setBatteryOptimizationIgnored(ignored: Boolean) {
        batteryIgnored = ignored
    }

    /**
     * Что из цепочки система позволяет запросить диалогом.
     *
     * Как на Android 13 и новее (ПЛ-13, ПЛ-14): уведомления — да, точные будильники — нет,
     * их выдают на экране особого доступа. Вендорские настройки не запрашиваются вовсе
     * (НАП-20). Ответ — свойство платформы, а не счётчик отказов.
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

    override fun vendorStepOpened(step: PermissionStep, result: StepOpenResult) {
        records += "vendor:$step:$result"
    }

    override fun manualStepConfirmed(step: PermissionStep, confirmed: Boolean) {
        records += "manual:$step:$confirmed"
    }

    override fun reliabilityRouteTaken(source: OnboardingSource, firstUnresolved: PermissionStep?) {
        records += "route:$source:$firstUnresolved"
    }

    override fun onboardingLeft(outcome: OnboardingOutcome, checklist: PermissionChecklist) {
        records += "left:$outcome:${checklist.unresolvedCount}"
    }

    override fun revocationDetected(steps: List<PermissionStep>) {
        records += "revoked:${steps.joinToString(",")}"
    }
}

/** Факты `Build` и наличие системного менеджера — данность сценариев классификации (SKL-112). */
data class VendorFacts(val manufacturer: String, val brand: String, val huaweiManagerPresent: Boolean)

/**
 * Какое из двух прав точных будильников объявлено сборкой (Р-59).
 *
 * Сценарии таблицы прогоняют шаг при обоих ответах: состояние шага обязано следовать
 * за ответом системы, а не за этим объявлением (ПЛ-14, ПЛ-15, НАП-16).
 */
enum class DeclaredExactAlarm { PRIMARY, FALLBACK }
