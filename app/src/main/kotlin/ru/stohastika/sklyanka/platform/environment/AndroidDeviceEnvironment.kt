package ru.stohastika.sklyanka.platform.environment

import android.os.Build
import ru.stohastika.sklyanka.domain.onboarding.port.AutostartHintReader
import ru.stohastika.sklyanka.domain.reminder.DeviceEnvironment
import ru.stohastika.sklyanka.domain.reminder.port.DeviceEnvironmentSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Факты устройства для журнала среды (SKL-85, `TDD.md §5.7`).
 *
 * Только чтение `Build.*` и существующий косвенный признак энергосбережения: второго
 * чтения `PowerManager` здесь нет — оно уже живёт в `BatteryOptimizationHintReader`
 * (НАП-20), и второй путь отвечал бы на тот же вопрос по-своему.
 *
 * Медицинских данных здесь нет по построению: производитель, версия Android и сборка
 * прошивки — служебные строки платформы (НФТ-19).
 */
@Singleton
class AndroidDeviceEnvironment @Inject constructor(private val autostartHint: AutostartHintReader) :
    DeviceEnvironmentSource {

    override suspend fun environment(): DeviceEnvironment = DeviceEnvironment(
        manufacturer = Build.MANUFACTURER,
        brand = Build.BRAND,
        osRelease = Build.VERSION.RELEASE,
        buildDisplay = Build.DISPLAY,
        batteryUnrestricted = autostartHint.batteryOptimizationIgnored(),
    )
}
