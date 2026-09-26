package ru.heuri.sklyanka.ui.token

import androidx.compose.ui.graphics.Color

/**
 * Цвет профиля. Набор закрыт (ПРФ-15, критерий 3).
 *
 * Перечисление, а не список цветов, и тем более не произвольный `Color`: цвет
 * профиля — это защита от отметки чужого приёма как своего, и произвольный ввод
 * позволил бы назначить профилю цвет, неотличимый от основного акцента. Тогда
 * индикация формально есть, а человек её не видит.
 *
 * Основной акцент `#B4512F` в набор не входит по той же причине.
 *
 * Состав взят из макетов и дизайном не утверждён (ОВ-10). При ответе меняется одно
 * место — это перечисление.
 */
enum class ProfileColor(val color: Color) {

    /** Цвет чужого профиля из `design/SPEC-original.md` §9. */
    Terracotta(Palette.ProfileTerracotta),

    Teal(Palette.ProfileTeal),

    Olive(Palette.ProfileOlive),

    Plum(Palette.ProfilePlum),
}

/** Набор цветов профиля как целое: выбор в настройках и опора тестов. */
object ProfilePalette {

    /** Порядок здесь — порядок выбора в настройках профиля. */
    val all: List<ProfileColor> = ProfileColor.entries.toList()

    /** Основной акцент приложения. В набор не входит; нужен тестам и теме владельца. */
    val ownerAccent: Color = Palette.Accent
}
