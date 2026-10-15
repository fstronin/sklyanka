package ru.stohastika.sklyanka.feature.widgets.glance

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.glance.unit.ColorProvider
import ru.stohastika.sklyanka.domain.widget.WidgetProfileIndication
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import ru.stohastika.sklyanka.ui.token.SklyankaColors
import ru.stohastika.sklyanka.ui.token.lightColors
import androidx.glance.text.FontWeight as GlanceFontWeight
import androidx.glance.text.TextStyle as GlanceTextStyle

/**
 * Токены дизайн-системы в терминах Glance (НФТ-47, §5.11).
 *
 * Разметка виджета живёт вне Compose-контекста приложения: `CompositionLocal` темы ей
 * недостижим, а `Color`/`TextStyle` Compose не понимает. Поэтому значения берутся
 * у токенов напрямую (`SklyankaSizes`, `SklyankaSpacing`, `SklyankaType`, `lightColors()`)
 * и переводятся в типы Glance здесь — в одном месте. Литералов цвета и размера
 * в разметке нет: их не пропускает ни ревью, ни правило `HardcodedDesignToken`.
 */
internal object WidgetLook {

    /**
     * Светлые роли — всегда, независимо от среды телефона.
     *
     * Тёмная среда виджетов — SKL-120, и до неё виджет рисуется светлыми ролями (§5.11, §9):
     * половина ролей тёмной среды на плите читалась бы как состояние, которого нет.
     */
    val colors: SklyankaColors = lightColors()

    val surface: ColorProvider = ColorProvider(colors.surface)
    val line: ColorProvider = ColorProvider(colors.lineOnSurface)
    val text: ColorProvider = ColorProvider(colors.text)
    val textMuted: ColorProvider = ColorProvider(colors.textMuted)
    val textTertiary: ColorProvider = ColorProvider(colors.textTertiary)
    val onAccent: ColorProvider = ColorProvider(colors.onAccent)
    val lineInteractive: ColorProvider = ColorProvider(colors.lineInteractive)
    val ring: ColorProvider = ColorProvider(colors.missed)
    val dot: ColorProvider = ColorProvider(colors.switchOff)

    /**
     * Акцент плиты: у чужого профиля — его цвет (ПР-24, ПРФ-12).
     *
     * Подменяется здесь, а не в каждой строке: залитая кнопка отметки, слово «Сейчас»
     * и надпись профиля обязаны нести один и тот же цвет, и три отдельных решения
     * однажды разошлись бы.
     */
    fun accent(profile: WidgetProfileIndication?): ColorProvider = profile
        ?.let { ColorProvider(ProfilePalette.of(it.color).color) }
        ?: ColorProvider(colors.accent)

    /** Роль кружка полосы недели по состоянию дня (§5.2): форма и роль, без второго словаря. */
    fun role(role: Color): ColorProvider = ColorProvider(role)

    /**
     * Стиль текста Glance из стиля дизайн-системы.
     *
     * Начертание переводится в три доступные ступени Glance: `SemiBold` и `Bold` шкалы
     * ложатся в `Bold`, `Medium` — в `Medium`, обычное — в `Normal`. Полужирного начертания
     * у Glance 1.1 нет вовсе, и подменять его здесь нечем — это ограничение библиотеки,
     * названное в отчёте задачи.
     */
    fun style(base: TextStyle, color: ColorProvider): GlanceTextStyle = GlanceTextStyle(
        color = color,
        fontSize = base.fontSize,
        fontWeight = base.fontWeight?.glance() ?: GlanceFontWeight.Normal,
    )

    private fun FontWeight.glance(): GlanceFontWeight = when (weight) {
        FontWeight.Medium.weight -> GlanceFontWeight.Medium
        FontWeight.Normal.weight -> GlanceFontWeight.Normal
        else -> GlanceFontWeight.Bold
    }
}
