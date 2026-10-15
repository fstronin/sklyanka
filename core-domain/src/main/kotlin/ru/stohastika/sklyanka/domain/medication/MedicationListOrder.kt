package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import java.time.LocalDate
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

        // Дней просмотра хватает на один полный период вида: если сегодня приём есть, но его
        // время уже прошло, следующий приём — не раньше чем через период. У курса «по средам»
        // это и значит, что список не назовёт ближайшим приём другого дня.
        val horizon = minOf(firstDay.plusDays(medication.schedule.periodDays()), end ?: LocalDate.MAX)
        return firstMoment(firstDay, horizon, medication, now)
    }

    /** Первый приём вида на отрезке дней, не раньше [now]; `null` — приёма на нём нет. */
    private fun firstMoment(
        from: LocalDate,
        horizon: LocalDate,
        medication: Medication,
        now: LocalDateTime,
    ): LocalDateTime? {
        val rule = medication.schedule
        val times = medication.times.distinct().sorted()
        var day = from
        while (!day.isAfter(horizon)) {
            if (rule.includes(day, medication.startDate)) {
                val moment = times.map { time -> day.atTime(time) }.firstOrNull { it >= now }
                if (moment != null) return moment
            }
            day = day.plusDays(1)
        }
        return null
    }

    /**
     * Длина периода вида в сутках: сколько дней достаточно посмотреть, чтобы найти
     * ближайший приём. День недели повторяется раз в неделю, интервал — раз в N дней,
     * цикл — раз в N + M; ежедневное и ступенчатое порождают приём каждый день.
     */
    private fun ScheduleRule.periodDays(): Long = when (this) {
        ScheduleRule.Daily -> 1L
        is ScheduleRule.Weekdays -> DAYS_IN_WEEK
        is ScheduleRule.EveryNDays -> maxOf(days.toLong(), 1L)
        is ScheduleRule.Cycles -> maxOf(onDays.toLong() + offDays.toLong(), 1L)
        is ScheduleRule.Staged -> 1L
    }

    /** Неделя — период вида «по дням недели»: дальше искать приём нечего. */
    private const val DAYS_IN_WEEK = 7L

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
