package ru.stohastika.sklyanka.feature.widgets.glance

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.unit.ColorProvider
import ru.stohastika.sklyanka.domain.widget.WidgetIntake
import ru.stohastika.sklyanka.domain.widget.WidgetProfileIndication
import ru.stohastika.sklyanka.feature.widgets.receiver.MarkIntakeAction
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import java.time.LocalDate

/**
 * Плита дня — содержимое «Сегодня» и «по лекарству» (SKL-118, §5.11).
 *
 * Отдельной сборкой, а не внутри класса виджета: правило «снимок чужих суток не рисуется»
 * обязано проверяться без устройства. Устаревший снимок даёт тихое состояние — заголовок
 * без приёмов, — и это проверяет JVM-стенд разметки, а не только домен (П-7).
 *
 * @param takenTaken число принятых и плановых приёмов для счётчика
 * @param counterWords счётчик словами: у «Сегодня» и «по лекарству» он свой
 */
/** Что рисует плита дня: снимок и его тихие состояния (§5.11). */
internal data class DayPlateState(
    val subject: String,
    val profileLabel: String?,
    val profile: WidgetProfileIndication?,
    val date: LocalDate,
    val today: LocalDate,
    val items: List<WidgetIntake>,
    val taken: Int,
    val planned: Int,
    val showCounter: Boolean,
)

@Composable
internal fun DayPlate(state: DayPlateState, accent: ColorProvider, open: Intent, quiet: @Composable () -> Unit) {
    Plate(profile = state.profile, accent = accent, onClick = actionStartActivity(open)) {
        PlateHeader(subject = state.subject, profileLabel = state.profileLabel, accent = accent)
        // Снимок чужих суток не рисуется: до перерисовки плита показывает тихое состояние,
        // а не прошлый день как сегодняшний (П-7).
        if (state.date != state.today) return@Plate
        when {
            state.planned > 0 && state.taken == state.planned -> {
                if (state.showCounter) {
                    PlateCounter(WidgetText.counter(LocalContext.current.resources, state.taken, state.planned))
                }
                PlateQuietLine(WidgetText.dayDone(LocalContext.current.resources))
            }

            state.planned == 0 -> quiet()
            else -> {
                if (state.showCounter) {
                    PlateCounter(WidgetText.counter(LocalContext.current.resources, state.taken, state.planned))
                }
                IntakeRows(
                    items = state.items,
                    accent = accent,
                    resources = LocalContext.current.resources,
                    mark = MarkIntakeAction::action,
                )
            }
        }
    }
}

/** Счётчик и число строк решает ступень плиты, а не свободная растяжка (§5.11). */
@Composable
internal fun plateShowsCounter(): Boolean = LocalSize.current.height >= SklyankaSizes.WidgetStandard.height
