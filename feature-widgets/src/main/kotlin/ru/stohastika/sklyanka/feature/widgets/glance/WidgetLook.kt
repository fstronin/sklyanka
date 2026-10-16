package ru.stohastika.sklyanka.feature.widgets.glance

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.glance.unit.ColorProvider
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.widget.WidgetProfileIndication
import ru.stohastika.sklyanka.feature.widgets.R
import ru.stohastika.sklyanka.ui.component.AdherenceDayState
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import androidx.glance.text.FontWeight as GlanceFontWeight
import androidx.glance.text.TextStyle as GlanceTextStyle

/**
 * Токены дизайн-системы в терминах Glance (НФТ-47, §5.11).
 *
 * Разметка виджета живёт вне Compose-контекста приложения: `CompositionLocal` темы ей
 * недостижим, а `Color`/`TextStyle` Compose не понимает. Поэтому значения берутся
 * у токенов напрямую (`SklyankaSizes`, `SklyankaSpacing`, `SklyankaType`) и переводятся
 * в типы Glance здесь — в одном месте. Литералов цвета и размера в разметке нет:
 * их не пропускает ни ревью, ни правило `HardcodedDesignToken`.
 *
 * **Роли среды приходят ресурсами** (§9, SKL-120). У `ColorProvider` Glance 1.1 нет пары
 * «день · ночь»: он умеет либо одно значение, либо ресурс. Значит среду выбирает система
 * по квалификатору `-night`, а значения ролей живут в `res/values/widget_colors.xml`
 * и `res/values-night/widget_colors.xml` — дубликатом токенов, потому что другого
 * источника у ресурса нет. Сходимость дубликата стережёт приёмочная проверка палитры.
 *
 * **Про `RestrictedApi`.** Ресурсная краска берётся `ColorProvider(@ColorRes)` — это
 * документированный вход, и другого у Glance 1.1 нет: пары «день · ночь» у `ColorProvider`
 * не существует. Обход (разрешить цвет заранее) отнял бы у роли следование квалификатору
 * `-night`, поэтому проверка подавлена узко — по идентификатору и по этому файлу
 * (`feature-widgets/lint.xml` с обоснованием).
 *
 * **И акцентная тема человека** (§2.5, §5.11, SKL-120): у акцента четыре значения на четыре
 * темы в каждой среде, и тему выбирает человек, а не квалификатор ресурса. Поэтому её
 * называет [accent] параметром, а `-night` выбирает светлую или тёмную половину её пары.
 */
internal object WidgetLook {

    val surface: ColorProvider = ColorProvider(R.color.widget_surface)
    val line: ColorProvider = ColorProvider(R.color.widget_line)
    val text: ColorProvider = ColorProvider(R.color.widget_text)
    val textMuted: ColorProvider = ColorProvider(R.color.widget_text_muted)
    val textTertiary: ColorProvider = ColorProvider(R.color.widget_text_tertiary)
    val onAccent: ColorProvider = ColorProvider(R.color.widget_on_accent)
    val lineInteractive: ColorProvider = ColorProvider(R.color.widget_line_interactive)
    val ring: ColorProvider = ColorProvider(R.color.widget_ring)
    val dot: ColorProvider = ColorProvider(R.color.widget_dot)

    /**
     * Акцент плиты: у чужого профиля — его цвет (ПР-24, ПРФ-12), у владельца — акцент темы.
     *
     * Подменяется здесь, а не в каждой строке: залитая кнопка отметки, слово «Сейчас»
     * и надпись профиля обязаны нести один и тот же цвет, и три отдельных решения
     * однажды разошлись бы.
     *
     * **Плита читает те же роли, что экраны** (§5.11, §9, SKL-120): акцент владельца — роль
     * `accent` выбранной темы (§2.5), а в тёмной среде — её тёмное значение. Тема приходит
     * параметром, потому что она принадлежит человеку, а не плите: узнаёт её `provideGlance`
     * у того же шва, каким её читает приложение (`ObserveAccentThemeUseCase`).
     *
     * **В чужом профиле акцент — цвет профиля, и он сильнее темы** (§2.2, ПРФ-12): палитра
     * профилей — отдельная система (Р-31), темой не подменяется, и здесь она проверяется
     * первой. Темы у чужого профиля не спрашивают вовсе — не «сначала профиль, потом тема
     * поверх», а одна ветка без второй.
     */
    fun accent(profile: WidgetProfileIndication?, theme: AccentTheme): ColorProvider = profile
        ?.let { ColorProvider(ProfilePalette.of(it.color).color) }
        ?: ColorProvider(accentOf(theme))

    /**
     * Ресурс акцента темы: пара «день · ночь» выбирается квалификатором ресурса.
     *
     * Значения лежат в `res/values/widget_colors.xml` и `res/values-night/widget_colors.xml`
     * дубликатом токенов — у `ColorProvider` Glance 1.1 пары «день · ночь» нет, и другого
     * источника у ресурса не бывает. Сходимость дубликата стережёт приёмочная проверка
     * палитры (`WidgetThemeAccentTest`).
     */
    private fun accentOf(theme: AccentTheme): Int = when (theme) {
        AccentTheme.OLIVE -> R.color.widget_accent_olive
        AccentTheme.INDIGO -> R.color.widget_accent_indigo
        AccentTheme.PINE -> R.color.widget_accent_pine
        AccentTheme.AMETHYST -> R.color.widget_accent_amethyst
    }

    /**
     * Роль кружка полосы недели по состоянию дня (§5.2): форма и роль, без второго словаря.
     *
     * Ресурсом, а не готовым цветом: роль у состояния своя, и в тёмной среде она другая
     * (§9) — «принято» в светлой `#1F5F6B`, в тёмной `#96B2B4`. Значение, вычисленное
     * на светлой среде, на тёмной плите читалось бы светящейся заливкой.
     */
    fun dayRole(state: AdherenceDayState): ColorProvider = when (state) {
        AdherenceDayState.Taken -> ColorProvider(R.color.widget_day_taken)
        AdherenceDayState.Partial -> ColorProvider(R.color.widget_day_partial)
        else -> ColorProvider(R.color.widget_day_missed)
    }

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
