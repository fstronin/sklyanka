package ru.stohastika.sklyanka.pro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import ru.stohastika.sklyanka.domain.pro.ProPoint
import ru.stohastika.sklyanka.ui.pro.LocalProUiEnabled
import ru.stohastika.sklyanka.ui.pro.ProContour
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Витрина «Про» в бесплатной сборке — её нет (Р-86, `ADR-0016` п. 1).
 *
 * [enabled] называет правду сразу, а [Content] не рисует ни шторки, ни затемнения:
 * открыватель из точки-повода — пустая функция, потому что платных границ в `free`
 * не осталось. `onClosed` не вызывается ни разу: доступа, который покупка могла бы
 * изменить, здесь нет.
 *
 * Флаг интерфейса берётся у [ProContour], а не задаётся литералом: у экрана настройки
 * виджета тот же порт спрашивают напрямую, и два источника значения разошлись бы.
 */
@Singleton
class FreeProHost @Inject constructor(private val contour: ProContour) : ProHost {

    override val enabled: Boolean = false

    @Composable
    override fun Content(
        onPolicy: () -> Unit,
        onClosed: () -> Unit,
        content: @Composable (openPro: (ProPoint) -> Unit) -> Unit,
    ) {
        CompositionLocalProvider(LocalProUiEnabled provides contour.uiEnabled) {
            content { }
        }
    }
}
