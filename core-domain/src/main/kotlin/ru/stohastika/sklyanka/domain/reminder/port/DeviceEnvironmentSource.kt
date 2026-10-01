package ru.stohastika.sklyanka.domain.reminder.port

import ru.stohastika.sklyanka.domain.reminder.DeviceEnvironment

/**
 * Факты устройства для журнала среды (НФТ-19, НФТ-46).
 *
 * Порт в домене: `Build`, `PowerManager` и версии прошивки — это `android.*`, которого
 * домен не видит физически. Реализация — `app`.
 */
interface DeviceEnvironmentSource {

    /** Вендор, бренд, версия Android, сборка прошивки и состояние энергосбережения. */
    suspend fun environment(): DeviceEnvironment
}
