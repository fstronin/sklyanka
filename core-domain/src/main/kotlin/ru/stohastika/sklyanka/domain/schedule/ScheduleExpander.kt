package ru.stohastika.sklyanka.domain.schedule

import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Плановый приём: момент и доза, которую расписание назначило на этот день (ПР-19).
 *
 * Доза здесь потому, что её знает только правило вида: ступенчатое расписание берёт её
 * из этапа, остальные виды оставляют `null` — «доза берётся у лекарства». Разделять
 * «момент» и «дозу» на два списка значило бы сопоставлять их по индексу и однажды
 * сопоставить неверно.
 */
data class PlannedIntake(val scheduledAt: LocalDateTime, val doseAmount: BigDecimal?)

/**
 * Разворачивание расписания лекарства в плановые приёмы окна.
 *
 * Чистая функция «правило плюс границы окна → как должно быть». О базе и о уже
 * существующих записях не знает ничего: сравнение плана с фактом — дело
 * [IntakePlanDiffer]. Разделение существенно, потому что только так план проверяется
 * на JVM без базы и без Android (НФТ-46).
 *
 * Вид расписания отбирает **дни**, времена берутся общие ([Medication.times]) — как
 * у ежедневного курса (ПР-19). Интервал и цикл считаются от даты начала курса, а не от
 * дня заведения записи (ПР-17, ПР-18).
 */
class ScheduleExpander(private val time: TimeSource, private val policy: IntakeGenerationPolicy) {

    /**
     * Плановые приёмы внутри окна, по возрастанию.
     *
     * Пустой список — законный ответ, а не ошибка: так выглядят курс по потребности,
     * архивированный курс, курс, не пересекающийся с окном, и вид, которому в эти сутки
     * приёмов не назначено («циклами» в перерыве, ПР-18).
     */
    fun expand(medication: Medication, window: GenerationWindow): List<PlannedIntake> {
        if (!producesScheduledIntakes(medication)) return emptyList()

        val first = maxOf(medication.startDate, window.from)
        // ПР-6: пустая дата окончания — бессрочный курс, а не неполные данные;
        // ИНВ-15: последний день курса входит в него целиком.
        val last = minOf(medication.endDate ?: window.toInclusive, window.toInclusive)

        val times = medication.times.distinct().sorted()
        val rule = medication.schedule
        val nowLocal = time.nowLocal()
        return generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(last) }
            // Дни отбирает вид расписания; отсчёт — от даты начала курса, а не от начала
            // окна: окно скользит вперёд, а «каждый третий день» привязан к назначению.
            .filter { date -> rule.includes(date, medication.startDate) }
            .flatMap { date ->
                val dose = rule.doseFor(date, medication.startDate)
                times.asSequence().map { time -> PlannedIntake(date.atTime(time), dose) }
            }
            // ИНВ-24: приём внутри курса по построению; прошедшие времена дня начала
            // снимаются тем же правилом, что и у ежедневного курса.
            .filterNot { planned -> bornInThePast(planned.scheduledAt, medication.startDate, nowLocal) }
            .toList()
    }

    /**
     * Приём, которого не было в природе: время дня начала курса, уже прошедшее к моменту,
     * когда курс завели.
     *
     * Человек, заводящий лекарство в 14:30, в 08:00 его не принимал и принять не мог.
     * Только про день начала курса это и верно: в любой другой день прошедшее время —
     * настоящий приём, который человек мог принять и не отметить либо честно пропустить,
     * и снимать его нельзя, иначе перегенерация в середине дня стирала бы сегодняшнюю
     * историю. Решение обратимо настройкой [IntakeGenerationPolicy.skipPastTimesOnCourseStart].
     */
    private fun bornInThePast(moment: LocalDateTime, startDate: LocalDate, nowLocal: LocalDateTime): Boolean =
        policy.skipPastTimesOnCourseStart && moment.toLocalDate() == startDate && moment.isBefore(nowLocal)

    /**
     * Порождает ли курс приёмы по расписанию вообще.
     *
     * ПР-11: архивированный курс не порождает новых приёмов — ни по расписанию, ни по
     * потребности. ПР-4, ПР-8, НАП-10: запись о приёме по потребности рождается в момент
     * нажатия «Принять», и планировать по ней нечего — вид расписания у неё не действует
     * вовсе (ПР-15).
     */
    private fun producesScheduledIntakes(medication: Medication): Boolean = !medication.isArchived &&
        !medication.isDeleted &&
        medication.scheduleMode == ScheduleMode.SCHEDULED &&
        medication.times.isNotEmpty()
}
