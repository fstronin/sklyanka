package ru.stohastika.sklyanka.domain.history

import ru.stohastika.sklyanka.domain.medication.CourseOutcome
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * Расчёт приверженности за период (`TDD.md §5.1`, `§9.1`).
 *
 * Считает запросом, а не хранимыми агрегатами: месяц — порядка 300 записей при году
 * в 3 650 (НФТ-24), и запас по НФТ-25 (300 мс) этого достаточно, зато инвалидировать
 * нечего (`TDD.md §4`).
 *
 * Правила счёта, которые стоит знать, читая экран:
 *
 * - знаменатель лекарства — **все** его приёмы периода, включая сегодняшние; числитель —
 *   только отмеченные принятыми. Это правило SKL-10 («12 из 14» при шести полных днях
 *   по два приёма), и второго ответа на тот же вопрос в продукте быть не должно;
 * - «пропусков за период» считает и отметку человека, и автоматический перевод по
 *   истечении окна (ПР-1): для картины врача приём не состоялся в обоих случаях;
 * - «дней без пропусков» считает только завершившиеся сутки (ЭКР-6);
 * - приёмы, назначенные **после** архивации курса, из картины выпадают целиком —
 *   и из числителя, и из знаменателя, и из календаря (ОВ-44, решение согласовано
 *   с SKL-11: `OutcomeDenominator.UP_TO_ARCHIVING`).
 *
 * Порог низкой приверженности — параметр, а не константа по месту: «выделить лекарство
 * с низкой приверженностью» (`docs/norm/04-behavior.md`) числом не задано нигде.
 */
class AdherenceCalculator(
    /**
     * Правило «вовремя» (Р-21). Обязательный параметр, а не значение по умолчанию:
     * пока правило приходило с умолчанием «не согласовано», доля не считалась вовсе,
     * и продукт показывал прочерк (V-2). Теперь его подставляет привязка
     * (`app/di/HistoryModule.kt`), а расчёт часов не знает.
     */
    private val onTimeRule: OnTimeRule.Agreed,
    /**
     * Ниже этой доли строка лекарства выделяется акцентом.
     *
     * Значение выведено из макета `design/screens/History.dc.html`: 71 % нарисован
     * акцентом, 91 % и 96 % — цветом «принято». Порог обязан лежать между ними;
     * 0,8 — середина промежутка. Требует подтверждения дизайна.
     */
    private val lowAdherenceBelow: Double = LOW_ADHERENCE_BELOW,
) {

    fun of(slice: HistorySlice, window: HistoryWindow, today: LocalDate, zone: ZoneId): AdherencePeriod {
        val medications = slice.medications.associateBy { it.id }
        val counted = countedIntakes(slice, zone)
        val byDate = counted.groupBy { it.scheduledAt.toLocalDate() }

        val days = window.dates.map { date ->
            DayOutcome(
                date = date,
                state = DayOutcomeRule.state(
                    intakesOfDay = byDate[date].orEmpty(),
                    date = date,
                    today = today,
                    historyStartsAt = slice.historyStartsAt,
                ),
                isToday = date == today,
            )
        }

        return AdherencePeriod(
            profileId = slice.profileId,
            from = window.from,
            to = window.to,
            days = days,
            byMedication = breakdown(counted, medications),
            daysWithoutMisses = days.count { it.state == DayState.ALL_TAKEN },
            missesInPeriod = counted.count { it.isMiss },
            onTimeShare = onTimeShare(counted, medications, zone),
            missesByTime = counted.filter { it.isMiss }
                .groupingBy { it.scheduledAt.toLocalTime() }
                .eachCount(),
            scheduledTimes = counted.mapTo(linkedSetOf()) { it.scheduledAt.toLocalTime() },
        )
    }

    /**
     * Строки блока «По лекарствам».
     *
     * Порядок — по доле принятого снизу вверх? Нет: по названию, как в макете. Сортировка
     * по доле переставляла бы строки при каждой отметке, и человек терял бы место, на
     * которое смотрел.
     */
    private fun breakdown(intakes: List<Intake>, medications: Map<String, Medication>): List<MedicationAdherence> =
        intakes.groupBy { it.medicationId }
            .mapNotNull { (medicationId, ofMedication) ->
                val medication = medications[medicationId] ?: return@mapNotNull null
                val taken = ofMedication.count { it.status == IntakeStatus.TAKEN }
                MedicationAdherence(
                    medicationId = medicationId,
                    name = medication.name,
                    taken = taken,
                    planned = ofMedication.size,
                    misses = ofMedication.count { it.isMiss },
                    archived = medication.isArchived,
                    low = taken.toDouble() / ofMedication.size < lowAdherenceBelow,
                    markedByOtherProfile = ofMedication.any { intake ->
                        intake.actorProfileId != null && intake.actorProfileId != medication.profileId
                    },
                )
            }
            .sortedBy { it.name }

    /**
     * Доля принятого вовремя — или признак, что мерить нечего ([OnTimeShare.Absent], Р-33).
     *
     * Знаменатель — **наступившие плановые** приёмы периода (Р-27): приём, который человеку
     * ещё предстоит, не бывает ни принятым вовремя, ни принятым с опозданием, а приём
     * «по потребности» не с чем сравнивать — назначенного времени у него нет. Исключаются
     * оба целиком: ни в числитель, ни в знаменатель.
     */
    private fun onTimeShare(intakes: List<Intake>, medications: Map<String, Medication>, zone: ZoneId): OnTimeShare {
        val due = intakes.filter { intake ->
            intake.status != IntakeStatus.PENDING &&
                medications[intake.medicationId]?.scheduleMode == ScheduleMode.SCHEDULED
        }
        return if (due.isEmpty()) {
            OnTimeShare.Absent
        } else {
            OnTimeShare.Value(onTime = due.count { isOnTime(it, onTimeRule, zone) }, due = due.size)
        }
    }

    /**
     * Приём отмечен в своё окно.
     *
     * Момент отметки хранится в UTC и переводится в местное время ровно здесь: это
     * единственное место расчёта, где нужен не календарный день, а сравнение моментов.
     */
    private fun isOnTime(intake: Intake, rule: OnTimeRule.Agreed, zone: ZoneId): Boolean {
        val actedAt = intake.actedAt?.atZone(zone)?.toLocalDateTime()
        return intake.status == IntakeStatus.TAKEN && actedAt != null && rule.isOnTime(intake.scheduledAt, actedAt)
    }

    private companion object {
        const val LOW_ADHERENCE_BELOW = 0.8
    }
}

/**
 * Приёмы среза, вошедшие в картину периода.
 *
 * Правило одно на весь продукт: им считается и приверженность, и состав отчёта, и выгрузка
 * истории (SKL-119). Второго его изложения быть не может — разойдясь, они показали бы врачу
 * один период, а выгрузили другой.
 *
 * Запись, чьё лекарство выборка не принесла, отбрасывается: это либо лекарство другого
 * профиля, либо строка с непонятым значением перечисления (МГ-6) — и в обоих случаях
 * приписывать её профилю нельзя.
 */
internal fun countedIntakes(slice: HistorySlice, zone: ZoneId): List<Intake> {
    val medications = slice.medications.associateBy { it.id }
    return slice.intakes.filter { intake -> isCountedInPeriod(intake, medications[intake.medicationId], zone) }
}

/**
 * Приём участвует в картине периода.
 *
 * Граница по назначенному времени, а не по моменту отметки: знаменатель — это то, что
 * приложение обещало человеку (ОВ-44, тот же выбор, что у `CourseOutcome`).
 */
internal fun isCountedInPeriod(intake: Intake, medication: Medication?, zone: ZoneId): Boolean = when {
    medication == null -> false
    !medication.isArchived -> true
    else -> !intake.scheduledAt.toLocalDate().isAfter(CourseOutcome.archivedDate(medication, zone))
}

/**
 * Пропуск — и отметка человека, и автоматический перевод по истечении окна (ПР-1).
 *
 * Расширение [Intake], а не поле модели: это правило истории, а не свойство записи,
 * и в других задачах «пропущено» встречается в других смыслах.
 */
internal val Intake.isMiss: Boolean
    get() = status == IntakeStatus.SKIPPED || status == IntakeStatus.MISSED
