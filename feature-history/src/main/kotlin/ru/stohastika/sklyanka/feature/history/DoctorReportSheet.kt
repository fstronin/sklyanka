package ru.stohastika.sklyanka.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.component.ButtonForm
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderKind
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.component.StackingEqualButtons
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Отчёт врачу: состав, предупреждение, передача системным обменом, сохранение файлом.
 *
 * Документ один — PDF (решение дизайна по ОВ-15), и с экрана он не показывается:
 * просмотрщик в приложении не заводится. Вместо текстовой сводки шторка показывает
 * **состав**: профиль, период и число принятых приёмов — чтобы человек видел, что
 * именно отдаёт, до нажатия «Передать».
 *
 * Предупреждение о медицинских данных в открытом виде стоит выше действий и не
 * скрывается (НФТ-18): человек обязан прочитать его до того, как отдаст файл.
 *
 * Уйти с отчёта можно «Отменой» в шапке — второй кнопки «Закрыть» внизу нет: два
 * одинаковых действия на одном экране заставляют выбирать там, где выбора нет.
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
            CompositionCard(report.summary)
        }
        ReportActions(onIntent)
    }
}

@Composable
private fun CompositionCard(summary: String) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block)
            .testTag(TAG_REPORT_CONTENT),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        summary.lines().forEach { line ->
            Text(text = line, style = SklyankaType.BodyDefault, color = colors.text)
        }
    }
}

@Composable
private fun ReportActions(onIntent: (HistoryIntent) -> Unit) {
    StackingEqualButtons(
        first = {
            PrimaryButton(
                text = stringResource(R.string.history_report_share),
                onClick = { onIntent(HistoryIntent.ShareReport) },
                modifier = Modifier.testTag(TAG_REPORT_SHARE),
            )
        },
        second = {
            SecondaryButton(
                text = stringResource(R.string.history_report_save),
                onClick = { onIntent(HistoryIntent.SaveRequested) },
                form = ButtonForm.Screen,
                modifier = Modifier.testTag(TAG_REPORT_SAVE),
            )
        },
        gap = SklyankaSpacing.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Section, vertical = SklyankaSpacing.Block),
    )
}

const val TAG_REPORT_SHEET = "history_report_sheet"
const val TAG_REPORT_WARNING = "history_report_warning"
const val TAG_REPORT_CONTENT = "history_report_content"
const val TAG_REPORT_SHARE = "history_report_share"
const val TAG_REPORT_SAVE = "history_report_save"
