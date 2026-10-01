package ru.stohastika.sklyanka.feature.settings.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.transfer.TransferCounts
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderKind
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.ZoneId

/**
 * Сводка выбранного файла до всякого применения (ПЕР-15 п. 3).
 *
 * Общий шаг для обоих режимов и единственное место, где человек видит, что именно он
 * собирается загрузить. Числа взяты из содержимого файла, а не из его раздела `counts`:
 * сводка, разошедшаяся с содержимым, — это ложь о том, что произойдёт дальше.
 *
 * Экрана в макетах нет; собран из существующих компонентов дизайн-системы (ОВ-67).
 */
@Composable
fun TransferSummaryScreen(
    counts: TransferCounts,
    state: TransferUiState,
    zone: ZoneId,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    Column(modifier = modifier.fillMaxSize().navigationBarsPadding().testTag(TAG_SUMMARY)) {
        ScreenHeader(
            labels = ScreenHeaderLabels(title = stringResource(R.string.transfer_summary_title)),
            kind = ScreenHeaderKind.Nested,
            onBack = onCancel,
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SklyankaSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
        ) {
            state.fileExportedAt?.let { moment ->
                Text(
                    text = stringResource(R.string.transfer_summary_exported_at, moment.dateAndTime(zone)),
                    style = SklyankaType.BodyDefault,
                    color = colors.textMuted,
                )
            }
            CompositionChips(
                medications = counts.medications,
                archived = counts.archived,
                historyMonths = null,
                profiles = counts.profiles,
                settingsLabel = stringResource(R.string.transfer_export_chip_settings),
            )
            counts.historyFrom?.let { from ->
                Text(
                    text = stringResource(R.string.transfer_replace_history_from, from.fullDate()),
                    style = SklyankaType.CaptionSmall,
                    color = colors.textMuted,
                )
            }
            Text(
                text = intakesCount(counts.intakes),
                style = SklyankaType.CaptionSmall,
                color = colors.textMuted,
            )
            // Файл старой версии формата читается через миграцию, и человек предупреждён
            // одной строкой (ПЕР-30).
            state.oldFormatVersion?.let { version ->
                Banner(
                    text = stringResource(R.string.transfer_summary_old_format, version),
                    modifier = Modifier.testTag(TAG_OLD_FORMAT),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium)) {
                SecondaryButton(
                    text = stringResource(R.string.transfer_summary_cancel),
                    onClick = onCancel,
                )
                PrimaryButton(
                    text = stringResource(R.string.transfer_summary_continue),
                    onClick = onContinue,
                    modifier = Modifier.testTag(TAG_SUMMARY_CONTINUE),
                )
            }
        }
    }
}

const val TAG_SUMMARY = "transfer_summary"
const val TAG_SUMMARY_CONTINUE = "transfer_summary_continue"
const val TAG_OLD_FORMAT = "transfer_old_format"
