package ru.heuri.sklyanka.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ru.heuri.sklyanka.ui.token.ProfileColor
import ru.heuri.sklyanka.ui.token.ProfilePalette

/**
 * В чьём профиле собран экран.
 *
 * Тема получает это значение от активного профиля (SKL-15) и решает, каким окажется
 * акцент и появятся ли признаки чужого профиля. Экран в этом решении не участвует:
 * он читает роль из темы и не знает, чья она (ПРФ-12).
 */
@Immutable
sealed interface ProfileAccent {

    /** Акцентный цвет действий в этом профиле. */
    val color: Color

    /** Нужны ли признаки чужого профиля: полоса, подкрашенная шапка, подпись. */
    val isForeign: Boolean

    /**
     * Профиль владельца.
     *
     * Никакой дополнительной индикации ему не добавляется: её отсутствие само по
     * себе сигнал «ты у себя» (ПРФ-16, критерий 2).
     */
    data object Owner : ProfileAccent {
        override val color: Color get() = ProfilePalette.ownerAccent
        override val isForeign: Boolean get() = false
    }

    /**
     * Чужой профиль.
     *
     * Цвет приходит перечислением, а не значением: произвольный цвет мог бы
     * совпасть с основным акцентом и снять защиту (ПРФ-15, критерий 3).
     */
    @Immutable
    data class Foreign(val profileColor: ProfileColor) : ProfileAccent {
        override val color: Color get() = profileColor.color
        override val isForeign: Boolean get() = true
    }
}
