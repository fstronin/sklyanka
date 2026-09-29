package ru.stohastika.sklyanka.ui.profile

import androidx.compose.runtime.Immutable
import ru.stohastika.sklyanka.ui.theme.ProfileAccent
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.ProfilePalette

/**
 * В чьём профиле собрано это поддерево композиции.
 *
 * Закрытая иерархия, а не пара «имя и акцент»: собрать «чужой акцент без имени» или
 * «имя без акцента» здесь нельзя по типу. Это и есть ПРФ-11 конструкцией — шесть
 * признаков показываются одновременно, и отключить их по отдельности нечем.
 */
@Immutable
sealed interface ProfileChromeState {

    /** Акцент действий и признак «нужна индикация чужого профиля». */
    val accent: ProfileAccent

    /** Имя чужого профиля для надписи и баннера; у владельца отсутствует (ПРФ-16). */
    val foreignName: String?

    /** Профиль владельца: никакой дополнительной индикации (ПРФ-16, ПРФ-26). */
    data object Owner : ProfileChromeState {
        override val accent: ProfileAccent get() = ProfileAccent.Owner
        override val foreignName: String? get() = null
    }

    /** Чужой профиль: имя и цвет из ограниченного набора (ПРФ-15). */
    @Immutable
    data class Foreign(val name: String, val colour: ProfileColor) : ProfileChromeState {
        override val accent: ProfileAccent get() = ProfileAccent.Foreign(colour)
        override val foreignName: String get() = name
    }

    companion object {

        /**
         * Оформление по активному профилю.
         *
         * Принимает примитивы, а не доменную модель: `core-ui` не знает про `Profile` —
         * дизайн-система не должна зависеть от предметной области. Зато и разбирать
         * «свой или чужой» каждому экрану самому не приходится.
         */
        fun of(name: String, colorArgb: Int, isSelf: Boolean): ProfileChromeState = if (isSelf) {
            Owner
        } else {
            Foreign(name = name, colour = ProfilePalette.of(colorArgb))
        }
    }
}
