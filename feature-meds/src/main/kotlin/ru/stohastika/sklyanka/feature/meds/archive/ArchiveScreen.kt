package ru.stohastika.sklyanka.feature.meds.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import ru.stohastika.sklyanka.domain.medication.ArchiveCaption
import ru.stohastika.sklyanka.domain.medication.ArchiveCard
import ru.stohastika.sklyanka.feature.meds.DeletionSnackbarHost
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.SectionLabel
import ru.stohastika.sklyanka.feature.meds.Segment
import ru.stohastika.sklyanka.feature.meds.SegmentedControl
import ru.stohastika.sklyanka.feature.meds.screenPadding
import ru.stohastika.sklyanka.feature.meds.sinceDayAndMonth
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.component.EmptyState
import ru.stohastika.sklyanka.ui.component.MedicationGlyph
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Вкладка «Архив» экрана «Лекарства» (`design/screens/Archive.dc.html`).
 *
 * Тот же заголовок и тот же сегментный переключатель, что на вкладке активных: это одна
 * вкладка на два состояния списка, а не два экрана. Нижней панели навигации здесь нет по
 * той же причине, что и на списке лекарств: она принадлежит экрану «Сегодня».
 */
@Composable
fun ArchiveScreen(state: ArchiveUiState, onIntent: (ArchiveIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .testTag(TAG_ARCHIVE_SCREEN),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
        ) {
            ScreenHeader(labels = ScreenHeaderLabels(title = stringResource(R.string.meds_title)))
            SegmentedControl(
                segments = listOf(
                    Segment(
                        label = stringResource(R.string.meds_tab_active, state.activeCount),
                        selected = false,
                        onClick = { onIntent(ArchiveIntent.SelectActive) },
                    ),
                    Segment(
                        label = stringResource(R.string.meds_tab_archive, state.archiveCount),
                        selected = true,
                        onClick = {},
                    ),
                ),
                modifier = Modifier.screenPadding(),
            )
            state.notice?.let { notice -> NoticeBanner(notice, onIntent) }
            val content = state.content
            when {
                state.loading -> Box(modifier = Modifier.weight(1f))
                content == null -> EmptyState(
                    title = stringResource(R.string.archive_empty_title),
                    text = stringResource(R.string.archive_empty_text),
                    modifier = Modifier
                        .padding(top = SklyankaSpacing.Screen)
                        .testTag(TAG_ARCHIVE_EMPTY),
                )

                else -> ArchiveList(
                    groups = listOf(
                        ArchiveGroupUi(
                            title = stringResource(R.string.archive_group_finished),
                            tag = TAG_GROUP_FINISHED,
                            cards = content.finished,
                        ),
                        ArchiveGroupUi(
                            title = stringResource(R.string.archive_group_manual),
                            tag = TAG_GROUP_MANUAL,
                            cards = content.manual,
                        ),
                    ),
                    banner = stringResource(R.string.archive_history_banner),
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Окно «Отменить» после удаления из архивного вида (ОВ-124): тот же снэкбар §4.12.
        DeletionNotice(state, onIntent)
    }
}

/** Окно отмены удаления на архивном экране: разметка не знает, откуда взялось окно. */
@Composable
private fun BoxScope.DeletionNotice(state: ArchiveUiState, onIntent: (ArchiveIntent) -> Unit) {
    DeletionSnackbarHost(state.deletion) { deletion ->
        onIntent(ArchiveIntent.UndoDeletion(deletion.medicationId))
    }
}

/**
 * Группа архива с уже переведённым заголовком.
 *
 * Заголовок приходит готовой строкой: содержимое ленивого списка — не композиция, и
 * обратиться к ресурсам изнутри `LazyListScope` нельзя.
 */
private data class ArchiveGroupUi(val title: String, val tag: String, val cards: List<ArchiveCard>)

/**
 * Список архива: две группы и плашка о сохранности истории.
 *
 * Прокручивается: в макете нарисованы четыре карточки при счётчике 12. Плашка ЭКР-9 едет
 * вместе со списком, а не прилипает к низу — иначе при двенадцати карточках она закрывает
 * последнюю.
 */
@Composable
private fun ArchiveList(
    groups: List<ArchiveGroupUi>,
    banner: String,
    onIntent: (ArchiveIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().screenPadding().testTag(TAG_ARCHIVE_LIST),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
        contentPadding = PaddingValues(bottom = SklyankaSpacing.Screen),
    ) {
        groups.forEach { group -> group(group, onIntent) }
        item(key = KEY_HISTORY_BANNER) {
            Banner(
                text = banner,
                kind = BannerKind.Neutral,
                modifier = Modifier
                    .padding(top = SklyankaSpacing.Small)
                    .testTag(TAG_HISTORY_BANNER),
            )
        }
    }
}

/**
 * Одна группа архива.
 *
 * Пустая группа скрывается целиком вместе с заголовком: заголовок над пустотой сообщает,
 * что раздел есть и в нём ничего нет, — а его просто нет.
 */
private fun LazyListScope.group(group: ArchiveGroupUi, onIntent: (ArchiveIntent) -> Unit) {
    if (group.cards.isEmpty()) return
    item(key = group.tag) {
        SectionLabel(
            text = group.title,
            modifier = Modifier
                .padding(top = SklyankaSpacing.Small)
                .testTag(group.tag),
        )
    }
    items(group.cards.size, key = { index -> group.cards[index].medicationId }) { index ->
        ArchivedMedicationCard(group.cards[index], onIntent)
    }
}

/**
 * Карточка архивного курса: приглушённая плитка, приглушённое название, архивный фон.
 *
 * Отдельным компонентом дизайн-системы не является (§4.11) и собирается из токенов.
 * Подпись внутри карточки называет либо итог курса, либо дату архивации — принадлежность
 * разделу читается текстом, а не цветом (НФТ-33, ЭКР-8).
 */
@Composable
private fun ArchivedMedicationCard(card: ArchiveCard, onIntent: (ArchiveIntent) -> Unit) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.archiveBackground, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card)
            .clickable(role = Role.Button, onClick = { onIntent(ArchiveIntent.OpenMedication(card.medicationId)) })
            .padding(SklyankaSpacing.Large)
            .testTag(TAG_ARCHIVE_CARD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        Box(
            modifier = Modifier
                .size(SklyankaSizes.IconPlate)
                .background(colors.neutralSoft, SklyankaShapes.Button),
            contentAlignment = Alignment.Center,
        ) {
            // Размер силуэта сохраняется прежним (22 dp): иконография архива решением
            // ОВ-94 не пересматривается, и снимок архива меняться не должен.
            MedicationGlyph(
                form = card.form,
                shape = null,
                color = colors.textTertiary,
                size = SklyankaSizes.MedsListPlateGlyph,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair),
        ) {
            Text(text = card.title, style = SklyankaType.BodyStrong, color = colors.archiveText)
            Text(
                text = card.caption.text(),
                style = SklyankaType.CaptionSmall,
                color = colors.textMuted,
                modifier = Modifier.testTag(TAG_ARCHIVE_CAPTION),
            )
        }
        SecondaryButton(
            text = stringResource(R.string.archive_restore),
            onClick = { onIntent(ArchiveIntent.Restore(card.medicationId)) },
            modifier = Modifier.testTag(TAG_ARCHIVE_RESTORE),
        )
    }
}

/** «7 дней · принято 13 из 14» либо «в архиве с 3 августа» (ЭКР-8). */
@Composable
private fun ArchiveCaption.text(): String = when (this) {
    is ArchiveCaption.Outcome -> pluralStringResource(R.plurals.archive_outcome, days, days, taken, planned)
    is ArchiveCaption.ArchivedOn -> stringResource(R.string.archive_caption_since, date.sinceDayAndMonth())
}

@Composable
private fun NoticeBanner(notice: ArchiveNotice, onIntent: (ArchiveIntent) -> Unit) {
    val text = when (notice) {
        ArchiveNotice.PeriodNeedsUpdate -> stringResource(R.string.card_notice_period_needs_update)
    }
    Banner(
        text = text,
        kind = BannerKind.Warning,
        actionLabel = stringResource(R.string.card_notice_dismiss),
        onAction = { onIntent(ArchiveIntent.NoticeDismissed) },
        modifier = Modifier
            .screenPadding()
            .testTag(TAG_ARCHIVE_NOTICE),
    )
}

/** Опора ключа плашки в ленивом списке: ключи обязаны быть уникальными и стабильными. */
private const val KEY_HISTORY_BANNER = "archive_history_banner"

const val TAG_ARCHIVE_SCREEN = "archive_screen"
const val TAG_ARCHIVE_LIST = "archive_list"
const val TAG_ARCHIVE_EMPTY = "archive_empty"
const val TAG_ARCHIVE_CARD = "archive_card"
const val TAG_ARCHIVE_CAPTION = "archive_card_caption"
const val TAG_ARCHIVE_RESTORE = "archive_card_restore"
const val TAG_GROUP_FINISHED = "archive_group_finished"
const val TAG_GROUP_MANUAL = "archive_group_manual"
const val TAG_HISTORY_BANNER = "archive_history_banner_row"
const val TAG_ARCHIVE_NOTICE = "archive_notice"
