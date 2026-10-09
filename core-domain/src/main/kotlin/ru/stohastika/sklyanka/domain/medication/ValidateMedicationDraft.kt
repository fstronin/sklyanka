package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.math.BigDecimal

/**
 * Правила формы лекарства (`TDD.md §5.3`).
 *
 * Возвращает **список** нарушений, а не первое найденное: форма длинная, и показывать
 * ошибки по одной значит гонять человека по экрану несколько раз. Экран прокручивается
 * к первому нарушению, но знает обо всех.
 *
 * Порядок списка — порядок блоков формы: первое нарушение в списке и есть то, к которому
 * экран прокручивается.
 *
 * Функция ничего не знает об Android и о форме: те же правила применяются к записям
 * из файла переноса данных (SKL-19), где формы нет вовсе.
 */
class ValidateMedicationDraft {

    operator fun invoke(draft: MedicationDraft): List<DraftViolation> = buildList {
        if (draft.name.isBlank()) add(DraftViolation.NameEmpty)
        addAll(doseViolations(draft))
        addAll(scheduleViolations(draft))
        addAll(periodViolations(draft))
        addAll(stockViolations(draft))
    }

    /** ИНВ-4 и ИНВ-5: доза строго больше нуля, дозировка и её единица — вместе или никак. */
    private fun doseViolations(draft: MedicationDraft): List<DraftViolation> = buildList {
        val dose = draft.doseAmount
        if (dose == null || dose <= BigDecimal.ZERO) add(DraftViolation.DoseNotPositive)
        // Единица дозировки — значение закрытого списка (Р-45), поэтому «пустая строка»
        // как отдельный случай ушла: единица либо выбрана, либо её нет.
        if (draft.strength != null && draft.strengthUnit == null) add(DraftViolation.StrengthWithoutUnit)
        if (draft.strength == null && draft.strengthUnit != null) add(DraftViolation.UnitWithoutStrength)
    }

    /**
     * ИНВ-6, ИНВ-17, ПР-7 и ПР-8: расписание и «по потребности» — взаимоисключающие наборы.
     *
     * Неприменимые поля здесь не проверяются вовсе: их сбрасывает [normalized], и проверять
     * то, чего в черновике уже нет, значит заводить правило, которое никогда не сработает.
     */
    private fun scheduleViolations(draft: MedicationDraft): List<DraftViolation> = buildList {
        when (draft.scheduleMode) {
            ScheduleMode.SCHEDULED -> {
                if (draft.times.isEmpty()) add(DraftViolation.NoIntakeTimes)
                // Повтор называется первым по порядку в списке: человек ищет его глазами
                // сверху вниз, и назвать второй из двух одинаковых — сбить с толку.
                val duplicate = draft.times.groupBy { it }.entries.firstOrNull { it.value.size > 1 }?.key
                if (duplicate != null) add(DraftViolation.DuplicateTime(duplicate))
            }

            ScheduleMode.PRN -> {
                val limit = draft.maxPerDay
                when {
                    limit == null -> add(DraftViolation.MaxPerDayMissing)
                    limit <= 0 -> add(DraftViolation.MaxPerDayNotPositive)
                }
            }
        }
    }

    /**
     * ИНВ-7: дата окончания не раньше даты начала.
     *
     * Сравнение календарное, без времени и без пояса: иначе курс, заведённый вечером,
     * при смене пояса начинал бы «вчера». Совпадающие даты — курс на один день,
     * а не пустой курс: последний день курса включительный.
     */
    private fun periodViolations(draft: MedicationDraft): List<DraftViolation> = buildList {
        val end = draft.endDate
        if (end != null && end < draft.startDate) add(DraftViolation.EndDateBeforeStart)
    }

    /**
     * ИНВ-8 и ИНВ-20: остаток не отрицателен, заполненный размер упаковки больше нуля.
     *
     * Пустой остаток — «человек остаток не ведёт», пустой размер — «размер не задан»:
     * оба не ошибки. Размер без остатка тоже не ошибка: без остатка он ни на что
     * не влияет, а блок появится, когда появится остаток (ОВ-45).
     */
    private fun stockViolations(draft: MedicationDraft): List<DraftViolation> = buildList {
        val stock = draft.stockLeft
        if (stock != null && stock < BigDecimal.ZERO) add(DraftViolation.StockNegative)
        val size = draft.packageSize
        if (size != null && size <= BigDecimal.ZERO) add(DraftViolation.PackageSizeNotPositive)
    }
}
