package ru.stohastika.sklyanka.domain.schedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

@DisplayName("The generation window covers whole local days ahead")
class GenerationWindowTest {

    @Test
    fun `a seven day window starts today and ends on the seventh date`() {
        val window = GenerationWindow.of(Planning.TODAY, Planning.DEPTH_DAYS)

        assertEquals(Planning.TODAY, window.from)
        assertEquals(Planning.WINDOW_END, window.toInclusive)
        assertEquals(Planning.DEPTH_DAYS, window.days)
        assertEquals(Planning.DEPTH_DAYS, window.dates().size)
    }

    @Test
    fun `the last date of the window is included and the next one is not`() {
        val window = GenerationWindow.of(Planning.TODAY, Planning.DEPTH_DAYS)

        assertTrue(Planning.WINDOW_END.atTime(23, 59) in window)
        assertFalse(Planning.WINDOW_END.plusDays(1).atStartOfDay() in window)
        assertEquals(Planning.WINDOW_END.plusDays(1).atStartOfDay(), window.endExclusive)
    }

    @Test
    fun `the window starts at the local midnight of today`() {
        val window = GenerationWindow.of(Planning.TODAY, Planning.DEPTH_DAYS)

        assertEquals(Planning.TODAY.atStartOfDay(), window.start)
        assertFalse(Planning.TODAY.minusDays(1).atTime(23, 59) in window)
    }

    /**
     * НАП-6 и НАП-7: будильник ставится на уже существующий приём в пределах 48 часов.
     * Окно генерации обязано перекрывать эти 48 часов из любого момента суток — иначе
     * будильник адресует приём, которого ещё нет.
     */
    @Test
    fun `the shallowest allowed window still outlives the forty eight hour alarm window`() {
        val window = GenerationWindow.of(Planning.TODAY, GenerationWindow.MIN_DEPTH_DAYS)
        val latestMomentOfToday = Planning.TODAY.atTime(23, 59)

        val covered = Duration.between(latestMomentOfToday, window.endExclusive)

        assertTrue(covered > Duration.ofHours(48), "window covers only $covered")
    }

    @Test
    fun `a window shallower than the alarm window is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            GenerationWindow.of(Planning.TODAY, GenerationWindow.MIN_DEPTH_DAYS - 1)
        }
    }

    @Test
    fun `a window that ends before it starts is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            GenerationWindow(from = Planning.TODAY, toInclusive = Planning.TODAY.minusDays(1))
        }
    }

    @Test
    fun `a window of one day is a legal shape but not a legal depth`() {
        val singleDay = GenerationWindow(from = Planning.TODAY, toInclusive = Planning.TODAY)

        assertEquals(1, singleDay.days)
        assertEquals(listOf<LocalDate>(Planning.TODAY), singleDay.dates())
    }

    /** ОВ-8: глубина — параметр. Смена значения обязана менять окно, а не ломать планировщик. */
    @Test
    fun `the depth is a parameter and not a constant of the planner`() {
        val fortnight = GenerationWindow.of(Planning.TODAY, FORTNIGHT)

        assertEquals(FORTNIGHT, fortnight.days)
        assertEquals(Planning.TODAY.plusDays((FORTNIGHT - 1).toLong()), fortnight.toInclusive)
    }

    private companion object {
        const val FORTNIGHT = 14
    }
}
