package ru.stohastika.sklyanka.feature.widgets.receiver

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import ru.stohastika.sklyanka.feature.widgets.di.WidgetEntryPoint
import ru.stohastika.sklyanka.feature.widgets.glance.WeekWidget

/**
 * Провайдер виджета «Неделя» (SKL-118).
 *
 * Свой приёмник, а не общий на три типа: у каждого типа свои параметры в `res/xml`
 * (ступени `SizeMode.Responsive`, ширина полосы дней), и различать их по имени компонента
 * в одном приёмнике значило бы держать вторую таблицу типов.
 */
class WeekWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = WeekWidget()

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
