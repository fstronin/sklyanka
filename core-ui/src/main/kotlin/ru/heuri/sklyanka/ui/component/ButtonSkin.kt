package ru.heuri.sklyanka.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color

/** Внешность кнопки одним значением: иначе оболочка разрастается до девяти параметров. */
internal data class ButtonSkin(
    val background: Color?,
    val content: Color,
    val border: Color?,
    val shape: RoundedCornerShape,
)
