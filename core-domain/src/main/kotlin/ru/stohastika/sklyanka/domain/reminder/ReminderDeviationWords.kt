package ru.stohastika.sklyanka.domain.reminder

/**
 * Отклонение предъявления напоминания, пригодное для слов (ОВ-49, SKL-79).
 *
 * Первый уровень экрана говорит «вовремя» или «с опозданием на N мин», а не секундами:
 * секунды — техническая подробность, и человек, пришедший узнать, всё ли работает,
 * не должен их разбирать. Порог «вовремя» тот же, что у НАП-2 (`ReminderOnTimeRule`),
 * чтобы экран и сводка считали одно и то же одним правилом.
 *
 * Домен не хранит текста: слова складывает экран из ресурсов (НФТ-42, НФТ-44).
 */
object ReminderDeviationWords {

    fun isOnTime(deviationSeconds: Long?): Boolean = ReminderOnTimeRule.isOnTime(deviationSeconds)

    /** Целые минуты опоздания: секунды, оставшиеся от деления, — уже не повод для слова «мин». */
    fun lateMinutes(deviationSeconds: Long): Long = deviationSeconds / SECONDS_IN_MINUTE

    private const val SECONDS_IN_MINUTE = 60
}
