package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Формат среды измерения (SKL-85).
 *
 * Нагрузка записи журнала и разметка окон A/B-прогона: обратимость обязана держаться
 * на любых значениях, включая разделители внутри них.
 */
class ReminderEnvironmentTest {

    private val environment = ReminderEnvironment(
        alarmKind = AlarmKind.ALARM_CLOCK,
        device = DeviceEnvironment(
            manufacturer = "HUAWEI",
            brand = "HUAWEI",
            osRelease = "12",
            buildDisplay = "HUAWEI Pura 80 Ultra",
            batteryUnrestricted = true,
        ),
    )

    @Test
    fun `encoding keeps the fixed key order`() {
        assertEquals(
            "alarm_kind=alarm_clock;manufacturer=HUAWEI;brand=HUAWEI;os_release=12;" +
                "build_display=HUAWEI Pura 80 Ultra;battery_unrestricted=true",
            environment.encode(),
        )
    }

    @Test
    fun `a payload round trips through parse`() {
        assertEquals(environment, ReminderEnvironment.parse(environment.encode()))
    }

    @Test
    fun `separators and percent inside values survive the round trip`() {
        val tricky = environment.copy(
            device = environment.device.copy(
                manufacturer = "A;B=C%25",
                buildDisplay = "50%=half;yes",
            ),
        )

        assertEquals(tricky, ReminderEnvironment.parse(tricky.encode()))
        assertEquals("A%3BB%3DC%2525", tricky.encode().substringAfter("manufacturer=").substringBefore(';'))
    }

    @Test
    fun `the other mechanism is a valid payload`() {
        val idle = environment.copy(alarmKind = AlarmKind.EXACT_IDLE)

        assertEquals(AlarmKind.EXACT_IDLE, ReminderEnvironment.parse(idle.encode())?.alarmKind)
    }

    @Test
    fun `an empty battery flag does not parse`() {
        val broken = environment.encode().replace("battery_unrestricted=true", "battery_unrestricted=")

        assertNull(ReminderEnvironment.parse(broken))
    }

    @Test
    fun `garbage does not parse as an environment`() {
        listOf(
            "",
            "restored",
            "alarm_clock",
            "alarm_kind=alarm_clock;manufacturer=HUAWEI",
            "alarm_kind=unknown;manufacturer=HUAWEI;brand=HUAWEI;os_release=12;" +
                "build_display=x;battery_unrestricted=true",
            "alarm_kind=alarm_clock;manufacturer=HUAWEI;brand=HUAWEI;os_release=12;" +
                "build_display=x;battery_unrestricted=perhaps",
        ).forEach { value -> assertNull(ReminderEnvironment.parse(value), "must not parse: $value") }
    }

    @Test
    fun `the journal entry carries no medical data by construction`() {
        val encoded = environment.encode()

        // В нагрузке только служебные коды и версии; человеком введённого текста в ней
        // взяться неоткуда (НФТ-19).
        assertEquals(encoded.filter { it.code in 32..126 }, encoded)
    }
}
