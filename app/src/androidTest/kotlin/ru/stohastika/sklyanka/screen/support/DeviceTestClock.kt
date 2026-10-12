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

    /**
     * Часы проверок — **один и тот же объект** на весь прогон.
     *
     * `val`, а не `var`: в граф приложения уезжает именно этот экземпляр
     * (`DeviceTestModules.provideTimeSource`), и подмена объекта в `reset()` оставляла бы
     * приложение со старыми часами — поход «архивация и удаление» видел «пятницу, 2 октября»
     * там, где проверка уже перешла на 3-е (SKL-147). Возврат к началу делает тот же
     * объект: перевести его назад — операция значения, а не личности.
     */
    val source: FixedTimeSource = fixed(start)

    val today: LocalDate get() = source.today()

    /** Слот карточки напоминания и время самопроверки: дата и время одного управляемого момента. */
    val scheduledAt: LocalDateTime get() = LocalDateTime.of(today, scheduledTime)

    fun reset() {
        source.advanceTo(start.atZone(zone).toInstant())
    }

    fun advanceTo(local: LocalDateTime) {
        source.advanceTo(local.atZone(zone).toInstant())
    }

    fun advanceBy(duration: Duration) {
        source.advanceTo(source.now().plus(duration))
    }

    private fun fixed(local: LocalDateTime) = FixedTimeSource(local.atZone(zone).toInstant(), zone)
}
