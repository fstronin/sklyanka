package ru.stohastika.sklyanka.platform.startup

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.startup.SplashRoute
import ru.stohastika.sklyanka.startup.StartDestination
import ru.stohastika.sklyanka.startup.StartReason
import ru.stohastika.sklyanka.startup.StartupConditions
import ru.stohastika.sklyanka.startup.StartupDecision
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Состояние запуска, прочитанное из системы.
 *
 * Разрешения спрашиваются у платформы при каждом запуске, а не берутся из
 * сохранённого флага (НФТ-52): человек мог отозвать разрешение из настроек, и
 * сохранённое «онбординг пройден» означало бы молча сломанные напоминания.
 *
 * Факт «приложение уже запускалось» системе неизвестен — его больше неоткуда взять,
 * кроме собственной отметки. Она и хранится здесь, отдельной парой ключ-значение,
 * и означает ровно запуск, а не пройденный онбординг: состояние онбординга — дело
 * SKL-14, и подменять одно другим значило бы спрятать разницу.
 */
@Singleton
class SystemStartupState @Inject constructor(@ApplicationContext private val context: Context) {

    /** Признаки текущего запуска. Отметку о запуске не ставит — это делает [rememberLaunch]. */
    fun read(): StartupConditions = StartupConditions(
        firstLaunch = !preferences().getBoolean(KEY_LAUNCHED, false),
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        exactAlarmsAllowed = exactAlarmsAllowed(),
    )

    /** Отметить, что запуск состоялся: со следующего раза он уже не первый. */
    fun rememberLaunch() {
        preferences().edit { putBoolean(KEY_LAUNCHED, true) }
    }

    /**
     * Точные будильники.
     *
     * До Android 12 разрешения не существовало и будильник ставился всегда; с 12
     * его может не быть, и тогда напоминание придёт «когда-нибудь», что для
     * «Склянки» отказ продукта, а не неудобство (`docs/v2/10-behavior.md`).
     */
    private fun exactAlarmsAllowed(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    } else {
        true
    }

    private fun preferences() = context.getSharedPreferences(STARTUP_PREFERENCES, Context.MODE_PRIVATE)

    /**
     * Имена файла и ключа отметки о запуске — `internal`, а не `private` (SKL-97):
     * стенд устройства снимает отметку перед каждой проверкой и не дублирует строки.
     */
    internal companion object {
        const val STARTUP_PREFERENCES = "sklyanka_startup"
        const val KEY_LAUNCHED = "launched"
    }
}

/**
 * Журнал запуска: куда ушла заставка и почему.
 *
 * Отдельно от журнала операций над данными (`OperationLog`): тот отвечает на вопрос
 * «куда делся приём», этот — на жалобу «приложение каждый раз просит разрешения».
 * Смешивать их значит потерять оба при подрезке.
 *
 * Запись локальная и наружу не уходит: сети нет, крашлитики нет, телеметрии нет.
 * В записи только перечисления — ни идентификаторов, ни медицинских данных (НФТ-19).
 */
@Singleton
class StartupLog @Inject constructor() {

    fun splashLeftTo(destination: StartDestination, reason: StartReason) {
        Log.i(TAG, "splash -> $destination (reason=$reason)")
    }

    private companion object {
        const val TAG = "SklyankaStartup"
    }
}

/**
 * Решение о том, куда уходит заставка, вместе с его причиной.
 *
 * Собрано в один класс, чтобы заставка не спрашивала систему дважды: первый запуск
 * перестаёт быть первым сразу после отметки, и повторное чтение дало бы другой ответ.
 */
@Singleton
class StartupRouter @Inject constructor(private val state: SystemStartupState, private val log: StartupLog) {

    suspend fun resolve(): StartupDecision {
        val conditions = state.read()
        val resolved = SplashRoute.decisionOf(conditions)
        log.splashLeftTo(resolved.destination, resolved.reason)
        state.rememberLaunch()
        return resolved
    }
}
