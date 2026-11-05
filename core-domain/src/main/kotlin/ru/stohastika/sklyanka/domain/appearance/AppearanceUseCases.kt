package ru.stohastika.sklyanka.domain.appearance

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Действующая акцентная тема (§2.5, Р-31).
 *
 * Читается и потоком — экран обязан перекраситься сразу, — и разово, на старте: тема
 * применяется **до первой композиции**, иначе первый кадр мелькнул бы «Оливой» и человек
 * с «Аметистом» увидел бы чужой цвет на своём экране (`TDD.md` §5).
 */
class ObserveAccentThemeUseCase(private val preferences: AppearancePreferences) {

    operator fun invoke(): Flow<AccentTheme> = preferences.accentTheme

    /** Разовое чтение для старта приложения: тема становится известна до первого кадра. */
    suspend fun current(): AccentTheme = preferences.accentTheme.first()
}

/**
 * Действующий вариант иконки (§2.4, Р-31).
 *
 * Тем же швом, что и тема: переключение лончера — следствие выбора, а не отдельное
 * действие человека, и второй путь чтения разошёлся бы с записанным выбором.
 */
class ObserveIconVariantUseCase(private val preferences: AppearancePreferences) {

    operator fun invoke(): Flow<IconVariant> = preferences.iconVariant

    suspend fun current(): IconVariant = preferences.iconVariant.first()
}

/**
 * Выбор акцентной темы (§5.17).
 *
 * Продукт бесплатен, поэтому выбор применяется сразу и сохраняется: границы доступа
 * у оформления больше нет (Р-86). Правило записи осталось доменным, а не переехало
 * в экран, — иначе вторая поверхность, однажды выбравшая тему, завела бы свой путь
 * записи и обошла хранилище.
 */
class SetAccentThemeUseCase(private val preferences: AppearancePreferences) {

    suspend operator fun invoke(theme: AccentTheme) {
        preferences.setAccentTheme(theme)
    }
}

/**
 * Выбор варианта иконки (§5.17) — тем же швом, что и тема.
 *
 * Тем же правилом, а не вторым: оба выбора — свойства оформления устройства (Р-31),
 * и разойтись в том, как они записываются, им нельзя.
 */
class SetIconVariantUseCase(private val preferences: AppearancePreferences) {

    suspend operator fun invoke(variant: IconVariant) {
        preferences.setIconVariant(variant)
    }
}
