package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/**
 * Внешность кнопки одним значением: фон, граница, форма, кегль и поле подписи.
 *
 * Собирается на месте вызова из темы и формы; оболочка получает один параметр вместо
 * шести и не растёт с каждым новым видом кнопки.
 */
internal data class ButtonSkin(
    val background: Color?,
    val content: Color,
    val border: Color?,
    val shape: RoundedCornerShape,
    val labelStyle: TextStyle,
    val labelPadding: Dp,
)
