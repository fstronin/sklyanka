package ru.stohastika.sklyanka.reminder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.di.ReminderScope
import ru.stohastika.sklyanka.domain.reminder.ReminderLogRotation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ротация журнала как операция — рядом с `ReminderOutageDetector`: правило живёт
 * в домене, а планированию нужен только исход.
 */
fun interface ReminderJournalRotation {
    suspend operator fun invoke(): ReminderLogRotation?
}

/**
 * Запуск ротации журнала срабатываний (SKL-65, ПМ-30).
 *
 * Очистка — не критический путь: она планируется в области корутин подсистемы
 * напоминаний (`@ReminderScope`) и сразу возвращает управление, поэтому не задерживает
 * ни `onCreate`, ни первый кадр, ни пересборку будильников. Фоновой службы и
 * уведомлений не поднимается (НФТ-26); сеть не используется (ПЛ-3).
 */
@Singleton
class ReminderLogRotationTrigger @Inject constructor(
    private val rotate: ReminderJournalRotation,
    @ReminderScope private val scope: CoroutineScope,
) {

    /** Запланировать очистку и сразу вернуть управление. */
    fun schedule() {
        scope.launch { rotate() }
    }
}
