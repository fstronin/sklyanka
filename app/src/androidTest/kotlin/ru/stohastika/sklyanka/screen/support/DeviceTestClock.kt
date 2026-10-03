package ru.stohastika.sklyanka.screen.support

import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Часы проверок устройства (SKL-97, TDD §5, §6.4).
 *
 * Момент — литеральная константа, а не системные часы: НФТ-48 запрещает прямое чтение
 * часов и в тестовых исходниках, и стережёт это `GuardTest`. Базовое время — на минуту
 * позже времени посеянного приёма: приём уже наступил, но ещё не считается
 * несостоявшимся (`OutageDetector.GRACE` — две минуты), поэтому баннер перерыва
 * не попадает на кадры.
 */
internal object DeviceTestClock {

    val zone: ZoneId = ZoneId.of("Europe/Moscow")

    /** Время приёма, которое сеют сценарии волны: час до базового момента. */
    val scheduledTime: LocalTime = LocalTime.of(9, 0)

    private val start: LocalDateTime = LocalDateTime.of(2026, 10, 3, 9, 1)

    var source: FixedTimeSource = fixed(start)
        private set

    val today: LocalDate get() = source.today()

    fun reset() {
        source = fixed(start)
    }

    fun advanceTo(local: LocalDateTime) {
        source.advanceTo(local.atZone(zone).toInstant())
    }

    fun advanceBy(duration: Duration) {
        source.advanceTo(source.now().plus(duration))
    }

    private fun fixed(local: LocalDateTime) = FixedTimeSource(local.atZone(zone).toInstant(), zone)
}
