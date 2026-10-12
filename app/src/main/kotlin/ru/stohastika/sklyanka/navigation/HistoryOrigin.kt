package ru.stohastika.sklyanka.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.feature.history.HistoryRoute
import ru.stohastika.sklyanka.feature.history.HistoryViewModel

/**
 * Откуда открыта история приёмов.
 *
 * Значение, а не предположение. Входов два: `Настройки → Данные → История приёмов`
 * (`docs/norm/04-behavior.md`, «Навигация») и строка прогресса дня на «Сегодня»
 * (ОВ-84, SKL-31), и возврат обязан вести туда же, откуда пришли. В макете
 * `History.dc.html` стрелка «Назад» ведёт на «Сегодня»; спецификация называет это
 * несоответствием, подлежащим устранению, и устраняется оно здесь: место входа
 * хранится, а не угадывается по тому, какой экран был отрисован последним.
 *
 * Второе значение заведено заранее не «на будущее»: без него возврат выражался бы
 * константой, и появление второго входа потребовало бы найти и исправить эту константу —
 * а искать её стали бы после жалобы «вышел из истории и оказался не там».
 */
enum class HistoryOrigin {

    /** Вход из настроек: `Данные → История приёмов` (SKL-20). */
    SETTINGS,

    /** Строка прогресса дня на «Сегодня» (ОВ-84, SKL-31). */
    TODAY,
}

/**
 * Экран истории приёмов внутри навигации приложения (SKL-18).
 *
 * Маршрут собран здесь, а не в экранном модуле: `feature-*` друг от друга не зависят,
 * и знание о том, куда ведёт возврат, принадлежит `app`.
 *
 * Стрелка «Назад» на экране и системная кнопка «Назад» ведут в одно место — [onLeave]
 * с тем же [origin]. Две разные ветки здесь означали бы, что человек, вышедший кнопкой
 * телефона, попадает не туда, куда вышел бы стрелкой; поймать это ревью не может, потому
 * что обе ветки по отдельности выглядят правильными.
 *
 * Звенья обоих входов собраны и проверены: из настроек — SKL-20, со «Сегодня» — SKL-31;
 * в корне приложения (MainActivity.Root) история открывается одной веткой на оба места
 * входа, и решает переход именно [origin].
 */
@Composable
fun HistorySection(
    viewModel: HistoryViewModel,
    origin: HistoryOrigin,
    onLeave: (HistoryOrigin) -> Unit,
    modifier: Modifier = Modifier,
) {
    val leave = { onLeave(origin) }
    BackHandler(enabled = true) { leave() }
    HistoryRoute(viewModel = viewModel, onBack = leave, modifier = modifier)
}
