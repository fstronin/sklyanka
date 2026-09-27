package ru.heuri.sklyanka.reminder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.heuri.sklyanka.domain.reminder.ReminderOutage
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обнаружение перерыва как зависимость — операция домена, а не класс.
 *
 * Функциональный интерфейс по той же причине, что `ReminderCheckNow`: обнаружение считает
 * признак по журналу и плану и пишет запись, а объявляющему нужен только исход. Заодно
 * правило объявления проверяется на JVM без хранилища и плана.
 */
fun interface ReminderOutageDetector {
    suspend operator fun invoke(): ReminderOutage?
}

/**
 * Объявление перерыва в напоминаниях при входе в приложение (НАП-38).
 *
 * **Зачем отдельный держатель.** Признак перерыва считается при запуске (`SklyankaApp`)
 * и пишется в журнал, но требование НАП-38 — не про журнал: человек обязан **увидеть**
 * сообщение, войдя в приложение. Экран диагностики для этого не годится: туда человек
 * сам не пойдёт, а пойдёт он именно тогда, когда уже потерял приёмы. Поэтому исход
 * обнаружения живёт здесь, а корень приложения показывает его над содержимым.
 *
 * **Почему признак перечитывается, а не запоминается флагом.** Обнаружение идемпотентно:
 * второй раз тот же перерыв второй записи в журнале не создаёт (`RecordReminderOutageUseCase`),
 * а сохранённый флаг устаревал бы ровно в тот момент, когда напоминания снова пошли.
 *
 * Объявление снимается, когда человек ушёл по нему в диагностику: сообщение доставлено,
 * и там оно повторено баннером вместе с ответом «что именно не так». Тот же перерыв
 * второй раз не объявляется — иначе баннер возвращался бы при каждом переходе между
 * вкладками. Новый (более поздний) перерыв объявляется снова.
 */
@Singleton
class ReminderOutageAnnouncer @Inject constructor(private val detect: ReminderOutageDetector) {

    private val _current = MutableStateFlow<ReminderOutage?>(null)

    /** Что показать над содержимым приложения. `null` — перерыва нет или он уже объявлен. */
    val current: StateFlow<ReminderOutage?> = _current.asStateFlow()

    private var acknowledged: Instant? = null

    suspend fun refresh() {
        val outage = detect()
        _current.value = outage?.takeIf { it.since != acknowledged }
    }

    fun acknowledge() {
        acknowledged = _current.value?.since ?: acknowledged
        _current.value = null
    }
}
