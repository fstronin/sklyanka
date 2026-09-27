package ru.heuri.sklyanka.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import ru.heuri.sklyanka.ui.component.Banner
import ru.heuri.sklyanka.ui.component.BannerKind
import ru.heuri.sklyanka.ui.component.PrimaryButton
import ru.heuri.sklyanka.ui.component.ScreenHeader
import ru.heuri.sklyanka.ui.component.ScreenHeaderKind
import ru.heuri.sklyanka.ui.component.ScreenHeaderLabels
import ru.heuri.sklyanka.ui.component.SecondaryButton
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType

/**
 * Отчёт врачу: показ с экрана, передача системным обменом, сохранение файлом.
 *
 * Показ с экрана — не украшение, а один из способов отдать отчёт: у врача на приёме
 * телефон в руках, и «посмотреть» бывает достаточно (`ОВ-15`, предложение исполнителя).
 * Формат документа не подтверждён заказчиком, поэтому экран показывает **состав**
 * отчёта тем же текстом, который уходит в файл: подтверждение формата меняет отрисовку,
 * а не этот экран.
 *
 * Предупреждение о медицинских данных в открытом виде стоит выше действий и не
 * скрывается (НФТ-18): человек обязан прочитать его до того, как отдаст файл.
 *
 * Уйти с отчёта можно «Отменой» в шапке — второй кнопки «Закрыть» внизу нет: два
 * одинаковых действия на одном экране заставляют выбирать там, где выбора нет.
 *
 * Печати здесь нет: `PrintManager` без устройства не проверяется ничем, и молчаливо
 * нерабочая кнопка «Печать» хуже её отсутствия (вынесено в ручной чек-лист).
 */
@Composable
internal fun DoctorReportSheet(report: ReportUi, onIntent: (HistoryIntent) -> Unit) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(TAG_REPORT_SHEET),
    ) {
        ScreenHeader(
            labels = ScreenHeaderLabels(title = stringResource(R.string.history_report_title)),
            kind = ScreenHeaderKind.Modal,
            onBack = { onIntent(HistoryIntent.ReportDismissed) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SklyankaSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
        ) {
            Banner(
                text = stringResource(R.string.history_report_warning),
                kind = BannerKind.Warning,
                modifier = Modifier.testTag(TAG_REPORT_WARNING),
            )
            Text(
                text = report.document.content,
                style = SklyankaType.Caption,
                color = colors.text,
                modifier = Modifier.testTag(TAG_REPORT_CONTENT),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SklyankaSpacing.Section, vertical = SklyankaSpacing.Block),
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            PrimaryButton(
                text = stringResource(R.string.history_report_share),
                onClick = { onIntent(HistoryIntent.ShareReport) },
                modifier = Modifier.weight(1f).testTag(TAG_REPORT_SHARE),
            )
            SecondaryButton(
                text = stringResource(R.string.history_report_save),
                onClick = { onIntent(HistoryIntent.SaveRequested) },
                modifier = Modifier.weight(1f).testTag(TAG_REPORT_SAVE),
            )
        }
    }
}

const val TAG_REPORT_SHEET = "history_report_sheet"
const val TAG_REPORT_WARNING = "history_report_warning"
const val TAG_REPORT_CONTENT = "history_report_content"
const val TAG_REPORT_SHARE = "history_report_share"
const val TAG_REPORT_SAVE = "history_report_save"
