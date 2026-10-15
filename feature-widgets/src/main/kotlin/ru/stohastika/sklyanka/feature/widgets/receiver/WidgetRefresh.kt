package ru.stohastika.sklyanka.feature.widgets.receiver

import android.content.BroadcastReceiver
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.feature.widgets.di.WidgetEntryPoint
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetUpdates

/**
 * Что делает приёмник виджета, когда платформа просит обновление (SKL-118, `TDD.md §7`).
 *
 * Обновление плиты — не только перерисовка: перед ней выполняется проход по просроченным
 * приёмам (ПР-1). Иначе утренняя плита держала бы вчерашние приёмы ожидающими и предлагала
 * бы отметить их «сейчас» — состояние приёма приходит из домена, и порядок здесь важен.
 *
 * Работа уходит в корутину, а процесс продлевается `goAsync`: приёмнику отведены
 * секунды, а чтение и запись — работа с базой. Плата за это названа: если процесс убьют
 * сразу после `onReceive`, плита останется прошлой, и её поправит следующее событие
 * (открытие приложения, отметка, суточная страховка платформы).
 */
internal fun refreshOnUpdate(context: Context, receiver: BroadcastReceiver, graph: WidgetEntryPoint) {
    val pending = receiver.goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            // Причина — граница суток, а не запуск приложения: этот проход идёт
            // от системы (загрузка, смена пояса, суточное обновление плиты).
            graph.sweepMissed()(SweepReason.TIMER)
            WidgetUpdates.updateAll(context)
        } finally {
            pending.finish()
        }
    }
}
