package ru.stohastika.sklyanka.feature.meds.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.feature.meds.DeletionSnackbarHost
import ru.stohastika.sklyanka.feature.meds.PlusGlyph
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.Segment
import ru.stohastika.sklyanka.feature.meds.SegmentedControl
import ru.stohastika.sklyanka.feature.meds.screenPadding
import ru.stohastika.sklyanka.ui.component.EmptyState
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing

/**
 * Экран «Лекарства» (`Meds.dc.html`).
 *
 * Без состояния: всё приходит в [state], наружу уходят только намерения. Счётчик
 * «Активные» считает лишь курсы по расписанию (ЭКР-3), поэтому карточек в списке может
 * быть больше, чем говорит чип, — и это не рассогласование, а правило.
 *
 * Нижней панели навигации здесь нет: она принадлежит экрану «Сегодня» (SKL-08,
 * макет `Main.dc.html`) и в каталоге дизайн-системы пока отсутствует.
 */
@Composable
fun MedicationListScreen(state: MedsUiState, onIntent: (MedsIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(TAG_MEDICATION_LIST),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large)) {
            ScreenHeader(labels = ScreenHeaderLabels(title = stringResource(R.string.meds_title)))
            SegmentedControl(
                segments = listOf(
                    Segment(
                        label = stringResource(R.string.meds_tab_active, state.activeCount),
                        selected = state.tab == MedsTab.ACTIVE,
                        onClick = { onIntent(MedsIntent.SelectTab(MedsTab.ACTIVE)) },
                    ),
                    Segment(
                        label = stringResource(R.string.meds_tab_archive, state.archiveCount),
                        selected = state.tab == MedsTab.ARCHIVE,
                        onClick = { onIntent(MedsIntent.SelectTab(MedsTab.ARCHIVE)) },
                    ),
                ),
                modifier = Modifier.screenPadding(),
            )
            if (state.isEmpty) {
                EmptyState(
                    title = stringResource(R.string.meds_empty_title),
                    text = stringResource(R.string.meds_empty_text),
                    actionLabel = stringResource(R.string.meds_empty_action),
                    onAction = { onIntent(MedsIntent.AddMedication) },
                    modifier = Modifier.padding(top = SklyankaSpacing.Screen),
                )
            } else {
                MedicationList(state.items, onIntent, Modifier.weight(1f))
            }
        }
        AddMedicationButton(
            onClick = { onIntent(MedsIntent.AddMedication) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(SklyankaSpacing.Section),
        )
        // Окно «Отменить» после удаления лекарства (ОВ-124): тот же снэкбар §4.12.
        DeletionSnackbarHost(state.deletion) { deletion ->
            onIntent(MedsIntent.UndoDeletion(deletion.medicationId))
        }
    }
}

@Composable
private fun MedicationList(
    items: List<MedicationCardUi>,
    onIntent: (MedsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().screenPadding(),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
        // Нижнее поле оставляет место круглой кнопке добавления: иначе она накрывает
        // последнюю карточку, и до неё нельзя дотянуться, не прокрутив список дальше.
        contentPadding = PaddingValues(bottom = SklyankaSpacing.EmptyStateSide),
    ) {
        items(items = items, key = { it.medicationId }) { card ->
            MedicationCard(
                card = card,
                onOpen = { onIntent(MedsIntent.OpenMedication(card.medicationId)) },
                onTake = { onIntent(MedsIntent.TakeNow(card.medicationId)) },
            )
        }
    }
}

/**
 * Круглая кнопка добавления 60×60 (`Meds.dc.html`).
 *
 * Видимого текста у неё нет, поэтому подпись для чтения с экрана обязательна и взята
 * дословно из `aria-label` макета (НФТ-30).
 */
@Composable
private fun AddMedicationButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    val label = stringResource(R.string.cd_add_medication)
    Box(
        modifier = modifier
            .size(SklyankaSizes.AddButton)
            .background(colors.accent, SklyankaShapes.Pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(TAG_ADD_MEDICATION),
        contentAlignment = Alignment.Center,
    ) {
        PlusGlyph(color = colors.surface, size = SklyankaSizes.AddButtonGlyph)
    }
}

const val TAG_MEDICATION_LIST = "medication_list"
const val TAG_ADD_MEDICATION = "add_medication"
