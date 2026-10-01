package ru.stohastika.sklyanka.domain.reminder

import java.time.Duration
import java.time.Instant

/**
 * Показывать ли обычное (первичное) срабатывание, опоздавшее к моменту обработки
 * (ПМ-22, ОВ-109).
 *
 * Порог — та же граница 30 минут, что у восстановленного повтора (ОВ-99): на продукт
 * одна граница «показ ещё уместен / уже нет». Объект отдельный: у повтора отсчёт идёт
 * от момента `fire_at`, здесь — от планового времени приёма, и единство числа
 * зафиксировано тестом, а не общим типом.
 *
 * Порог касается только показа: приём остаётся ожидающимся и отмечаемым до конца
 * суток (ОВ-4), поэтому константа намеренно отдельная и не равна окну `pending → missed`.
 */
object FiredShowPolicy {

    /** Порог опоздания обычного срабатывания: 30 минут (ОВ-109, ОВ-99). */
    val MAX_LATENESS: Duration = Duration.ofMinutes(30)

    /** `now − scheduledAt` не больше порога — показываем. Ровно порог — ещё показываем. */
    fun shouldShow(now: Instant, scheduledAt: Instant): Boolean = Duration.between(scheduledAt, now) <= MAX_LATENESS
}
