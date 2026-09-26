package ru.heuri.sklyanka.ui.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import ru.heuri.sklyanka.ui.token.SklyankaSizes

/**
 * Доводит измеряемую область элемента до [size], не меняя нарисованного размера.
 *
 * Правило системы (§3.3, НФТ-29): визуальный размер может быть меньше 44 dp,
 * область нажатия — нет. Макеты нарушают его в четырёх местах — крестик «удалить
 * время» 34×34, стрелки перелистывания месяца 32×32, кнопки высотой 36 и 38, — и
 * «переверстать покрупнее» значит сломать плотность, ради которой они такие.
 *
 * `minimumInteractiveComponentSize()` из Material 3 не годится: он про 48 dp, а
 * система задаёт 44, и расхождение в четыре точки на плотной вёрстке видно.
 *
 * Порядок модификаторов важен. Обработчик нажатия ставится **перед** этим
 * модификатором:
 *
 * ```
 * Modifier
 *     .clickable(onClick = onClick)   // получает расширенную область
 *     .minTouchTarget()
 *     .size(SklyankaSizes.MarkButtonGlyph)  // нарисованный размер
 * ```
 *
 * Обратный порядок оставит нажатие на нарисованном размере, и модификатор будет
 * выглядеть работающим, ничего не делая.
 *
 * Расширенная область может перекрыть соседа. Конфликт разрешается в пользу цели
 * нажатия, а не плотности вёрстки (ОВ-17).
 */
fun Modifier.minTouchTarget(size: Dp = SklyankaSizes.MinTouchTarget): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val minimum = size.roundToPx()
        val width = maxOf(placeable.width, minimum)
        val height = maxOf(placeable.height, minimum)
        layout(width, height) {
            // Содержимое центрируется: расширение области не должно сдвигать рисунок.
            placeable.place(
                x = (width - placeable.width) / 2,
                y = (height - placeable.height) / 2,
            )
        }
    }
