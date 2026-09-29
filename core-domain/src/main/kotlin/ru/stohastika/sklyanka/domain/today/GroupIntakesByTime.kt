package ru.stohastika.sklyanka.domain.today

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Группировка приёмов суток по точному времени (`Main.dc.html`).
 *
 * Приёмы одного времени идут одной группой, группы — по возрастанию времени.
 * Метка «Сейчас» ставится последней группе, время которой уже наступило: в 14:05
 * это группа «14:00», даже если утренняя группа тоже позади. Метка производная от
 * момента, а не сохранённое значение — по ходу дня она переезжает сама (`TDD.md §10`).
 */
object GroupIntakesByTime {

    operator fun invoke(items: List<IntakeItem>, now: LocalDateTime): List<TimeGroup> {
        val byTime: Map<LocalTime, List<IntakeItem>> = items
            .sortedWith(compareBy({ it.intake.scheduledAt }, { it.medication.name }))
            .groupBy { it.intake.scheduledAt.toLocalTime() }
        val times = byTime.keys.sorted()
        val nowTime = currentGroupTime(items, times, now)
        return times.map { time ->
            TimeGroup(
                time = time,
                partOfDay = PartOfDay.of(time),
                isNow = time == nowTime,
                items = byTime.getValue(time),
            )
        }
    }

    /**
     * Время группы, которой достаётся метка «Сейчас».
     *
     * Сравнение идёт по полной дате и времени приёма, а не по одному времени суток:
     * список показывает одни сутки, но их дата может быть и не сегодняшней (история,
     * открытая на прошедшем дне), и тогда «наступило» означает «уже прошло целиком».
     */
    private fun currentGroupTime(items: List<IntakeItem>, times: List<LocalTime>, now: LocalDateTime): LocalTime? {
        val date = items.firstOrNull()?.intake?.scheduledAt?.toLocalDate() ?: return null
        return times.lastOrNull { time -> !date.atTime(time).isAfter(now) }
    }
}
