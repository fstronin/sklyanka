package ru.stohastika.sklyanka.feature.widgets.glance

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.widget.WidgetDaySnapshot
import ru.stohastika.sklyanka.feature.widgets.di.WidgetEntryPoint
import ru.stohastika.sklyanka.feature.widgets.open.WidgetPlace
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import java.time.LocalDate

/**
 * Виджет «Сегодня» (SKL-118, §5.11): приёмы дня активного профиля и быстрая отметка.
 *
 * Плита отзывчива: объявленный набор ступеней — компакт, обычный, просторный. Другой
 * ширины и высоты у неё не бывает: хост сводит плиту к ближайшей ступени, а число строк
 * решает ступень, а не свободная растяжка.
 *
 * Снимок читается разово, а не подпиской: виджет перерисовывается по событиям (отметка,
 * полночь, смена профиля, правка лекарства), и фоновой работы без человека перед экраном
 * у него нет (П-6, `TDD.md §7`).
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SklyankaSizes.WidgetCompact, SklyankaSizes.WidgetStandard, SklyankaSizes.WidgetRoomy),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Граф может отсутствовать: в процессе без приложения продукта система всё равно
        // доставляет обновление плиты, и виджет обязан это пережить (SKL-97).
        val graph = runCatching { WidgetEntryPoint.of(context) }.getOrNull() ?: return
        val snapshot = graph.snapshots().day()
        val today = graph.time().today()
        val theme = graph.accentTheme().current()
        val open = graph.openTarget().intentFor(context, WidgetPlace.TODAY)
        provideContent {
            TodayPlate(snapshot = snapshot, today = today, theme = theme, open = open)
        }
    }
}

/**
 * Содержимое «Сегодня»: объявленный состав дня и его тихие состояния (§5.11).
 *
 * Правило отрисовки живёт в [DayPlate]: здесь — только то, чем день «Сегодня» отличается
 * от «по лекарству»: слова тихих состояний профиля и предмет в шапке.
 */
@Composable
private fun TodayPlate(snapshot: WidgetDaySnapshot?, today: LocalDate, theme: AccentTheme, open: Intent) {
    val resources = LocalContext.current.resources
    val profile = snapshot?.profile
    val accent = WidgetLook.accent(profile, theme)
    val items = snapshot?.items.orEmpty()
    DayPlate(
        state = DayPlateState(
            subject = WidgetText.subject(resources, WidgetType.TODAY),
            profileLabel = profile?.let { WidgetText.profileLabel(resources, it.name) },
            profile = profile,
            date = snapshot?.date ?: today,
            today = today,
            items = items,
            taken = items.count { it.taken },
            planned = items.size,
            showCounter = plateShowsCounter(),
        ),
        accent = accent,
        open = open,
        quiet = {
            PlateQuietLine(
                if (snapshot?.activeMedications == 0) {
                    WidgetText.noMedications(resources)
                } else {
                    WidgetText.dayEmpty(resources)
                },
            )
        },
    )
}
