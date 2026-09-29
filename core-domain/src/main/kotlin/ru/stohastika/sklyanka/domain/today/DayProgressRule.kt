package ru.stohastika.sklyanka.domain.today

import ru.stohastika.sklyanka.domain.medication.MedicationListRules
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode

/**
 * Прогресс дня: «Принято 2 из 5 приёмов · 12 лекарств».
 *
 * Знаменатель — запланированные приёмы суток, числитель — принятые из них (ЭКР-1).
 * Приём «по потребности» не входит ни туда, ни сюда: он не был запланирован, и его
 * появление не имеет права ни сдвинуть долю вверх, ни увеличить план задним числом.
 *
 * Третье число — активные лекарства по расписанию (ЭКР-2). Считается тем же правилом,
 * что и чип «Активные · N» на экране «Лекарства»: два независимых подсчёта одного
 * числа расходятся на первой же правке.
 */
object DayProgressRule {

    operator fun invoke(items: List<IntakeItem>, activeMedications: List<Medication>): DayProgress {
        val scheduled = items.filter { it.medication.scheduleMode == ScheduleMode.SCHEDULED }
        return DayProgress(
            taken = scheduled.count { it.intake.status == IntakeStatus.TAKEN },
            planned = scheduled.size,
            medicationCount = MedicationListRules.activeCount(activeMedications),
        )
    }
}
