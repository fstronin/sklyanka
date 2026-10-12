package ru.stohastika.sklyanka.platform.onboarding

import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile

/**
 * Системный экран производителя, к которому ведёт вендорский шаг.
 *
 * Адресуется компонентом, а не действием: стандартного действия для этих экранов
 * в Android нет вовсе (`docs/norm/01-product.md` §6), и каждый производитель называет
 * свою Activity по-своему.
 */
data class SystemScreenTarget(
    val packageName: String,
    val className: String,
    /**
     * Экрану нужен пакет приложения, о котором речь.
     *
     * Некоторые вендорские экраны открываются «в общем», а страницу нужного приложения
     * показывают только с именем пакета в extras (так устроен экран энергосбережения MIUI);
     * без него человек попал бы на пустой раздел.
     */
    val carriesAppIdentity: Boolean = false,
) {

    /** Для журнала и отчёта об устройстве: короткое имя без пакета не опознаётся. */
    override fun toString(): String = "$packageName/$className"
}

/**
 * Известные экраны производителей: запуск приложений и энергосбережение (ПЛ-6, ПЛ-18).
 *
 * Адреса сгруппированы по профилю прошивки и шагу; выбор списка — [targets]. Списки, а не
 * одна константа, по двум причинам, и обе проверены на практике: имя Activity менялось между
 * версиями прошивки, и на части сборок экран не экспортирован вовсе. Порядок — от нового
 * к старому: на живом устройстве первым должен сработать первый же адрес.
 *
 * Списки заведомо неполны и полными быть не могут — именно поэтому обязателен запасной путь
 * ([ru.stohastika.sklyanka.domain.onboarding.StepOpenResult.FALLBACK_TO_INSTRUCTIONS]):
 * текстовая инструкция с названиями пунктов меню работает на любой прошивке, включая ту,
 * которой в списке нет. Точный состав подтверждается сеансом на устройстве матрицы,
 * а не догадкой; пустой список — законное состояние (закрепление в недавних системного
 * экрана не имеет ни на одной прошивке).
 *
 * Разрешение на перечисление пакетов (`QUERY_ALL_PACKAGES`) для этого не объявляется:
 * оно не даёт состояния настройки и потребовало бы объяснений ревью магазина. Видимость
 * пакетов менеджеров объявлена в манифесте элементами `<queries>` — иначе на Android 11
 * и новее разделы молча не открывались бы.
 */
object SystemScreenTargets {

    /** Пакет системного менеджера EMUI: и разделы, и косвенный признак профиля Huawei. */
    const val HUAWEI_SYSTEM_MANAGER: String = "com.huawei.systemmanager"

    /** Пакет центра безопасности HyperOS/MIUI: автозапуск приложений. */
    const val XIAOMI_SECURITY_CENTER: String = "com.miui.securitycenter"

    /** Пакет управления питанием MIUI: ограничения энергосбережения. */
    const val XIAOMI_POWER_KEEPER: String = "com.miui.powerkeeper"

    /** Пакет «Ухода за устройством» One UI (и его китайской сборки) — раздел батареи. */
    const val SAMSUNG_DEVICE_CARE: String = "com.samsung.android.lool"

    /** Китайская сборка One UI: тот же «Уход за устройством» под своим пакетом. */
    const val SAMSUNG_DEVICE_CARE_CN: String = "com.samsung.android.sm_cn"

    val EMUI_APP_STARTUP: List<SystemScreenTarget> = listOf(
        // EMUI 8 и новее: «Настройки → Батарея → Запуск приложений».
        emui("com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        // EMUI 5–7: тот же смысл под другим именем.
        emui("com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        // Более старые сборки: «Защищённые приложения».
        emui("com.huawei.systemmanager.optimize.process.ProtectActivity"),
    )

    /**
     * Экраны ограничений энергосбережения EMUI.
     *
     * Имена различаются между версиями EMUI, и первый адрес придётся проверять на устройстве:
     * отказ закрывается перебором и запасной инструкцией.
     */
    val EMUI_BATTERY_SAVING: List<SystemScreenTarget> = listOf(
        emui("com.huawei.systemmanager.power.HwPowerManagerActivity"),
        emui("com.huawei.systemmanager.power.ui.HwPowerManagerActivity"),
    )

    /**
     * Автозапуск HyperOS/MIUI.
     *
     * Экран живёт в центре безопасности и открывается без extras; список начинается
     * с одного адреса и растёт по фактам сеансов на устройствах Xiaomi.
     */
    val XIAOMI_APP_STARTUP: List<SystemScreenTarget> = listOf(
        xiaomi(XIAOMI_SECURITY_CENTER, "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    )

    /**
     * Ограничения энергосбережения HyperOS/MIUI.
     *
     * Экран показывает страницу приложения только с его именем пакета в extras
     * (`carriesAppIdentity`), иначе открывается пустым.
     */
    val XIAOMI_BATTERY_SAVING: List<SystemScreenTarget> = listOf(
        xiaomi(XIAOMI_POWER_KEEPER, "com.miui.powerkeeper.ui.HiddenAppsConfigActivity", appScoped = true),
    )

    /**
     * Ограничения фоновой работы One UI.
     *
     * Раздел «Батарея» в «Уходе за устройством»; китайская сборка называет свой пакет,
     * а класс тот же.
     */
    val SAMSUNG_BATTERY_SAVING: List<SystemScreenTarget> = listOf(
        SystemScreenTarget(SAMSUNG_DEVICE_CARE, "com.samsung.android.sm.ui.battery.BatteryActivity"),
        SystemScreenTarget(SAMSUNG_DEVICE_CARE_CN, "com.samsung.android.sm.ui.battery.BatteryActivity"),
    )

    /**
     * Адреса конкретного шага у конкретного профиля.
     *
     * Пустой список — обычный и законный ответ: шага у профиля нет либо системного экрана
     * для него не существует. Навигатор в этом случае идёт запасным путём и отдаёт
     * `FALLBACK_TO_INSTRUCTIONS`, а не открывает чужой экран.
     */
    fun targets(profile: VendorProfile, step: PermissionStep): List<SystemScreenTarget> = when (profile) {
        VendorProfile.EMUI -> when (step) {
            PermissionStep.APP_STARTUP -> EMUI_APP_STARTUP
            PermissionStep.BATTERY_SAVING -> EMUI_BATTERY_SAVING
            else -> emptyList()
        }

        VendorProfile.XIAOMI -> when (step) {
            PermissionStep.APP_STARTUP -> XIAOMI_APP_STARTUP
            PermissionStep.BATTERY_SAVING -> XIAOMI_BATTERY_SAVING
            else -> emptyList()
        }

        VendorProfile.SAMSUNG -> when (step) {
            PermissionStep.BATTERY_SAVING -> SAMSUNG_BATTERY_SAVING
            // Отдельного автозапуска у One UI в цепочке нет, и адреса ему не заводятся.
            else -> emptyList()
        }

        // Незнакомой прошивке чужих адресов не приписывается (ПЛ-18).
        VendorProfile.GENERIC -> emptyList()
    }

    private fun emui(className: String) = SystemScreenTarget(HUAWEI_SYSTEM_MANAGER, className)

    private fun xiaomi(packageName: String, className: String, appScoped: Boolean = false) =
        SystemScreenTarget(packageName, className, carriesAppIdentity = appScoped)

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
