package ru.stohastika.sklyanka.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import ru.stohastika.sklyanka.ui.component.FlatMark
import ru.stohastika.sklyanka.ui.component.ListPickerDialog
import ru.stohastika.sklyanka.ui.component.ProMark
import ru.stohastika.sklyanka.ui.token.MarkEnvironment
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.flatMarkColor
import ru.stohastika.sklyanka.ui.token.lightColors
import ru.stohastika.sklyanka.ui.token.markTileBackground

/**
 * Блок «Оформление» — две строки между «Системой» и «Данными» (§5.17, SKL-120).
 *
 * Строки собраны той же сборкой, что прочие строки настроек: подпись, значение справа
 * и шеврон. Отличие одно — **метка «Про» сразу за подписью** у обеих строк, и она стоит
 * там всегда (Р-67 п. 5): метка называет строку, а не действие, и не исчезает с покупкой.
 *
 * Блок выше «Данных» намеренно: оформление принадлежит приложению, а не данным, и под
 * строку «Склянка Про» ему не место — та отвечает за подписку, а не за выбор оформления
 * (Р-67 п. 6).
 *
 * **Строки «Тёмная тема» здесь нет и не будет.** Среда интерфейса следует системной
 * настройке и принадлежит базе (Р-23, §9), а второй переключатель того же состояния
 * расходился бы с системой и обещал бы управление, которого нет (§5.17).
 *
 * Доступ строкам не проверяется: обе открывают диалог и без него (§5.17, Р-67 п. 5) — человек
 * видит четыре образца темы и обе миниатюры иконки до покупки, а «Про» стоит у платных строк
 * диалога, а не у поверхности (Р-71 не продаёт бесплатное). Решает про доступ домен
 * ([ru.stohastika.sklyanka.domain.appearance.AppearanceGate.choose]) на самом выборе, а не
 * строка — экран лишь называет намерение.
 */
internal fun LazyListScope.appearanceBlock(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    item {
        SettingsBlock(stringResource(R.string.settings_appearance_rubric)) {
            SettingsRow(
                title = stringResource(R.string.settings_theme_row),
                value = stringResource(state.accentTheme.nameRes()),
                // Зазор между последним словом подписи и меткой — `Small` (§5.9).
                titleTrailing = { ProMark(modifier = Modifier.padding(start = SklyankaSpacing.Small)) },
                onClick = { onIntent(SettingsIntent.OpenThemePicker) },
                modifier = Modifier.testTag(TAG_THEME_ROW),
            )
            RowDivider()
            SettingsRow(
                title = stringResource(R.string.settings_icon_row),
                value = stringResource(state.iconVariant.nameRes()),
                titleTrailing = { ProMark(modifier = Modifier.padding(start = SklyankaSpacing.Small)) },
                onClick = { onIntent(SettingsIntent.OpenIconPicker) },
                modifier = Modifier.testTag(TAG_ICON_ROW),
            )
        }
    }
}

/**
 * Выбор акцентной темы (§5.17).
 *
 * Четыре строки высотой не меньше 44 dp (это держит сам `ListPickerDialog`), слева —
 * образец цвета `IconPlate` 40 dp, залитый **светлым** акцентом темы: тема выбирается
 * по своему виду в светлой среде, и подменять образец текущей средой значило бы показывать
 * не то, что человек выбирает. «Олива» идёт первой, метки не несёт и доступна всегда:
 * это тема базы (Р-20), и метка у неё продавала бы бесплатное (Р-71, П-7).
 *
 * Выбор применяется сразу и закрывает диалог: человек видит новую тему на целом экране,
 * а не сквозь затемнение. Второго экрана предпросмотра не заводится.
 */
@Composable
internal fun ThemePickerDialog(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    ListPickerDialog(
        title = stringResource(R.string.settings_theme_row),
        options = AccentTheme.entries,
        selected = state.accentTheme,
        label = { theme -> stringResource(theme.nameRes()) },
        leading = { theme -> ThemeSample(theme) },
        mark = { theme -> if (!theme.isBase) ProMark(modifier = Modifier.padding(start = SklyankaSpacing.Small)) },
        onSelect = { theme -> onIntent(SettingsIntent.SelectTheme(theme)) },
        onDismiss = { onIntent(SettingsIntent.DismissThemePicker) },
        modifier = Modifier.testTag(TAG_THEME_DIALOG),
    )
}

/**
 * Выбор варианта иконки (§5.17).
 *
 * Две строки, и слева не образец цвета, а **миниатюра иконки** 40 dp: иконку выбирают
 * глазами, и подпись «Тёплая» без картинки заставляла бы человека представлять её самому.
 * «Ледяная» — базовая (Р-56) и метки не несёт.
 *
 * После выбора строка настроек показывает новое значение, а лончер обновляет иконку,
 * когда сочтёт нужным: приложение этого не обещает и слова «готово» не показывает
 * (П-7, §2.4).
 */
@Composable
internal fun IconPickerDialog(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    ListPickerDialog(
        title = stringResource(R.string.settings_icon_row),
        options = IconVariant.entries,
        selected = state.iconVariant,
        label = { variant -> stringResource(variant.nameRes()) },
        leading = { variant -> IconMiniature(variant) },
        mark = { variant -> if (!variant.isBase) ProMark(modifier = Modifier.padding(start = SklyankaSpacing.Small)) },
        onSelect = { variant -> onIntent(SettingsIntent.SelectIconVariant(variant)) },
        onDismiss = { onIntent(SettingsIntent.DismissIconPicker) },
        modifier = Modifier.testTag(TAG_ICON_DIALOG),
    )
}

/**
 * Образец цвета темы: круг 40 dp, залитый светлым акцентом (§5.13, §5.17).
 *
 * Той же формы, что образец цвета в мастере профиля: одна форма значит «здесь выбирают
 * цвет» во всей системе, и второй формы для того же смысла задача не заводит.
 *
 * Видимого текста у образца нет, поэтому имя варианта объявляется чтению с экрана здесь
 * (§5.17, НФТ-30): без него вариант остался бы безымянным образцом.
 */
@Composable
private fun ThemeSample(theme: AccentTheme) {
    val label = stringResource(theme.nameRes())
    Box(
        modifier = Modifier
            .size(SklyankaSizes.IconPlate)
            .background(lightColors(theme).accent, SklyankaShapes.Pill)
            .semantics { contentDescription = label }
            .testTag(TAG_THEME_SAMPLE),
    )
}

/**
 * Миниатюра варианта иконки: плитка 40 dp со знаком (§5.17).
 *
 * Плитка и краска знака берутся у того же источника, что у самих ресурсов иконки
 * (`markTileBackground` / `flatMarkColor`, §2.4): миниатюра обещает ровно тот вариант,
 * который включится в лончере, а не «похожий на него» рисунок по месту.
 */
@Composable
private fun IconMiniature(variant: IconVariant) {
    val label = stringResource(variant.nameRes())
    Box(
        modifier = Modifier
            .size(SklyankaSizes.IconPlate)
            .background(markTileBackground(variant, MarkEnvironment.Light), SklyankaShapes.Compact)
            .semantics { contentDescription = label }
            .testTag(TAG_ICON_MINIATURE),
        contentAlignment = Alignment.Center,
    ) {
        FlatMark(color = flatMarkColor(variant, MarkEnvironment.Light), size = SklyankaSizes.IconPlateGlyph)
    }
}

/** Имя темы из каталога строк: слово переводится, ключ хранения — нет (§10). */
@StringRes
internal fun AccentTheme.nameRes(): Int = when (this) {
    AccentTheme.OLIVE -> R.string.theme_olive
    AccentTheme.INDIGO -> R.string.theme_indigo
    AccentTheme.PINE -> R.string.theme_pine
    AccentTheme.AMETHYST -> R.string.theme_amethyst
}

/** Имя варианта иконки из каталога строк. */
@StringRes
internal fun IconVariant.nameRes(): Int = when (this) {
    IconVariant.ICE -> R.string.icon_variant_ice
    IconVariant.WARM -> R.string.icon_variant_warm
}

/**
 * Строки блока «Оформление» (SKL-120, §5.17).
 *
 * Отдельные теги: по ним экранный стенд и поход находят строки и диалоги, и они же
 * называют поверхность в реестре `docs/tasks/SKL-140/TDD.md` §7.
 */
internal const val TAG_THEME_ROW = "settings_theme_row"
internal const val TAG_ICON_ROW = "settings_icon_row"
internal const val TAG_THEME_DIALOG = "settings_theme_dialog"
internal const val TAG_ICON_DIALOG = "settings_icon_dialog"
internal const val TAG_THEME_SAMPLE = "settings_theme_sample"
internal const val TAG_ICON_MINIATURE = "settings_icon_miniature"
