package ru.stohastika.sklyanka.feature.widgets.glance

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.medication.WeekCounter
import ru.stohastika.sklyanka.domain.widget.WidgetWeekSnapshot
import ru.stohastika.sklyanka.feature.widgets.di.WidgetEntryPoint
import ru.stohastika.sklyanka.feature.widgets.open.WidgetPlace
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import java.time.LocalDate
import java.time.ZoneId

/**
 * Виджет «Неделя» (SKL-118, §5.11): семь последних дней и счётчик по правилу ЭКР-6.
 *
 * Ступеней две: полоса и счётчик не растут бесконечно, а добавить к ним нечего —
 * пустой высоты система не объявляет. Полоса — тот же словарь, что у блока «Последние
 * 7 дней» в карточке лекарства: человек, видевший блок в приложении, узнаёт его
 * на домашнем экране.
 */
class WeekWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SklyankaSizes.WidgetWeekCompact, SklyankaSizes.WidgetWeekStandard),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = runCatching { WidgetEntryPoint.of(context) }.getOrNull() ?: return
        val snapshot = graph.snapshots().week()
        val today = graph.time().today()
        val zone = graph.time().zone()
        val theme = graph.accentTheme().current()
        val open = graph.openTarget().intentFor(context, WidgetPlace.HISTORY)
        provideContent { WeekPlate(snapshot, today, zone, theme, open) }
    }
}

@Composable
private fun WeekPlate(
    snapshot: WidgetWeekSnapshot?,
    today: LocalDate,
    zone: ZoneId,
    theme: AccentTheme,
    open: Intent,
) {
    val resources = LocalContext.current.resources
    val profile = snapshot?.profile
    val accent = WidgetLook.accent(profile, theme)
    Plate(profile, accent, actionStartActivity(open)) {
        PlateHeader(
            subject = WidgetText.subject(resources, WidgetType.WEEK),
            profileLabel = profile?.let { WidgetText.profileLabel(resources, it.name) },
            accent = accent,
        )
        val week = snapshot?.takeIf { it.isFresh(today) } ?: return@Plate
        PlateCounter(WidgetText.weekCounter(resources, week.counter))
        // Пустая неделя: полосы нет вовсе — кружок «сегодня» без соседей отвечает
        // на вопрос, которого блок не задаёт (§5.2, V-1). Счётчик при этом уже сказал
        // словами, что приёмов не было.
        if (week.counter is WeekCounter.Fraction) {
            WeekStrip(days = week.days, resources = resources, zone = zone)
        }
    }
}
