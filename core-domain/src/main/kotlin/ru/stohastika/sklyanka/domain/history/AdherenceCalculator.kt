package ru.stohastika.sklyanka.domain.history

import ru.stohastika.sklyanka.domain.medication.CourseOutcome
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
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
 * с низкой приверженностью» (`docs/v2/10-behavior.md`) числом не задано нигде.
 */
class AdherenceCalculator(
    private val onTimeRule: OnTimeRule = OnTimeRule.NotAgreed,
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
        val counted = slice.intakes.filter { intake -> isCounted(intake, medications[intake.medicationId], zone) }
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
            onTimeShare = onTimeShare(counted, zone),
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
     * Доля принятого вовремя — или отказ её считать (ОВ-4, [OnTimeShare.Undefined]).
     */
    private fun onTimeShare(intakes: List<Intake>, zone: ZoneId): OnTimeShare {
        val rule = onTimeRule as? OnTimeRule.Agreed
        // Знаменатель — приёмы, у которых исход уже есть: приём, который человеку ещё
        // предстоит, не бывает ни принятым вовремя, ни принятым с опозданием.
        val decided = intakes.filter { it.status != IntakeStatus.PENDING }
        return if (rule == null || decided.isEmpty()) {
            OnTimeShare.Undefined
        } else {
            OnTimeShare.Value(decided.count { isOnTime(it, rule, zone) }.toDouble() / decided.size)
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

    /**
     * Приём участвует в картине месяца.
     *
     * Запись, чьё лекарство выборка не принесла, отбрасывается: это либо лекарство
     * другого профиля, либо строка с непонятым значением перечисления (МГ-6) — и в обоих
     * случаях приписывать её профилю нельзя.
     */
    private fun isCounted(intake: Intake, medication: Medication?, zone: ZoneId): Boolean = when {
        medication == null -> false
        !medication.isArchived -> true
        // Граница по назначенному времени, а не по моменту отметки: знаменатель — это то,
        // что приложение обещало человеку (ОВ-44, тот же выбор, что у CourseOutcome).
        else -> !intake.scheduledAt.toLocalDate().isAfter(CourseOutcome.archivedDate(medication, zone))
    }

    private companion object {
        const val LOW_ADHERENCE_BELOW = 0.8
    }
}

/**
 * Пропуск — и отметка человека, и автоматический перевод по истечении окна (ПР-1).
 *
 * Расширение [Intake], а не поле модели: это правило истории, а не свойство записи,
 * и в других задачах «пропущено» встречается в других смыслах.
 */
internal val Intake.isMiss: Boolean
    get() = status == IntakeStatus.SKIPPED || status == IntakeStatus.MISSED
