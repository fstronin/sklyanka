package ru.stohastika.sklyanka.ui.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ru.stohastika.sklyanka.domain.appearance.AccentTheme

/**
 * Краски акцентной темы — три значения на тему (§2.5, Р-31).
 *
 * Домен знает **выбор** ([AccentTheme]), интерфейс — краски: это разделение и есть причина,
 * по которой значения живут здесь, а не в перечислении. Остальные роли семьи
 * (`accentBorder`, `onAccentSoft`, подложка тёмной среды) выводятся правилом из этих трёх,
 * поэтому четвёртой константы на тему нет: она разошлась бы с правилом при первой правке.
 *
 * Значения — токены [Palette], а не литералы: цвет по месту запрещён и правилом
 * `HardcodedDesignToken`, и Правилом 1 нормы.
 */
@Immutable
data class AccentPalette(
    /** Акцент светлой среды: основная кнопка, активная вкладка, прогресс, метка «Сейчас». */
    val lightAccent: Color,
    /** Мягкая подложка светлой среды: выбранный чип, плашка иконки, баннер остатка. */
    val lightSoft: Color,
    /**
     * Акцент тёмной среды: тот же смысл, но светлее.
     *
     * Светлая светлота не переносится: `#5F6F2E` на фоне тёмной среды даёт 3,25 и ролью
     * текста быть не может (§6.1). В тёмной среде акцент служит и заливкой кнопки, и цветом
     * текста, поэтому у каждой темы он назван отдельно, а не выведен из светлого «на глаз».
     */
    val darkAccent: Color,
)

/** Краски темы по её имени: четыре набора, по три значения (§2.5). */
internal val AccentTheme.palette: AccentPalette
    get() = when (this) {
        AccentTheme.OLIVE -> AccentPalette(Palette.Accent, Palette.AccentSoft, Palette.AccentOnDark)
        AccentTheme.INDIGO -> AccentPalette(Palette.AccentIndigo, Palette.AccentSoftIndigo, Palette.AccentOnDarkIndigo)
        AccentTheme.PINE -> AccentPalette(Palette.AccentPine, Palette.AccentSoftPine, Palette.AccentOnDarkPine)
        AccentTheme.AMETHYST ->
            AccentPalette(Palette.AccentAmethyst, Palette.AccentSoftAmethyst, Palette.AccentOnDarkAmethyst)
    }
