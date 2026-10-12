package ru.stohastika.sklyanka.platform.onboarding

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.StepOpenResult
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import ru.stohastika.sklyanka.feature.onboarding.SystemSettingsNavigator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Переходы в системные настройки (ПЛ-13, ПЛ-14, ПЛ-18, ОВ-106).
 *
 * Работает от контекста приложения с `FLAG_ACTIVITY_NEW_TASK`, а не от Activity: экраны
 * производителя принадлежат чужому приложению и открываются своей задачей всё равно,
 * а зависимость на Activity сделала бы навигацию непригодной для повторного входа из настроек.
 *
 * Каждый метод возвращает [StepOpenResult]: неудача открытия — не исключительная ситуация,
 * а обычный исход на неэталонной прошивке, и экран обязан показать запасную инструкцию,
 * а не притвориться, что всё открылось (НФТ-8).
 *
 * Системного запроса разрешения здесь нет намеренно: он требует контракта результата
 * активности и живёт в хозяине экрана.
 */
@Singleton
class AndroidSystemSettingsNavigator @Inject constructor(@ApplicationContext private val context: Context) :
    SystemSettingsNavigator {

    /**
     * Экран особого доступа «Будильники и напоминания» (ПЛ-14).
     *
     * До Android 12 экрана не существует, и деваться, кроме настроек приложения, некуда:
     * там же человек проверит и уведомления.
     */
    override fun openExactAlarmSettings(): StepOpenResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && start(Intent(EXACT_ALARM_SETTINGS, appUri()))) {
            return StepOpenResult.OPENED
        }
        return if (start(appDetails())) StepOpenResult.OPENED else StepOpenResult.FALLBACK_TO_INSTRUCTIONS
    }

    /**
     * Настройки уведомлений приложения, а с [channelId] — конкретного канала (ОВ-107).
     *
     * Нужны там, где системный диалог не покажется: до Android 13 разрешения нет вовсе,
     * после двух отказов система диалог больше не показывает, а канал и полноэкранное право
     * исправляются только здесь.
     */
    override fun openNotificationSettings(channelId: String?): StepOpenResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && start(notificationIntent(channelId))) {
            return StepOpenResult.OPENED
        }
        return if (start(appDetails())) StepOpenResult.OPENED else StepOpenResult.FALLBACK_TO_INSTRUCTIONS
    }

    /**
     * Экран права на полноэкранные уведомления (Android 14+).
     *
     * Ниже 14 права не существует, и запасной путь — тот же экран уведомлений приложения:
     * человек всё равно попадает туда, где настройка видна.
     */
    override fun openFullScreenIntentSettings(): StepOpenResult {
        if (Build.VERSION.SDK_INT >= FULL_SCREEN_INTENT_API && start(fullScreenIntentIntent())) {
            return StepOpenResult.OPENED
        }
        return openNotificationSettings()
    }

    /**
     * Вендорский шаг: раздел профиля и шага (ПЛ-18).
     *
     * Перебор известных адресов этого профиля: имя Activity менялось между версиями прошивки,
     * и на части сборок экран не экспортирован вовсе. Ни один вендорский адрес не сработал —
     * человек получает текстовую инструкцию своей прошивки, даже если открылся стандартный
     * список энергосбережения: это не тот раздел, а инструкция — рабочий путь (критерий
     * приёмки 6). Пустой список адресов (закрепление в недавних, чужой профиль) — тот же
     * запасной путь, а не попытка открыть чужой экран.
     */
    override fun openVendorStep(profile: VendorProfile, step: PermissionStep): StepOpenResult {
        val opened = SystemScreenTargets.firstOpenable(SystemScreenTargets.targets(profile, step)) { target ->
            start(componentIntent(target))
        }
        if (opened != null) return StepOpenResult.OPENED
        openAndroidFallbacks()
        return StepOpenResult.FALLBACK_TO_INSTRUCTIONS
    }

    /**
     * Экран доступа к политике «Не беспокоить» (НАП-17, SKL-79).
     *
     * Запрашивается отдельно, не в онбординге; исход здесь не нужен — экран диагностики
     * не показывает инструкцию для этой строки.
     */
    fun openDndAccessSettings() {
        if (!start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))) start(appDetails())
    }

    /** Стандартный список энергосбережения, затем настройки приложения — обычный запасной путь EMUI. */
    private fun openAndroidFallbacks() {
        // Разрешения он не требует — это экран списка, а не запрос исключения.
        if (!start(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) start(appDetails())
    }

    /** @return удалось ли открыть. Отказ — обычный исход на неэталонной прошивке, а не сбой. */
    private fun start(intent: Intent): Boolean {
        // `resolveActivity` не отменяет `try/catch`: между проверкой и запуском состояние
        // системы может измениться, а `SecurityException` проверка не ловит вовсе.
        if (!canOpen(intent)) {
            Log.i(TAG, "system screen is absent on this firmware: ${intent.component ?: intent.action}")
            return false
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (notFound: ActivityNotFoundException) {
            Log.i(TAG, "system screen is absent on this firmware: ${intent.component ?: intent.action}", notFound)
            false
        } catch (denied: SecurityException) {
            // Экран есть, но не экспортирован: на части сборок EMUI это именно так.
            Log.i(TAG, "system screen is not exported: ${intent.component ?: intent.action}", denied)
            false
        }
    }

    private fun canOpen(intent: Intent): Boolean =
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null

    private fun notificationIntent(channelId: String?): Intent = Intent(NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .apply {
            // Канал открывается сразу выключенный — это путь исправления ОВ-107.
            if (channelId != null) putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        }

    private fun fullScreenIntentIntent(): Intent = Intent(FULL_SCREEN_INTENT_SETTINGS, appUri())

    private fun componentIntent(target: SystemScreenTarget): Intent =
        Intent().setComponent(ComponentName(target.packageName, target.className)).apply {
            // Некоторым вендорским экранам нужен пакет приложения: без него раздел
            // открывается пустым (экран энергосбережения MIUI).
            if (target.carriesAppIdentity) {
                putExtra(EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(EXTRA_PACKAGE_LABEL, appLabel())
            }
        }

    /** Имя приложения для чужого экрана: у MIUI оно подпись строки, а не идентификатор. */
    private fun appLabel(): String = runCatching {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(context.packageName)

    private fun appDetails(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri())

    private fun appUri(): Uri = Uri.fromParts(PACKAGE_SCHEME, context.packageName, null)

    private companion object {
        const val TAG = "SklyankaOnboarding"
        const val PACKAGE_SCHEME = "package"

        /** API 34: экран «Разрешить полноэкранные уведомления». */
        const val FULL_SCREEN_INTENT_API = 34

        /**
         * Действия, объявленные в новых API, — строками.
         *
         * Константы `Settings.ACTION_*` при компиляции подставляются значением, поэтому Lint
         * считает их обращением к более новому API (`InlinedApi`, ошибка по конвейеру проекта).
         * Сами значения неизменны, а ветвление по версии стоит выше — там, где ему и место.
         */
        const val EXACT_ALARM_SETTINGS = "android.settings.REQUEST_SCHEDULE_EXACT_ALARM"
        const val NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
        const val FULL_SCREEN_INTENT_SETTINGS = "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT"

        /** Ключи, которыми MIUI ждёт пакет приложения на экране ограничений энергосбережения. */
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_PACKAGE_LABEL = "package_label"
    }
}
