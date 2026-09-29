package ru.stohastika.sklyanka.domain.transfer

import java.time.LocalTime

/**
 * Встроенный сигнал напоминания (ПЕР-12).
 *
 * Перечисление, а не свободная строка: настройки переносятся **значениями**, а не
 * системными ссылками, и ссылка на рингтон чужого устройства на приёмнике не разрешится.
 * Код латиницей — он попадает в файл, а файл читает машина (НФТ-42).
 *
 * Индивидуальный звук на лекарство отложен во вторую версию (ADR-0004), поэтому звук
 * один на всё приложение и живёт в настройках, а не в записи лекарства.
 */
enum class ReminderSound(val code: String) {
    SOFT("soft"),
    DROP("drop"),
    SPROUT("sprout"),
    TUNING_FORK("tuning_fork"),
    HEATHER("heather"),
    ;

    companion object {
        val DEFAULT: ReminderSound = SOFT

        /** Незнакомый код даёт null: подстановку значения по умолчанию решает вызывающий (ПЕР-12). */
        fun ofCode(code: String?): ReminderSound? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Тихие часы: пара границ либо выключенный режим.
 *
 * Границы — локальное время без пояса, как и расписание приёма: «с 23:30 до 07:00»
 * остаётся тем же после перелёта (НФТ-34).
 */
data class QuietHours(val from: LocalTime, val to: LocalTime)

/**
 * Настройки приложения, переносимые файлом (ПЕР-3, ПЕР-12).
 *
 * Разрешения, автозапуск, подключение часов и идентификаторы каналов уведомлений здесь
 * отсутствуют намеренно: они принадлежат системе, а не файлу (ПЕР-24, ПЕР-26). Значение
 * «часы включены» — это желание человека, а не выданное право Wear Engine, и оно
 * переносимо.
 */
data class TransferSettings(
    val reminderSound: ReminderSound = ReminderSound.DEFAULT,
    val persistentReminder: Boolean = false,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val quietHours: QuietHours? = null,
    val watchEnabled: Boolean = false,
    val watchSnoozeButton: Boolean = false,
) {
    companion object {
        /** Значение по умолчанию у отложенного напоминания — те же 15 минут, что в макете. */
        const val DEFAULT_SNOOZE_MINUTES: Int = 15

        val DEFAULT: TransferSettings = TransferSettings()
    }
}
