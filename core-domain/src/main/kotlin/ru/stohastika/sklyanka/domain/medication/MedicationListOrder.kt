package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.time.LocalDateTime

/**
 * Порядок списка лекарств «что ближе — то выше» (ПМ-15 п. 1).
 *
 * Правило живёт в домене, а не в разметке: ближайший приём считается по расписанию
 * (`times`, `startDate`, `endDate`) того же вида, что у [ru.stohastika.sklyanka.domain.schedule.ScheduleExpander],
 * и проверяется тестом на JVM без базы и без Android. Порядок — не украшение списка,
 * а лечебный сценарий: сверху обязано стоять лекарство, которое нужно принять раньше.
 *
 * Архив и «Сегодня» правило не трогает: у архива свой порядок (по дате архивации),
 * а «Сегодня» — хронология приёмов как есть.
 */
object MedicationListOrder {

    /**
     * Ближайший следующий плановый приём в локальном времени профиля.
     *
     * `null` — расписания нет (ПРН), курс архивирован, времён нет либо приёмов больше
     * не будет (курс окончился). Прошедшее сегодня время переносится на завтра; если
     * курс начинается в будущем, ближайший приём — в его первый день.
     */
    fun nextIntakeAt(medication: Medication, now: LocalDateTime): LocalDateTime? {
        // Курс не порождает приёмов раньше начала (ScheduleExpander.kt:28).
        val firstDay = maxOf(now.toLocalDate(), medication.startDate)
        val end = medication.endDate
        // ПР-6: пустая дата окончания — бессрочный курс; ИНВ-15: последний день входит целиком.
        val hasNextDay = end == null || !firstDay.isAfter(end)
        val producesIntakes = !medication.isArchived &&
            medication.scheduleMode == ScheduleMode.SCHEDULED &&
            medication.times.isNotEmpty()
        if (!producesIntakes || !hasNextDay) return null

        val times = medication.times.distinct().sorted()
        // Двух суток достаточно: времена приёма лежат внутри суток, поэтому ближайшее
        // будущее время — сегодня или завтра.
        return sequence {
            for (offset in 0L..1L) {
                val day = firstDay.plusDays(offset)
                if (end != null && day.isAfter(end)) break
                for (time in times) {
                    val moment = day.atTime(time)
                    // Время ровно `now` остаётся ближайшим: приём назначен на текущую минуту.
                    if (!moment.isBefore(now)) yield(moment)
                }
            }
        }.minOrNull()
    }

    /**
     * Активные лекарства в порядке «что ближе — то выше» (ПМ-15 п. 1).
     *
     * Ключ — `(группа, ближайший приём, название)`. Группа 0 — лекарства по расписанию
     * с ближайшим приёмом, растут по времени; группа 1 — «по потребности» и лекарства
     * без ближайшего приёма, растут по названию. Имя — тай-брейк внутри групп, сравнение
     * код-поинтное (как `ORDER BY name` в SQLite); регистр не нормализуется.
     */
    fun sorted(medications: List<Medication>, now: LocalDateTime): List<Medication> = medications.sortedWith(
        compareBy<Medication> { nextIntakeAt(it, now) == null }
            .thenBy { nextIntakeAt(it, now) }
            .thenBy { it.name },
    )
}
