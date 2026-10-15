package ru.stohastika.sklyanka.domain.schedule

import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.math.BigDecimal

/**
 * Разница между планом и фактом для одного лекарства внутри окна.
 *
 * [toCreate] — плановые приёмы, записей о которых ещё нет (вместе с дозой дня: снимок
 * дозы рождается здесь, а не пересчитывается при отметке — ПР-19); [toDelete] —
 * идентификаторы записей, которые расписанию больше не соответствуют; [toRefreshDose] —
 * новые снимки дозы для ожидающих записей, которым план назначил другую дозу;
 * [unchanged] — совпавшие записи, которые никто не трогает.
 */
data class IntakePlanDiff(
    val toCreate: List<PlannedIntake>,
    val toDelete: List<String>,
    val toRefreshDose: List<IntakeDoseRefresh> = emptyList(),
    val unchanged: Int,
) {
    /** Ничего не изменилось: повторный запуск на тех же данных (НАП-9). */
    val isEmpty: Boolean get() = toCreate.isEmpty() && toDelete.isEmpty() && toRefreshDose.isEmpty()

    companion object {
        val NOTHING = IntakePlanDiff(toCreate = emptyList(), toDelete = emptyList(), unchanged = 0)
    }
}

/**
 * Новый снимок дозы ожидающего приёма (ПР-19).
 *
 * Запись адресуется идентификатором, а не моментом: пересобирается та же строка — та же,
 * что стоит в будильнике и на часах (НАП-9). `null` — плановый приём дозы не несёт,
 * и доза берётся у лекарства.
 */
data class IntakeDoseRefresh(val intakeId: String, val doseAmount: BigDecimal?)

/**
 * Сравнение плана с существующими записями.
 *
 * Правило выбрано третьим из трёх разобранных в `task.md` («Перегенерация при изменении
 * расписания»): пересоздаются только затронутые записи. С точки зрения человека это ведёт
 * себя как полная перегенерация, а с точки зрения напоминаний — как её отсутствие:
 * совпавшая по времени запись сохраняет `intake_id`, а вместе с ним поставленный будильник
 * (НАП-9) и адрес карточки на часах (ЧАС-16).
 *
 * Сравнение идёт **по моменту времени**: доза дня не участвует в решении «создавать ли
 * запись». Иначе смена ступени пересоздавала бы уже отмеченные приёмы, а точнее — не
 * пересоздавала бы, но числилась изменением, и журнал напоминаний врал бы о правке
 * расписания (ПР-19, `TDD.md §5`).
 *
 * Ожидающей записи доза всё же обновляется ([IntakePlanDiff.toRefreshDose]): снимок
 * лежит на приёме, и без этого окно до семи суток показывало бы и списывало прежнюю дозу,
 * пока человек уже принимает по новой (SKL-117, критика F1/F7). Отмеченные и снятые
 * записи не трогаются никогда: на них стоит история, и переписанная задним числом доза
 * разошлась бы с остатком.
 */
class IntakePlanDiffer(private val time: TimeSource) {

    fun diff(planned: List<PlannedIntake>, existing: List<Intake>): IntakePlanDiff {
        val plannedMoments = planned.mapTo(mutableSetOf()) { it.scheduledAt }
        val existingMoments = existing.mapTo(mutableSetOf()) { it.scheduledAt }
        val nowLocal = time.nowLocal()

        // Существующая запись любого статуса занимает своё время: отмеченный приём
        // не воскрешается вторым ожидающимся (ПР-2, ИНВ-13).
        val toCreate = planned.filterNot { it.scheduledAt in existingMoments }

        val pendingByMoment = existing.filter { it.isPending }.associateBy { it.scheduledAt }
        val toRefreshDose = planned.mapNotNull { plan ->
            val pending = pendingByMoment[plan.scheduledAt] ?: return@mapNotNull null
            if (sameDose(pending.doseAmount, plan.doseAmount)) {
                null
            } else {
                IntakeDoseRefresh(intakeId = pending.id, doseAmount = plan.doseAmount)
            }
        }

        val toDelete = existing
            // Отметка неприкосновенна: taken, skipped и missed не удаляются ни при какой
            // правке расписания — на них стоит история и отчёт врачу (ПР-11).
            .filter { it.isPending }
            .filterNot { it.scheduledAt in plannedMoments }
            // Прошедшие ожидающиеся приёмы оставляются SKL-07: он переведёт их в MISSED.
            // Удалять прожитое время — значит переписывать историю задним числом.
            .filter { it.scheduledAt.isAfter(nowLocal) }
            .map { it.id }

        return IntakePlanDiff(
            toCreate = toCreate,
            toDelete = toDelete,
            toRefreshDose = toRefreshDose,
            unchanged = existing.count { it.scheduledAt in plannedMoments },
        )
    }

    /** Совпадают ли дозы: пустая и записанная — разные, записанные — по значению (ПР-19). */
    private fun sameDose(current: BigDecimal?, planned: BigDecimal?): Boolean = when {
        current == null && planned == null -> true
        current == null || planned == null -> false
        else -> current.compareTo(planned) == 0
    }
}
