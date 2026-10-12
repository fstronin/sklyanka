package ru.stohastika.sklyanka.domain.onboarding

/**
 * Состав вендорских шагов профиля прошивки (ПЛ-18, НАП-19).
 *
 * Одна точка правды: сколько вендорских шагов у профиля и в каком порядке они идут, решает
 * эта таблица, а не экран и не перечисление по имени профиля по месту. Цепочка
 * ([PermissionChecklist.steps]) собирает общие шаги (`NOTIFICATIONS`, `EXACT_ALARMS`) сама
 * и дописывает сюда пришедшие шаги в объявленном порядке — «от того, что система выдаёт
 * сама, к ручному» (НАП-19).
 *
 * Порядок внутри вендорской части — «запуск приложений (там, где он есть) → энергосбережение
 * → закрепление в недавних». Пустой список у [VendorProfile.GENERIC] — законное состояние:
 * незнакомой прошивке чужие шаги не приписываются, у неё остаются только общие шаги Android.
 */
object VendorSteps {

    /** Вендорские шаги профиля в порядке показа. */
    fun of(profile: VendorProfile): List<PermissionStep> = when (profile) {
        // EMUI: три настройки, и автозапуск там зовётся «Запуском приложений».
        VendorProfile.EMUI -> listOf(
            PermissionStep.APP_STARTUP,
            PermissionStep.BATTERY_SAVING,
            PermissionStep.RECENTS_PIN,
        )

        // HyperOS/MIUI: тот же смысл, но шаг называется «Автозапуск».
        VendorProfile.XIAOMI -> listOf(
            PermissionStep.APP_STARTUP,
            PermissionStep.BATTERY_SAVING,
            PermissionStep.RECENTS_PIN,
        )

        // One UI: отдельного автозапуска нет, есть ограничения фоновой работы и закрепление.
        VendorProfile.SAMSUNG -> listOf(
            PermissionStep.BATTERY_SAVING,
            PermissionStep.RECENTS_PIN,
        )

        VendorProfile.GENERIC -> emptyList()
    }
}
