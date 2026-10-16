package ru.stohastika.sklyanka.ui.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ru.stohastika.sklyanka.domain.appearance.AccentTheme

/**
 * Роли цвета — верхний уровень дизайн-системы.
 *
 * Отступление от `docs/norm/06-design.md`, объявленное вслух (TDD §4.1):
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
// Ролей столько, сколько их в docs/norm/06-design.md §1.1 и §1.2. Сокращать
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
     * существующие Surface (#FFFFFF) и DarkBackground (#181712). В тёмной среде
     * Surface на AccentOnDark даёт 1,88 — ниже AA; тёмная пара даёт 9,52.
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
     * Предупреждающий знак и слово на **светлой** подложке мягкого акцента (§9, V-74).
     *
     * Подложка `accentSoft` светлая в обеих средах (Р-20: акцент, разбавленный к белой
     * поверхности), поэтому и краски на ней светлые — решает подложка, а не среда. Роль
     * заведена потому, что тёмная среда поднимает [warning] к свету для тёмных подложек,
     * и на светлой он дал бы 1,68 при пороге 4,5. Значение — то же в обеих средах, как
     * у [textOnDark]: это не переопределение §9, а отдельное место применения (§2.1).
     */
    val warningOnSoft: Color,
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
 * Светлый набор — основной для продуктовых экранов.
 *
 * Три роли профиля в нём нейтральны: полоса прозрачна, подкрашенные поверхности
 * совпадают с обычным фоном. Это и есть ПРФ-16: у владельца индикации нет, потому
 * что показывать нечего, а не потому, что экран её спрятал.
 *
 * [theme] меняет ровно семью акцента (§2.5): `accent`, `accentSoft`, `accentBorder`,
 * `onAccentSoft`, `onAccent`. Остальные роли общие для всех тем — «олива на бумаге»
 * (Р-20) остаётся фоном, а не свойством одной темы. Значение по умолчанию — базовая
 * «Олива»: вызывающий, которому тема не важна (документ отчёта, выгрузка истории,
 * щиток знака), получает базовый акцент и не обязан её называть.
 */
fun lightColors(theme: AccentTheme = AccentTheme.DEFAULT): SklyankaColors = SklyankaColors(
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
    accent = theme.palette.lightAccent,
    accentSoft = theme.palette.lightSoft,
    accentBorder = accentBorderOf(theme.palette.lightAccent, Palette.Surface),
    onAccentSoft = onAccentSoftOf(theme.palette.lightAccent),
    onAccent = Palette.Surface,
    textOnDark = Palette.TextOnDark,
    warning = Palette.ProfileForeign,
    warningOnSoft = Palette.ProfileForeign,
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
 * Тёмный набор — среда интерфейса, следующая за системной настройкой (§9, Р-23).
 *
 * Девять прежних тёмных токенов (SKL-109) здесь доведены до **полного** набора: тёмная
 * среда покрывает все поверхности, поэтому переопределяется каждая роль, которая на
 * экранах встречается. Наследовать «тихие» роли от светлой среды нельзя: светлое
 * значение на тёмном фоне читается светящейся заливкой, а не тихой плашкой.
 *
 * Не переопределяются ровно четыре роли, и у каждой причина: `textOnDark` — отдельная
 * роль §1.2 с одним значением в обеих средах (ОВ-146); `onAccentSoft` — по §2.5 значение
 * **то же, что в светлой**; `profileStripe` — прозрачность, отсутствие цвета; `scrim` —
 * один токен и одна доля на все модальные слои (§2.3). Их имена — в
 * [DARK_INHERITED_ROLES], и список там не «на всякий случай»: тест падает и когда
 * тёмную роль забыли, и когда роли разошлись молча.
 *
 * Две нейтральные роли профиля ([NEUTRAL_PROFILE_ROLES]) повторяют фон среды: у владельца
 * подкраски нет, и «нет подкраски» в тёмной среде — это тёмный фон (ПРФ-16).
 *
 * Правило «основной акцент `#5F6F2E` в тёмной среде на тексте не применяется»
 * (контраст 3,25) держится подменой роли: `accent` здесь — тёмное значение темы,
 * и запрещать отдельно нечего.
 */
fun darkColors(theme: AccentTheme = AccentTheme.DEFAULT): SklyankaColors = lightColors(theme).copy(
    background = Palette.DarkBackground,
    surface = Palette.SurfaceDark,
    // §5: в тёмной среде фон и текст меняются местами.
    text = Palette.Background,
    textMuted = Palette.TextMutedOnDark,
    textTertiary = Palette.TextTertiaryOnDark,
    textDisabled = Palette.TextDisabledOnDark,
    line = Palette.LineDark,
    lineOnSurface = Palette.LineOnSurfaceDark,
    lineInteractive = Palette.LineInteractiveDark,
    iconFaint = Palette.IconFaintOnDark,
    accent = theme.palette.darkAccent,
    // Подложка тёмной среды — тёмный акцент, разбавленный к белой поверхности той же
    // долей 0,55, которой выведена подложка базовой темы (§2.5, §9).
    accentSoft = darkAccentSoftOf(theme.palette.darkAccent),
    accentBorder = accentBorderOf(theme.palette.darkAccent, Palette.SurfaceDark),
    // Подпись на акцентной заливке в тёмной среде — фон среды, а не поверхность:
    // белая поверхность на тёмном акценте даёт 1,88 и не читается (ОВ-90).
    onAccent = Palette.DarkBackground,
    // «Громкие» роли подняты к свету: они служат цветом текста и заливкой с подписью (§9).
    taken = Palette.TakenOnDark,
    takenSoft = Palette.TakenSoftOnDark,
    takenPartial = Palette.TakenPartialOnDark,
    warning = Palette.WarningOnDark,
    danger = Palette.WarningOnDark,
    // «Тихие» роли остаются тихими: они стоят от фона на той же доле, что в светлой (§9).
    missed = Palette.MissedOnDark,
    neutralSoft = Palette.NeutralSoftOnDark,
    progressTrack = Palette.ProgressTrackOnDark,
    switchOff = Palette.SwitchOffOnDark,
    archiveBackground = Palette.ArchiveBackgroundOnDark,
    // Производный цвет считается тем же приёмом от тёмных ролей: архив тише списка в любой среде.
    archiveText = tint(Palette.Background, Palette.ArchiveBackgroundOnDark, TINT_ARCHIVE_TEXT),
    profileBackground = Palette.DarkBackground,
    profileHeader = Palette.DarkBackground,
)

/**
 * Имена ролей, переопределяемых тёмной средой (§9).
 *
 * Список — не перечень «того, что успели»: он полон и проверяется тестом на равенство
 * множеству фактически изменённых ролей. Дописать роль в код и забыть здесь нельзя,
 * и завести тёмный токен «на всякий случай» тоже: неиспользуемая тёмная роль —
 * приглашение нарисовать экран, которого в объёме нет.
 */
val DARK_OVERRIDDEN_ROLES: List<String> = listOf(
    "background",
    "surface",
    "text",
    "textMuted",
    "textTertiary",
    "textDisabled",
    "line",
    "lineOnSurface",
    "lineInteractive",
    "iconFaint",
    "accent",
    "accentSoft",
    "accentBorder",
    "onAccent",
    "taken",
    "takenSoft",
    "missed",
    "takenPartial",
    "warning",
    "danger",
    "neutralSoft",
    "progressTrack",
    "switchOff",
    "archiveBackground",
    "archiveText",
    "profileBackground",
    "profileHeader",
)

/**
 * Роли, которые тёмная среда **не** переопределяет, и почему (§9, §2.3, §2.5).
 *
 * Их четыре: `textOnDark` — отдельная роль §1.2 с одним значением в обеих средах (ОВ-146);
 * `onAccentSoft` — по §2.5 значение то же, что в светлой; `profileStripe` — прозрачность,
 * а не цвет; `scrim` — один токен и одна доля на все модальные слои (§2.3).
 */
val DARK_INHERITED_ROLES: List<String> = listOf(
    // Предупреждение на светлой подложке мягкого акцента: подложка светлая в обеих средах,
    // и краска на ней одна и та же (V-74, §9).
    "warningOnSoft",
    "textOnDark",
    "onAccentSoft",
    "profileStripe",
    "scrim",
)

/** Доля вывода `accentBorder` из акцента и поверхности (§2.5). */
internal const val ACCENT_BORDER_RATIO: Float = 0.30f

/** Доля вывода `onAccentSoft` из акцента и основного текста (§2.5). */
internal const val ON_ACCENT_SOFT_RATIO: Float = 0.50f

/** Доля вывода подложки тёмной среды из тёмного акцента и белой поверхности (§2.5, §9). */
internal const val DARK_ACCENT_SOFT_RATIO: Float = 0.55f

/** Рамка карточки, требующей действия: акцент, разбавленный к поверхности на 0,30 (§2.5). */
internal fun accentBorderOf(accent: Color, surface: Color): Color = tint(surface, accent, ACCENT_BORDER_RATIO)

/** Подпись на мягком акценте: акцент, затемнённый к тексту на 0,50, одно значение на обе среды. */
internal fun onAccentSoftOf(accent: Color): Color = tint(Palette.Text, accent, ON_ACCENT_SOFT_RATIO)

/** Подложка тёмной среды: тёмный акцент, разбавленный к белой поверхности на 0,55. */
internal fun darkAccentSoftOf(accent: Color): Color = tint(accent, Palette.Surface, DARK_ACCENT_SOFT_RATIO)

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
