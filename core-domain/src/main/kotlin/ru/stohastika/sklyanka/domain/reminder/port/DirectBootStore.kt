package ru.stohastika.sklyanka.domain.reminder.port

import ru.stohastika.sklyanka.domain.reminder.DirectBootAlarm
import ru.stohastika.sklyanka.domain.reminder.DirectBootAlarms
import ru.stohastika.sklyanka.domain.reminder.DirectBootTrace

/**
 * Зеркало поставленных будильников в device-protected storage.
 *
 * Порт не знает, где лежит файл и как он сериализуется: домен только формулирует, что
 * снимок существует и обновляется в точке постановки/снятия будильника. Отсутствие снимка
 * и отказ записи — штатная деградация, а не отказ: следующая пересборка перезапишет его.
 */
interface DirectBootAlarmStore {
    suspend fun load(): DirectBootAlarms?
    suspend fun put(alarm: DirectBootAlarm)
    suspend fun remove(intakeId: String)

    /**
     * Заменить снимок целиком составом поставленных будильников окна.
     *
     * Нужен полной пересборке: она обязана оставить снимок соответствующим системе даже
     * тогда, когда дифф пуст и ставить заново нечего. Пустой список — валидный снимок:
     * окно пусто, восстанавливать нечего. Операция идемпотентна.
     */
    suspend fun replace(alarms: List<DirectBootAlarm>)
}

/**
 * Ограниченная очередь следов locked-фазы.
 *
 * Следы забираются пачкой, а не по одному, потому что журнал недоступен до разблокировки,
 * а потерять след восстановления нельзя (НАП-14): [drain] должен забрать и очистить очередь
 * одной операцией, чтобы двойная запись и потеря следа были невозможны.
 */
interface DirectBootTraceStore {
    suspend fun append(trace: DirectBootTrace)
    suspend fun drain(): List<DirectBootTrace>
}
