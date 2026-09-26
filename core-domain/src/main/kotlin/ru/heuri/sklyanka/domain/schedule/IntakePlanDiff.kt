package ru.heuri.sklyanka.domain.schedule

import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.time.TimeSource
import java.time.LocalDateTime

/**
 * Разница между планом и фактом для одного лекарства внутри окна.
 *
 * [toCreate] — плановые моменты, записей о которых ещё нет; [toDelete] — идентификаторы
 * записей, которые расписанию больше не соответствуют; [unchanged] — совпавшие записи,
 * которые никто не трогает.
 */
data class IntakePlanDiff(val toCreate: List<LocalDateTime>, val toDelete: List<String>, val unchanged: Int) {
    /** Ничего не изменилось: повторный запуск на тех же данных (НАП-9). */
    val isEmpty: Boolean get() = toCreate.isEmpty() && toDelete.isEmpty()

    companion object {
        val NOTHING = IntakePlanDiff(toCreate = emptyList(), toDelete = emptyList(), unchanged = 0)
    }
}

/**
 * Сравнение плана с существующими записями.
 *
 * Правило выбрано третьим из трёх разобранных в `task.md` («Перегенерация при изменении
 * расписания»): пересоздаются только затронутые записи. С точки зрения человека это ведёт
 * себя как полная перегенерация, а с точки зрения напоминаний — как её отсутствие:
 * совпавшая по времени запись сохраняет `intake_id`, а вместе с ним поставленный будильник
 * (НАП-9) и адрес карточки на часах (ЧАС-16).
 */
class IntakePlanDiffer(private val time: TimeSource) {

    fun diff(planned: List<LocalDateTime>, existing: List<Intake>): IntakePlanDiff {
        val plannedMoments = planned.toSet()
        val existingMoments = existing.mapTo(mutableSetOf()) { it.scheduledAt }
        val nowLocal = time.nowLocal()

        // Существующая запись любого статуса занимает своё время: отмеченный приём
        // не воскрешается вторым ожидающимся (ПР-2, ИНВ-13).
        val toCreate = planned.filterNot { it in existingMoments }

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
            unchanged = existing.count { it.scheduledAt in plannedMoments },
        )
    }
}
