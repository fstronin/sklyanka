package ru.heuri.sklyanka.domain.schedule

import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.time.TimeSource
import java.time.LocalDateTime

/**
 * Разворачивание расписания лекарства в плановые моменты окна.
 *
 * Чистая функция «правило плюс границы окна → как должно быть». О базе и о уже
 * существующих записях не знает ничего: сравнение плана с фактом — дело
 * [IntakePlanDiffer]. Разделение существенно, потому что только так план проверяется
 * на JVM без базы и без Android (НФТ-46).
 */
class ScheduleExpander(private val time: TimeSource, private val policy: IntakeGenerationPolicy) {

    /**
     * Плановые локальные моменты приёма внутри окна, по возрастанию.
     *
     * Пустой список — законный ответ, а не ошибка: так выглядят курс по потребности,
     * архивированный курс и курс, не пересекающийся с окном.
     */
    fun expand(medication: Medication, window: GenerationWindow): List<LocalDateTime> {
        if (!producesScheduledIntakes(medication)) return emptyList()

        val first = maxOf(medication.startDate, window.from)
        // ПР-6: пустая дата окончания — бессрочный курс, а не неполные данные;
        // ИНВ-15: последний день курса входит в него целиком.
        val last = minOf(medication.endDate ?: window.toInclusive, window.toInclusive)

        val times = medication.times.distinct().sorted()
        val nowLocal = time.nowLocal()
        return generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(last) }
            .flatMap { date -> times.asSequence().map(date::atTime) }
            // Окно начинается сегодняшними сутками, поэтому «раньше сейчас» бывает
            // только у времён текущих суток — отдельная проверка даты не нужна.
            .filterNot { moment -> policy.skipPastTimesToday && moment.isBefore(nowLocal) }
            .toList()
    }

    /**
     * Порождает ли курс приёмы по расписанию вообще.
     *
     * ПР-11: архивированный курс не порождает новых приёмов — ни по расписанию, ни по
     * потребности. ПР-4, ПР-8, НАП-10: запись о приёме по потребности рождается в момент
     * нажатия «Принять», и планировать по ней нечего.
     */
    private fun producesScheduledIntakes(medication: Medication): Boolean =
        !medication.isArchived && medication.scheduleMode == ScheduleMode.SCHEDULED && medication.times.isNotEmpty()
}
