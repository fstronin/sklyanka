package ru.heuri.sklyanka.domain.reminder.port

/**
 * Две настройки напоминания из `design/screens/Settings.dc.html`.
 *
 * Экран настроек — SKL-20; здесь только значения. Ключей тихих часов в этом типе нет
 * и заготовки под них не будет: решение по НАП-28 не принято, а настройка, глушащая
 * напоминание, прямо противоречит НАП-1.
 */
data class ReminderSettingsValues(
    /** «Настойчивое напоминание · Повторить, если не отметили». По умолчанию выключено. */
    val persistentEnabled: Boolean = false,
    /**
     * «Отложить на» — интервал в минутах, по умолчанию 15.
     *
     * Один интервал на два смысла: подпись кнопки откладывания и период проверки условия
     * повтора (НАП-25). Два разных интервала в одном экране настроек человек не разведёт.
     */
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
) {
    companion object {
        const val DEFAULT_SNOOZE_MINUTES: Int = 15
    }
}

/** Чтение и запись настроек напоминания. Реализация — `core-data` (DataStore). */
interface ReminderSettings {

    suspend fun read(): ReminderSettingsValues

    suspend fun setPersistentEnabled(enabled: Boolean)

    suspend fun setSnoozeMinutes(minutes: Int)
}
