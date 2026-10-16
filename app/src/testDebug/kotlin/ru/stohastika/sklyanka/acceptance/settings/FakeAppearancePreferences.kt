package ru.stohastika.sklyanka.acceptance.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.AppearancePreferences
import ru.stohastika.sklyanka.domain.appearance.IconVariant

/**
 * Постоянные настройки оформления в памяти для стендов `app` (SKL-120, §5.17).
 *
 * Заменяет собою носитель значения, а не правило: правило выбора живёт в домене
 * (`AppearanceGate`, `SetAccentThemeUseCase`) и в стенде остаётся доменным. Тот же приём,
 * что у кэша подписки: подменяется место хранения, а не решение.
 *
 * Умолчания — базовые «Олива» (Р-20) и «Ледяная» (Р-56): так же ведёт себя настоящее
 * хранилище, когда записи нет вовсе.
 */
class FakeAppearancePreferences(
    initialTheme: AccentTheme = AccentTheme.DEFAULT,
    initialVariant: IconVariant = IconVariant.DEFAULT,
) : AppearancePreferences {

    private val theme = MutableStateFlow(initialTheme)
    private val variant = MutableStateFlow(initialVariant)

    override val accentTheme: Flow<AccentTheme> = theme
    override val iconVariant: Flow<IconVariant> = variant

    override suspend fun setAccentTheme(theme: AccentTheme) {
        this.theme.value = theme
    }

    override suspend fun setIconVariant(variant: IconVariant) {
        this.variant.value = variant
    }
}
