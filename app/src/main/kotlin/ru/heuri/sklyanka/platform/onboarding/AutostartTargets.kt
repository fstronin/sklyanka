package ru.heuri.sklyanka.platform.onboarding

/**
 * Экран управления запуском приложений на прошивке производителя.
 *
 * Адресуется компонентом, а не действием: стандартного действия для этого экрана
 * в Android нет вовсе (`docs/spec/01-platform.md` §6), и каждый производитель называет
 * свою Activity по-своему.
 */
data class AutostartTarget(val packageName: String, val className: String) {

    /** Для журнала и отчёта об устройстве: короткое имя без пакета не опознаётся. */
    override fun toString(): String = "$packageName/$className"
}

/**
 * Известные экраны автозапуска (ПЛ-18).
 *
 * Список, а не одна константа, по двум причинам, и обе проверены на практике:
 * имя Activity в EMUI менялось между версиями прошивки, и на части сборок экран
 * не экспортирован вовсе. Порядок — от нового EMUI к старому: на эталонном устройстве
 * первый же адрес и должен сработать.
 *
 * Список заведомо неполон и полным быть не может — именно поэтому обязателен запасной путь
 * (`AutostartOpenResult.FALLBACK_TO_INSTRUCTIONS`): текстовая инструкция с названиями пунктов
 * меню работает на любой прошивке, включая ту, которой в этом списке нет.
 *
 * Разрешение на перечисление пакетов (`QUERY_ALL_PACKAGES`) для этого не объявляется:
 * оно не даёт состояния настройки и потребовало бы объяснений ревью магазина. Видимость
 * одного пакета системного менеджера объявлена в манифесте элементом `<queries>`.
 */
object AutostartTargets {

    /** Пакет системного менеджера EMUI — единственный, чья видимость объявлена в манифесте. */
    const val HUAWEI_SYSTEM_MANAGER: String = "com.huawei.systemmanager"

    val EMUI: List<AutostartTarget> = listOf(
        // EMUI 8 и новее: «Настройки → Батарея → Запуск приложений».
        emui("com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        // EMUI 5–7: тот же смысл под другим именем.
        emui("com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        // Более старые сборки: «Защищённые приложения».
        emui("com.huawei.systemmanager.optimize.process.ProtectActivity"),
    )

    private fun emui(className: String) = AutostartTarget(HUAWEI_SYSTEM_MANAGER, className)

    /**
     * Первый адрес, который удалось открыть.
     *
     * Функция чистая и принимает пробу параметром: путь «ни один адрес не открывается»
     * обязателен к проверке, а на настоящем устройстве его не воспроизвести — там открывается
     * первый же (`TODO.md` §5).
     */
    fun firstOpenable(targets: List<AutostartTarget> = EMUI, open: (AutostartTarget) -> Boolean): AutostartTarget? =
        targets.firstOrNull(open)
}
