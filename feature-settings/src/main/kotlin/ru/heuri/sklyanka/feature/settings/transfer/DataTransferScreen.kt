package ru.heuri.sklyanka.feature.settings.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.heuri.sklyanka.domain.transfer.TransferMode
import ru.heuri.sklyanka.feature.settings.R
import ru.heuri.sklyanka.ui.component.Banner
import ru.heuri.sklyanka.ui.component.BannerKind
import ru.heuri.sklyanka.ui.component.ButtonState
import ru.heuri.sklyanka.ui.component.PrimaryButton
import ru.heuri.sklyanka.ui.component.ScreenHeader
import ru.heuri.sklyanka.ui.component.ScreenHeaderKind
import ru.heuri.sklyanka.ui.component.ScreenHeaderLabels
import ru.heuri.sklyanka.ui.component.SecondaryButton
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType

/**
 * Экран «Перенос данных» (макет `design/screens/DataTransfer.dc.html`).
 *
 * Вёрстка и тексты — по макету дословно, с одной осознанной правкой состава чипов
 * (ОВ-66): чип «звуки» убран, потому что индивидуальные звуки отложены во вторую версию
 * и переносить нечего, кроме одного значения настройки; чип «настройки» добавлен, потому
 * что настройки в файле есть (ПЕР-3), а набор чипов обязан соответствовать содержимому.
 *
 * Предупреждения о приватности стоят рядом с кнопкой «Сохранить файл», а не в мелком
 * шрифте внизу (ПЕР-34): файл содержит медицинские данные в открытом виде, и узнать
 * об этом человек должен до нажатия, а не после.
 */
@Composable
fun DataTransferScreen(
    state: TransferUiState,
    onIntent: (TransferIntent) -> Unit,
    onSave: () -> Unit,
    onPickFile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    Column(modifier = modifier.fillMaxSize().testTag(TAG_TRANSFER_SCREEN)) {
        ScreenHeader(
            labels = ScreenHeaderLabels(title = stringResource(R.string.transfer_title)),
            kind = ScreenHeaderKind.Nested,
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SklyankaSpacing.Section)
                .padding(bottom = SklyankaSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
        ) {
            // Принцип продукта формулируется прямо, а не прячется в мелкий шрифт (ПЕР-35).
            Text(
                text = stringResource(R.string.transfer_principle),
                style = SklyankaType.BodyDefault,
                color = colors.textMuted,
            )

            if (state.importDidNotHappen) {
                Banner(text = stringResource(R.string.transfer_error_import_interrupted))
            }
            state.failure?.let { failure ->
                FailureBanner(failure = failure, onDismiss = { onIntent(TransferIntent.DismissFailure) })
            }

            ExportCard(state = state, onSave = onSave)
            ImportCard(state = state, onIntent = onIntent, onPickFile = onPickFile)

            // Разрешения и автозапуск принадлежат системе, а не файлу (ПЕР-24).
            Banner(
                text = stringResource(R.string.transfer_permissions_note),
                kind = BannerKind.Neutral,
            )
        }
    }
}

@Composable
private fun ExportCard(state: TransferUiState, onSave: () -> Unit) {
    val colors = SklyankaTheme.colors
    TransferCard(
        title = stringResource(R.string.transfer_export_title),
        subtitle = stringResource(R.string.transfer_export_file, state.exportFileName),
    ) {
        val counts = state.deviceCounts
        if (counts != null) {
            CompositionChips(
                medications = counts.medications,
                archived = counts.archived,
                historyMonths = state.deviceHistoryMonths,
                profiles = counts.profiles,
                settingsLabel = stringResource(R.string.transfer_export_chip_settings),
            )
        }
        PrimaryButton(
            text = stringResource(R.string.transfer_export_action),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().testTag(TAG_SAVE),
            state = if (state.busy) ButtonState.Busy else ButtonState.Normal,
        )
        Text(
            text = stringResource(R.string.transfer_privacy_content),
            style = SklyankaType.CaptionSmall,
            color = colors.textMuted,
        )
        Text(
            text = stringResource(R.string.transfer_privacy_place),
            style = SklyankaType.CaptionSmall,
            color = colors.textMuted,
        )
        // Дата последней выгрузки — факт, без напоминаний и без давления (ПЕР-4).
        // До первой выгрузки строки нет вовсе (критерий 5).
        state.lastExportDate?.let { date ->
            Text(
                text = stringResource(R.string.transfer_last_export, date.dayAndMonth()),
                style = SklyankaType.CaptionSmall,
                color = colors.textMuted,
                modifier = Modifier.testTag(TAG_LAST_EXPORT),
            )
        }
    }
}

@Composable
private fun ImportCard(state: TransferUiState, onIntent: (TransferIntent) -> Unit, onPickFile: () -> Unit) {
    TransferCard(
        title = stringResource(R.string.transfer_import_title),
        subtitle = stringResource(R.string.transfer_import_subtitle),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
            ModeOption(
                title = stringResource(R.string.transfer_mode_merge),
                hint = stringResource(R.string.transfer_mode_merge_hint),
                selected = state.mode == TransferMode.MERGE,
                onSelect = { onIntent(TransferIntent.SelectMode(TransferMode.MERGE)) },
                modifier = Modifier.testTag(TAG_MODE_MERGE),
            )
            ModeOption(
                title = stringResource(R.string.transfer_mode_replace),
                hint = stringResource(R.string.transfer_mode_replace_hint),
                selected = state.mode == TransferMode.REPLACE_ALL,
                onSelect = { onIntent(TransferIntent.SelectMode(TransferMode.REPLACE_ALL)) },
                modifier = Modifier.testTag(TAG_MODE_REPLACE),
            )
        }
        SecondaryButton(
            text = stringResource(R.string.transfer_import_action),
            onClick = onPickFile,
            modifier = Modifier.fillMaxWidth().testTag(TAG_PICK_FILE),
            state = if (state.busy) ButtonState.Busy else ButtonState.Normal,
        )
    }
}

/**
 * Сообщение об отказе.
 *
 * У битого файла — заголовок ПЕР-28 плюс техническое пояснение одной строкой; у файла
 * из будущего — только формулировка ПЕР-29, потому что пояснять там нечего: приложение
 * не читало содержимое и ничего о нём не знает.
 */
@Composable
private fun FailureBanner(failure: TransferFailure, onDismiss: () -> Unit) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TAG_FAILURE),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Banner(
            text = when (failure) {
                is TransferFailure.NotAnExport -> stringResource(R.string.transfer_error_not_an_export)
                TransferFailure.FromFuture -> stringResource(R.string.transfer_error_from_future)
                TransferFailure.NoSpace -> stringResource(R.string.transfer_error_no_space)
                TransferFailure.ImportNotApplied -> stringResource(R.string.transfer_error_not_applied)
                TransferFailure.ExportFailed -> stringResource(R.string.transfer_error_export_failed)
                TransferFailure.NoFilePicker -> stringResource(R.string.transfer_error_no_file_picker)
            },
            actionLabel = stringResource(R.string.transfer_summary_cancel),
            onAction = onDismiss,
        )
        if (failure is TransferFailure.NotAnExport) {
            Text(
                text = failure.reject.explanation(),
                style = SklyankaType.CaptionSmall,
                color = colors.textMuted,
                modifier = Modifier.testTag(TAG_FAILURE_REASON),
            )
        }
    }
}

const val TAG_TRANSFER_SCREEN = "transfer_screen"
const val TAG_SAVE = "transfer_save"
const val TAG_PICK_FILE = "transfer_pick_file"
const val TAG_MODE_MERGE = "transfer_mode_merge"
const val TAG_MODE_REPLACE = "transfer_mode_replace"
const val TAG_LAST_EXPORT = "transfer_last_export"
const val TAG_FAILURE = "transfer_failure"
const val TAG_FAILURE_REASON = "transfer_failure_reason"
