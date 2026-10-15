package ru.stohastika.sklyanka.feature.widgets.receiver

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import ru.stohastika.sklyanka.feature.widgets.di.WidgetEntryPoint
import ru.stohastika.sklyanka.feature.widgets.glance.MedicationWidget

/**
 * Провайдер виджета «По лекарству» (SKL-118).
 *
 * Свой приёмник, а не общий на три типа: привязка к лекарству хранится у отдельного
 * экземпляра виджета, и приёмник, обслуживающий все три типа сразу, пришлось бы различать
 * по имени компонента в каждой операции обновления.
 */
class MedicationWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = MedicationWidget()

    /**
     * Системное обновление плиты.
     *
     * Кроме собственно перерисовки (её делает Glance) приёмник выполняет проход
     * по просроченным приёмам: состояние приёма приходит из домена, и без прохода
     * утренняя плита держала бы вчерашние приёмы ожидающими (ПР-1, ПР-22).
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Граф может отсутствовать: в процессе без приложения продукта (инструментальный
        // прогон тестовой сборки) система всё равно доставляет обновление плиты, и приёмник
        // обязан это пережить, а не уронить процесс (SKL-97).
        val graph = runCatching { WidgetEntryPoint.of(context) }.getOrNull() ?: return
        refreshOnUpdate(context, this, graph)
    }
}
