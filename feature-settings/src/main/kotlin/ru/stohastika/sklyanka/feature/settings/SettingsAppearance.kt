package ru.stohastika.sklyanka.feature.settings

import ru.stohastika.sklyanka.domain.appearance.ObserveAccentThemeUseCase
import ru.stohastika.sklyanka.domain.appearance.ObserveIconVariantUseCase
import ru.stohastika.sklyanka.domain.appearance.SetAccentThemeUseCase
import ru.stohastika.sklyanka.domain.appearance.SetIconVariantUseCase
import javax.inject.Inject

/**
 * Оформление настроек одним узлом: чтение и выбор темы и иконки (SKL-120, §5.17).
 *
 * Четыре шва оформления идут вместе и по одной причине: тема и вариант иконки —
 * одна возможность с общим хранилищем выбора. Отдельным типом, а не четырьмя параметрами
 * конструктора, они и живут: у `SettingsViewModel` набирается достаточно других
 * зависимостей, и «сколько у экрана швов оформления» перестаёт быть вопросом о подписи
 * конструктора.
 *
 * Hilt собирает узел сам: все четыре шва уже есть в графе (`AppearanceModule` в `app`
 * даёт чтение и запись, реализация порта — в `core-data`). Отдельного модуля узлу
 * не нужно — `@Inject constructor` и есть его сборка.
 */
class SettingsAppearance @Inject constructor(
    /** Действующая тема потоком: строка обязана называть то, что человек видит (§5.17). */
    val observeTheme: ObserveAccentThemeUseCase,
    val observeIcon: ObserveIconVariantUseCase,
    /**
     * Выбор темы и иконки — через домен, а не записью в хранилище.
     *
     * Сценарии применяют выбор и пишут его: второго пути записи нет, и вторая поверхность,
     * однажды выбравшая тему, не заведёт своего обхода.
     */
    val setTheme: SetAccentThemeUseCase,
    val setIcon: SetIconVariantUseCase,
)
