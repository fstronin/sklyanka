package ru.stohastika.sklyanka.debug

import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import java.time.Duration
import java.time.Instant

/**
 * Протокол измерительного пробника (SKL-89).
 *
 * Строки лога — машинный контракт между debug-хуком и `tools/spike/probe.sh`: ключи
 * и порядок фиксированы, потому что скрипт разбирает их, не зная кода приложения.
 * Тег [TAG] отличает пробы от прочих записей logcat; персистентного журнала у проб нет
 * намеренно — в журнал напоминаний продукта они не пишут, иначе измерительные срабатывания
 * попали бы в арифметику гейта НАП-3.
 *
 * Здесь же собираются строки лога: формат должен проверяться тестом, а не глазами
 * в двух местах одновременно.
 */
internal object ProbeProtocol {

    const val TAG = "SklyankaProbe"

    const val ACTION_SCHEDULE = "ru.stohastika.sklyanka.debug.SCHEDULE_PROBE"
    const val ACTION_SET_STRATEGY = "ru.stohastika.sklyanka.debug.SET_PROBE_STRATEGY"
    const val ACTION_FIRE = "ru.stohastika.sklyanka.debug.PROBE_FIRE"

    const val EXTRA_MINUTES = "probe_minutes"
    const val EXTRA_STRATEGY = "strategy"
    const val EXTRA_INDEX = "probe_index"
    const val EXTRA_SCHEDULED_AT = "scheduled_at"

    /** Больше проб за прогон не ставится; столько же адресов снимается перед постановкой. */
    const val MAX_PROBES = 32

    /** Тестовый канал уведомлений; в продуктовых каналах он не появляется. */
    const val CHANNEL_ID = "sklyanka_probe"

    /** Разбор варианта измерения; незнакомый код отвергается, а не подменяется надёжным. */
    fun parseStrategy(code: String?): AlarmKind? = AlarmKind.entries.firstOrNull { it.code == code }

    /** Плановые моменты проб: смещения в минутах от момента постановки. */
    fun instants(now: Instant, minutes: List<Int>): List<Instant> =
        minutes.map { now.plus(Duration.ofMinutes(it.toLong())) }

    fun scheduledLine(index: Int, at: Instant, kind: AlarmKind, placed: Boolean): String =
        "probe=$index;event=scheduled;scheduled_at=${at.toEpochMilli()};strategy=${kind.code};placed=$placed"

    fun firedLine(index: Int, scheduledAt: Instant, firedAt: Instant, kind: AlarmKind): String {
        val deviation = Duration.between(scheduledAt, firedAt).seconds
        return "probe=$index;event=fired;scheduled_at=${scheduledAt.toEpochMilli()};" +
            "fired_at=${firedAt.toEpochMilli()};deviation_seconds=$deviation;strategy=${kind.code}"
    }

    fun strategySetLine(kind: AlarmKind): String = "strategy_set=${kind.code}"
}
