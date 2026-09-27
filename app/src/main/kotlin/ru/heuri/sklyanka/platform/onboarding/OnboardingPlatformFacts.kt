package ru.heuri.sklyanka.platform.onboarding

import android.content.Context
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.heuri.sklyanka.domain.onboarding.PermissionStep
import ru.heuri.sklyanka.domain.onboarding.port.AutostartHintReader
import ru.heuri.sklyanka.domain.onboarding.port.PermissionRequestSupport
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Косвенный признак автозапуска: снята ли оптимизация батареи (НАП-20).
 *
 * Это единственное, что Android об энергосбережении приложения отвечает, и это **не**
 * состояние автозапуска EMUI: список ручного управления запуском — фирменная настройка,
 * и из снятой оптимизации батареи она не следует. Поэтому ответ приходит признаком,
 * а не состоянием шага, и на экране подписан словом «косвенный».
 */
@Singleton
class BatteryOptimizationHintReader @Inject constructor(@ApplicationContext private val context: Context) :
    AutostartHintReader {

    override fun batteryOptimizationIgnored(): Boolean {
        // До Android 6 оптимизации батареи не существует, и «снята» о ней сказать нельзя:
        // отсутствие признака честнее, чем признак, которого система не давала.
        val power = context.getSystemService(PowerManager::class.java)
            ?.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.M }
        return power?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }
}

/**
 * Что из цепочки система даёт запросить диалогом (ПЛ-13, ПЛ-14).
 *
 * Ответ зависит только от версии Android, и поэтому живёт здесь, а не в домене: домен
 * версий системы не знает и знать не должен (НФТ-46).
 */
@Singleton
class SdkPermissionRequestSupport @Inject constructor() : PermissionRequestSupport {

    override fun canRequestFromCode(step: PermissionStep): Boolean = when (step) {
        // До Android 13 разрешения на уведомления не существует: их выключают
        // в системных настройках приложения, и запрос ничего бы не сделал.
        PermissionStep.NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        // Право на точные будильники не запрашивается диалогом ни на одной версии:
        // его выдают на экране особого доступа (ПЛ-14). Сценарий приёмки «человек нажимает
        // «Разрешить» и отказывает в системном запросе» для этого шага неисполним —
        // разбор расхождения в отчёте задачи.
        PermissionStep.EXACT_ALARMS -> false

        // Программного запроса автозапуска не существует вовсе (НАП-20, ПЛ-18).
        PermissionStep.AUTOSTART -> false
    }
}
