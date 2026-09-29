package ru.stohastika.sklyanka.platform.onboarding

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.domain.onboarding.AutostartOpenResult
import ru.stohastika.sklyanka.feature.onboarding.SystemSettingsNavigator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Переходы в системные настройки (ПЛ-13, ПЛ-14, ПЛ-18).
 *
 * Работает от контекста приложения с `FLAG_ACTIVITY_NEW_TASK`, а не от Activity: экран
 * автозапуска принадлежит чужому приложению и открывается своей задачей всё равно, а
 * зависимость на Activity сделала бы навигацию непригодной для повторного входа из настроек.
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
    override fun openExactAlarmSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(EXACT_ALARM_SETTINGS, appUri())
        } else {
            appDetails()
        }
        start(intent) { start(appDetails()) }
    }

    /**
     * Настройки уведомлений приложения.
     *
     * Нужны там, где системный диалог не покажется: до Android 13 разрешения нет вовсе,
     * а после двух отказов система диалог больше не показывает.
     */
    override fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            appDetails()
        }
        start(intent) { start(appDetails()) }
    }

    /**
     * Экран управления запуском приложений производителя (ПЛ-18).
     *
     * Перебор известных адресов, а не один интент: имя Activity в EMUI менялось между
     * версиями прошивки, и на части сборок экран не экспортирован вовсе. Ни один не сработал —
     * человек получает текстовую инструкцию, и это рабочий путь, а не сообщение об ошибке
     * (критерий приёмки 6).
     *
     * Ни `QUERY_ALL_PACKAGES`, ни проба `resolveActivity` здесь не нужны: попытка открытия
     * и есть проба, а её отказ — тот самый ответ, который нужен.
     */
    override fun openAutostartSettings(): AutostartOpenResult {
        val opened = AutostartTargets.firstOpenable { target ->
            start(Intent().setComponent(ComponentName(target.packageName, target.className)))
        }
        if (opened != null) return AutostartOpenResult.OPENED

        // Запасной ход перед инструкцией: стандартный список энергосбережения существует
        // на любой прошивке и ведёт человека в нужный раздел настроек, хотя и не в тот же.
        // Разрешения он не требует — это экран списка, а не запрос исключения.
        val batteryList = start(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        Log.i(TAG, "autostart screen not found, battery list opened=$batteryList")
        return AutostartOpenResult.FALLBACK_TO_INSTRUCTIONS
    }

    /** @return удалось ли открыть. Отказ — обычный исход на неэталонной прошивке, а не сбой. */
    private fun start(intent: Intent, onFailure: () -> Unit = {}): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (notFound: ActivityNotFoundException) {
        Log.i(TAG, "system screen is absent on this firmware: ${intent.component ?: intent.action}", notFound)
        onFailure()
        false
    } catch (denied: SecurityException) {
        // Экран есть, но не экспортирован: на части сборок EMUI это именно так.
        Log.i(TAG, "system screen is not exported: ${intent.component ?: intent.action}", denied)
        onFailure()
        false
    }

    private fun appDetails(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri())

    private fun appUri(): Uri = Uri.fromParts(PACKAGE_SCHEME, context.packageName, null)

    private companion object {
        const val TAG = "SklyankaOnboarding"
        const val PACKAGE_SCHEME = "package"

        /**
         * Действие экрана особого доступа к точным будильникам, строкой.
         *
         * `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` объявлена в API 31 и подставляется
         * значением при компиляции, поэтому Lint считает её обращением к более новому API
         * (`InlinedApi`, ошибка по конвейеру проекта). Само значение неизменно, а ветвление
         * по версии стоит выше — там, где ему и место.
         */
        const val EXACT_ALARM_SETTINGS = "android.settings.REQUEST_SCHEDULE_EXACT_ALARM"
    }
}
