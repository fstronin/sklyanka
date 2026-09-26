package ru.heuri.sklyanka.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import ru.heuri.sklyanka.ui.R
import ru.heuri.sklyanka.ui.component.Banner
import ru.heuri.sklyanka.ui.component.BannerKind
import ru.heuri.sklyanka.ui.component.ButtonState
import ru.heuri.sklyanka.ui.component.ChipState
import ru.heuri.sklyanka.ui.component.EmptyState
import ru.heuri.sklyanka.ui.component.IntakeCard
import ru.heuri.sklyanka.ui.component.IntakeCardActions
import ru.heuri.sklyanka.ui.component.IntakeCardLabels
import ru.heuri.sklyanka.ui.component.IntakeCardState
import ru.heuri.sklyanka.ui.component.OnboardingSteps
import ru.heuri.sklyanka.ui.component.PrimaryButton
import ru.heuri.sklyanka.ui.component.ProfileStripe
import ru.heuri.sklyanka.ui.component.ProgressPurpose
import ru.heuri.sklyanka.ui.component.QuietButton
import ru.heuri.sklyanka.ui.component.ScreenHeader
import ru.heuri.sklyanka.ui.component.ScreenHeaderKind
import ru.heuri.sklyanka.ui.component.ScreenHeaderLabels
import ru.heuri.sklyanka.ui.component.SecondaryButton
import ru.heuri.sklyanka.ui.component.SklyankaChip
import ru.heuri.sklyanka.ui.component.SklyankaLockup
import ru.heuri.sklyanka.ui.component.SklyankaMark
import ru.heuri.sklyanka.ui.component.SklyankaNumberField
import ru.heuri.sklyanka.ui.component.SklyankaProgressBar
import ru.heuri.sklyanka.ui.component.SklyankaSwitch
import ru.heuri.sklyanka.ui.component.SklyankaSwitchRow
import ru.heuri.sklyanka.ui.component.SklyankaTextField
import ru.heuri.sklyanka.ui.component.SwitchState
import ru.heuri.sklyanka.ui.component.TextFieldState
import ru.heuri.sklyanka.ui.modifier.withCappedFontScale
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.MarkEnvironment
import ru.heuri.sklyanka.ui.token.SklyankaShapes
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType
import ru.heuri.sklyanka.ui.token.markTileBackground

/**
 * Каталог компонентов: каждый компонент во всех состояниях из
 * `docs/spec/08-design-system.md` §4.
 *
 * Состав каталога задан перечислениями самих компонентов (`entries`), а не списком
 * вручную. Это не экономия строк: добавленное состояние появляется в каталоге само,
 * и не бывает состояния, которое есть в коде и отсутствует в каталоге (критерий 10).
 *
 * Все подписи приходят из ресурсов, включая заглушки: русский текст в коде
 * дизайн-системы запрещён и в превью тоже (НФТ-42, критерий 12). Правдоподобных
 * названий лекарств и имён людей среди заглушек нет — снимок экрана разработчика не
 * должен превращаться в медицинские данные (НФТ-19).
 */
@Composable
fun IntakeCardGallery(modifier: Modifier = Modifier) {
    val labels = IntakeCardLabels(
        medication = stringResource(R.string.preview_medication),
        dose = stringResource(R.string.preview_dose),
        time = stringResource(R.string.preview_marked_time),
    )
    GallerySection(stringResource(R.string.catalog_intake_card), modifier) {
        IntakeCardState.entries.forEach { state ->
            IntakeCard(
                state = state,
                labels = labels,
                actions = IntakeCardActions(onMark = {}, onSkip = {}),
            )
        }
        // Шестое состояние таблицы §4.1: тот же приём, отмеченный из чужого профиля.
        IntakeCard(state = IntakeCardState.Taken, labels = labels, markedByYou = true)
    }
}

@Composable
fun ChipGallery(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.preview_chip_label)
    GallerySection(stringResource(R.string.catalog_chip), modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
            ChipState.entries.forEach { state ->
                SklyankaChip(text = label, state = state, onClick = {})
            }
        }
        // Метка времени: тот же чип круглой формы (§4.2).
        SklyankaChip(
            text = stringResource(R.string.preview_time),
            state = ChipState.Info,
            shape = SklyankaShapes.Pill,
        )
    }
}

@Composable
fun ButtonGallery(modifier: Modifier = Modifier) {
    val primary = stringResource(R.string.preview_primary_action)
    val secondary = stringResource(R.string.preview_secondary_action)
    val quiet = stringResource(R.string.preview_quiet_action)
    GallerySection(stringResource(R.string.catalog_buttons), modifier) {
        ButtonState.entries.forEach { state ->
            PrimaryButton(text = primary, onClick = {}, state = state)
        }
        ButtonState.entries.filter { it != ButtonState.Busy }.forEach { state ->
            SecondaryButton(text = secondary, onClick = {}, state = state)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
            ButtonState.entries.filter { it != ButtonState.Busy }.forEach { state ->
                QuietButton(text = quiet, onClick = {}, state = state)
            }
            QuietButton(text = quiet, onClick = {}, muted = true)
        }
    }
}

@Composable
fun TextFieldGallery(modifier: Modifier = Modifier) {
    GallerySection(stringResource(R.string.catalog_text_field), modifier) {
        SklyankaTextField(
            value = "",
            onValueChange = {},
            hint = stringResource(R.string.preview_field_label),
            forcedState = TextFieldState.Empty,
        )
        SklyankaTextField(
            value = stringResource(R.string.preview_field_value),
            onValueChange = {},
            forcedState = TextFieldState.Filled,
        )
        SklyankaTextField(
            value = stringResource(R.string.preview_field_value),
            onValueChange = {},
            forcedState = TextFieldState.Focused,
        )
        SklyankaTextField(
            value = stringResource(R.string.preview_field_value),
            onValueChange = {},
            unitLabel = stringResource(R.string.preview_field_unit),
            forcedState = TextFieldState.Disabled,
        )
        SklyankaNumberField(value = stringResource(R.string.preview_time), onValueChange = {})
    }
}

@Composable
fun SwitchGallery(modifier: Modifier = Modifier) {
    GallerySection(stringResource(R.string.catalog_switch), modifier) {
        SklyankaSwitchRow(
            label = stringResource(R.string.preview_switch_label),
            caption = stringResource(R.string.preview_switch_caption),
            checked = true,
            onCheckedChange = {},
        )
        SklyankaSwitchRow(
            label = stringResource(R.string.preview_switch_label),
            checked = false,
            onCheckedChange = {},
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
            SwitchState.entries.forEach { state -> SklyankaSwitch(state = state) }
        }
    }
}

@Composable
fun BannerGallery(modifier: Modifier = Modifier) {
    GallerySection(stringResource(R.string.catalog_banner), modifier) {
        Banner(
            text = stringResource(R.string.preview_banner_warning),
            kind = BannerKind.Warning,
            actionLabel = stringResource(R.string.preview_quiet_action),
            onAction = {},
        )
        Banner(text = stringResource(R.string.preview_banner_profile), kind = BannerKind.Profile)
        Banner(text = stringResource(R.string.preview_banner_neutral), kind = BannerKind.Neutral)
    }
}

@Composable
fun ProgressGallery(modifier: Modifier = Modifier) {
    GallerySection(stringResource(R.string.catalog_progress), modifier) {
        ProgressPurpose.entries.forEach { purpose ->
            SklyankaProgressBar(
                progress = PREVIEW_PROGRESS,
                // Число рядом с полосой берётся через plurals, а не конкатенацией:
                // «1 приёма» и «2 приёмов» ломают фразу одинаково незаметно (НФТ-44).
                label = pluralStringResource(
                    R.plurals.intakes_taken_of_total,
                    PREVIEW_TOTAL,
                    PREVIEW_TAKEN,
                    PREVIEW_TOTAL,
                ),
                purpose = purpose,
            )
        }
        SklyankaProgressBar(
            progress = PREVIEW_PROGRESS,
            label = pluralStringResource(R.plurals.pills_left, PREVIEW_PILLS, PREVIEW_PILLS),
            purpose = ProgressPurpose.StockCritical,
        )
        OnboardingSteps(total = PREVIEW_STEPS, current = PREVIEW_STEP)
    }
}

@Composable
fun EmptyStateGallery(modifier: Modifier = Modifier) {
    GallerySection(stringResource(R.string.catalog_empty_state), modifier) {
        EmptyState(
            title = stringResource(R.string.preview_empty_title),
            text = stringResource(R.string.preview_empty_text),
            actionLabel = stringResource(R.string.preview_primary_action),
            onAction = {},
        )
    }
}

/**
 * Лестница знака целиком: стекло в двух средах и четыре ступени редукции.
 *
 * Каталог показывает их рядом не для красоты. Знак редуцируется тремя ступенями,
 * и единственный способ увидеть, что предельная ступень остаётся тем же знаком, —
 * поставить её рядом с крупной (`acceptance.feature`, «Мелкий знак остаётся
 * узнаваемым рядом с крупным»).
 */
@Composable
fun MarkGallery(modifier: Modifier = Modifier) {
    GallerySection(stringResource(R.string.catalog_mark), modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Section),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SklyankaMark(
                contentDescription = stringResource(R.string.cd_icon_on_dark),
                size = SklyankaSizes.LockupMark,
                environment = MarkEnvironment.Dark,
                modifier = Modifier
                    .background(markTileBackground(MarkEnvironment.Dark), SklyankaShapes.Card)
                    .padding(SklyankaSpacing.Block),
            )
            SklyankaMark(
                contentDescription = stringResource(R.string.cd_icon_on_light),
                size = SklyankaSizes.LockupMark,
                environment = MarkEnvironment.Light,
                modifier = Modifier
                    .background(markTileBackground(MarkEnvironment.Light), SklyankaShapes.Card)
                    .padding(SklyankaSpacing.Block),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Section),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FLAT_LADDER_PREVIEW.forEach { (descriptionId, size) ->
                SklyankaMark(
                    contentDescription = stringResource(descriptionId),
                    size = size.dp,
                    environment = MarkEnvironment.Light,
                )
            }
        }
    }
}

/** Логотип с названием: он же уходит в материалы магазинов (SKL-22). */
@Composable
fun LockupGallery(modifier: Modifier = Modifier) {
    GallerySection(stringResource(R.string.catalog_lockup), modifier) {
        SklyankaLockup()
    }
}

@Composable
fun ScreenHeaderGallery(modifier: Modifier = Modifier) {
    val title = stringResource(R.string.preview_screen_title)
    GallerySection(stringResource(R.string.catalog_screen_header), modifier) {
        ScreenHeader(
            labels = ScreenHeaderLabels(title = title, overline = stringResource(R.string.preview_date)),
        )
        ScreenHeaderKind.entries.filter { it != ScreenHeaderKind.Root }.forEach { kind ->
            ScreenHeader(labels = ScreenHeaderLabels(title = title), kind = kind, onBack = {})
        }
        ScreenHeader(
            labels = ScreenHeaderLabels(
                title = title,
                profileName = stringResource(R.string.preview_profile_name),
            ),
        )
    }
}

@Composable
fun ProfileStripeGallery(modifier: Modifier = Modifier) {
    GallerySection(stringResource(R.string.catalog_profile_stripe), modifier) {
        // В теме владельца компонент не рисует ничего, и это видно рядом с темой
        // чужого профиля на одном снимке (критерий 2).
        ProfileStripe()
    }
}

/**
 * Типографическая шкала целиком.
 *
 * Имена стилей латиницей: это идентификаторы шкалы для разработчика, а не текст
 * интерфейса. Образец набирается строкой из ресурсов — как и всё остальное в
 * каталоге (НФТ-42).
 *
 * У `Display` виден потолок масштаба: 64 sp при системном шрифте 200 % не
 * помещается на 390 dp, и в каталоге это должно быть видно так же, как на
 * полноэкранном напоминании (§6.3).
 */
@Composable
fun TypographyGallery(modifier: Modifier = Modifier) {
    val sample = stringResource(R.string.preview_screen_title)
    val colors = SklyankaTheme.colors
    GallerySection(stringResource(R.string.catalog_typography), modifier) {
        namedStyles().forEach { (name, style) ->
            Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair)) {
                Text(text = name, style = SklyankaType.Micro, color = colors.textMuted)
                Text(text = sample, style = style, color = colors.text)
            }
        }
    }
}

/**
 * Имена стилей рядом со стилями.
 *
 * Список собран вручную: перечислить свойства объекта иначе нечем, а список,
 * разошедшийся со шкалой, обнаружит тест `ScalesTest` — он считает стили по
 * `SklyankaType.all`.
 */
@Composable
private fun namedStyles(): List<Pair<String, TextStyle>> = listOf(
    "Display" to SklyankaType.Display.withCappedFontScale(),
    "Headline" to SklyankaType.Headline,
    "TitleScreen" to SklyankaType.TitleScreen,
    "Title" to SklyankaType.Title,
    "Stat" to SklyankaType.Stat,
    "Subtitle" to SklyankaType.Subtitle,
    "BodyLarge" to SklyankaType.BodyLarge,
    "Body" to SklyankaType.Body,
    "BodyStrong" to SklyankaType.BodyStrong,
    "BodyDefault" to SklyankaType.BodyDefault,
    "BodyDefaultStrong" to SklyankaType.BodyDefaultStrong,
    "Label" to SklyankaType.Label,
    "LabelStrong" to SklyankaType.LabelStrong,
    "Caption" to SklyankaType.Caption,
    "CaptionStrong" to SklyankaType.CaptionStrong,
    "CaptionSmall" to SklyankaType.CaptionSmall,
    "Overline" to SklyankaType.Overline,
    "OverlineDate" to SklyankaType.OverlineDate,
    "Micro" to SklyankaType.Micro,
    "MicroStrong" to SklyankaType.MicroStrong,
    "MicroOverline" to SklyankaType.MicroOverline,
)

/** Заголовок раздела каталога и его содержимое. */
@Composable
private fun GallerySection(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
    ) {
        Text(
            text = title,
            style = SklyankaType.Overline,
            color = SklyankaTheme.colors.textMuted,
        )
        content()
    }
}

/** Четыре ступени редукции из `SklyankaScale.dc.html` с их подписями для читалки. */
private val FLAT_LADDER_PREVIEW = listOf(
    R.string.cd_flat_mark_72 to 72,
    R.string.cd_flat_mark_44 to 44,
    R.string.cd_flat_mark_28 to 28,
    R.string.cd_flat_mark_18 to 18,
)

private const val PREVIEW_PROGRESS = 0.4f
private const val PREVIEW_TAKEN = 2
private const val PREVIEW_TOTAL = 5
private const val PREVIEW_PILLS = 9
private const val PREVIEW_STEPS = 3
private const val PREVIEW_STEP = 2
