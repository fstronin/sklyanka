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
 * одна возможность «Про» (Р-31), у них общий гейт (`AppearanceGate`) и общее хранилище
 * выбора. Отдельным типом, а не четырьмя параметрами конструктора, они и живут:
 * у `SettingsViewModel` набирается достаточно других зависимостей, и «сколько у экрана
 * швов оформления» перестаёт быть вопросом о подписи конструктора.
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
     * Граница «Про» (Р-22, Р-31) живёт в самих сценариях: без доступа платный выбор
     * не применяется, и запись не меняется. Экран не решает этого сам — иначе вторая
     * поверхность, однажды выбравшая тему, завела бы свой путь записи и обошла границу.
     */
    val setTheme: SetAccentThemeUseCase,
    val setIcon: SetIconVariantUseCase,
)
