package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.ScheduleRule
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
        addAll(scheduleRuleViolations(draft))
        addAll(scheduleViolations(draft))
        addAll(periodViolations(draft))
        addAll(stockViolations(draft))
    }

    /**
     * ИНВ-23…ИНВ-25: заполнены ровно параметры своего вида, и вид этот настраиваем
     * (ПР-16…ПР-19).
     *
     * Проверки идут перед временами приёма: блок вида расписания стоит в форме выше,
     * а экран прокручивается к первому нарушению списка — то есть к тому блоку, который
     * человек не дозаполнил.
     *
     * У «по потребности» вида нет вовсе (ПР-7, ПР-15), и правило здесь не судится:
     * неприменимое значение сбрасывает [normalized] до этой проверки.
     */
    private fun scheduleRuleViolations(draft: MedicationDraft): List<DraftViolation> = buildList {
        if (draft.scheduleMode != ScheduleMode.SCHEDULED) return@buildList
        when (val rule = draft.schedule) {
            ScheduleRule.Daily -> Unit
            is ScheduleRule.Weekdays -> if (rule.days.isEmpty()) add(DraftViolation.ScheduleDaysEmpty)

            is ScheduleRule.EveryNDays ->
                if (rule.days < MIN_INTERVAL_DAYS) add(DraftViolation.ScheduleIntervalTooShort)

            is ScheduleRule.Cycles ->
                if (rule.onDays < MIN_CYCLE_DAYS || rule.offDays < MIN_CYCLE_DAYS) {
                    add(DraftViolation.ScheduleCycleLengthNotPositive)
                }

            is ScheduleRule.Staged -> addAll(stageViolations(draft, rule))
        }
    }

    /**
     * ИНВ-23 и ИНВ-24: этапы непусты, каждый этап имеет дозу и длительность, и последний
     * этап кончается не позже конца курса.
     *
     * Схема целиком — требование ко курсу, а не к отдельному этапу: этапы идут подряд
     * от даты начала, поэтому сумма их длительностей и есть требуемая длина курса.
     */
    private fun stageViolations(draft: MedicationDraft, rule: ScheduleRule.Staged): List<DraftViolation> = buildList {
        if (rule.stages.isEmpty()) {
            add(DraftViolation.ScheduleStagesEmpty)
            return@buildList
        }
        rule.stages.forEach { stage ->
            if (stage.doseAmount <= BigDecimal.ZERO) add(DraftViolation.ScheduleStageDoseNotPositive)
            if (stage.days < MIN_STAGE_DAYS) add(DraftViolation.ScheduleStageDaysNotPositive)
        }
        val end = draft.endDate
        val totalDays = rule.stages.sumOf { stage -> maxOf(stage.days, 0).toLong() }
        if (end != null && totalDays > 0) {
            val lastDay = draft.startDate.plusDays(totalDays - 1)
            if (lastDay.isAfter(end)) add(DraftViolation.ScheduleStagesBeyondCourse)
        }
    }

    /** ИНВ-4 и ИНВ-5: доза строго больше нуля, дозировка и её единица — вместе или никак. */
    private fun doseViolations(draft: MedicationDraft): List<DraftViolation> = buildList {
        // У ступенчатого вида дозу несёт этап (ПР-19, ИНВ-24): поле «Доза на один приём»
        // не показывается, и требовать его — заводить отказ по полю, которого человек
        // не видит. О дозе скажет этап (`error_stage_dose`, §10), а доза курса равна
        // дозе первого этапа.
        val doseInStages = draft.scheduleMode == ScheduleMode.SCHEDULED && draft.schedule is ScheduleRule.Staged
        if (!doseInStages) {
            val dose = draft.doseAmount
            if (dose == null || dose <= BigDecimal.ZERO) add(DraftViolation.DoseNotPositive)
        }
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

    private companion object {

        /** ПР-17: «через N дней» начинается с двух; «через каждый день» — это ежедневное. */
        const val MIN_INTERVAL_DAYS = 2

        /** ПР-18: и приём, и перерыв в цикле длятся хотя бы сутки. */
        const val MIN_CYCLE_DAYS = 1

        /** ИНВ-24: этап длится хотя бы сутки. */
        const val MIN_STAGE_DAYS = 1
    }
}
