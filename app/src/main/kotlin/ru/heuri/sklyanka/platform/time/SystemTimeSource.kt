package ru.heuri.sklyanka.platform.time

import ru.heuri.sklyanka.domain.time.TimeSource
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единственное место в проекте, где системные часы читаются напрямую.
 *
 * Правило DirectSystemClockAccess разрешает это только здесь (НФТ-48): всё остальное
 * получает время через [TimeSource] и потому проверяемо тестом с подменёнными часами.
 */
@Singleton
class SystemTimeSource @Inject constructor() : TimeSource {

    override fun now(): Instant = Instant.now()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
