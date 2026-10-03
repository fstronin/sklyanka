package ru.stohastika.sklyanka.domain.reminder

import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Instant

/**
 * Ротация журнала срабатываний (ПМ-30, НФТ-51).
 *
 * Правило — сочетание возраста и объёма: сначала удаляются записи старше [ReminderLogRetention.maxAge],
 * затем, если журнал всё ещё больше [ReminderLogRetention.maxBytes], вытесняются
 * старейшие снаружи неприкосновенного окна. В окно ротация не заходит ни при каком
 * объёме: по последним [ReminderLogRetention.protectedWindow] работает диагностика
 * и разбор недавних жалоб.
 *
 * Факт ротации пишется в сам журнал — числами, без содержимого: «журнал стал короче»
 * не должно выглядеть потерей данных. Если удалять было нечего, сценарий молчит:
 * журнал ведётся как журнал изменений, а не как журнал уборок.
 */
class RotateReminderJournalUseCase(
    private val journal: ReminderJournal,
    private val time: TimeSource,
    private val retention: ReminderLogRetention = ReminderLogRetention.DEFAULT,
) {

    /**
     * Выполнить ротацию; `null` — удалять было нечего.
     *
     * Возвращает число удалённых записей по порогам: его показывает тест и приёмка,
     * а в журнал уходит закодированная нагрузка ([ReminderLogRotation.encode]).
     */
    suspend operator fun invoke(): ReminderLogRotation? {
        val now = time.now()
        val protectedSince = now.minus(retention.protectedWindow)
        val removedByAge = journal.deleteOlderThan(ageCutoff(now))
        val removedBySize = evictOldestUntilWithinBudget(protectedSince)
        if (removedByAge + removedBySize == 0) return null

        val rotation = ReminderLogRotation(removedByAge = removedByAge, removedBySize = removedBySize)
        journal.record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.ROTATED,
                reason = rotation.encode(),
            ),
        )
        return rotation
    }

    /**
     * Возрастная граница не заходит в неприкосновенное окно даже при непривычных
     * пределах: удаляется только запись старше **обеих** границ.
     */
    private fun ageCutoff(now: Instant): Instant = now.minus(maxOf(retention.maxAge, retention.protectedWindow))

    /**
     * Вытеснение по объёму: пачки старейших, пока журнал не влезет.
     *
     * Пачка, а не запись: пересчёт объёма — полный проход по журналу, и по одной
     * записи он стоил бы десятки проходов на каждой ротации. Перелёт на пачку —
     * максимум [DELETE_BATCH] записей — потолок не нарушает: он максимум, а не минимум.
     */
    private suspend fun evictOldestUntilWithinBudget(protectedSince: Instant): Int {
        var removed = 0
        while (journal.storedBytes() + ROTATION_FACT_RESERVE_BYTES > retention.maxBytes) {
            val batch = journal.deleteOldest(protectedSince, DELETE_BATCH)
            // Хранилище ответило «снаружи окна удалять нечего»: окно важнее потолка.
            if (batch == 0) break
            removed += batch
        }
        return removed
    }

    private companion object {
        /** Размер пачки вытеснения: компромисс между проходами по журналу и перелётом. */
        const val DELETE_BATCH = 128

        /**
         * Запас под саму запись о ротации.
         *
         * Она пишется после очистки и не имеет права вывести журнал за потолок;
         * 128 байт с запасом покрывают строку `rotated` с короткой нагрузкой.
         */
        const val ROTATION_FACT_RESERVE_BYTES = 128L
    }
}
