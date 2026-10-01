package ru.stohastika.sklyanka.platform.reminder

import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.port.ReminderMeasurement
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Действующий механизм будильников приёмов (SKL-85, `TDD.md §5.6`).
 *
 * Обычная сборка игнорирует сохранённое значение и всегда ставит системный будильник:
 * переключателя в ней нет, а значение могло остаться от измерительной сборки и обязано
 * быть инертным. Измерительная сборка отдаёт то, что выбрал пилот.
 *
 * Живёт в `app`, а не в домене: доступность режима — свойство артефакта (`BuildConfig`),
 * которого домен не видит (НФТ-46).
 */
@Singleton
class EffectiveAlarmStrategy @Inject constructor(
    private val settings: ReminderSettings,
    private val measurement: ReminderMeasurement,
) {

    /**
     * Механизм на сейчас.
     *
     * Читается в момент постановки будильника: разойдись он с тем, что названо в журнале,
     * окна измерения перестали бы отвечать на вопрос «каким механизмом поставлено».
     */
    suspend fun kind(): AlarmKind =
        if (measurement.available()) settings.read().alarmStrategy else AlarmKind.ALARM_CLOCK
}
