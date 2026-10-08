package ru.stohastika.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

/**
 * Расчёт даты окончания по пресету (ПМ-12, SKL-75).
 *
 * Таблица `Примеры` структуры сценария «Расчёт даты окончания по календарю» из
 * `acceptance/src/main/resources/features/SKL-75.feature`, плюс проверка состава и порядка [CourseEndPreset.Options].
 */
class CourseEndPresetTest {

    @ParameterizedTest(name = "{0} from {1} ends on {2}")
    @CsvSource(
        "WEEK_1,   2026-09-25, 2026-10-02",
        "WEEKS_2,  2026-09-25, 2026-10-09",
        "MONTH_1,  2026-09-25, 2026-10-25",
        "MONTHS_2, 2026-09-25, 2026-11-25",
        "MONTHS_3, 2026-09-25, 2026-12-25",
        "MONTHS_6, 2026-09-25, 2027-03-25",
        "YEAR_1,   2026-09-25, 2027-09-25",
    )
    fun `the end date is counted from the start`(preset: CourseEndPreset, start: LocalDate, end: LocalDate) {
        assertEquals(end, preset.endDateFrom(start))
    }

    @ParameterizedTest(name = "{1} + {0} is clamped to {2}")
    @CsvSource(
        "MONTH_1,  2026-01-31, 2026-02-28",
        "MONTHS_6, 2026-08-31, 2027-02-28",
        "YEAR_1,   2028-02-29, 2029-02-28",
    )
    fun `the calendar month is clamped and never rolls over`(
        preset: CourseEndPreset,
        start: LocalDate,
        end: LocalDate,
    ) {
        assertEquals(end, preset.endDateFrom(start))
    }

    @Test
    fun `the list is the only place the presets are enumerated`() {
        assertEquals(
            listOf(
                CourseEndPreset.WEEK_1,
                CourseEndPreset.WEEKS_2,
                CourseEndPreset.MONTH_1,
                CourseEndPreset.MONTHS_2,
                CourseEndPreset.MONTHS_3,
                CourseEndPreset.MONTHS_6,
                CourseEndPreset.YEAR_1,
            ),
            CourseEndPreset.Options,
        )
    }
}
