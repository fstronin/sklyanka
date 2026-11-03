# Design: screens, motion, and layout

This document specifies the visible behavior of surfaces: header composition, header collapse on
scroll, motion, the dark environment, layout tolerance across six interface languages, and the
prohibitions of the visual language. The interface is Russian; strings are quoted and glossed.

## 7. Screen header and scrolling

A header is one of four kinds. The kind fixes composition; typography, spacing, and color come from
design tokens.

| Kind | Composition |
|---|---|
| `Root` | date `OverlineDate`, title `TitleScreen`, profile avatar indicator 44 dp on the right. No settings button in the header; the settings tab is enough. |
| `Nested` | back on the left, quiet action on the right if the screen has one (History has none: «Отчёт врачу», "Report for the doctor", lives at the end of the content). If the title does not fit on one line with the action, the header takes two rows: an action row (top padding counted once for the whole header, bottom gap 8 dp) and the title below it with a gap of 4 to 8 dp, up to two lines, without clipping or ellipsis. A second-level screen without back (medication form, medication card) does not acquire one: the exit is the system Back. On the form, saving is carried by the bottom button; on the card, «Изменить» ("Edit") is the primary button of the bottom action row. |
| `Modal` | the surface's leading action, if it has one, on the left in `BodyDefault` 15/400 `textMuted` (on the report-for-the-doctor sheet, «Отмена», "Cancel"); the title immediately after it in `BodyDefaultStrong` 15/600 `text`; the closing action on the right in `BodyDefaultStrong` 15/600 `accent` («Готово», "Done", on the same sheet). "Cancel" is not mandatory for the kind: a surface has it if it has it, and the delivery self-check sheet has none, its only exit being the system Back. Action words sit on the side margin `SklyankaSpacing.Section` 20 dp, on one vertical with the content and one horizontal line. An empty slot is never filled: no invisible placeholders, no fake centering. |
| Foreign profile | tinted `profileHeader` background, the label «Профиль · Имя» ("Profile · Name") in capitals, a non-collapsible banner, and a bottom `line` border. |

**Scrolling pattern.** The large title is content; back and action are chrome. On scroll the large
title and the header's service blocks (date, day strip, banners, the «Активные/Архив»
("Active/Archive") segment, the note) travel with the content; a fixed compact bar, **56 dp** on opaque
`background`, keeps back 44 dp, the functional action if the screen has one, and the profile avatar in
reach at any scroll position. While the header is expanded the bar is absent and holds no space.

**Compact title.** `BodyLarge` 17 sp/600, appears when the large title leaves the top edge; the bar's
`line` border exists only collapsed. Appearance and fading take `DurationFast` 150 ms with
`EasingEnter`/`EasingExit`, instant at animation scale 0 with identical end states (§8.3). The large
title never shrinks continuously. The compact title is drawn only if it fits on one line at the current
scale, measured on the actual label width; otherwise it is not drawn, the bar grows with its buttons,
and the large title is never clipped. Content that does not scroll keeps the header expanded. Dark
surfaces (reminder, splash) are unaffected; the medication form is affected entirely, its large title
being content while the bar carries the title only; the modal header's single line travels with the
content and the bar repeats its leading action.

**Pinned bar.** Height `SklyankaSizes.PinnedHeaderBar` 56 dp is a lower bound: with a large font the bar
grows with its buttons and never shrinks. Background is opaque `background` (`profileHeader` on a
foreign profile), because it lies over departing content. Side margin `SklyankaSpacing.Section` 20 dp,
top `SklyankaSpacing.Hair` 2 dp, bottom `SklyankaSpacing.Default` 10 dp: 44 of the 56 dp go to touch
targets. The `line` border, `DividerThickness` 1 dp, exists only collapsed.

| Kind | Compact bar composition |
|---|---|
| `Root` | compact title; on Today also the avatar indicator 44 dp. No other actions. |
| `Nested` | back on the left (chevron in a 44 dp target), compact title, functional action on the right if the screen has one. A screen without back (medication form, medication card) carries the compact title only: no chevron, no action, because the card's action lives in the bottom row. |
| `Modal` | the surface's leading action, if it has one (on the report sheet «Отмена», a word, no chevron), plus the compact title; a surface without a leading action carries the title alone. The threshold here is the bottom edge of **the modal header row itself**: the modal header has no large title to measure. |

The action in the bar is the same button as in the expanded header (secondary, `Card` form, 44 dp,
`Label` 14); back is the same chevron in a 44 dp target; the gap is `SklyankaSpacing.Medium` 12 dp and
the title takes weight `1f`.

**Expanded state.** No 56 dp are reserved, the bar background is not drawn, and no strip stands above
the large title. The header starts with its top padding, 30 dp (24 dp foreign), and padding plus top
inset is at most 56 dp; an empty 56 dp strip is a defect. The bar is a **layer over the scroll, not a
sibling in the column**.

**Bottom padding.** A header in the scroll carries no bottom padding of its own: the gap is the
screen's scroll rhythm, Today `SklyankaSpacing.Section` 20 dp, Settings `Block` 16 dp, Medications,
Archive, and History `Large` 14 dp. `SklyankaSizes.HeaderBottomPadding` 14 dp remains only on a header
outside the scroll (no large title, expanded foreign-profile header); adding it to the rhythm would
widen the gap by 10 to 16 dp. The foreign-profile `line` sits on the header's bottom edge. Where the
content rhythm is tighter (list cards, `SklyankaSpacing.Default` 10 dp), the header-to-first-card
boundary comes from the top-level rhythm (14 dp) and 10 dp stays between cards. The gap comes from the
screen rhythm, never from a mockup value outside the spacing scale.

**Avatar.** Expanded, `ProfileAvatarButton` 44 dp stands in the scrolling header to the right of the
"date plus large title" block, on the same line and aligned to its top edge; collapsed, it stands in
the bar to the right of the compact title. The bar's left edge belongs to back (`Nested`) or to the
compact title start (`Root`). The avatar never switches profiles.

**Threshold.** Geometry, not pixels: the compact title appears when the large title's bottom edge
crosses the bottom edge of the bar, that is, when the large title has gone fully under it, and the same
threshold extinguishes it when scrolling up, with no hysteresis. The reference is the bottom edge of
the **bar strip** (56 dp), not of the whole pinned part: a foreign profile has a non-collapsible banner
below the strip, which would add hysteresis. A pixel threshold would lie at other font scales, locale
lengths, or foreign-profile header heights. The large title does not morph. The header's other service
blocks (date, "Profile · Name", day strip, stock banner, segment, note) are content, scroll away, and
have no copies in the bar; only the foreign-profile banner is rebuilt by it.

**Foreign profile.** Collapsed, the indication stays: `profileHeader` tint, `line` border, the
non-collapsible banner below the bar, and the profile color on the avatar. "Profile · Name" is
scrolling content; collapsed, the banner names the profile in words and the avatar in color, and the
six signs remain six. Expanded, the banner sits in the header under the large title; at the threshold
the bar puts it where it was, below the bar strip at `SklyankaSpacing.Large`, and it never scrolls
away. The pinned part is opaque throughout (strip, gap, banner), so the banner stays on screen at any
scroll position.

**Back and modal header.** Back is a leftward glyph, 22 dp (`SklyankaSizes.BackChevron`), in a
44×44 dp target (`MinTouchTarget`), `text` role (never `iconFaint`); stroke and roundings are fractions
of the size. The target is shifted 10 dp left of the side margin so the glyph aligns with the title
start, and the direction comes from separate drawing (a mirrored path), not a 180° rotation, which would
blur the stroke. No "Back" word appears; the target's accessible name is `cd_back` («Назад», "Back") and
the glyph is decorative. Tab roots have no visible back. The modal header gets no chevron: back in the
modal layer is always the system Back, and a pressable scrim belongs to the shared sheet, not to each
modal surface. «Отмена» stands on the surface that has it (on the report sheet it is the action of
sending itself) and is not mandatory for the kind. The medication form has no back at all; its only
visible exit is the system Back. The header glyph, the system Back button, and the gesture share one
motion scenario (§8.2).

## 8. Motion

### 8.1 Principle

Motion answers only three questions. **Deeper?** A second-level screen opens over the previous one, and
the previous one stays in place. **Side by side?** An adjacent layer (a tab, the "Active/Archive"
segment) changes without direction. **Interrupted?** A full-screen reminder appears from the background
and has no place in the stack. Nothing else is animated: micro-interactions (button press, marking an
intake, a checkbox, a switch, a chip) change instantly, an active-profile switch is instant (two
profiles never mix for a frame), and the first screen after the splash appears without motion. There are
no springs, bounces, rotations, or parallaxes.

The single exception is the bottom-bar tab press: icon and label color (`pressedTint`) appears over
150 ms with `EasingEnter` and fades over 150 ms with `EasingExit`, with no displacement, scale, shadow,
or ripple.

### 8.2 Tokens and scenarios

- `DurationFast` 150 ms: a small layer leaves (snackbar, tab press response, compact title).
- `DurationShort` 200 ms: an adjacent layer changes (tabs and segments, snackbar appearance, closing of
  a sheet or a reminder).
- `DurationMedium` 300 ms: a screen change (a second level either way, appearance of a sheet or a
  reminder).
- `EasingEnter` `cubic-bezier(0; 0; 0,2; 1)` for a layer appearing; `EasingExit`
  `cubic-bezier(0,4; 0; 1; 1)` for one disappearing or departing.

No transition lasts longer than 300 ms. Scenarios are declared by the `MotionPhase` enum and the
`motionTiming` function.

| Scenario | What happens | Duration and curve |
|---|---|---|
| `SecondLevelEnter` | The new screen enters from the right and covers the current one; the current one does not move. | 300 ms, `EasingEnter` |
| `SecondLevelExit` | The closing screen departs to the right, revealing the previous one; the previous one does not move. | 300 ms, `EasingExit` |
| `AdjacentEnter` · `AdjacentExit` | Cross-fade of an adjacent layer: tabs, the "Active/Archive" segment; the bottom bar and the header stay in place. | 200 ms, `EasingEnter` · `EasingExit` |
| `ReminderEnter` · `ReminderExit` | The reminder appears from the background and fades out, with no displacement. | 300 ms · 200 ms |
| `SnackbarEnter` · `SnackbarExit` | It rises from below by 16 dp while appearing and descends while fading. | 200 ms · 150 ms |
| `SheetEnter` · `SheetExit` | The sheet canvas slides up from below and departs downward; the scrim takes 200 ms · 150 ms. | 300 ms · 200 ms |
| Dialog, appearance and closing | The window scales 0.96 → 1 while appearing and back, with a scrim over the background. | 200 ms · 150 ms |

The dialog scrim appears and fades by the same scenarios as the sheet scrim; the dialog window itself
has no motion.

Rules. Forward goes from the right, back to the right, one direction for the whole application. A new
press during a transition is not queued but leads immediately to the new target from the current
position, and the target screen answers taps and reading from the first frame: motion is a visual layer
only. Direction duplicates the header and the explicit back, never carrying meaning alone.

### 8.3 Reduced motion

The only source is the system animation scale. A value of 0 means no animation: layers change instantly
and no intermediate frame is shown. The application introduces no scale of its own and does not override
the system value. End states must coincide at any duration: no element stays displaced, semi-transparent,
or "not arrived", and reference screenshots are taken in end states. There is no separate animation
setting in the application; the rule is provided by the environment and verified by running transitions
on a device at scales 1 and 0.

## 9. Dark environment

The dark environment is not a second theme but a context, with two parts: surfaces that are dark **by
their nature**, and the dark **interface environment**, which follows the system setting.

Four surfaces are always dark in any environment: the full-screen reminder, the delivery self-check
card, the splash screen, and the branded lockup block. Their canvas is cold or olive by decision of the
brand palette, and the system setting does not change it.

**The system dark theme belongs to the base.** The interface environment follows the system setting
(`isSystemInDarkTheme()` is read at the scaffold), so a dark device theme gives dark screens; the
environment has no switch of its own, and the light environment stays the default.

**The widget reads the environment.** The widget tile reads environment roles like any screen, so it
turns dark with the environment; it also reads the accent theme, which on a foreign profile is the
profile color.

**Dark roles are a full set, not nine.** Every role on screens is overridden. Plaque, status, and
service-graphics colors are not inherited from the light environment: on a dark background a light value
would read as a glowing fill rather than a quiet plaque.

| Role | Light | Dark |
|---|---|---|
| `background` · `surface` | `#F8F6EF` · `#FFFFFF` | `#181712` · `#342D26` |
| `text` · `textMuted` · `textTertiary` | `#241F1B` · `#5F564D` · `#8C8177` | `#F8F6EF` · `#C9BFB1` · `#A79C90` |
| `textDisabled` | `#A79C90` | `#6E6459` |
| `line` · `lineOnSurface` · `lineInteractive` | `#E6DED2` · `#EFE7DB` · `#D9CDBD` | `#4A4139` · `#453C34` · `#5C5148` |
| `iconFaint` | `#B0A597` | `#7C7266` |
| `accent` · `accentSoft` | by theme | by theme: `accent` is its dark value; `accentSoft` is the same accent diluted toward `#FFFFFF` by 0.55. For the default olive theme the dark values are `accent` `#B5C47A` and `accentSoft` `#DEE4C3` |
| `onAccent` · `onAccentSoft` | `#FFFFFF` · by theme | `#181712` · by theme, the **same value as in the light environment** (not overridden) |
| `taken` · `takenSoft` | `#1F5F6B` · `#E2EEF0` | `#96B2B4` · `#2F4347` |
| `missed` · `takenPartial` | `#C9BFB1` · `#6E9FA8` | `#68635A` · `#C8D8D6` |
| `warning` · `danger` | `#8F3E23` · `#8F3E23` | `#C9A393` · `#C9A393` |
| `warningOnSoft` | `#8F3E23` | `#8F3E23`, **not overridden**: its plaque is light in both environments |
| `neutralSoft` · `progressTrack` · `switchOff` | `#F3EDE3` · `#EDE3D6` · `#DCD1C2` | `#3D372F` · `#3B342C` · `#574C42` |
| `archiveBackground` · `archiveText` | `#F6F1E8` · derived (share 0.18) | `#2B2620` · derived the same way from dark roles |
| `profileStripe` · `profileBackground` · `profileHeader` | transparent · as the environment background | transparent · as the environment background |
| `scrim` | `#181712` at opacity 0.32 | the same token and share |

**Loud and quiet roles.** Loud roles (theme accent, `taken`, `warning` and `danger`, `textDisabled`,
`iconFaint`, lines) **rise toward the light** in the dark environment: they serve as text color and
labeled fills, so they must be legible on dark (7.97 for `taken`, 7.81 for `warning` and `danger`; they
do not mark the negative differently there). Quiet roles (`missed`, `takenPartial`, plaques, tracks,
`switchOff`, `neutralSoft`) **stay quiet**, standing off the background by the same share as in the
light environment (`missed` 3.01 against 1.68, enough for a legible circle on dark without lending the
role emphasis; meaning is carried by label and order, never by color alone). Plaques are derived over
the environment `surface`, so soft plaques read as tinted grays and color is carried by glyph and label.

**Modal scrim.** It exists in the dark environment. The role and share do not change (`#181712` at
0.32); the darkening step is smaller because background and scrim are one token: the surface under the
layer drops by 6 to 9 channel points, enough for the layer to read as the top one. No second share is
introduced.

**Plaque and inks come from one environment, and the plaque decides.** The light `accentSoft` plaque
stays light in the dark environment, so only light-pair inks stand on it: word and glyph in
`onAccentSoft`, warning sign and quiet action in the **light** `warning` (`#8F3E23`, 6.17 on the
plaque). Dark raises `accent`, `warning`, and `danger` for the dark background, where they are legible;
on a light plaque they would give 1.4 to 1.75. Status plaques (`takenSoft`, `neutralSoft`,
`progressTrack`, `switchOff`, `archiveBackground`) are dark in the dark environment, so their inks are
dark roles (`taken` at 4.63 on `takenSoft`; the glyph on an icon plaque is `onAccentSoft` where the
plaque is light).

**Foreign-profile tinting knows the environment where roles do.** Surfaces (background, header, lines)
come from environment roles; the accent family is derived from the profile color by one rule for both
environments: accent and stripe rise to the dark `text` value (`#F8F6EF`), the plaque stays light paper,
and its inks are one set. Otherwise the tint would vanish exactly where it is easiest to miss, at night.

**What the dark environment does not touch.** (1) The report for the doctor and the CSV export: white
paper, light roles, the base theme accent; no theme, profile, or environment recolors the document.
(2) The mark and its palette: separate, subordinate neither to theme nor environment. (3) The splash,
the branded block, and the database state screens: dark and cold always. (4) System surfaces
(notifications, system switches): the firmware draws them. Forced system darkening is never applied:
system inversion would paint a second theme over our roles.

**Splash and branded block are dark but cold.** They stay dark surfaces (a nightly launch does not
flash) on a **cold** canvas, `#111A1E` instead of `#181712`; the system splash background and status bar
use the same paint, the wordmark the mark color `#C4F0F7`, and the subtitle and "Stohastika" derive from
the brand palette. The reminder and the delivery self-check card stay the dark interface environment
(olive); only the mark is cold there. The database state screens («База новее установленной сборки»,
"The database is newer than the installed build", and «Не хватает места», "Not enough space") continue
the splash on the same `#111A1E` plate and use dark environment roles. Text on dark surfaces uses
`textOnDark` `#E5DCD1`, one value in both environments.

**Dark button form.** The dark form of the buttons belongs to **dark surfaces**, not to the
environment, so the reminder's buttons ignore the system setting. Heights are 58 and 54 dp, radius
18 dp, label padding 0, air between label and frame at least 4 dp. The row repeats the canvas margin of
24 dp: «Принято» ("Taken") takes the full width, while «Отложить N мин» ("Defer N min") / «Пропустить»
("Skip") halve the row with a 12 dp gap and collapse into a column at the label-width threshold. There
is no disabled state, and snackbars and sheets on dark surfaces use the shared `scrim`.

## 10. Layout localization

Six interface languages: `ru` (base and fallback), `en-US`, `es`, `de`, `zh-CN`, `kk`. The interface
language is set by the application, not the device: `locale/InterfaceLocale.kt` pins it in each
activity's context configuration, and the plural form, string set, and formatter locale depend on it. A
device language outside the set gets the Russian fallback set entirely, never a mixture. Plurals go
through `plurals`, dates and times through platform facilities, the 12- or 24-hour preference comes from
the system setting, and capitalized overlines follow the interface locale. All strings live in
`strings.xml` of the six catalogs.

**The localization matrix is required for accepting any screen change:** 23 screens and states ×
6 languages, 138 contact sheets. A violation is clipping by height, an unresolved ellipsis, or a line
break that split a word. The only permitted ellipsis is a medication name, keeping at least three
visible characters. Contact sheets are captured by a visual run with a per-pixel comparison tolerance
of 8 out of 255 per channel. Every new or changed string ships in all six catalogs, including previews
and placeholders; a confirmation word is compared against the locale resource, never a Russian literal,
and textless elements are checked by width measurement.

Known long-string problems and their layout solutions:

- German, Spanish, and Kazakh are longer than Russian. Strings grow, they are not clipped: the screen
  title wraps to two lines, the card and header grow in height, a row action moves under the text, and
  a row of equal buttons collapses into a column.
- A Settings transition row title is longer than other row labels: at 200 percent it wraps in three
  whole words, the label in two, and the screen grows. Hence **a word must fit the text column**: breaks
  inside a word are forbidden, and the wording is chosen to fit. The German
  `System-Benachrichtigungseinstellungen` (36 characters) broke inside the word and became
  "Benachrichtigungen im System".
- Golos Text has no CJK: `zh-CN` strings are drawn by the system fallback font, so a label's width is
  measured on the font actually rendered, and row thresholds and the matrix use the real render.
- The dark row of the reminder at 130 percent in `ru` («Отложить 10/15/30/60 мин», "Defer 10/15/30/60
  min") collapses into a column, and at 200 percent so does every language: the threshold measures label
  width, not scale.
- Tab labels are hidden above 130 percent and the icon with its accessible name remains. The machine
  time column grows with its value and aligns a group's times on one vertical.
- **An orphan line, one word on the last line, is a defect at 100 percent** for short labels and notes:
  a phrase carrying one thought fits the line entirely, and if it does not fit it wraps with at least
  two words. The wording is chosen to satisfy this. At 130 and 200 percent an orphan is not a defect:
  the column is the same and the text grows, so only freedom from clipping, intra-word breaks, and
  overlap is required.

## 11. Prohibitions

The visual language forbids a short list of things outright, each enforced by a build rule rather than
by review attention.

| Forbidden | Enforced by |
|---|---|
| Color, type size, radius, or spacing literals outside the token package | Detekt rule `HardcodedDesignToken`: `.dp`, `.sp`, `.em`, `.pt` literals, `Color(…)` calls, and `Color.*` constants outside `ru.stohastika.sklyanka.ui.token` |
| Building a typeface on site | Detekt rule `HardcodedTextStyle`: `TextStyle(…)`, `SpanStyle(…)`, `ParagraphStyle(…)` outside the token package; editing a ready style through `copy(…)` is allowed |
| Numeric literals outside tokens | `style/MagicNumber` with an exception only for `**/ui/token/**` |
| Hardcoded interface strings in code, including previews and placeholders | Detekt rule `RussianTextInCode`: Cyrillic in a string literal outside `ru.stohastika.sklyanka.acceptance` |
| A screen reading a raw palette value, or picking a profile, dark-environment, or pressed-state color on site | The palette is `internal`; only roles are visible outside the module, and the only points where color is obtained are `tintedColors`, `darkColors`, `pressedTint` |
| A mark palette value used as an interface role, or the reverse | The mark palette is a **separate closed palette**: its values live only in the mark tokens (`SklyankaMarkColors`) and the `mark_*` resources, while interface roles live only in `SklyankaColors`. The mark takes neither the accent nor interface roles (`glassMarkColors`, `markTileBackground`, `flatMarkColor`), so cold glass never recolors warm screens |

The rules are declared in the Detekt configuration without a warning mode: `build.maxIssues = 0`, so any
finding fails the build, and each rule has a test proving it fires on a sample violation. The consequence
is that recoloring the accent and overriding the dark roles work by themselves, because a screen reads a
role and not a value; a literal on site would erase the foreign-profile indication.
