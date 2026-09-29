package ru.stohastika.sklyanka.data.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.model.MedicationForm
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Конвертеры хранения.
 *
 * Проверяются границы, на которых ломается формат: полночь, 23:59 и 29 февраля —
 * то есть ровно те даты, на которых ошибка формата проявляется у человека, а не в тесте.
 */
class SklyankaConvertersTest {

    @Test
    fun decimalSurvivesTheRoundTrip() {
        val value = BigDecimal("0.5")

        assertEquals(value, SklyankaConverters.textToDecimal(SklyankaConverters.decimalToText(value)))
    }

    @Test
    fun decimalIsStoredInPlainForm() {
        val value = BigDecimal("0.0000001")

        assertEquals("0.0000001", SklyankaConverters.decimalToText(value))
    }

    @Test
    fun nullDecimalStaysNull() {
        assertNull(SklyankaConverters.decimalToText(null))
        assertNull(SklyankaConverters.textToDecimal(null))
    }

    @Test
    fun midnightSurvivesTheRoundTrip() {
        val midnight = LocalDateTime.of(LEAP_DAY, LocalTime.MIDNIGHT)

        val text = SklyankaConverters.dateTimeToText(midnight)

        assertEquals("2028-02-29T00:00", text)
        assertEquals(midnight, SklyankaConverters.textToDateTime(text))
    }

    @Test
    fun lastMinuteOfTheDaySurvivesTheRoundTrip() {
        val lastMinute = LocalDateTime.of(LEAP_DAY, LocalTime.of(LAST_HOUR, LAST_MINUTE))

        val text = SklyankaConverters.dateTimeToText(lastMinute)

        assertEquals("2028-02-29T23:59", text)
        assertEquals(lastMinute, SklyankaConverters.textToDateTime(text))
    }

    @Test
    fun leapDaySurvivesTheRoundTrip() {
        val text = SklyankaConverters.dateToText(LEAP_DAY)

        assertEquals("2028-02-29", text)
        assertEquals(LEAP_DAY, SklyankaConverters.textToDate(text))
    }

    @Test
    fun localTextOrdersLexicographicallyAsItOrdersChronologically() {
        val earlier = SklyankaConverters.dateTimeToText(MORNING_OF_THE_DAY)
        val later = SklyankaConverters.dateTimeToText(LATE_EVENING_OF_THE_DAY)
        val nextDay = SklyankaConverters.dateTimeToText(MIDNIGHT_OF_THE_NEXT_DAY)

        assertTrue(earlier < later)
        assertTrue(later < nextDay)
    }

    @Test
    fun timesAreStoredSortedAndReadBackAsAList() {
        val evening = LocalTime.of(LAST_HOUR, 0)
        val morning = LocalTime.of(MORNING_HOUR, 0)

        val text = SklyankaConverters.timesToText(listOf(evening, morning))

        assertEquals("08:00,23:00", text)
        assertEquals(listOf(morning, evening), SklyankaConverters.textToTimes(text))
    }

    @Test
    fun anEmptyTimesStringMeansAnEmptyList() {
        assertEquals(emptyList<LocalTime>(), SklyankaConverters.textToTimes(""))
    }

    @Test
    fun instantIsStoredAsEpochMillis() {
        val moment = Instant.parse("2026-09-25T05:05:00Z")

        assertEquals(moment.toEpochMilli(), SklyankaConverters.instantToMillis(moment))
        assertEquals(moment, SklyankaConverters.millisToInstant(moment.toEpochMilli()))
    }

    @Test
    fun aKnownEnumIsReadByName() {
        assertEquals(MedicationForm.CAPSULE, SklyankaConverters.textToEnum<MedicationForm>("CAPSULE"))
    }

    @Test
    fun anUnknownEnumValueDoesNotThrow() {
        assertNull(SklyankaConverters.textToEnum<MedicationForm>("POWDER"))
    }

    @Test
    fun aBrokenDateTimeIsReportedAsNullRatherThanThrown() {
        assertNull(SklyankaConverters.textToDateTimeOrNull("not a date"))
    }

    private companion object {
        val LEAP_DAY: LocalDate = LocalDate.of(2028, 2, 29)

        /** Сутки с поздним приёмом: он обязан остаться в своём дне, а не уехать в соседний. */
        val MORNING_OF_THE_DAY: LocalDateTime = LocalDateTime.of(2026, 9, 25, 8, 0)
        val LATE_EVENING_OF_THE_DAY: LocalDateTime = LocalDateTime.of(2026, 9, 25, 23, 30)
        val MIDNIGHT_OF_THE_NEXT_DAY: LocalDateTime = LocalDateTime.of(2026, 9, 26, 0, 0)
        const val LAST_HOUR = 23
        const val LAST_MINUTE = 59
        const val MORNING_HOUR = 8
    }
}
