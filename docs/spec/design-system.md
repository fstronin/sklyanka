# Design system

Tokens, palette, typography, geometry, components, accessibility. Kotlin sources cite these section numbers.

## 1. System structure

Color has three layers, consumed top down only: raw palette values (`token/Palette.kt`, `internal`), 31 roles, the places where color applies (`token/SklyankaColors.kt`, `token/Tint.kt`), and the theme, the active role set for one environment and one profile (`theme/SklyankaTheme.kt`, `theme/ProfileAccent.kt`).

A screen reads a role (`SklyankaTheme.colors.accent`) and knows neither the environment nor the profile. The accent is substituted inside the theme, never passed as a component parameter: a forgotten parameter fails invisibly.

Other tokens are constant objects in `token/`, independent of profile and environment: `SklyankaType` (22 styles), `SklyankaBrandType` (5 brand-block styles), `SklyankaShapes`, `SklyankaSpacing`, `SklyankaSizes` (named sizes and font-scale ceilings), `SklyankaMotion`, `SklyankaMarkTokens` (mark geometry, 5 reduction steps), `ProfileColor`, `ProfilePalette`, families `GolosText`, `Literata`, `LiterataTime`.

**Rule 1.** A new shade is never picked to match a mockup: it is derived from a token by a tint function or added to the palette with an edit of this document. **Rule 2.** A component state is an enum value from `component/ComponentStates.kt`, never a boolean at the call site.

## 2. Color

### 2.1 Light environment roles

| Role | Value | Where it applies |
|---|---|---|
| `background` | `#F8F6EF` | light screens, as-needed plate |
| `surface` | `#FFFFFF` | cards, inputs, bottom bar, snackbar, dialog and sheet shell |
| `text` | `#241F1B` | primary text, headings, values |
| `textMuted` | `#5F564D` | secondary lines, section labels, unselected tabs |
| `textTertiary` | `#8C8177` | quiet icons, third text level; large text only (§6.1) |
| `textDisabled` | `#A79C90` | unavailable text, meaning carried by a neighbor (§6.1) |
| `line` | `#E6DED2` | dividers, input border, compact header rule |
| `lineOnSurface` | `#EFE7DB` | card border on white, snackbar and dialog frame |
| `lineInteractive` | `#D9CDBD` | secondary and mark button borders, dashed "add time" |
| `iconFaint` | `#B0A597` | non-informative graphics: chevron, decorative arrow |
| `accent` | `#5F6F2E` | primary button, active tab, progress, selected chip, «Сейчас»; in a foreign profile the profile color |
| `accentSoft` | `#EBEEDA` | selected chip plate, icon plate, stock banner |
| `accentBorder` | `#CFD4C0` | 1.5 dp border of a card needing action: `accent` toward `surface` by 0.30 |
| `onAccentSoft` | `#424724` | text on soft accent: `accent` toward `text` by 0.50 |
| `onAccent` | `#FFFFFF` | label on an accent fill |
| `textOnDark` | `#E5DCD1` | label and glyph of a secondary button on dark; one value in both environments |
| `warning` | `#8F3E23` | critical stock, warning banner; never tinted by profile |
| `warningOnSoft` | `#8F3E23` | warning mark and quiet action on the light soft plate, one value equal to `warning` |
| `danger` | `#8F3E23` | irreversible primary fill, field error; never tinted by profile |
| `taken` | `#1F5F6B` | mark time, check mark, filled history day, owner avatar |
| `takenSoft` | `#E2EEF0` | taken icon plate, owner avatar background |
| `missed` | `#C9BFB1` | missed status |
| `takenPartial` | `#6E9FA8` | partially taken status |
| `neutralSoft` | `#F3EDE3` | neutral icon plate, time chip, neutral banner |
| `progressTrack` | `#EDE3D6` | progress track, unpassed step, disabled button |
| `switchOff` | `#DCD1C2` | off switch track, empty weekday dot (§5.2) |
| `archiveBackground` | `#F6F1E8` | archived course card |
| `archiveText` | derived | archived medication name: `text` toward `archiveBackground` by 0.18 |
| `profileStripe` | transparent | 6 dp stripe: transparent for the owner, profile color in a foreign profile |
| `profileBackground` | `#F8F6EF` | tinted background of a foreign profile; equals `background` for the owner |
| `profileHeader` | `#F8F6EF` | tinted header of a foreign profile; equals `background` for the owner |
| `scrim` | `#181712` at 0.32 | dimming under a modal layer (§2.3) |

`warning` and `danger` are different roles on one value: profiles are tinted, while warning and danger read the same in any profile. `#8F3E23` is never changed for a new accent and stays "error", not "action": distance 69 to `accent` `#5F6F2E`, 137 to `taken` `#1F5F6B`; contrast 6.74 on `background`, 6.17 on `accentSoft`, 7.29 on `surface`.

Palette "olive on paper": `background` `#F8F6EF`, `accent` `#5F6F2E`, `accentSoft` `#EBEEDA` are fixed; `accentBorder` `#CFD4C0` and `onAccentSoft` `#424724` derive from `accent` by 0.30 and 0.50; `onAccent` stays white at 5.53 on `#5F6F2E`.

The accent does not mark the negative: the negative is carried by `warning` and quiet neutrals (`missed`, `takenPartial`, `progressTrack`), the accent by action, choice, progress. In the per-medication breakdown every bar is filled with `taken`, and a low row names itself with the percentage in `CaptionStrong` in `text` instead of `Caption` in `textMuted`.

### 2.2 Foreign profile tinting

`token/Tint.kt` mixes component wise in sRGB; `Color.lerp` is not used, since linear-space mixing shifts a tint by about ten points per channel. Environment surfaces are tinted from the roles of the environment the screen is built in: `background` and `profileBackground` 0.035; `line`, `lineOnSurface`, `lineInteractive` 0.07; `profileHeader` 0.12.

| Role | Rule |
|---|---|
| `accent` · `profileStripe` | profile color; on dark raised toward the dark `text` value (`#F8F6EF`, `Palette.Background`) by 0.55, since the profile color on dark gives 2.5–3.2 |
| `accentSoft` | profile color toward the white surface by 0.14, one value for both environments |
| `onAccentSoft` | profile color toward the light `text` (`#241F1B`) by 0.50, one value for both environments; the five colors give 8.78–9.87 (Terracotta 9.10, Teal 9.17, Olive 9.87, Plum 9.16, Indigo 8.78) |
| `accentBorder` | profile color toward the environment `surface` by 0.20 |

Not tinted: `taken`, `takenSoft`, `text`, `textMuted`, `archiveBackground`, `warning`, `danger`. The raised dark accent reads on its background at 7.52–8.22 (Olive 7.52, Terracotta 7.81, Teal 7.97, Plum 7.99, Indigo 8.22). Soft pairs do not depend on the environment, so `ContrastTest` checks them once per color.

Profile colors (closed set): Terracotta `#8F3E23`, Teal `#1F5F6B`, Olive `#3C5014`, Plum `#6B4A6B`, Indigo `#4555B1`, five colors for five profiles. `#5F6F2E` is not in the set, since an arbitrary color could coincide with the accent; an unknown value from a transfer file falls back to the first color. Pressed state `pressedTint(color)` is toward `#241F1B` by 0.16. Each color as an accent on the tinted background and on `surface`: Terracotta 6.41/7.29, Teal 6.35/7.22, Olive 7.81/8.94, Plum 6.55/7.45, Indigo 5.79/6.59.

Distinctness thresholds (`ProfilePaletteTest`: Euclidean sRGB distance, channel 0–255, rounded): at least 50 from the accent, at least 75 between set colors. Distances from the accent: Olive 53, Terracotta 69, Teal 90, Plum 72, Indigo 136. Pairwise minimum Teal–Plum 79; Indigo 80 from Teal and from Plum, 157 from Olive, 162 from Terracotta. Thresholds hold for palette values only: on the raised dark set distances compress (Terracotta–Teal 61).

Themes and profile colors are independent: in a foreign profile the accent is the profile color and the whole accent family derives from it, not from the theme. A theme and a profile color need not differ: distinction is carried by the six markers of foreign intakes, stripe, tinted header, «Профиль · Имя» label, banner, action accent, «отметили вы» caption.

### 2.3 Modal layer dimming

`scrim` is the only role carrying opacity: token `DarkBackground` `#181712` at 0.32. The application draws it itself, one layer over the whole screen including system bars, intercepting touches so the background is unreachable, with the platform window dim switched off. Applies to `SklyankaBottomSheet`, `DateTimePickerDialog`, `ListPickerDialog`, the medication deletion sheet, and any later modal layer; the dark environment uses the same value and fraction. Motion 200 ms in, 150 ms out (§8.2). AA does not apply; what is checked is the 0.32 fraction and that an opaque `surface` always lies over the layer.

### 2.4 Mark palette

The mark is the only cold element of the brand: a separate closed palette, not derived from interface roles, not recolored by the accent theme (`glassMarkColors` takes no accent). Shapes, geometry, reduction steps, silhouette, monochrome layer, and notification icon are not redrawn.

| Value | Where |
|---|---|
| `#C4F0F7` | mark paint on the dark tile; brand block title, splash |
| `#0B2831` | mark paint on the light tile |
| `#111A1E` | dark tile: icon, splash and brand block canvas |
| `#E7F0F4` | light tile |
| `#FFFFFF` | white highlight: crescent of the light tile, first two body stops |
| `#A86A22` | amber arc and caustic, one paint in both versions |

Mark paint holds AA 4.5: 14.41 on the dark tile, 13.34 on the light tile. The arc and caustic sit at the large-graphics ceiling 3.0: 3.99 on dark, 3.83 on light. This is a named ceiling, not a lowered norm: 4.5 with one paint is unreachable (light tile needs L ≤ 0.1519, dark tile L ≥ 0.2178, the regions do not intersect) and two arcs are forbidden. Distance from `taken` `#1F5F6B` on the §2.2 scale (threshold 75): 260 on the dark tile, 82 on the light tile.

`#111A1E` against dark wallpaper `#14181B` gives 1.012, so the edge is invisible and the mark is held by the dot (12.34) and crescent (9.95). Splash derivatives are mark paint mixed with the dark tile in sRGB: 0.62 gives `#809FA5` for the «ПРИЁМ ЛЕКАРСТВ» caption (6.23), 0.54 gives `#728E93` for the "Stohastika" line (5.05).

Decorative alphas are not raised, otherwise the mark loses glass depth. Layers over the tile (comparison tolerance 0.02 as in `ContrastTest`):

| Layer | Dark tile `#111A1E` | Light tile `#E7F0F4` |
|---|---|---|
| Body (three stops) | 0.24 · 0.07 · 0.015 → 2.00 · 1.18 · 1.03 | 0.95 · 0.35 · 0.06 → 1.15 · 1.05 · 1.12 |
| Rim | 0.85 → 10.65 · 0.16 → 1.54 · 0.42 → 3.55 | 0.16 · 0.50 · 0.22 → 1.36 · 3.04 · 1.55 |
| Rings | 0.13 · 0.26 → 1.41 · 2.15 · 0.50 → 4.48 | 0.09 · 0.20 · 0.42 → 1.19 · 1.48 · 2.46 |
| Reflection | 0.30 → 2.43 | no layer, dark version only |
| Caustic | 0.62 → 2.29 | 0.55 → 1.99 |
| Crescent | 0.82 → 9.95 (holds the mark) | 0.95 → 1.15 (white crescent) |
| Dot | 0.92 → 12.34 (holds the mark) | 0.72 → 5.76 |

The only weak layer above threshold is the middle rim stop on the light tile, 3.04. The warm palette has the same mark, layers, and alphas with mark paint `#F8F6EF` on the dark tile and `#241F1B` on the light tile, dark tile `#181712`, light tile `#EFE7DB`, arc and caustic `#B5C47A`.

Two icon variants, one geometry, two icons of one application:

| Variant | Tile: light · dark (`-night`) | Mark paint: light · dark | Arc and caustic | Launcher name |
|---|---|---|---|---|
| Cold, base | `#E7F0F4` · `#111A1E` | `#0B2831` · `#C4F0F7` | `#A86A22` | «Склянка» (`app_name`) |
| Warm | `#EFE7DB` · `#181712` | `#241F1B` · `#F8F6EF` | `#B5C47A` | «Склянка» (`app_name`) |

Layers: background (108 × 108 in the variant tile color with its `-night` color), foreground (mark in a field of 60 of 108), monochrome (system-colored silhouette, shared). Mechanism: two `activity-alias` entries carry the `MAIN`/`LAUNCHER` filter removed from `MainActivity`, exactly one enabled (`setComponentEnabledSetting`, `DONT_KILL_APP`), the choice persisting across restarts; both use `android:label` = `app_name`. `shortcuts.xml` and `ic_notification_mark` do not change. The variant recolors no screen; the launcher caches the icon, so no "done" state is shown. Flat reduction uses mark paint `#0B2831` on the light tile, in the empty state (92 dp) and the "About" plate (40 dp).

### 2.5 Accent themes

A theme is the accent and its family and nothing else. Four palettes, all free; background, surface, text, lines, outcome roles, and the mark palette are untouched. A theme changes exactly `accent`, `accentSoft`, `accentBorder`, `onAccentSoft`, `onAccent`. The dark environment is not a theme and is not chosen.

| Theme | Light: `accent` · `accentSoft` | Dark: `accent` |
|---|---|---|
| Olive, base | `#5F6F2E` · `#EBEEDA` | `#B5C47A` |
| Indigo | `#3E4FA8` · `#D9DCEB` | `#A9B6EA` |
| Pine | `#1E5533` · `#D9EDE1` | `#8DC7A2` |
| Amethyst | `#6E3A72` · `#ECD9ED` | `#CBA6D8` |

Derivations, identical for all: `accentBorder` = accent toward `surface` by 0.30; `onAccentSoft` = accent toward `text` by 0.50, one value for both environments; `onAccent` = light `surface` on light and `background` on dark (white on the dark accent would give 1.88); dark soft plate = dark accent toward white by 0.55. For the base olive theme the derived dark `accentSoft` is `#DEE4C3`. A new theme costs three values. The first four columns are normative, the last is derived:

| Theme | `accent` on `background` · on `surface` | `onAccent` on `accent` | Dark: `accent` on `background` | `onAccentSoft` on `accentSoft`: light · dark |
|---|---|---|---|---|
| Olive | 5.11 · 5.53 | 5.53 | 9.52 | 8.24 · 7.41 |
| Indigo | 6.72 · 7.27 | 7.27 | 9.03 | 8.30 · 8.47 |
| Pine | 8.08 · 8.74 | 8.74 | 9.25 | 10.09 · 9.32 |
| Amethyst | 7.73 · 8.36 | 8.36 | 8.53 | 8.98 · 8.79 |

The last column belongs to its own plate and is held by the AA threshold 4.5 and the derivation test, not by the table. Tightest pair is the base 5.11, margin 0.61.

Hues are spread 75° (olive), 143° (pine), 230° (indigo), 296° (amethyst), neighbors at least 65° apart. No theme takes `warning`/`danger` `#8F3E23` (15°); the themes avoid `taken` `#1F5F6B` (189°) and `takenPartial` `#6E9FA8` (193°), nearest Pine at 47°. The dark accent is lighter because it also serves as text on `background` `#181712` (all four 8.53–9.52); `#5F6F2E` on dark gives 3.25. Not a theme: the dark environment, the profile palette (§2.2), the mark palette (§2.4), the application icon (§5.17).

## 3. Typography

Both families live in `core-ui/src/main/res/font/` and load directly; network font loading is inadmissible. The build includes Golos Text 400/500/600/700, Literata 600, and `literata_time` (Literata 600 at a 1.125 em line) for time dialog digits. Literata Regular is not included.

### 3.1 Heading scale, Literata 600

| Style | Size | Line height · tracking | Where it applies |
|---|---:|---|---|
| `Display` | 64 sp | 1.0 · −0.02em | intake time on the reminder and self-check card |
| `Headline` | 32 sp | 1.15 · −0.01em | medication name on the reminder, selected date heading |
| `TitleScreen` | 30 sp | 1.15 · −0.01em | screen title: «Сегодня», «Лекарства», «История», «Настройки» |
| `Title` | 28 sp | 1.2 · −0.01em | entity name, onboarding step heading |
| `Stat` | 26 sp | 1.0 · 0 | number in a metric tile |
| `Subtitle` | 23 sp | 1.25 · 0 | empty state heading |

### 3.2 Interface scale, Golos Text

| Style | Size / weight | Line height · tracking | Where it applies |
|---|---|---|---|
| `BodyLarge` | 17 / 600 | 1.3 · 0.01em | schedule-row time, dose under a name, compact collapsed-header title, primary dark-form label |
| `Body` · `BodyStrong` | 16 / 400 · 600 | 1.4 · 0 | input text, screen-form button labels, key value |
| `BodyDefault` · `BodyDefaultStrong` · `BodyDefaultMedium` | 15 / 400 · 600 · 500 | 1.45 · 0 | list row, medication name in an intake card, modal header action (400 leading, 600 confirmation), dark-form secondary label (500), «Добавить профиль» (500, §5.13) |
| `Label` · `LabelStrong` | 14 / 500 · 600 | 1.3 · 0 | card-form and quiet row button labels, time label above a group, right-hand row value |
| `Caption` · `CaptionStrong` · `CaptionSmall` | 13 / 400 · 600; 12 / 400 | 1.35 · 0; 1.45 · 0 | secondary card line, dose and strength, banner text, mark time, explanations, statuses, field labels and errors |
| `Overline` · `OverlineDate` · `MicroOverline` | 12 / 600 · 12 / 400 · 10 / 400 | 1.2 · 0.06em · 0.08em · 0.16em, uppercase | section heading («РАСПИСАНИЕ», «ОСТАТОК») · date above «Сегодня» · calendar weekdays |
| `Micro` · `MicroStrong` | 11 / 500 · 600 | 1.3 · 0 | bottom bar labels, week legend, «отметили вы» |

Rules: Literata covers headings, numeric metrics, and the reminder time, everything interface is Golos Text. Every weight has its own constant, no weight parameter and no 12 sp/500 step exist. `includeFontPadding = false` everywhere. Uppercase overlines use one transformation with the interface locale (`component/ScreenHeader.kt`), never `uppercase()` at a call site. A numeric metric inside a row is a `NumberSpan` fragment (Literata 600).

Button labels by form: screen `BodyStrong` 16/600; card `LabelStrong` 14/600 and `Label` 14/500; quiet row `LabelStrong` 14/600; modal header action `BodyDefault` 15/400 and `BodyDefaultStrong` 15/600; dark form `BodyLarge` 17/600 and `BodyDefaultMedium` 15/500. No separate button sizes exist; 14, 15, 16, 17 are scale steps assigned to forms. Brand block styles (title 42 and 62 sp, label 12 and 14 sp) are outside the scale.

## 4. Geometry

**Radii**, `SklyankaShapes`: `Small` 9 dp (month calendar cell) · `Compact` 12 dp (small button 36–38 dp, tab in a segment, small field) · `Button` 13 dp (intake card button, dosage-form chip, icon plate 40×40) · `Medium` 14 dp (input field, time row, banner) · `Large` 16 dp (primary screen button, settings group card, metric tile, dialog shell, sheet top corners `TopLarge`) · `Card` 18 dp (intake card, medication card, section card, dark form buttons) · `Pill` 999 dp (chip, switch, avatar, progress bar, round mark button). Outside the steps: `Preview` 22 dp for the 72×72 preview tile. The 62×62 medication title icon plate uses `Large` 16 dp; the 20 dp mockup value does not exist in code; the 48 dp icon mask is a resource.

**Spacing**, `SklyankaSpacing`: `None` 0 dp (dark form label field) · `Hair` 2 dp · `Micro` 4 dp (segment and step indicator gap, weekday row gap, minimum air between label and dark button frame) · `Small` 8 dp · `Default` 10 dp · `Medium` 12 dp · `Large` 14 dp · `Block` 16 dp (horizontal snackbar padding, its rise) · `Section` 20 dp (screen side padding) · `Screen` 24 dp (onboarding and reminder side padding, tab bar bottom padding, dialog padding) · `EmptyStateSide` 44 dp. Steps of 3, 5, 6, 7, and 13 dp do not exist and round to the nearest.

**Sizes**, `SklyankaSizes`:

| Item | Value |
|---|---|
| Touch target | 44 dp |
| Primary button height: screen · card · dark; radius 16 · 13 · 18 dp | 52 · 44 · 58 dp |
| Secondary button height: screen · card · dark | 52 · 44 · 54 dp |
| Label side padding: light forms · dark | 28 · 0 dp |
| Input field: normal · compact numeric · multiline (minimum) | 50 · 42 · 112 dp |
| Chip 44 dp with 14 dp side padding · switch 52×32, thumb 26, padding 3 · round mark button 44, glyph 18 · "add" button 60, glyph 26 · chevron 16 |
| Progress bar 8 dp · week bar 7 dp · weekday 26 dp · step indicator equal width shares 4 dp high · profile stripe 6 dp |
| Header: top padding 30 dp (24 dp in a foreign profile, where the stripe takes 6 dp), bottom 14 dp for a header outside the scroll, side padding 20 dp; a header inside the scroll has no own bottom padding. "Top inset + header top padding" at most 56 dp from the top edge |
| Borders: thin 1 dp · accent 1.5 dp · divider 1 dp |
| Icon plate: intake 40 · list 42 · title 62 · preview 72 dp; silhouette 20 · 22 · 34 · 38 dp; dosage-form tile 80 with silhouette 30 dp; tablet-shape tile a square by a share of row width (62 dp at 390 dp width, gap 10 dp) with silhouette 30 dp |
| Text column in an "icon — text — action" row at least 96 dp · action column of a permission row 100 dp |
| Empty state mark 92 dp · splash 170 dp · logo 128 dp · brand block gap 34 dp |
| Pinned compact header row 56 dp · back chevron glyph 22 dp in a 44 dp target · row chevron 16 dp |
| Scroll bottom inset under an overlay: overlay height + 2 × `SklyankaSpacing.Section`, 100 dp for the 60 dp round button (§4.1) |
| Dark button check mark 20 dp with 2.4 dp stroke · sheet success check mark 72 dp with 6 dp stroke |
| Widget (§5.11): `WidgetRow` 48 dp · `WidgetCompact` 250×104 · `WidgetStandard` 340×176 · `WidgetRoomy` 340×320 · `WidgetWeekCompact` 340×104 · `WidgetWeekStandard` 340×128 |
| Font-scale ceilings: `Display` 1.3 · sheet header folds above 1.3 · tab label lives at any scale and the bar grows |

A size is a lower bound, not an exact dimension: at a large system font heights grow (§6.3). Named dimensions outside the scale: `PinnedHeaderBar` 56 dp, `BackChevron` 22 dp, `FloatingActionInset` 100 dp. No dial dimensions exist among them: the M3 mechanism exposes no sizes (§5.5).

**System panels.** The application draws edge to edge (`enableEdgeToEdge` in all five activities: `MainActivity`, `PermissionsActivity`, `ReminderActivity`, `DiagnosticsActivity`, `DeliveryCheckActivity`). Background, profile tint, and dark canvas continue under the panels; content is inset once per screen frame: status bar (the header keeps its own top padding after it, the sum with the inset at most 56 dp), navigation bar (its surface continues under the panel, the tab row stays above it, bottom padding at least the system panel and 24 dp), and IME insets where input exists. Bar icons dark on light screens, light on dark screens (reminder, self-check, splash), bar transparent. In a foreign profile the header tint lies under the status bar and the 6 dp stripe is the first content element below it. No tokens are introduced: mode, padding, and icon color are platform concerns.

### 4.1 Scroll bottom inset under an anchored action

Action as a neighbor: an action panel flows under the scroll and covers nothing.

- Scroll bottom padding `SklyankaSpacing.Screen` (24 dp), the air between the last block and the panel; the button height is not added.
- Panel on `background`, no shadow, no divider, top gap `Large` 14 dp, bottom padding 24 dp.
- The system navigation bar is taken once per screen frame (`navigationBarsPadding`), not as a second padding inside the panel.
- `imePadding()` is applied once to the scrolling part: with the keyboard open the bottom padding does not double and the panel goes entirely under the keyboard.

The rule covers the medication form, the medication card, profile deletion and replacement confirmation, the medication list, and the archive.

Action as an overlay: the round button and snackbar lie over the scroll, so the scroll bottom padding is the overlay height plus two `Section` steps, which is `FloatingActionInset` 100 dp for the 60 dp button. A smaller padding makes the last card unreachable. The tab bar is not an overlay: full width, fixed compact height, 4 dp top and bottom padding, content centered; its surface continues under the system navigation exactly by its inset (no extra strip at a zero inset, growth by a three-button bar height, tab row staying above), side insets shift the row, not the surface; the scroll gets a bottom padding equal to the bar height.

## 5. Components

A component takes tokens and ready-made strings, never colors or the domain model; `core-ui` holds no Russian text. Components own their geometry, so a screen cannot choose another height, radius, or type size.

| Component | States and variants |
|---|---|
| `IntakeCard` | `Pending`, `DueNow`, `Taken`, `Missed`, `AsNeeded` (§5.6); a foreign profile is not a state, and the «отметили вы» ("marked by you") caption is a parameter |
| `Chip` | `Info`, `Unselected`, `Selected`, `Disabled`; selectable chip ≥44 dp, selected border 1.5 dp; `Info` is not clickable and carries no `selected` or `disabled` semantics |
| `PrimaryButton`, `SecondaryButton`, `QuietButton` | `Normal`, `Pressed`, `Disabled`, `Busy`; kinds `Primary/Danger`; forms `Card/Screen/Dark`, quiet `Row/Header` |
| `SklyankaTextField`, `SklyankaNumberField` | `Empty`, `Filled`, `Focused`, `Disabled`, `Error`; multiline is the same component |
| `SklyankaSwitch`, `SklyankaSwitchRow` | `On/Off/Disabled`; the whole row is the target |
| `Banner` | `Warning/Profile/Neutral`; at most one quiet action; no danger banner |
| `ProgressBar`, `OnboardingSteps` | `Day/Course/Stock/StockCritical`; always with text naming the same number |
| `EmptyState` | flat mark 92 dp in `#0B2831` on the light plate (§2.4), `Subtitle`, `BodyDefault` in `textMuted`, button, side padding 44 dp |
| `ScreenHeader`, `ProfileIndication` | `Root/Nested/Modal`; pinned compact row 56 dp; a locally built header must still place `ProfileIndication` |
| `ProfileStripe`, `ProfileChrome`, `ProfileAvatar` | 6 dp stripe, not clickable, not hidden on scroll; the wrapper draws five of six foreign markers once at the root; the avatar is an indicator, not a switch (§5.8) |
| `BottomBar` | `Today/Meds/Settings`; full width, 4 dp vertical padding, equal shares, selected `accent` 600, others `textMuted`; grows with font scale |
| `Snackbar` | one variant, one quiet «Отменить» ("Undo"); no shadow, no icon; live region `Polite` |
| `SklyankaBottomSheet` | `scrim` 0.32 (§2.3); content-sized panel, `TopLarge` corners; no handle, no drag-dismiss; header and actions pinned, middle scrolls |
| `PrivacyConsentDialog` | `surface`, 1 dp border, `Large`, `scrim`; «Принимаю» ("I accept") primary, «Отказываюсь» ("I decline") secondary; back and outside taps do not dismiss |
| `DateTimePickerDialog`, `ListPickerDialog` | `surface`, 1 dp border, `Large`, `scrim`; rows ≥44 dp, choice applies at once; 12/24-hour from the system; dial per §5.5; date grid ≥336 dp |
| `AdherenceWeek` | `Taken/Partial/Missed/Today/Empty`; each day has a screen-reader label; an empty week collapses (§5.2) |
| `MedicationGlyph`, `MedicationPreview` | six forms, five variants on tablets only; optical box 0.84 of the side; preview 72 dp tile, `Preview` radius, 38 dp silhouette |
| `StackingRow`, `StackingEqualButtons` | the action goes under the text; the button row breaks into a column at the measured threshold (§5.1) |
| Glyphs (`CheckGlyph`, `WarningGlyph`, `InfoGlyph`, `ChevronGlyph`, `ChatGlyph`, `ProfileGlyph`, `MedicationPictogram`, `CalendarGlyph`, `GearGlyph`, `FlatMark`) | drawn on `Canvas` from tokens; decorative glyphs are not announced |
| `SklyankaMark`, `SklyankaLockup` | five reduction steps (≥90 dp glass, 45–89, 29–44, 19–28, ≤18 dp) by `markVariantFor(size)`; the brand block sits on dark in the cold palette (§2.4) |

### 5.1 What must be assembled from components, not written at the call site

1. **Buttons.** `ButtonForm` sets height, radius, and label size, never the screen. A pair splits the row in half, hierarchy is carried by fill and label, and the row is built with `StackingEqualButtons`.
2. **Row threshold.** A row is allowed only if each one-line label fits in half the row with at least `minLabelSideGap` to the frame (4 dp in the dark form): `max(w₁, w₂) + 2 × gap + 2 × labelPadding ≤ (W − 12 dp) / 2`. If one label fails, both buttons stack full width; labels are never wrapped or truncated.
3. **Header and indication.** A screen uses `ScreenHeader`; a locally built header (the medication card title) must still place `ProfileIndication`, or the «Профиль · Имя» ("Profile · Name") line and the banner drop out, and the banner cannot be switched off separately.
4. **Row with a trailing action.** The action is a fixed 100 dp column, label flush left, chevron flush right; the state slot is reserved in every state, so the text column keeps one width whether the row is selected or not.
5. **Dialogs.** Date, time, and list choice are system components, not the platform dialog, whose look is set by the firmware.
6. **Snackbar and banner are not interchangeable.** The banner is permanent and warns, the snackbar temporary and confirms. No snackbar without an action; no toast.
7. **The mark.** The reduction step is chosen from the size; the light flat reduction is drawn in `#0B2831` on the light plate, not in an interface role, at 92 dp in the empty state and 40 dp on the "About" plate.
8. **Widgets.** A widget is assembled from tokens and reads theme roles, never palette literals. Grades come from `SklyankaSizes` (`WidgetCompact`, `WidgetStandard`, `WidgetRoomy`, `WidgetWeekCompact`, `WidgetWeekStandard`, `WidgetRow`).

### 5.2 The last seven days block

Overline «Последние 7 дней» ("Last 7 days") in `Overline` 12/600 `textMuted`; at the right on the same baseline a `CaptionSmall` 12/400 `textMuted` counter, «12 из 14 приёмов» ("12 of 14 intakes") via `plurals`; then seven columns and a legend. A column is a day label (`Micro` 11/500 `textMuted`, today `MicroStrong` 11/600 `text`) and an `AdherenceDay` circle 26 dp with a `Small` gap. Row-to-legend gap `Large`, legend item gap `Block`, legend dot `AdherenceLegendDot` 10 dp.

| State | Drawing |
|---|---|
| `Taken` | 26 dp circle filled with `taken` |
| `Partial` | 26 dp circle filled with `takenPartial` |
| `Missed` | 26 dp circle filled with `missed` |
| `Today` | 26 dp hollow circle, 2 dp `AdherenceRing` stroke in `missed` |
| `Empty` | 10 dp dot in the day field, `switchOff` |

**Empty week.** With no completed day carrying intakes the block collapses to one line: the overline and, where the counter sits, «Приёмов пока нет» ("No intakes yet") in `CaptionSmall` `textMuted`; columns, today circle, and legend are not drawn. "0 of 0" and "0 %" are never shown, while "0 of 14" stays a number.

### 5.3 The dose row of the medication form

**Two blocks, not one row with two fields**, so that an unfinished form is never read from a single heading over two fields, the second unnamed.

| Block | Field | Unit | Required |
|---|---|---|---|
| «Доза на один приём» ("Dose per intake") | quantity, `SklyankaNumberField`, `FieldHeight` 50 dp, `Medium` radius | suffix inside the field (`Caption` 13/400, `textMuted`), by release form: tablet, capsule, ml, drop, spray, injection | yes |
| «Дозировка препарата» ("Medication strength") | strength, same field | closed list {mg, mcg, g, ml, IU, %}, default "mg" | no |

Dose comes first, then strength; both headings are `Overline`. The unit belongs to the **value on screen**, not the input: the field prints it outside the caret, and it follows the number in preview and card. Requirement is a form convention, not the word "required": required fields stay silent, optional ones name themselves; under strength a `CaptionSmall` 12/400 `textMuted` note starts with «Необязательно.» ("Optional.").

**A stepped schedule has no dose block**, since the stages carry doses (§5.10); strength stays. **Empty fields are normal**: the save button is unavailable only for a missing name, an empty strength is legal, and an empty dose is a violation shown with the existing error state (`danger` border, `CaptionSmall` label on the left). **The preview** updates with the draft and prints form, variant, and numbers in that order, «2 таблетки · 48 мг» ("2 tablets · 48 mg"); an empty strength drops out.

**The dose unit word** is the field suffix, by release form, living in `plurals` (`field_unit_*`) without a number, since the field prints the number. **Agreement is rewritten on every change:**

1. **Number typed** — the word takes that number's form: «1 таблетка», «2 таблетки», «5 таблеток», «1 капля», «2 капли», «5 капель», from one `plurals` line, never a call-site concatenation.
2. **Field empty** — the name form, the one used for 1: «таблетка», «капсула», «капля», «впрыск», «инъекция», «мл»; stock and refill rows use the same form.
3. **Fraction** (0.5 · 1.5 · 2.5) — genitive singular: «0,5 таблетки», «1,5 капли», «2,5 мл». A fraction uses the plural category of 2 — `few` in Russian, which must carry the same word as `other`, and `other` in the other five catalogs.
4. **Zero** takes the plural genitive like 5 and 11: «0 таблеток». «мл» never changes.

| Key (`plurals`) | Release form · `DoseUnit` | 1 · 2 · 5 · 0.5 |
|---|---|---|
| `field_unit_tablets` | «Таблетка» ("Tablet") · `tablet` | «таблетка · таблетки · таблеток · таблетки» |
| `field_unit_capsules` | «Капсула» ("Capsule") · `capsule` | «капсула · капсулы · капсул · капсулы» |
| `field_unit_millilitres` | «Сироп» ("Syrup") · `millilitre` | «мл · мл · мл · мл» |
| `field_unit_drops` | «Капли» ("Drops") · `drop` | «капля · капли · капель · капли» |
| `field_unit_sprays` | «Спрей» ("Spray") · `spray` | «впрыск · впрыска · впрысков · впрыска» |
| `field_unit_injections` | «Инъекция» ("Injection") · `injection` | «инъекция · инъекции · инъекций · инъекции» |

**The strength unit is a target:** tapping it opens `ListPickerDialog` with the six values, a 14 dp down chevron (`FieldUnitChevron`) beside it, and the target spanning the field height of 50 dp, since a 13 sp word alone is under 44 dp (§6.2). Its accessible name is «Единица дозировки» ("Strength unit"), and the default "mg" is visible before any number is typed. The dose suffix stays a label: derived, never chosen.

### 5.4 The summary tiles of History

Three equal tiles, `Default` gap: `surface`, 1 dp `lineOnSurface`, `Large` radius, `Medium` 12 side and `Large` 14 vertical padding, `Micro` 4 inner gap; number `Stat` (Literata 600, 26 sp, `text`), label `Micro` 11/500 `textMuted`.

**The rate tile is a fraction, not a counter.** «Принято вовремя» ("Taken on time") is a percentage: numerator — intakes marked taken inside [−2 h, +2 h] of the scheduled time; denominator — elapsed scheduled intakes (taken + consciously missed + missed without a mark), as-needed excluded from both. Round to a whole percent; a non-zero numerator rounding to zero shows «< 1 %». There is no dash: the fraction is computed or the tile is absent.

**Hiding.** No elapsed scheduled intake (as-needed-only profile, empty period) hides the tile; the other two split the row **in half**, with no filler column, and their labels fit one line in all six languages at 100 %. A small number is not masked, and the tile reads as one node, "taken on time, 12 of 14 intakes".

The other tiles are counters with a number, zero included: «Дней без пропусков» ("Days without misses") counts completed days on which everything scheduled was taken, today excluded; «Пропусков за месяц» ("Misses this month") counts human-marked and automatic misses together.

### 5.5 The time dialog dial and what M3 allows

The mechanism stays M3; no custom dial is drawn.

| Parameter | How it arrives | Effect |
|---|---|---|
| Dial field color (`clockDialColor`) | `TimePickerDefaults.colors(...)` | the field reads as a disk, not a hole |
| Selection colors (`selectorColor`) | same parameter | selected digit `onAccent` on an `accent` circle, others `text` |
| Digit typography | local `MaterialTheme(typography = …)`, as for the slots (`LiterataTime`, 72 sp line height) | larger, denser digits |
| Digit scale | `LocalDensity(fontScale = 1f)` | digits do not grow with the system font |
| `layoutType` | mechanism parameter | at 390 dp only the vertical variant applies |

The dial field takes `neutralSoft` so the circle reads as an object, not empty space, and its digits are `BodyLarge` (Golos Text 17/600) at a fixed scale, since `Subtitle` (23 sp) is too large for 24 numbers in one M3 ring.

### 5.6 Intake card states

Five `IntakeCardState` values; the component owns the dictionary and screens add nothing.

| State | On screen |
|---|---|
| `Pending` | future intake: 44 dp round check button, no mark |
| `DueNow` | due intake: two equal buttons «Принято» ("Taken") and «Пропустить» ("Skip"), the card demands action |
| `Taken` | marked intake: check mark and actual time, no buttons |
| `Missed` | overdue and missed: muted card, no mark buttons, status as a word |
| `AsNeeded` | as-needed: dashed border, «Принять» ("Take") button, one-per-day limit |

### 5.7 Confirming a destructive action by a typed word

The action is confirmed by typing a sample word, part of the sentence «Введите слово %1$s, чтобы подтвердить» ("Type %1$s to confirm"), one resource for all actions; the field is `SklyankaTextField` at `FieldHeight` 50 dp; the button is a `PrimaryButton` of kind `Danger`.

- **Input.** Plain text, caret in the field, autocorrection and auto-capitalization off, since the system must not "improve" a copied word. Comparison ignores case; the field never enters an error state, and wrong input is visible through the unavailable button.
- **Sample word** stands as a visible line above the field, so it is copied by eye; comparison uses the same resource. Matching is `trim()`-ed, case-insensitive, with inner spaces significant.
- **One component.** Field, caption, and button are one design-system component (`ConfirmWordField`), sharing look and the comparison rule; consumers are profile deletion, replacement confirmation, and the medication deletion sheet.
- **Accessibility.** The caption is read as is; the field has a `contentDescription`; traversal is warning → caption → field → button; confirmation is not carried by button color. At 200 % the caption wraps, the field and button grow (`FieldHeight` is a lower bound), and the sheet panel grows to its ceiling and scrolls with actions visible.

| Action | Word | Resource |
|---|---|---|
| Profile deletion | «Удалить» ("Delete") | `profile_delete_word` |
| Medication deletion | «Удалить» ("Delete") | `medication_delete_word` |
| "Replace everything" | «Заменить» ("Replace") | `transfer_replace_word` |

### 5.8 The profile avatar in the header

The avatar is a **profile indicator**, not a switch: it shows whose screen is open and changes the profile on no tap.

- **Visible.** A 30 dp `ProfileAvatar` circle with the initial and a 14 dp down chevron inside a `ProfileAvatarButton` pill 44 dp, 1 dp `lineInteractive` border, `surface` background, `ProfileAvatarPadding` 6 dp. Owner: circle `takenSoft`, initial `taken`, the literal «Я» ("Me"). Foreign profile: circle `accent`, initial `onAccent`, first letter of the name.
- **Foreign intakes:** always visible in a foreign profile, including the collapsed header, its profile color being one of the six markers.

### 5.10 The schedule view of the medication form

Five kinds, all unrestricted: every day, by weekdays, every few days, in cycles, by stages. The form shows the default kind (every day) and six block states — the four complex kinds, the kind list, and as-needed.

**The kind is chosen by a row, not a third segment.** The «Режим приёма» ("Intake mode") row answers whether time is needed and keeps two segments; the kind asks which rule generates the days, five values do not fit a segment. The block opens with a **kind row** like the period row: «Расписание» ("Schedule") in `BodyDefault` 15/400 `text`, the value in `BodyDefaultStrong` 15/600 `text`, a 14 dp down chevron in `iconFaint`. The row is clickable in full (≥44 dp) and opens `ListPickerDialog` titled «Расписание».

**Order is the order of the norm:** «Каждый день», «По дням недели», «Через несколько дней», «Циклами», «По этапам»; not sorted by frequency. Parameters sit under the row, then the unchanged «Приёмов в сутки» ("Intakes per day") and «Время приёма» ("Intake time").

| Kind | Parameters | Assembly |
|---|---|---|
| Every day | none | — |
| By weekdays | «Дни недели» ("Days of the week"), seven chips, note «Больше одного дня» ("More than one day") | `Chip` `Selected`/`Unselected` at `ChipHeight` 44 dp with `Micro` 4 dp gaps: 7 × 44 + 6 × 4 = 332 dp fits 350 dp of screen width |
| Every few days | «Интервал, дней» ("Interval, days") with an explanation | `BodyDefault` 15/400 label, `SklyankaNumberField` of `StockField` width 54 dp |
| In cycles | «Дней приёма подряд» ("Intake days in a row") and «Дней перерыва» ("Pause days") with an explanation | same rows; the label names the unit |
| By stages | stage list, dashed «Добавить этап» ("Add stage"), two explanations | stage card below |

**A stage is a card like a time row:** `surface`, 1 dp `line`, `Medium` radius, `Large` side and `Small` vertical padding. Header «Этап N» ("Stage N") in `BodyDefaultStrong` 15/600 with a delete cross (`GlyphButton`, 44 dp target, label «Удалить этап N»); under it two numeric fields with the unit word **inside** — dose with `field_unit_*`, duration with `form_stage_days_*`. No visible labels; screen-reader names `cd_stage_dose` and `cd_stage_days`. The cross exists from two stages up; stage count is unlimited, and at a large font the fields stack. «Добавить этап» reuses «Добавить время»: a dashed full-width button of `SecondaryButtonHeight` with a `Label` 14/500 `accent` caption. Two `CaptionSmall` `textMuted` notes follow: «Доза приёма задаётся этапами» ("The intake dose is set by stages") and «Этапы идут подряд от даты начала курса» ("Stages run consecutively from the course start date").

**Refusals** are worded and block saving; `ViolationText` prints them under their own block and the field takes `Error`:

| Case | Words |
|---|---|
| No weekday | «Выберите хотя бы один день недели» ("Choose at least one day of the week") |
| Interval under two days | «Интервал не меньше двух дней» ("The interval is at least two days") |
| Interval of one day | «Один день — это ежедневное расписание» ("One day is a daily schedule") |
| Zero cycle | «Длина цикла не может быть нулевой» ("The cycle length cannot be zero") |
| Stage without dose or duration | «Задайте дозу приёма для каждого этапа» · «Задайте длительность каждого этапа» |
| Stages past the end | «Этапы выходят за конец курса» ("Stages run past the end of the course") |

### 5.11 Home-screen widgets

Three types: «Сегодня» ("Today") — the day's intakes with quick marking; «Неделя» ("Week") — the last seven days; «По лекарству» ("By medication") — one medication's intakes. A widget lives on the firmware home screen, is assembled from the same tokens (§5.1), and overrides no roles, so a foreign profile tints it and the accent theme reaches it through the `accent` role (§2.2, §2.5). Type names live in three places: the setup row label (§5.12), the system widget list (`android:label`), and, for Today and Week, the plate header.

| Type | Compact | Standard | Roomy |
|---|---|---|---|
| Today | `WidgetCompact` 250×104: header, one row | `WidgetStandard` 340×176: header, counter, two rows | `WidgetRoomy` 340×320: header, counter, up to five rows |
| Week | `WidgetWeekCompact` 340×104: header, strip | `WidgetWeekStandard` 340×128: header, counter, strip | none, since no empty height is declared |
| By medication | 250×104: header, one row | 340×176: header, counter, two rows | 340×320: header, counter, up to five rows |

The plate is `surface` with 1 dp `lineOnSurface`, `Card` 18 dp radius, no shadow, rows split by a 1 dp divider. The header names the subject in `BodyDefaultStrong` 15/600 `text` (one-line truncation, the same single exception as the medication list); the counter is its own line in `CaptionSmall` 12/400 `textMuted`, «Принято 2 из 5 приёмов» ("2 of 5 intakes taken") from `intakes_taken_of_total`, counting completed days only.

- **Time column:** ≥44 dp, `HH:mm` atomic, `LabelStrong` 14/600 `text`; under a due intake «Сейчас» ("Now") in `CaptionStrong` 13/600 `accent`.
- **Text:** name `BodyDefaultStrong` 15/600 (`text`, `textMuted` when past and unmarked), dose in `Caption` 13/400 `textMuted`; no «отметили вы» caption.
- **Action:** `MarkButton` 44 dp, `surface`, 1.5 dp `lineInteractive`, 18 dp check in `textTertiary`; a due intake gets the only `accent` fill on the plate with an `onAccent` check; a past unmarked intake shows «Пропущено» ("Missed") in `CaptionStrong` 13/600 `textMuted`.

Rows are unmarked intakes of the day, and states are worded: «Сейчас», «Пропущено», «Всё отмечено» ("All marked"), «На сегодня приёмов нет» ("No intakes today"), «Пока ничего не запланировано» ("Nothing scheduled yet"), and «Приёмов пока нет» with no strip for an empty week. A tap marks the row like the app does and a tap on the plate opens the application, targets are ≥44 dp with names `cd_widget_open` and `cd_mark_intake`, and a foreign profile adds a 6 dp stripe and a «Профиль · Имя» overline. Glance lacks the 600 weight, so 600 steps are drawn Bold. Out of scope: skips, group marking, unmarking, stock, banners, per-instance profile choice, as-needed intakes.

### 5.12 The widget setup screen

Installation goes through a setup screen (the firmware configuration activity), unrestricted; it exists because By medication requires a medication from the active profile. Header: «Виджет» ("Widget") in `TitleScreen` 30/600 `text`, `HeaderTopPadding` 30 dp, `Section` 20 dp at the sides; no back chevron and no «Отмена» ("Cancel"), the only exit being system back. Under it, `BodyDefault` 15/400 `textMuted`: «Виджет показывает приёмы активного профиля» ("The widget shows intakes of the active profile"). Markers come from `ProfileChrome` and `ProfileIndication`.

| Block | Contents |
|---|---|
| Rubric «Тип виджета» ("Widget type") | `Overline` 12/600 `textMuted` |
| Type card | `surface`, 1 dp `lineOnSurface`, `Card` 18 dp; rows in `BodyDefault` 15/400 `text`; the selected row `BodyDefaultStrong` 15/600 `accent` with a `CheckGlyph` and `selected` semantics |
| Medication card (By medication only) | «Лекарство» ("Medication") in `BodyDefault` 15/400, value in `LabelStrong` 14/600 `text`, `Chevron` 16 dp `iconFaint`; choice applied at once |
| Action panel (§4.1) | on `background`, no shadow or divider: full-width button minus `Section` 20 dp |

Medication row states: nothing selected — «Не выбрано» ("Not selected") in `textMuted`, row clickable, completion `Disabled`; selected — the name in `LabelStrong` 14/600 `text`; no medications — no value and no clickability, «У профиля пока нет лекарств» ("The profile has no medications yet") in `CaptionSmall` 12/400 `textMuted`. Completion is «Добавить виджет» ("Add widget"), a `PrimaryButton` of `Screen` form, unavailable only for an incomplete choice; no widget preview is drawn.

### 5.13 The "Add profile" row and the new-profile wizard

The row is last in the profile list and **before** the deletion row, since a destructive row stays last. Assembly is the settings row: a plus `PlusGlyph` 16 dp in an `IconPlate` 40 dp with a 1 dp dashed `lineInteractive` outline; «Добавить профиль» ("Add profile") in `BodyDefaultMedium` 15/500 `accent`; the whole row is the target, at least 44 dp. Label wrapping is allowed, an ellipsis is not.

| State | Visible |
|---|---|
| Fewer than five profiles | the row as is |
| Five profiles | under the label, `CaptionSmall` 12/400 `textMuted`, the reason «Профилей не больше пяти» ("No more than five profiles"), read before the tap |

The wizard is a second-level screen without a back chevron: `TitleScreen` «Новый профиль» ("New profile"), no header action, the only exit system back, completion on the bottom button. Blocks: «Имя профиля» ("Profile name") in `Overline` 12/600 `textMuted` over a `SklyankaTextField` of 50 dp (`Medium` radius, `surface`, 1 dp `line`), empty in a new wizard; «Цвет профиля» ("Profile color") with five `IconPlate` samples 40 dp in 44 dp targets and `Small` 8 gaps, 252 dp total; a `SklyankaSwitchRow` «Дублировать напоминания» ("Mirror reminders") with the explanation «Напоминания о приёмах этого профиля будут приходить на этот телефон» ("Intake reminders of this profile will arrive on this phone"), **on** by default; and an action panel on `background` with the reason above the button.

A color sample is a choice: each is ≥44 dp with `RadioButton` role in a `selectableGroup` and an accessible name from the color name, and the selected one is distinguished **not by color alone** (§6.4) — a 2 dp `accent` ring and an 18 dp `CheckGlyph` in `onAccent` inside, a white check holding AA on all five colors (6.59-8.94). The default is the first free color of the set.

| Reason | Words | Button |
|---|---|---|
| Name empty | «Укажите имя профиля» ("Enter a profile name") | `Disabled` |
| Five profiles | «Профилей не больше пяти» | `Disabled`, the limit is not bypassed |

Exactly one `BodyDefault` 15/400 `textMuted` reason line stands above the button. With five profiles the wizard still opens and shows the whole form, unavailable with the limit reason. A created profile does not become active by itself.

### 5.14 The profile edit screen

One form with the wizard — name heading and field, color samples, mirroring card, action panel — differing only in the title «Профиль» ("Profile"), the button «Сохранить» ("Save"), and pre-filled fields. Entry is a quiet «Изменить» ("Edit") target inside the profile row: the row body switches profiles and that does not change, while editing is a separate target — a `QuietButton` of `Row` form, `Accent` kind, `LabelStrong` 14/600, ≥44 dp — right after the profile text and before the active mark or chevron, so editing stays with the edited profile, including an inactive one.

For the owner only the name is editable: there is no color block and no mirroring switch, because the owner's reminders are always scheduled and the owner accent is the application's main accent. The absence is a rule, not a state. The name reaches the report and the transfer file, and defaults to «Я» ("Me"). Saving writes name, color, and, for a foreign profile, mirroring; an empty name closes the button with «Укажите имя профиля» above it, and there are no other reasons.

### 5.15 The doctor report: composition choice and extended sections

The composition block stands above the composition card, which keeps «Профиль / Период / Принято / Формат» ("Profile / Period / Taken / Format"): heading «Состав отчёта» ("Report composition") in `Overline` 12/600 `textMuted` and a two-row group card with the widget-type choice assembly.

| Row | Label | Explanation |
|---|---|---|
| Base, default | «Базовый» ("Base") | «Сводка и разбивка по лекарствам» ("Summary and per-medication breakdown") |
| Extended | «Расширенный» ("Extended") | «Периоды, динамика, паттерн пропусков» ("Periods, dynamics, missed-dose pattern") |

PERIOD COMPARISON uses a period of the same length immediately before the report period, with a meta line «Предыдущий период: …» ("Previous period: …") and the four summary numbers, the right field carrying the previous period's number; a row appears only when both periods can fill it. MONTHLY DYNAMICS has one row per month in chronological order — the month name from the platform formatter with the pattern "LLLL yyyy", the taken fraction, and "44 of 48" in the right field — and a month without elapsed intakes gets no row. MISSED-DOSE PATTERN reuses the History insight rule and its morning, day, evening, and night resources, with a number line («Пропусков в это время: %1$d из %2$d за период» — "Misses at this time: %1$d of %2$d over the period") and the dates split into «отмечен человеком» ("marked by the person") and «без отметки» ("without a mark"), cut by time rather than by medication.

No section without data: comparison needs elapsed intakes in the previous period, dynamics needs at least two months with elapsed intakes, and the pattern needs a single leader, at least 0.6 misses of the period, at least four misses, and at least two intake times.

### 5.16 The history CSV export sheet

Entry is a settings row «Экспорт истории» ("History export") in «Данные» ("Data") right after «Истории приёмов» ("Intake history"): `BodyDefault` 15/400, second line `CaptionSmall` 12/400 `textMuted` «CSV для Excel» ("CSV for Excel"), `Chevron` 16 dp `iconFaint`. The short form is required, since at 200 % the full wording wraps into an orphan "Excel" line.

The sheet reuses the report assembly: modal header with «Отмена» ("Cancel") leading, title «Экспорт истории», «Готово» ("Done") trailing after success, `scrim` 0.32. Blocks: an outcome banner first when present; the same four period chips with the same "Other period" bounds; a label-value card with «Профиль» ("Profile"), «Период» ("Period"), «Записей» ("Records", rows in the file for all outcomes), and «Формат» ("Format") = `CSV`, not translatable, like `PDF`; the quiet note in `Caption` 13/400 `textMuted`, «Файл содержит медицинские данные и передаётся в открытом виде.» ("The file contains medical data and is transferred unencrypted"); and the actions «Передать» ("Share") and «Сохранить» ("Save") as equal halves.

Outcomes state facts. Success replaces the content: «Файл сформирован» ("File created"), then «Передан» ("Shared") and «Сохранён» ("Saved"). Errors and cancellation: «Передача файла отменена» ("File transfer cancelled"), «Экспорт не удалось передать» ("The export could not be shared"), «Файл не сохранён» ("File not saved"), «Места недостаточно. Файл не сохранён» ("Not enough space. File not saved"). A failure shows the inline outcome plate first, before the presets, as `BannerKind.Warning` for an error and `BannerKind.Neutral` for a cancelled dialog; content survives, retry is immediate, and there is no false success. An empty period puts «За период нет ни одной записи о приёме» ("No intake records for the period") in the records row instead of zero, and the file is produced with a header and no rows.

The file is `sklyanka-history-yyyy-MM-dd.csv` from the first day of the period, media type `text/csv`, with no Cyrillic; columns are "Date · Planned time · Medication · Dose per intake · Medication strength if set · Status · Mark time · On time". Columns are machine formats and words are interface words: date `yyyy-MM-dd`, planned time `HH:mm`, mark time `yyyy-MM-dd HH:mm`, while header, labels, four statuses, and yes/no are interface words. No local quotes, non-breaking spaces, or day-month order, and an empty cell stays empty. The report document is the opposite: it is for a person and uses interface formatters («1 — 30 сентября 2026» — "1 – 30 September 2026"). Statuses: «принято» ("taken"), «принято по потребности» ("taken as needed"), «пропущено — отмечено человеком» ("missed — marked by the person"), «пропущено — без отметки» ("missed — without a mark"); "On time" is «да» ("yes") inside [−2 h, +2 h] and «нет» ("no") outside. Only one active profile is exported.

### 5.17 Appearance: accent theme and icon choice

The «Оформление» ("Appearance") block stands between «Система» ("System") and «Данные» ("Data"): a rubric «Оформление» and a two-row group card with the usual settings assembly — `BodyDefault` 15/400 label, `LabelStrong` 14/600 `text` value, `Chevron` 16 dp `iconFaint`.

| Row | Value | Tap |
|---|---|---|
| «Акцентная тема» ("Accent theme") | the selected theme's name (§2.5) | dialog, applied at once |
| «Иконка приложения» ("App icon") | «Ледяная» ("Ice") · «Тёплая» ("Warm") | dialog, applied |

The theme dialog is a `ListPickerDialog` titled «Акцентная тема» with four rows ≥44 dp, each with an `IconPlate` 40 dp sample filled with the theme's light accent, then the name, and a `CheckGlyph` with `selected` semantics; «Олива» ("Olive") comes first, being the base theme. The icon dialog has two rows with an icon thumbnail 40 dp instead of a color sample. The choice applies at once and closes the dialog, no preview screen exists, and the launcher updates the icon when it sees fit without any "done" word. The dark environment has no choice, since the environment follows the system setting.

## 6. Accessibility

### 6.1 Contrast

The ratio follows WCAG 2.1: `(L₁ + 0.05) / (L₂ + 0.05)`; AA is 4.5 for normal text and 3.0 for large text (from 18.66 px at weight 700 or from 24 px). Ratios are computed from tokens, not judged by eye (`token/ContrastTest.kt`).

| Pair | Ratio |
|---|---:|
| `text` on `background` · on `surface`; `textMuted` on `background` · on `surface` | 15.09 · 16.32 · 6.64 · 7.18 |
| `accent` on `background` · on `surface`; `onAccent` on `accent` | 5.11 · 5.53 · 5.53 |
| `surface` on `danger`; `warning` on `background` · on `accentSoft`; `onAccentSoft` on `accentSoft` | 7.29 · 6.74 · 6.17 · 8.24 |
| `taken` on `surface` · on `takenSoft`; archive: `archiveText` · `textMuted` on `archiveBackground` | 7.22 · 6.10 · 8.42 · 6.38 |
| Dark: `text` on `background`; `onAccent` on `accent`; reminder: `textOnDark` · `textMuted` · `textDisabled` on the canvas | 16.60 · 9.52 · 12.04 · 8.99 · 6.06 |

Soft-plate pairs belong to the plate, not the role: `accentSoft` is light in both environments, so paints on it are not raised for dark, but `warningOnSoft` gives 6.17 on the light plate while each dark theme has its own plate and the same role gives 5.34-5.55 — above AA, not equal to the light number.

Three declared exceptions stay exceptions: `textTertiary` (3.52 on background, 3.80 on surface) is never used below large text, and a tab label is set in `textMuted`; `textDisabled` (2.69) fails even for large text and appears only next to an already unavailable element, never as the sole carrier of meaning; `iconFaint` is for non-informative graphics only, and an informative glyph (the delete-time cross) must be darker. `accent` `#5F6F2E` is not used as text on dark (3.25) and is replaced there by `#B5C47A` (9.52); white on the light olive gives 1.88, so an accent button's label on dark is its own background (9.52). Borders provide no contrast (1.23-1.57), so a border is never the only carrier of a state. The dark environment keeps `textMuted` `#C9BFB1` at 9.89 and `textTertiary` `#A79C90` at 6.67.

### 6.2 Touch targets and traversal

The minimum is 44 dp on both sides. The visual size may be smaller, the touch area may not: `Modifier.minTouchTarget()` expands the measured area without changing the drawn size, with the click handler attached **before** the modifier. Material 3's `minimumInteractiveComponentSize()` is not used, targeting 48 dp. A conflict with a neighbor resolves in favor of the target.

Every interactive element without visible text has a `contentDescription` from `strings.xml`. Traversal matches the visual order: time → medication → dose → action; the mark button is announced with the medication name, or it is indistinguishable in a list of five cards. Decorative glyphs are not announced; a snackbar message is a `Polite` live region; a selected option carries `selected`.

Targets are ≥44 dp even with smaller drawings: the back chevron (22 dp glyph in a 44 dp target), the profile avatar (44 dp, §5.8); dial targets are mechanism constants outside the norm (§5.5). Names: «Назад» ("Back"), «Профили» for the avatar, «Слово подтверждения» ("Confirmation word"), «Дозировка» ("Strength"); confirmation traversal is warning → caption → field → button. A weekday state, a summary tile, and an intake card are read as one node with words, not color (§5.2, §5.4, §5.6).

### 6.3 Font scale up to 200 %

Text sizes are in `sp`, padding and dimensions in `dp`. No text is clipped or overlapped, screens stay passable to their final action, and the check is mandatory at 100 %, 130 %, and 200 %. An ellipsis is allowed in exactly one place, a medication name on one line, leaving at least three visible characters.

- A card grows; when width runs out the action moves under the text (`StackingRow`, text column at least 96 dp) and the mark button does not shrink. A title wraps to two lines and the header grows. A tab label lives at any scale and the bar grows. A sheet grows to its ceiling and scrolls with actions visible; the 72 dp choice preview does not grow.
- `Display` 64 sp is capped at scale 1.3 (`CappedFontScale`), otherwise the time does not fit 390 dp; the rest of the typography scales freely. The dark form's postpone/skip row stacks by measured label width; the sheet header stacks above 1.3.
- Time dialog digits do not scale (ceiling 100 %): the slot field is fixed at 96×80 dp, the digit line is 72 sp and optically centered, and the keyboard field is 96×72 dp. The same holds for dial digits and the «:» separator, whose ring, step, and selection size are mechanism constants. The time value stays large (64 sp slots) at every step, so the check sees identical digits, a growing title, and no clipping.
- Machine time `HH:mm` is atomic: one line, no per-character wrapping, column width from the widest group line at the current scale but at least 44 dp.

The compact title of a collapsed header scales as `BodyLarge` but is drawn only when it fits one line entirely at the current scale: the threshold is the measured width, not `fontScale`, which would lie on long locales (German «Benachrichtigungen prüfen» — "Check notifications" — overflows at 130 % already). If it does not fit, the title is not drawn, the row grows by the buttons up to 44 dp, and the large title is not clipped.

### 6.4 State is more than color

«Принято» ("Taken") is color plus check mark plus mark time; «пропущено» ("Missed") is color plus a caption; a progress bar always carries text naming the same number; a card requiring action differs not only by its `accentBorder` but by the expanded button row; a weekday in the strip carries a screen-reader label; a foreign profile is six markers at once — five drawn by `ProfileChrome`, the sixth («отметили вы» — "marked by you") belonging to lists and history.

Nothing weakens the rule: a weekday with no intakes differs from an outcome by drawing size, not shade (§5.2); an unavailable confirmation button is a button state, not the sole carrier of confirmation, and the sample word stands as a visible line (§5.7); a hidden summary tile leaves no empty column. Widgets carry it too: the state is worded (`widget_now` «Сейчас», `status_missed` «Пропущено»), and the foreign-profile indication is assembled from stripe, «Профиль · Имя» line, and button accent (§5.11).
