package ru.stohastika.sklyanka.domain.log

import java.time.Instant

/**
 * Локальный журнал критичных операций над данными.
 *
 * Сети нет, крашлитики нет, телеметрии нет и не будет, поэтому единственный источник
 * правды при разборе жалобы «куда делся приём» находится на устройстве человека.
 *
 * В журнал не попадают названия лекарств, имена профилей и дозировки — только
 * идентификаторы (НФТ-19). Поле [detail] принимает служебные коды вроде `already_marked`,
 * а не текст для человека.
 */
interface OperationLog {

    @Suppress("LongParameterList")
    suspend fun record(
        event: OpEvent,
        profileId: String? = null,
        medicationId: String? = null,
        intakeId: String? = null,
        before: String? = null,
        after: String? = null,
        source: String? = null,
        detail: String? = null,
    )

    /** Последние записи журнала — для экрана диагностики (НФТ-52) и разбора инцидента. */
    suspend fun recent(limit: Int): List<OpLogRecord>

    /**
     * Момент последней записи этого события; `null` — таких записей ещё не было.
     *
     * Отдельный вопрос, а не разбор [recent]: «когда это было в прошлый раз» спрашивают
     * о суточном окне проверок (`ADR-0012` п. 4), и ответ обязан быть точным независимо
     * от того, сколько других записей накопилось между делом.
     */
    suspend fun lastOccurredAt(event: OpEvent): Instant?

    /**
     * Кольцевое вытеснение: сначала по возрасту, затем по числу записей.
     *
     * Вызывается вне транзакции основной операции: подрезка журнала не имеет права
     * откатить отметку приёма.
     */
    suspend fun prune(keepDays: Long, keepRecords: Int)
}
