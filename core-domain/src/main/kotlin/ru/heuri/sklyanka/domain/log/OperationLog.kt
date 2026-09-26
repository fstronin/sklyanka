package ru.heuri.sklyanka.domain.log

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
     * Кольцевое вытеснение: сначала по возрасту, затем по числу записей.
     *
     * Вызывается вне транзакции основной операции: подрезка журнала не имеет права
     * откатить отметку приёма.
     */
    suspend fun prune(keepDays: Long, keepRecords: Int)
}
