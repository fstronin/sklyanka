package ru.stohastika.sklyanka.feature.widgets.glance

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.widget.WidgetMedicationSnapshot
import ru.stohastika.sklyanka.feature.widgets.di.WidgetEntryPoint
import ru.stohastika.sklyanka.feature.widgets.open.WidgetPlace
import ru.stohastika.sklyanka.feature.widgets.receiver.MarkIntakeAction
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import java.time.LocalDate

/**
 * Виджет «По лекарству» (SKL-118, §5.11): приёмы одного лекарства активного профиля.
 *
 * Какое лекарство показывать — выбор человека, сделанный при установке и хранимый
 * привязкой экземпляра ([ru.stohastika.sklyanka.feature.widgets.bindings.WidgetBindingStore]).
 * Шапка называет лекарство: тип виджета называют два других места — строка экрана настройки
 * и системный список.
 *
 * Ступени те же, что у «Сегодня»: содержимое одинаковое — строки приёма.
 */
class MedicationWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SklyankaSizes.WidgetCompact, SklyankaSizes.WidgetStandard, SklyankaSizes.WidgetRoomy),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = runCatching { WidgetEntryPoint.of(context) }.getOrNull() ?: return
        // Идентификатор плиты у платформы берётся менеджером, а не приведением типа:
        // `AppWidgetId` — внутренний тип Glance, и жить он должен внутри неё.
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val medicationId = graph.bindings().medicationId(widgetId)
        val snapshot = medicationId?.let { graph.snapshots().medication(it) }
        // Удалённое лекарство не показывается нигде (ПР-14): плита остаётся тихой, а привязка
        // забывается — держать её больше не за чем. Чужой профиль сюда не попадает: там
        // привязка жива, и потерять её значило бы потерять выбор человека.
        if (medicationId != null && snapshot == null && graph.snapshots().medicationGone(medicationId)) {
            graph.bindings().forget(widgetId)
        }
        val today = graph.time().today()
        val theme = graph.accentTheme().current()
        val open = graph.openTarget().intentFor(context, WidgetPlace.MEDICATION, medicationId)
        provideContent { MedicationPlate(snapshot, today, theme, open) }
    }
}

@Composable
private fun MedicationPlate(snapshot: WidgetMedicationSnapshot?, today: LocalDate, theme: AccentTheme, open: Intent) {
    val resources = LocalContext.current.resources
    val profile = snapshot?.profile
    val accent = WidgetLook.accent(profile, theme)
    Plate(profile, accent, actionStartActivity(open)) {
        PlateHeader(
            subject = snapshot?.name ?: WidgetText.subject(resources, WidgetType.MEDICATION),
            profileLabel = profile?.let { WidgetText.profileLabel(resources, it.name) },
            accent = accent,
        )
        val medication = snapshot?.takeIf { it.isFresh(today) } ?: return@Plate
        val taken = medication.items.count { it.taken }
        val planned = medication.items.size
        val withCounter = LocalSize.current.height >= SklyankaSizes.WidgetStandard.height
        when {
            planned > 0 && taken == planned -> {
                if (withCounter) PlateCounter(WidgetText.counter(resources, taken, planned))
                PlateQuietLine(WidgetText.dayDone(resources))
            }

            planned == 0 -> PlateQuietLine(WidgetText.dayEmpty(resources))

            else -> {
                if (withCounter) PlateCounter(WidgetText.counter(resources, taken, planned))
                IntakeRows(
                    items = medication.items,
                    accent = accent,
                    resources = resources,
                    mark = MarkIntakeAction::action,
                )
            }
        }
    }
}
