package ru.stohastika.sklyanka.debug

import android.app.PendingIntent
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.platform.reminder.AlarmManagerGateway
import java.time.Instant

/** Куда встала проба: индекс, плановый момент, механизм и поставлена ли она. */
internal data class ProbePlacement(val index: Int, val at: Instant, val kind: AlarmKind, val placed: Boolean)

/**
 * Постановка проб тем же вызовом, что и продуктовые будильники приёмов (SKL-89).
 *
 * Проба не знает, как ставить будильник: она приносит только адрес срабатывания.
 * Механизм выбирает [AlarmManagerGateway] — тот же объект и тот же вызов `placeExact`,
 * которым живёт продукт. Дубль ветвления здесь разошёлся бы с продуктом при первой же
 * правке механизма и мерил бы сам себя.
 *
 * Механизм для подписи пробы читается один раз перед постановкой: смена варианта
 * в середине прогона протоколом не предусмотрена — окна измерения не пересекаются.
 */
internal object ProbePlan {

    suspend fun place(
        gateway: AlarmManagerGateway,
        now: Instant,
        minutes: List<Int>,
        pendingFor: (Int, Instant, AlarmKind) -> PendingIntent,
    ): List<ProbePlacement> {
        val kind = gateway.alarmKind()
        return ProbeProtocol.instants(now, minutes).mapIndexed { index, at ->
            val placed = gateway.placeExact(at, pendingFor(index, at, kind))
            ProbePlacement(index = index, at = at, kind = kind, placed = placed)
        }
    }
}
