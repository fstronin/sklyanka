package ru.heuri.sklyanka.ui.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Роли цвета — верхний уровень дизайн-системы.
 *
 * Отступление от `docs/spec/08-design-system.md`, объявленное вслух (TDD §4.1):
 * документ называет роли константами объекта (`SklyankaColors.Accent`).
 * Константой акцент быть не может — он обязан меняться на цвет профиля (ПРФ-11).
 * Поэтому роли живут набором значений, а обращение идёт через тему:
 * `SklyankaTheme.colors.accent`.
 *
 * Набор ролей полный и в светлой среде, и в профильной: компонент читает роль,
 * не спрашивая, какая сейчас тема.
 */
@Immutable
@Suppress("LongParameterList")
// Ролей столько, сколько их в docs/spec/08-design-system.md §1.1 и §1.2. Сокращать
// набор группировкой («цвета текста», «цвета линий») нельзя: роль — это место
// применения, а не оттенок, и группа скрыла бы именно место.
data class SklyankaColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val textMuted: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val line: Color,
    val lineOnSurface: Color,
    val lineInteractive: Color,
    val iconFaint: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentBorder: Color,
    val onAccentSoft: Color,
    /**
     * Цвет предупреждения: критический остаток, значок баннера остатка.
     *
     * Роль добавлена сверх списка TDD §4.1. §1.1 отдаёт этим двум смыслам один и тот
     * же токен `#8F3E23`, но смыслы разные: «чужой профиль» тонируется под профиль,
     * а предупреждение обязано оставаться предупреждением в любом профиле.
     */
    val warning: Color,
    val taken: Color,
    val takenSoft: Color,
    val missed: Color,
    val takenPartial: Color,
    val neutralSoft: Color,
    val progressTrack: Color,
    val switchOff: Color,
    val archiveBackground: Color,
    val profileStripe: Color,
    val profileBackground: Color,
    val profileHeader: Color,
)

/**
 * Светлый набор — основной и единственный для продуктовых экранов.
 *
 * Три роли профиля в нём нейтральны: полоса прозрачна, подкрашенные поверхности
 * совпадают с обычным фоном. Это и есть ПРФ-16: у владельца индикации нет, потому
 * что показывать нечего, а не потому, что экран её спрятал.
 */
fun lightColors(): SklyankaColors = SklyankaColors(
    background = Palette.Background,
    surface = Palette.Surface,
    text = Palette.Text,
    textMuted = Palette.TextMuted,
    textTertiary = Palette.TextTertiary,
    textDisabled = Palette.TextDisabled,
    line = Palette.Line,
    lineOnSurface = Palette.LineOnSurface,
    lineInteractive = Palette.LineInteractive,
    iconFaint = Palette.IconFaint,
    accent = Palette.Accent,
    accentSoft = Palette.AccentSoft,
    accentBorder = Palette.AccentBorder,
    onAccentSoft = Palette.OnAccentSoft,
    warning = Palette.ProfileForeign,
    taken = Palette.Taken,
    takenSoft = Palette.TakenSoft,
    missed = Palette.Missed,
    takenPartial = Palette.TakenPartial,
    neutralSoft = Palette.NeutralSoft,
    progressTrack = Palette.ProgressTrack,
    switchOff = Palette.SwitchOff,
    archiveBackground = Palette.ArchiveBackground,
    profileStripe = Palette.Transparent,
    profileBackground = Palette.Background,
    profileHeader = Palette.Background,
)

/**
 * Тёмный набор — контекст двух поверхностей, а не вторая тема оформления
 * (`docs/spec/08-design-system.md` §5, ОВ-9).
 *
 * Переопределяются ровно восемь ролей; остальные наследуются от светлого набора и
 * в тёмной среде не встречаются. Тёмный токен «на всякий случай» — приглашение
 * нарисовать экран, которого в объёме нет.
 *
 * Правило «основной акцент `#B4512F` в тёмной среде на тексте не применяется»
 * (контраст 3,59) держится подменой роли: `accent` здесь — уже `AccentOnDark`,
 * и запрещать отдельно нечего.
 */
fun darkColors(): SklyankaColors = lightColors().copy(
    background = Palette.DarkBackground,
    surface = Palette.SurfaceDark,
    // §5: в тёмной среде фон и текст меняются местами.
    text = Palette.Background,
    textMuted = Palette.TextMutedOnDark,
    textTertiary = Palette.TextTertiaryOnDark,
    line = Palette.LineDark,
    accent = Palette.AccentOnDark,
    accentSoft = Palette.AccentSoftOnDark,
)

/** Имена восьми ролей, переопределяемых в тёмной среде (§5). Опора теста полноты набора. */
val DARK_OVERRIDDEN_ROLES: List<String> = listOf(
    "background",
    "surface",
    "text",
    "textMuted",
    "textTertiary",
    "line",
    "accent",
    "accentSoft",
)
