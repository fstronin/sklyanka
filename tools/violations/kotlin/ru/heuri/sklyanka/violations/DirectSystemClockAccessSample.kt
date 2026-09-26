package ru.heuri.sklyanka.violations

// Нарушает НФТ-48: часы читаются в обход подменяемого TimeSource.
// Правило — DirectSystemClockAccess.

object DirectSystemClockAccessSample {
    fun startedAt() = System.currentTimeMillis()

    fun today() = LocalDate.now()

    fun moment() = Instant.now()

    fun zone() = ZoneId.systemDefault()
}
