package ru.stohastika.sklyanka.platform.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import ru.stohastika.sklyanka.BuildConfig

/**
 * Режим измерения как свойство артефакта (SKL-85, ПМ-20).
 *
 * Обычная и релизная сборки Gradle-опцию не передают: переключателя варианта в них нет,
 * а флаг совпадает с `BuildConfig` — то есть с тем, что реально приехало в артефакт.
 */
class BuildMeasurementModeTest {

    @Test
    fun `the flag answers exactly what the build carries`() {
        assertEquals(BuildConfig.REMINDER_MEASUREMENT, BuildMeasurementMode().available())
    }

    @Test
    fun `the default test build has the measurement mode off`() {
        assertFalse(
            "the default build must not carry the pilot measurement switch",
            BuildMeasurementMode().available(),
        )
    }
}
