package ru.stohastika.sklyanka.domain.reminder

import java.time.Duration
import java.time.Instant

/**
 * Показывать ли восстановленное отложенное срабатывание, опоздавшее к моменту
 * пересборки (НАП-41, ОВ-99).
 *
 * Порог касается только показа: приём остаётся ожидающимся и отмечаемым до конца
 * суток (ОВ-4), поэтому константа намеренно отдельная и не равна окну
 * `pending → missed`. Отдельным объектом, а не константой внутри use-case: правило
 * проверяется чистым JVM-тестом на границе, а смена решения меняет одно место
 * (по образцу [PersistentRepeatPolicy]).
 */
object DeferredShowPolicy {

    /** Порог опоздания восстановленного повтора: 30 минут (ОВ-99). */
    val MAX_LATENESS: Duration = Duration.ofMinutes(30)

    /**
     * `now − fireAt` не больше порога — показываем.
     *
     * Ровно порог — ещё показываем; отрицательная длительность (будущий [fireAt])
     * порога не превышает — запланированный на будущее повтор восстанавливается как прежде.
     */
    fun shouldShow(now: Instant, fireAt: Instant): Boolean = Duration.between(fireAt, now) <= MAX_LATENESS
}
