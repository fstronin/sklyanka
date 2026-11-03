package ru.stohastika.sklyanka.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import ru.stohastika.sklyanka.domain.analytics.AnalyticsScreen

/**
 * Кто и куда сообщает о показе экрана (SKL-165, TDD §5.4).
 *
 * Значение по умолчанию — пустая лямбда: экран, который никто не измеряет, работает как
 * раньше, и каталог дизайн-системы не тянет за собой ни аналитики, ни `app`. Подставляет
 * настоящий приёмник композиция приложения один раз — тем же приёмом, что
 * `LocalProUiEnabled`.
 */
val LocalScreenTracker = staticCompositionLocalOf<(AnalyticsScreen) -> Unit> { {} }

/**
 * Сообщить о показе экрана ровно один раз за вход.
 *
 * `LaunchedEffect(screen)` привязывает вызов к значению экрана, а не к композиции: повторная
 * композиция того же экрана событий не плодит, а переход на другой экран даёт новое
 * значение и новое событие. Экран знает только строку вида
 * `TrackScreenView(AnalyticsScreen.HISTORY)` — ни согласия, ни очереди, ни провайдера.
 */
@Composable
fun TrackScreenView(screen: AnalyticsScreen) {
    val tracker = LocalScreenTracker.current
    LaunchedEffect(screen) { tracker(screen) }
}
