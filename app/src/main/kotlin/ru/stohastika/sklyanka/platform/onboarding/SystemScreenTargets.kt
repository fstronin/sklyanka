package ru.stohastika.sklyanka.platform.onboarding

/**
 * Системный экран производителя, к которому ведёт вендорский шаг.
 *
 * Адресуется компонентом, а не действием: стандартного действия для этих экранов
 * в Android нет вовсе (`docs/spec/01-platform.md` §6), и каждый производитель называет
 * свою Activity по-своему.
 */
data class SystemScreenTarget(val packageName: String, val className: String) {

    /** Для журнала и отчёта об устройстве: короткое имя без пакета не опознаётся. */
    override fun toString(): String = "$packageName/$className"
}

/**
 * Известные экраны EMUI: запуск приложений и энергосбережение (ПЛ-6, ПЛ-18).
 *
 * Списки, а не одна константа, по двум причинам, и обе проверены на практике:
 * имя Activity в EMUI менялось между версиями прошивки, и на части сборок экран
 * не экспортирован вовсе. Порядок — от нового EMUI к старому: на эталонном устройстве
 * первый же адрес и должен сработать.
 *
 * Списки заведомо неполны и полными быть не могут — именно поэтому обязателен запасной путь
 * ([ru.stohastika.sklyanka.domain.onboarding.StepOpenResult.FALLBACK_TO_INSTRUCTIONS]):
 * текстовая инструкция с названиями пунктов меню работает на любой прошивке, включая ту,
 * которой в списке нет.
 *
 * Разрешение на перечисление пакетов (`QUERY_ALL_PACKAGES`) для этого не объявляется:
 * оно не даёт состояния настройки и потребовало бы объяснений ревью магазина. Видимость
 * одного пакета системного менеджера объявлена в манифесте элементом `<queries>`.
 */
object SystemScreenTargets {

    /** Пакет системного менеджера EMUI — единственный, чья видимость объявлена в манифесте. */
    const val HUAWEI_SYSTEM_MANAGER: String = "com.huawei.systemmanager"

    val APP_STARTUP: List<SystemScreenTarget> = listOf(
        // EMUI 8 и новее: «Настройки → Батарея → Запуск приложений».
        emui("com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        // EMUI 5–7: тот же смысл под другим именем.
        emui("com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        // Более старые сборки: «Защищённые приложения».
        emui("com.huawei.systemmanager.optimize.process.ProtectActivity"),
    )

    /**
     * Экраны ограничений энергосбережения.
     *
     * Имена различаются между версиями EMUI, и первый адрес придётся проверять на устройстве
     * (открытый вопрос `task.md`): отказ закрывается перебором и запасной инструкцией.
     */
    val BATTERY_SAVING: List<SystemScreenTarget> = listOf(
        emui("com.huawei.systemmanager.power.HwPowerManagerActivity"),
        emui("com.huawei.systemmanager.power.ui.HwPowerManagerActivity"),
    )

    private fun emui(className: String) = SystemScreenTarget(HUAWEI_SYSTEM_MANAGER, className)

    /**
     * Первый адрес, который удалось открыть.
     *
     * Функция чистая и принимает пробу параметром: путь «ни один адрес не открывается»
     * обязателен к проверке, а на настоящем устройстве его не воспроизвести — там открывается
     * первый же.
     */
    fun firstOpenable(targets: List<SystemScreenTarget>, open: (SystemScreenTarget) -> Boolean): SystemScreenTarget? =
        targets.firstOrNull(open)
}
