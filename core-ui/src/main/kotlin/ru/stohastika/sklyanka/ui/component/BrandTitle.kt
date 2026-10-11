package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow

/**
 * Название фирменного блока: одна строка, кегль уменьшается сам (V-16, НФТ-31).
 *
 * **Почему не перенос.** Имя собственное в колонку не переформулируешь, а §10
 * дизайн-системы называет перенос, разрезавший слово, нарушением. Поэтому при крупном
 * системном шрифте (до 200 %, НФТ-31) надпись не переносится и не обрезается: кегль
 * понижается настолько, чтобы слово уместилось в одну строку.
 *
 * **Как считается.** Стиль измеряется тем же плотностным масштабом, в котором будет
 * нарисован ([rememberTextMeasurer]); если измеренная ширина больше доступной, кегль
 * умножается на отношение ширин. Свойство «одна строка» задаётся `maxLines` и
 * `softWrap`, а не размером: измеренная разметка ничего не решает, решает готовый
 * результат. Ограничения ширины нет — уменьшать нечего, и надпись рисуется исходным
 * стилем (`cappedFontScale` здесь не подходит: он ограничивает системный масштаб всего
 * приложения, а не подгоняет строку).
 *
 * @param text готовая строка: компонент не читает ресурсы и не знает домена (НФТ-42).
 */
@Composable
fun BrandTitle(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val available = constraints.maxWidth
        val measured = measurer.measure(text = text, style = style, maxLines = ONE_LINE, softWrap = false)
        val fitted = if (constraints.hasBoundedWidth && available > 0 && measured.size.width > available) {
            style.fontSize * (available.toFloat() / measured.size.width)
        } else {
            style.fontSize
        }
        Text(
            text = text,
            style = style.copy(fontSize = fitted),
            color = color,
            maxLines = ONE_LINE,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Одна строка: переносом слово не разрезается (§10), автоуменьшением — тоже. */
private const val ONE_LINE = 1
