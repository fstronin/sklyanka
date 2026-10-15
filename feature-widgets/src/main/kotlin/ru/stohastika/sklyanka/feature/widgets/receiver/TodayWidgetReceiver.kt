package ru.stohastika.sklyanka.feature.widgets.receiver

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import ru.stohastika.sklyanka.feature.widgets.di.WidgetEntryPoint
import ru.stohastika.sklyanka.feature.widgets.glance.TodayWidget

/**
 * Провайдер виджета «Сегодня» (SKL-118).
 *
 * Отдельный класс, а не общий приёмник на три типа: `android:configure` и `meta-data`
 * с параметрами типа живут у каждого провайдера свои (размеры и ступени `SizeMode.Responsive`),
 * и общий приёмник пришлось бы различать по имени компонента.
 *
 * `exported="false"` в манифесте: обновление приходит от платформы и от приложения,
 * снаружи — некому.
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

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
