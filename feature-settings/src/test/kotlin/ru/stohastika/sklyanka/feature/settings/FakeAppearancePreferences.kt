package ru.stohastika.sklyanka.feature.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.AppearancePreferences
import ru.stohastika.sklyanka.domain.appearance.IconVariant

/**
 * Постоянные настройки оформления в памяти — для проверок экрана и стенда (SKL-120).
 *
 * Заменяет собою хранилище, а не правило: правило выбора («платное — только с доступом»)
 * живёт в `AppearanceGate` и `SetAccentThemeUseCase`, и эти проверки идут через него,
 * как продукт. Подмена ровно одна — носитель значения, как `FakeProCache` у подписки.
 *
 * Значения по умолчанию — базовые: «Олива» (Р-20) и «Ледяная» (Р-56). Так же ведёт себя
 * настоящее хранилище, когда записи нет вовсе.
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

    /** Что записано прямо сейчас: проверке нужен носитель, а не поток. */
    val storedTheme: AccentTheme get() = theme.value

    val storedVariant: IconVariant get() = variant.value
}
