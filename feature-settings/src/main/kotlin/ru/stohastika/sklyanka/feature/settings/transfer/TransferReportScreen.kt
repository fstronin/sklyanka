package ru.stohastika.sklyanka.feature.settings.transfer

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
import ru.stohastika.sklyanka.domain.transfer.TransferNotice
import ru.stohastika.sklyanka.domain.transfer.TransferReport
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderKind
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Отчёт о загрузке (ПЕР-15 п. 7, ПЕР-16 п. 6, критерий 17).
 *
 * Числа, а не «готово»: сколько профилей, лекарств и записей истории перенесено, сколько
 * совпадений и как разрешено, что не удалось перенести. Без чисел отличить «загрузка стёрла
 * историю» от «в файле её не было» невозможно, а сети и телеметрии у продукта нет.
 *
 * Дальше — экран настройки разрешений (ПЕР-25): разрешения файлом не переносятся, и человек,
 * перенёсший данные, находится в том же положении, что поставивший приложение впервые.
 *
 * @param medicationNames название лекарства по его идентификатору — для замечаний отчёта.
 *   В журнал названия не попадают, только идентификаторы (НФТ-19); на экране название нужно,
 *   иначе «форма выпуска не распознана» не говорит, у какого лекарства.
 */
@Composable
fun TransferReportScreen(
    report: TransferReport,
    medicationNames: Map<String, String>,
    safetyCopyName: String?,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    Column(modifier = modifier.fillMaxSize().testTag(TAG_REPORT)) {
        ScreenHeader(
            labels = ScreenHeaderLabels(title = stringResource(R.string.transfer_report_title)),
            kind = ScreenHeaderKind.Nested,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SklyankaSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        ) {
            Line(stringResource(R.string.transfer_report_profiles, report.profilesAdded))
            Line(stringResource(R.string.transfer_report_medications, report.medicationsAdded))
            if (report.medicationsUpdated > 0) {
                Line(stringResource(R.string.transfer_report_medications_updated, report.medicationsUpdated))
            }
            Line(stringResource(R.string.transfer_report_intakes, report.intakesAdded))
            if (report.intakesUpdated > 0) {
                Line(stringResource(R.string.transfer_report_intakes_updated, report.intakesUpdated))
            }
            if (report.matchesResolved > 0) {
                Line(stringResource(R.string.transfer_report_matches, report.matchesResolved))
                report.decisions.entries.sortedBy { it.key.ordinal }.forEach { (decision, count) ->
                    Line(stringResource(R.string.transfer_report_decision, decision.label(), count))
                }
            }
            if (report.notices.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.transfer_report_not_transferred),
                    style = SklyankaType.BodyStrong,
                    color = colors.text,
                    modifier = Modifier.testTag(TAG_NOT_TRANSFERRED),
                )
                report.notices.forEach { notice ->
                    Line(notice.label(medicationNames[notice.medicationId()]))
                }
            }
            safetyCopyName?.let { name ->
                Line(stringResource(R.string.transfer_report_safety_copy, name))
            }
            PrimaryButton(
                text = stringResource(R.string.transfer_report_continue),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().testTag(TAG_REPORT_CONTINUE),
            )
        }
    }
}

@Composable
private fun Line(text: String) {
    Text(text = text, style = SklyankaType.BodyDefault, color = SklyankaTheme.colors.text)
}

/** Идентификатор записи, к которой относится замечание; у замечаний о настройках его нет. */
private fun TransferNotice.medicationId(): String? = (this as? TransferNotice.UnknownEnum)?.medicationId

const val TAG_REPORT = "transfer_report"
const val TAG_REPORT_CONTINUE = "transfer_report_continue"
const val TAG_NOT_TRANSFERRED = "transfer_not_transferred"
