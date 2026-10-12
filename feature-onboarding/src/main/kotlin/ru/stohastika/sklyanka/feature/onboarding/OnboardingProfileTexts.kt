package ru.stohastika.sklyanka.feature.onboarding

import androidx.annotation.StringRes
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile

/**
 * Слова профиля прошивки на экране надёжности (НАП-19, ПЛ-18).
 *
 * Таблица «профиль × шаг → строка» в одном месте: строка чужой прошивки на телефоне
 * человека — дефект, а не мелочь вёрстки («Запуск приложений EMUI» на Samsung рассказывает
 * про меню, которого там нет). Экрану остаётся взять ресурс по профилю цепочки — ни одного
 * решения о вендоре в композиции не появляется.
 *
 * До первого чтения системы профиль неизвестен (`null`): экран в этот момент не имеет права
 * утверждать что-либо о прошивке и показывает слова общего профиля.
 *
 * Ключи, общие для всех профилей («Энергосбережение», «Закрепление в недавних»,
 * подтверждение закрепления), здесь не ветвятся: имя шага совпадает у EMUI, HyperOS и One UI,
 * а закрепление в недавних вообще не системный раздел. Ветка заводится там, где слова системы
 * различаются: «Запуск приложений» (EMUI) и «Автозапуск» (HyperOS/MIUI), пути к разделам
 * и запасные инструкции.
 */
internal object OnboardingProfileTexts {

    /** Вступление экрана: причина у каждой прошивки называется её словами. */
    @StringRes
    fun intro(profile: VendorProfile?): Int = when (profile) {
        VendorProfile.EMUI -> R.string.setup_intro
        VendorProfile.XIAOMI -> R.string.setup_intro_xiaomi
        VendorProfile.SAMSUNG -> R.string.setup_intro_samsung
        // Общий профиль не утверждает, какая это прошивка: он её не знает.
        VendorProfile.GENERIC, null -> R.string.setup_intro_android
    }

    /** Название карточки словами системы этой прошивки (ПЛ-18, НАП-19). */
    @StringRes
    fun title(step: PermissionStep, profile: VendorProfile?): Int = when (step) {
        PermissionStep.NOTIFICATIONS -> R.string.setup_step_notifications
        PermissionStep.EXACT_ALARMS -> R.string.setup_step_exact_alarms
        PermissionStep.APP_STARTUP -> when (profile) {
            VendorProfile.XIAOMI -> R.string.setup_step_autostart
            // Прочие профили этого шага не показывают; значение нужно лишь полноте таблицы.
            else -> R.string.setup_step_app_startup
        }

        PermissionStep.BATTERY_SAVING -> R.string.setup_step_battery_saving
        PermissionStep.RECENTS_PIN -> R.string.setup_step_recents_pin
    }

    /** Путь к разделу под названием карточки; `null` — вместо пути у шага ничего нет. */
    @StringRes
    fun caption(step: PermissionStep, profile: VendorProfile?): Int? = when (step) {
        PermissionStep.NOTIFICATIONS -> R.string.setup_step_notifications_caption
        PermissionStep.EXACT_ALARMS -> R.string.setup_step_exact_alarms_caption
        PermissionStep.APP_STARTUP -> when (profile) {
            VendorProfile.XIAOMI -> R.string.setup_step_autostart_caption
            else -> R.string.setup_step_app_startup_caption
        }

        PermissionStep.BATTERY_SAVING -> when (profile) {
            VendorProfile.XIAOMI -> R.string.setup_step_battery_saving_caption_xiaomi
            VendorProfile.SAMSUNG -> R.string.setup_step_battery_saving_caption_samsung
            // EMUI ведёт в раздел кнопкой, а путь называет запасная инструкция.
            else -> null
        }

        PermissionStep.RECENTS_PIN -> R.string.setup_step_recents_pin_caption
    }

    /**
     * Вторая строка карточки: что именно включить.
     *
     * Есть только у EMUI: там экран требует трёх решений, и одного пути мало. На других
     * прошивках конец цепочки пути и есть нужный выбор, и второй строки не заводится.
     */
    @StringRes
    fun extraAction(step: PermissionStep, profile: VendorProfile?): Int? = when (step) {
        PermissionStep.APP_STARTUP ->
            if (profile == VendorProfile.XIAOMI) null else R.string.setup_step_app_startup_action

        else -> null
    }

    /** Запасная инструкция пунктами меню этой прошивки; `null` — инструкции у шага нет. */
    @StringRes
    fun fallback(step: PermissionStep, profile: VendorProfile?): Int? = when (step) {
        PermissionStep.APP_STARTUP ->
            if (profile == VendorProfile.XIAOMI) {
                R.string.setup_fallback_autostart
            } else {
                R.string.setup_fallback_app_startup
            }

        PermissionStep.BATTERY_SAVING -> when (profile) {
            VendorProfile.XIAOMI -> R.string.setup_fallback_battery_saving_xiaomi
            VendorProfile.SAMSUNG -> R.string.setup_fallback_battery_saving_samsung
            else -> R.string.setup_fallback_battery_saving
        }

        // У закрепления в недавних системного экрана нет, и запасной инструкции тоже.
        PermissionStep.RECENTS_PIN -> null

        PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS -> null
    }

    /** Подпись переключателя: исход называется словами системы этой прошивки (НАП-20). */
    @StringRes
    fun confirmLabel(step: PermissionStep, profile: VendorProfile?): Int = when (step) {
        PermissionStep.APP_STARTUP ->
            if (profile == VendorProfile.XIAOMI) {
                R.string.setup_manual_confirm_autostart
            } else {
                R.string.setup_manual_confirm_app_startup
            }

        PermissionStep.BATTERY_SAVING -> R.string.setup_manual_confirm_battery_saving
        PermissionStep.RECENTS_PIN -> R.string.setup_manual_confirm_recents_pin
        PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS -> R.string.setup_manual_confirm_app_startup
    }

    /** Строка отзыва подтверждения — тоже словами профиля. */
    @StringRes
    fun revokedText(step: PermissionStep, profile: VendorProfile?): Int = when (step) {
        PermissionStep.NOTIFICATIONS -> R.string.setup_revoked_notifications
        PermissionStep.EXACT_ALARMS -> R.string.setup_revoked_exact_alarms
        PermissionStep.APP_STARTUP ->
            if (profile == VendorProfile.XIAOMI) {
                R.string.setup_revoked_autostart
            } else {
                R.string.setup_revoked_app_startup
            }

        PermissionStep.BATTERY_SAVING -> R.string.setup_revoked_battery_saving
        PermissionStep.RECENTS_PIN -> R.string.setup_revoked_recents_pin
    }

    /** Имя значка для чтения с экрана — тем же словом, что и карточка (НФТ-30). */
    @StringRes
    fun glyphLabel(step: PermissionStep, profile: VendorProfile?): Int = when (step) {
        PermissionStep.NOTIFICATIONS -> R.string.cd_onboarding_notifications
        PermissionStep.EXACT_ALARMS -> R.string.cd_onboarding_exact_alarms
        PermissionStep.APP_STARTUP ->
            if (profile == VendorProfile.XIAOMI) R.string.cd_setup_autostart else R.string.cd_setup_app_startup

        PermissionStep.BATTERY_SAVING -> R.string.cd_setup_battery_saving
        PermissionStep.RECENTS_PIN -> R.string.cd_setup_recents_pin
    }
}
