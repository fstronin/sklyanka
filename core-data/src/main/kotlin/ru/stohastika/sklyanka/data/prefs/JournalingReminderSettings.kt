package ru.stohastika.sklyanka.data.prefs

import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingKey
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.domain.time.TimeSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Настройки напоминания со следом в журнале (НАП-14).
 *
 * **Почему обёртка, а не запись со стороны экрана.** Настройку меняет экран настроек
 * (SKL-20), а дальше её будет менять онбординг (SKL-14) и загрузка файла переноса
 * (SKL-19, ПЕР-18). Запись из экрана дала бы след при одном пути изменения и молчание
 * при остальных — то есть журнал, по которому нельзя ответить «почему напоминание пришло
 * не так, как человек ожидал». Здесь место единственное и обойти его нельзя: хранилище
 * настроек лежит ниже порта, и любой вызывающий приходит сюда.
 *
 * **Почему не в самом хранилище.** [ReminderSettingsStore] отвечает за ключи и значения
 * по умолчанию; знание о журнале добавило бы ему вторую причину меняться и второй повод
 * падать. Разделение видно и в тестах: значения по умолчанию проверяются на хранилище,
 * след — на обёртке.
 *
 * Запись без изменения значения следа не оставляет: экран настроек перечитывает состояние
 * при каждом возвращении на передний план, и запись «на каждое касание» превратила бы
 * журнал срабатываний в журнал открытий настроек.
 */
@Singleton
class JournalingReminderSettings @Inject constructor(
    private val store: ReminderSettingsStore,
    private val journal: ReminderJournal,
    private val time: TimeSource,
) : ReminderSettings {

    override suspend fun read(): ReminderSettingsValues = store.read()

    override suspend fun setPersistentEnabled(enabled: Boolean) {
        val before = store.read().persistentEnabled
        store.setPersistentEnabled(enabled)
        if (before != enabled) trace(ReminderSettingKey.PERSISTENT_ENABLED.trace(enabled))
    }

    override suspend fun setSnoozeMinutes(minutes: Int) {
        val before = store.read().snoozeMinutes
        // Отказ хранилища от неположительного интервала обязан пройти наружу до записи следа:
        // след о значении, которого нет, хуже отсутствия следа.
        store.setSnoozeMinutes(minutes)
        if (before != minutes) trace(ReminderSettingKey.SNOOZE_MINUTES.trace(minutes), minutes = minutes)
    }

    /**
     * Запись об изменившейся настройке.
     *
     * В записи только код настройки и новое значение — ни названий лекарств, ни имён
     * профилей здесь взяться неоткуда, потому что этот класс их не видит (НФТ-19).
     */
    private suspend fun trace(value: String, minutes: Int? = null) {
        journal.record(
            ReminderLogEntry(
                occurredAt = time.now(),
                event = ReminderEvent.SETTINGS_CHANGED,
                reason = value,
                minutes = minutes,
            ),
        )
    }
}
