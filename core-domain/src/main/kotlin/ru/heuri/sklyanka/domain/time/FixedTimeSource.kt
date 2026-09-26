package ru.heuri.sklyanka.domain.time

import java.time.Instant
import java.time.ZoneId

/**
 * Управляемый источник времени для тестов.
 *
 * Живёт в основном наборе, а не в тестовом: им пользуются тесты всех модулей,
 * а дублировать подменяемые часы в каждом наборе — верный способ получить
 * пять слегка разных реализаций.
 */
class FixedTimeSource(private var instant: Instant, private var zone: ZoneId = ZoneId.of("UTC")) : TimeSource {

    override fun now(): Instant = instant

    override fun zone(): ZoneId = zone

    /** Перевести часы вперёд — проверка перехода через полночь и смены суток. */
    fun advanceTo(next: Instant) {
        instant = next
    }

    /** Сменить пояс — проверка поведения при перелёте (НФТ-34 … НФТ-37). */
    fun moveTo(next: ZoneId) {
        zone = next
    }
}
