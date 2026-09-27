package ru.heuri.sklyanka.data.room

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.heuri.sklyanka.data.transfer.TransferValues
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * Язык телефона не попадает в хранимые значения (SKL-28, НФТ-19, `ADR-0008`).
 *
 * **Что эта проверка сторожит, а что нет.** Существующего дефекта она не ловит: шаблоны
 * хранения состоят из цифр, а `DateTimeFormatter` берёт `DecimalStyle.STANDARD` независимо
 * от локали — под локалью с деванагари `ofPattern("HH:mm")` печатает латинские цифры.
 * Проверка охраняет свойство на будущее: первое же слово в шаблоне хранения — месяц
 * прописью, «AM/PM» — начнёт зависеть от телефона, и без `Locale.ROOT` это обнаружится
 * на чужом устройстве, то есть не у нас. Сказано прямо, потому что проверка, о которой
 * думают, что она ловит дефект, хуже отсутствующей.
 *
 * Локаль подменяется на время проверки и возвращается обратно: локаль по умолчанию — общая
 * на весь прогон, и оставленная чужой, она сломала бы соседние проверки.
 */
class StorageLocaleTest {

    private lateinit var original: Locale

    @Before
    fun rememberLocale() {
        original = Locale.getDefault()
        // Хинди с деванагари: цифры этой локали — не латинские, и именно это ловится.
        Locale.setDefault(Locale.forLanguageTag("hi-IN-u-nu-deva"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun theTimesOfACourseAreStoredInLatinDigits() {
        assertEquals("08:05,20:00", SklyankaConverters.timesToText(listOf(LocalTime.of(8, 5), LocalTime.of(20, 0))))
    }

    @Test
    fun theMomentOfAnIntakeIsStoredInLatinDigits() {
        assertEquals(
            "2026-09-25T14:00",
            SklyankaConverters.dateTimeToText(LocalDateTime.of(2026, 9, 25, 14, 0)),
        )
    }

    @Test
    fun theDateOfACourseIsStoredInLatinDigits() {
        assertEquals("2026-09-25", SklyankaConverters.dateToText(LocalDate.of(2026, 9, 25)))
    }

    @Test
    fun theStoredTimesAreReadBackUnderAnyLocale() {
        val stored = SklyankaConverters.timesToText(listOf(LocalTime.of(8, 5)))

        assertEquals(listOf(LocalTime.of(8, 5)), SklyankaConverters.textToTimes(stored))
    }

    @Test
    fun theTransferFileCarriesLatinDigits() {
        assertEquals("08:05", LocalTime.of(8, 5).format(TransferValues.TIME))
        assertEquals("2026-09-25", LocalDate.of(2026, 9, 25).format(TransferValues.DATE))
    }
}
