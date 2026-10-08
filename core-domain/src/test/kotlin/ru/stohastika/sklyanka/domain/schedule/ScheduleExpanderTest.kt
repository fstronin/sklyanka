package ru.stohastika.sklyanka.domain.schedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Разворачивание расписания в плановые моменты.
 *
 * Числа взяты из `acceptance/src/main/resources/features/SKL-06.feature`: «сейчас 25 сентября 2026, 07:00»,
 * окно — семь суток, то есть 25 сентября … 1 октября.
 */
@DisplayName("A schedule expands into the planned moments of the window")
class ScheduleExpanderTest {

    private val clock: FixedTimeSource = Planning.clock()
    private val expander = ScheduleExpander(clock, Planning.POLICY)

    private fun expand(
        times: List<LocalTime> = listOf(Planning.MORNING),
        startDate: LocalDate = Planning.TODAY,
        endDate: LocalDate? = null,
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        archivedAt: java.time.Instant? = null,
        deletedAt: java.time.Instant? = null,
    ): List<LocalDateTime> = expander.expand(
        Planning.medication(
            times = times,
            startDate = startDate,
            endDate = endDate,
            mode = mode,
            archivedAt = archivedAt,
            deletedAt = deletedAt,
        ),
        Planning.window(clock.today()),
    )

    // --- Happy path: числа сценариев приёмки -------------------------------------------

    @Test
    fun `three intakes a day over a seven day course give twenty one moments`() {
        val moments = expand(
            times = listOf(Planning.MORNING, Planning.NOON, Planning.EVENING),
            endDate = Planning.WINDOW_END,
        )

        assertEquals(21, moments.size)
        assertEquals(Planning.DEPTH_DAYS, moments.map { it.toLocalDate() }.distinct().size)
        moments.groupBy { it.toLocalDate() }.forEach { (date, ofDate) ->
            assertEquals(3, ofDate.size, "date $date")
            assertEquals(listOf(Planning.MORNING, Planning.NOON, Planning.EVENING), ofDate.map { it.toLocalTime() })
        }
    }

    /**
     * Числа `Примеров` из `acceptance.feature`.
     *
     * Курс начат раньше текущих суток — ровно как в сценарии: правило дня начала курса
     * тогда не вмешивается, и строка «06:00, 12:00, 18:00, 23:30» честно даёт 28, хотя
     * время 06:00 текущих суток по часам уже прошло.
     */
    @ParameterizedTest(name = "{0} over a course until {1} gives {2} moments")
    @CsvSource(
        "09:00,2026-10-01,7",
        "08:00|20:00,2026-10-01,14",
        "08:00|14:00|20:00,2026-10-01,21",
        "08:00|20:00,2026-09-27,6",
        "08:00|14:00|20:00,2026-09-25,3",
        "06:00|12:00|18:00|23:30,2026-10-01,28",
    )
    fun `the number of moments follows the schedule and the course end`(times: String, end: String, expected: Int) {
        val moments = expand(
            times = times.split("|").map(LocalTime::parse),
            startDate = Planning.TODAY.minusDays(5),
            endDate = LocalDate.parse(end),
        )

        assertEquals(expected, moments.size)
    }

    /** То же расписание, но курс начинается сегодня: утренние 06:00 задним числом не порождаются. */
    @Test
    fun `the same schedule started today loses the times that the course never had`() {
        val moments = expand(
            times = listOf(LocalTime.of(6, 0), NOON_SHARP, LocalTime.of(18, 0), LocalTime.of(23, 30)),
            startDate = Planning.TODAY,
            endDate = Planning.WINDOW_END,
        )

        assertEquals(27, moments.size)
        assertEquals(3, moments.count { it.toLocalDate() == Planning.TODAY })
    }

    // --- Окно вперёд, а не весь курс ---------------------------------------------------

    @Test
    fun `an open ended course is expanded to the window and not further`() {
        val moments = expand(times = listOf(Planning.MORNING, Planning.EVENING), endDate = null)

        assertEquals(14, moments.size)
        assertEquals(Planning.WINDOW_END.atTime(Planning.EVENING), moments.last())
        assertTrue(moments.none { it.toLocalDate() > Planning.WINDOW_END })
    }

    @Test
    fun `a six month course is not expanded beyond the window`() {
        val moments = expand(times = listOf(LocalTime.of(9, 0)), endDate = LocalDate.of(2027, 3, 25))

        assertEquals(7, moments.size)
        assertTrue(moments.none { it.toLocalDate() > Planning.WINDOW_END })
    }

    @Test
    fun `the window moves forward with the clock`() {
        clock.setLocal(Planning.TODAY.plusDays(1).atTime(3, 0))

        val moments = expand(times = listOf(LocalTime.of(9, 0)))

        assertEquals(7, moments.size)
        assertEquals(Planning.TODAY.plusDays(1).atTime(9, 0), moments.first())
        assertEquals(Planning.WINDOW_END.plusDays(1).atTime(9, 0), moments.last())
    }

    /** Перевод часов назад сужает окно, но за пределы курса не выводит: раньше начала приёмов нет. */
    @Test
    fun `turning the clock back never produces a moment before the course start`() {
        clock.setLocal(Planning.TODAY.minusDays(1).atTime(7, 0))

        val moments = expand(times = listOf(LocalTime.of(9, 0)))

        assertEquals(6, moments.size)
        assertEquals(Planning.TODAY.atTime(9, 0), moments.first())
    }

    // --- Границы курса -----------------------------------------------------------------

    @Test
    fun `a course of a single day gives exactly the times of that day`() {
        val moments = expand(
            times = listOf(Planning.MORNING, Planning.NOON, Planning.EVENING),
            endDate = Planning.TODAY,
        )

        assertEquals(3, moments.size)
        assertTrue(moments.all { it.toLocalDate() == Planning.TODAY })
    }

    @Test
    fun `the last day of the course is included whole`() {
        val moments = expand(times = listOf(Planning.EVENING), endDate = Planning.TODAY.plusDays(2))

        assertEquals(Planning.TODAY.plusDays(2).atTime(Planning.EVENING), moments.last())
    }

    @Test
    fun `a course starting in the future gives nothing before its start`() {
        val start = Planning.TODAY.plusDays(3)

        val moments = expand(
            times = listOf(Planning.MORNING, Planning.EVENING),
            startDate = start,
            endDate = Planning.TODAY.plusDays(5),
        )

        assertEquals(6, moments.size)
        assertEquals(start.atTime(Planning.MORNING), moments.first())
        assertTrue(moments.none { it.toLocalDate() < start })
    }

    @Test
    fun `a course that ended yesterday gives nothing`() {
        val moments = expand(
            times = listOf(LocalTime.of(9, 0)),
            startDate = Planning.TODAY.minusDays(5),
            endDate = Planning.TODAY.minusDays(1),
        )

        assertTrue(moments.isEmpty())
    }

    /** ИНВ-15: каждый порождённый момент лежит внутри курса. */
    @Test
    fun `every produced moment stays inside the course`() {
        val start = Planning.TODAY.plusDays(1)
        val end = Planning.TODAY.plusDays(4)

        val moments = expand(times = listOf(Planning.MORNING, Planning.EVENING), startDate = start, endDate = end)

        assertTrue(moments.all { it.toLocalDate() >= start && it.toLocalDate() <= end })
    }

    // --- Режимы и архив ----------------------------------------------------------------

    @Test
    fun `a prn medication produces no scheduled moments`() {
        val moments = expand(mode = ScheduleMode.PRN)

        assertTrue(moments.isEmpty())
    }

    @Test
    fun `an archived medication produces no moments`() {
        val moments = expand(archivedAt = Planning.TODAY.minusDays(1).atTime(10, 0).atZone(Planning.ZONE).toInstant())

        assertTrue(moments.isEmpty())
    }

    @Test
    fun `an archived prn medication produces no moments either`() {
        val moments = expand(
            mode = ScheduleMode.PRN,
            archivedAt = Planning.TODAY.atTime(10, 0).atZone(Planning.ZONE).toInstant(),
        )

        assertTrue(moments.isEmpty())
    }

    @Test
    fun `a deleted medication produces no moments`() {
        // ПР-14: удалённое лекарство новых приёмов не порождает — ни по расписанию, ни «по потребности».
        val scheduled = expand(deletedAt = Planning.TODAY.minusDays(1).atTime(10, 0).atZone(Planning.ZONE).toInstant())
        assertTrue(scheduled.isEmpty())

        val prn = expand(
            mode = ScheduleMode.PRN,
            deletedAt = Planning.TODAY.atTime(10, 0).atZone(Planning.ZONE).toInstant(),
        )
        assertTrue(prn.isEmpty())
    }

    // --- День начала курса и времена, которые уже прошли --------------------------------

    @Test
    fun `times of the course start day that already passed are not produced`() {
        clock.setLocal(Planning.TODAY.atTime(14, 30))

        val moments = expand(times = listOf(Planning.MORNING, Planning.NOON, Planning.EVENING))

        val ofToday = moments.filter { it.toLocalDate() == Planning.TODAY }
        assertEquals(listOf(Planning.TODAY.atTime(Planning.EVENING)), ofToday)
        assertEquals(3, moments.count { it.toLocalDate() == Planning.TODAY.plusDays(1) })
    }

    /**
     * Обычный день курса — не день его начала: прошедший приём человек мог принять
     * и не отметить либо честно пропустить, и снимать его нельзя.
     */
    @Test
    fun `a passed time of an ordinary day of the course is still produced`() {
        clock.setLocal(Planning.TODAY.atTime(14, 30))

        val moments = expand(
            times = listOf(Planning.MORNING, Planning.NOON, Planning.EVENING),
            startDate = Planning.TODAY.minusDays(5),
        )

        assertEquals(
            listOf(Planning.MORNING, Planning.NOON, Planning.EVENING),
            moments.filter { it.toLocalDate() == Planning.TODAY }.map { it.toLocalTime() },
        )
    }

    /** Перегенерация в середине дня не стирает сегодняшнюю историю курса, начатого раньше. */
    @Test
    fun `regenerating in the middle of the day keeps the whole day of a running course`() {
        clock.setLocal(Planning.TODAY.atTime(23, 0))

        val moments = expand(
            times = listOf(Planning.MORNING, Planning.NOON, Planning.EVENING),
            startDate = Planning.TODAY.minusDays(1),
        )

        assertEquals(3, moments.count { it.toLocalDate() == Planning.TODAY })
    }

    @Test
    fun `a time exactly equal to now is still produced on the course start day`() {
        clock.setLocal(Planning.TODAY.atTime(Planning.MORNING))

        val moments = expand(times = listOf(Planning.MORNING))

        assertEquals(Planning.TODAY.atTime(Planning.MORNING), moments.first())
    }

    /**
     * Противоположное решение открытого вопроса «день начала курса» (`task.md`).
     * Оно параметр, а не развилка в коде: заказчик меняет значение, а не планировщик.
     */
    @Test
    fun `keeping past times of the course start day is a policy switch and not a rewrite`() {
        clock.setLocal(Planning.TODAY.atTime(14, 30))
        val keeping = ScheduleExpander(clock, Planning.POLICY.copy(skipPastTimesOnCourseStart = false))

        val moments = keeping.expand(
            Planning.medication(times = listOf(Planning.MORNING, Planning.NOON, Planning.EVENING)),
            Planning.window(clock.today()),
        )

        assertEquals(21, moments.size)
        assertEquals(3, moments.count { it.toLocalDate() == Planning.TODAY })
    }

    // --- Полночь, пояс, перевод часов ---------------------------------------------------

    @Test
    fun `intakes on both sides of midnight belong to different dates`() {
        val moments = expand(
            times = listOf(LocalTime.of(23, 55), LocalTime.of(0, 5)),
            startDate = Planning.TODAY.minusDays(1),
            endDate = Planning.TODAY.plusDays(1),
        )

        assertEquals(4, moments.size)
        assertEquals(
            listOf(LocalTime.of(0, 5), LocalTime.of(23, 55)),
            moments.filter { it.toLocalDate() == Planning.TODAY }.map { it.toLocalTime() },
        )
        assertEquals(
            listOf(LocalTime.of(0, 5), LocalTime.of(23, 55)),
            moments.filter { it.toLocalDate() == Planning.TODAY.plusDays(1) }.map { it.toLocalTime() },
        )
    }

    @Test
    fun `a late evening intake never slides into the next day`() {
        val moments = expand(times = listOf(LocalTime.of(23, 30)), endDate = Planning.TODAY)

        assertEquals(listOf(Planning.TODAY.atTime(23, 30)), moments)
    }

    @Test
    fun `flying east changes neither the composition nor the times of the plan`() {
        clock.setLocal(Planning.TODAY.atTime(12, 0))
        val before = expand(times = listOf(Planning.MORNING, Planning.EVENING), endDate = Planning.WINDOW_END)

        clock.moveTo(EAST_OF_MOSCOW)
        val after = expand(times = listOf(Planning.MORNING, Planning.EVENING), endDate = Planning.WINDOW_END)

        assertEquals(before, after)
        assertTrue(after.all { it.toLocalTime() == Planning.MORNING || it.toLocalTime() == Planning.EVENING })
    }

    @Test
    fun `flying west changes neither the composition nor the times of the plan`() {
        clock.setLocal(Planning.TODAY.atTime(12, 0))
        val before = expand(times = listOf(Planning.MORNING, Planning.EVENING), endDate = Planning.WINDOW_END)

        clock.moveTo(WEST_OF_MOSCOW)
        val after = expand(times = listOf(Planning.MORNING, Planning.EVENING), endDate = Planning.WINDOW_END)

        assertEquals(before, after)
    }

    // --- Расписание как правило ---------------------------------------------------------

    @Test
    fun `times are produced in order regardless of how they were entered`() {
        val moments = expand(times = listOf(Planning.EVENING, Planning.MORNING), endDate = Planning.TODAY)

        assertEquals(listOf(Planning.MORNING, Planning.EVENING), moments.map { it.toLocalTime() })
    }

    @Test
    fun `a schedule without times produces nothing`() {
        val moments = expand(times = emptyList())

        assertTrue(moments.isEmpty())
    }

    private companion object {
        val NOON_SHARP: LocalTime = LocalTime.of(12, 0)

        /** Пояс на три часа восточнее Москвы и на три часа западнее — перелёт из сценариев @timezone. */
        val EAST_OF_MOSCOW: ZoneId = ZoneId.of("+06:00")
        val WEST_OF_MOSCOW: ZoneId = ZoneId.of("+00:00")
    }
}
