package ru.stohastika.sklyanka.domain.reminder

import java.time.Duration
import java.time.Instant

/**
 * Правило восстановления будильников по снимку до разблокировки (SKL-87).
 *
 * Чистый объект без зависимостей: границы проверяются JVM-тестом, а не памятью. Решение
 * целиком (а не частично) отбраковывается только при структурной порче: частичное
 * восстановление из «подозрительного» файла ставит неверные будильники — хуже, чем ноль,
 * потому что после разблокировки полная пересборка всё равно восстановит верные. Записи
 * с прошедшим моментом и испорченным адресом отбрасываются поштучно — это ожидаемые,
 * а не патологические случаи.
 */
object DirectBootReplayPolicy {

    /** Формат снимка, понимаемый этой сборкой. */
    const val FORMAT_VERSION = 1

    /** Потолок числа записей: окно планирования столько не даёт, значит файл испорчен. */
    const val MAX_ALARMS = 256

    /** Снимок старше горизонта планирования не мог сохранить будущих срабатываний. */
    val MAX_AGE: Duration = ReminderWindow.HORIZON

    /**
     * Допустимое расхождение часов вперёд. [Instant], заметно ушедший в будущее, — признак
     * порчи файла или перевода часов назад, а не штатное состояние.
     */
    private val FUTURE_TOLERANCE: Duration = Duration.ofMinutes(5)

    @Suppress("ReturnCount")
    fun decide(snapshot: DirectBootAlarms?, now: Instant): DirectBootReplayDecision {
        if (snapshot == null) return DirectBootReplayDecision.Skip(DirectBootTraceReason.MISSING)
        if (snapshot.version != FORMAT_VERSION) return DirectBootReplayDecision.Skip(DirectBootTraceReason.UNREADABLE)
        if (snapshot.alarms.size > MAX_ALARMS) return DirectBootReplayDecision.Skip(DirectBootTraceReason.UNREADABLE)
        if (Duration.between(snapshot.updatedAt, now) > MAX_AGE) {
            return DirectBootReplayDecision.Skip(DirectBootTraceReason.STALE)
        }
        if (snapshot.updatedAt.isAfter(now.plus(FUTURE_TOLERANCE))) {
            return DirectBootReplayDecision.Skip(DirectBootTraceReason.UNREADABLE)
        }

        val restored = mutableListOf<DirectBootAlarm>()
        var skipped = 0
        snapshot.alarms.forEach { alarm ->
            val broken = alarm.intakeId.isEmpty() ||
                alarm.attempt !in 0..PersistentRepeatPolicy.MAX_REPEATS ||
                !alarm.at.isAfter(now)
            if (broken) skipped++ else restored += alarm
        }
        return DirectBootReplayDecision.Restore(restored, skipped)
    }
}

/** Исход попытки восстановления: восстановить отобранное или отбросить снимок целиком. */
sealed interface DirectBootReplayDecision {
    data class Restore(val alarms: List<DirectBootAlarm>, val skipped: Int) : DirectBootReplayDecision
    data class Skip(val reason: DirectBootTraceReason) : DirectBootReplayDecision
}
