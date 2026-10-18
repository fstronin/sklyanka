package ru.stohastika.sklyanka.ui.modifier

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role

/**
 * Нажимаемая поверхность со скруглением: индикация нажатия обрезается её формой.
 *
 * Без обрезки рябь Material 3 рисуется по прямоугольнику габарита и выходит за
 * скругление тёмными углами — ровно тот дефект, который уже был у чипа (SKL-101,
 * дефект 3). Порядок один: сначала `clip(shape)`, затем `clickable`; обратный
 * оставил бы рябь снаружи.
 *
 * Форму принимает вызывающий, а не выводит из фона: у поверхности без заливки
 * (тихая строка, пунктирная кнопка) скругление всё равно есть, и индикация обязана
 * ему следовать. У кнопок с собственной подложкой нажатия (`indication = null`)
 * модификатор не нужен.
 */
fun Modifier.clickableShape(shape: Shape, enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier =
    clip(shape).clickable(enabled = enabled, role = role, onClick = onClick)
