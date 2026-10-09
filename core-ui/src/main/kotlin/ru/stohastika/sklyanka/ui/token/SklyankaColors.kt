package ru.stohastika.sklyanka.ui.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Роли цвета — верхний уровень дизайн-системы.
 *
 * Отступление от `docs/v2/12-design.md`, объявленное вслух (TDD §4.1):
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
// Ролей столько, сколько их в docs/v2/12-design.md §1.1 и §1.2. Сокращать
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
     * Подпись на акцентной заливке (ОВ-90). Роль, а не новый цвет: значения —
     * существующие Surface (#FFFFFF) и DarkBackground (#191512). В тёмной среде
     * Surface на AccentOnDark даёт 3,44 — ниже AA; тёмная пара даёт 4,61.
     */
    val onAccent: Color,
    /**
     * Подпись и глиф на тёмной поверхности: второстепенные кнопки полноэкранного
     * напоминания (§5.1, ОВ-146).
     *
     * Роль живёт только на тёмном экране, но значение одинаково в обеих средах:
     * это не десятое переопределение §5, а отдельная роль §1.2 — поэтому светлый
     * набор задаёт её тем же токеном, и `darkColors()` ничего не меняет
     * (ColorRolesTest).
     */
    val textOnDark: Color,
    /**
     * Цвет предупреждения: критический остаток, значок баннера остатка.
     *
     * Роль добавлена сверх списка TDD §4.1. §1.1 отдаёт этим двум смыслам один и тот
     * же токен `#8F3E23`, но смыслы разные: «чужой профиль» тонируется под профиль,
     * а предупреждение обязано оставаться предупреждением в любом профиле.
     */
    val warning: Color,
    /**
     * Цвет опасного действия: фон основной кнопки необратимого действия, рамка и подпись
     * ошибки поля.
     *
     * Роль, а не новый цвет (ОВ-38, ОВ-67): значение — существующий `#8F3E23`. От
     * [warning] роль отличается местом применения, и потому заведена отдельно: палитра
     * закрыта, а опасность обязана читаться одинаково в любом профиле — в чужом профиле
     * роль не тонируется (ПРФ-11 не распространяется на неё, см. `Tint.kt`).
     */
    val danger: Color,
    val taken: Color,
    val takenSoft: Color,
    val missed: Color,
    val takenPartial: Color,
    val neutralSoft: Color,
    val progressTrack: Color,
    val switchOff: Color,
    val archiveBackground: Color,
    /**
     * Название лекарства в карточке архива (§1.2, производный от [text]).
     *
     * Роль, а не вычисление по месту: вычисление требует вызова `Color(…)`, который вне
     * пакета токенов запрещён (НФТ-47), а экран, получивший функцию тонирования, снова
     * получил бы возможность подобрать цвет самому.
     */
    val archiveText: Color,
    val profileStripe: Color,
    val profileBackground: Color,
    val profileHeader: Color,
    /**
     * Затемнение модального слоя (§2.3) — единственная роль с непрозрачностью.
     *
     * Значение выведено из существующего токена тёмной среды ([Palette.DarkBackground]),
     * доля — та же 0,32, которой уже была нарисована шторка (ОВ-125). Роль заведена потому,
     * что место применения — **все** модальные слои, а не одна шторка: до неё доля жила
     * константой внутри `BottomSheet.kt`, и второй модальный слой неизбежно завёл бы
     * вторую долю. Нового оттенка нет: затемнение не подобрано под кадр, а выведено
     * из токена (Правило 1). В тёмной среде роль не переопределяется — тёмных модальных
     * слоёв в продукте нет (§9), а профилем не тонируется: затемнение — не место
     * применения цвета профиля ([tintedColors]).
     */
    val scrim: Color,
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
    onAccent = Palette.Surface,
    textOnDark = Palette.TextOnDark,
    warning = Palette.ProfileForeign,
    danger = Palette.ProfileForeign,
    taken = Palette.Taken,
    takenSoft = Palette.TakenSoft,
    missed = Palette.Missed,
    takenPartial = Palette.TakenPartial,
    neutralSoft = Palette.NeutralSoft,
    progressTrack = Palette.ProgressTrack,
    switchOff = Palette.SwitchOff,
    archiveBackground = Palette.ArchiveBackground,
    archiveText = tint(Palette.Text, Palette.ArchiveBackground, TINT_ARCHIVE_TEXT),
    profileStripe = Palette.Transparent,
    profileBackground = Palette.Background,
    profileHeader = Palette.Background,
    scrim = Palette.DarkBackground.copy(alpha = SCRIM_ALPHA),
)

/**
 * Доля затемнения модального слоя (§2.3, ОВ-125).
 *
 * Константа живёт в пакете токенов: непрозрачность — часть значения роли, а не параметр
 * экрана, и подбирать её по месту запрещено (НФТ-47).
 */
internal const val SCRIM_ALPHA: Float = 0.32f

/**
 * Тёмный набор — контекст двух поверхностей, а не вторая тема оформления
 * (`docs/v2/12-design.md` §5, ОВ-9).
 *
 * Своими тёмными токенами переопределяются ровно девять ролей §5; остальные
 * наследуются от светлого набора и в тёмной среде не встречаются. Тёмный токен
 * «на всякий случай» — приглашение нарисовать экран, которого в объёме нет.
 * Исключение — `textOnDark`: подпись второстепенной кнопки напоминания задана
 * отдельной ролью §1.2 с одним и тем же значением в обеих средах (ОВ-146),
 * поэтому здесь она не переопределяется.
 *
 * Отдельно от этих восьми — две нейтральные роли профиля. Нового цвета они не
 * вводят: «подкраски нет» означает «как фон среды», и в тёмной среде фон тёмный
 * (см. [NEUTRAL_PROFILE_ROLES]).
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
    // Подпись на акцентной заливке в тёмной среде — фон среды, а не поверхность:
    // Surface на AccentOnDark даёт 3,44 и не читается (ОВ-90).
    onAccent = Palette.DarkBackground,
    // Нейтральные роли профиля повторяют фон среды, а не заводят свой цвет: у
    // владельца подкраски нет, и «нет подкраски» в тёмной среде — это тёмный фон
    // (ПРФ-16). Оставленный светлым `profileHeader` давал светлую шапку на тёмном
    // экране и заголовок цветом фона, то есть невидимый заголовок (SKL-25).
    profileBackground = Palette.DarkBackground,
    profileHeader = Palette.DarkBackground,
)

/** Имена девяти ролей, переопределяемых в тёмной среде своими тёмными токенами (§5). */
val DARK_OVERRIDDEN_ROLES: List<String> = listOf(
    "background",
    "surface",
    "text",
    "textMuted",
    "textTertiary",
    "line",
    "accent",
    "accentSoft",
    "onAccent",
)

/**
 * Нейтральные роли профиля: подкраска, которой у владельца нет (ПРФ-16).
 *
 * Нового цвета они не вводят и в счёт девяти тёмных токенов §5 не идут — они равны
 * фону своей среды. Поэтому и меняются вместе с ним: роль «подкраски нет» обязана
 * означать «как фон», а не «как светлый фон».
 */
val NEUTRAL_PROFILE_ROLES: List<String> = listOf(
    "profileBackground",
    "profileHeader",
)
