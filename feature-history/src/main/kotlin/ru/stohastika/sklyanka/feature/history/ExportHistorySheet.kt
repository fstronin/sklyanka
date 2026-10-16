package ru.stohastika.sklyanka.feature.history

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.history.ReportPreset
import ru.stohastika.sklyanka.ui.component.SklyankaBottomSheet
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Шторка экспорта истории — CSV, открывается в Excel (SKL-119, Р-35, §5.16).
 *
 * Та же сборка, что у шторки отчёта ([DoctorReportSheet]): модальная шапка, чипы тех же
 * четырёх пресетов с теми же границами «Другого периода», карточка состава, тихое пояснение
 * и действия одной формой. Второго набора пресетов и второй поверхности не заводится —
 * отличается только содержимое карточки: у файла нет «Принято», зато есть «Записей» и «CSV».
 *
 * Тип файла назван честно: CSV открывается таблицей, и никакой табличной библиотеки
 * в продукт не добавлено (Р-35). Файл формируется **одного активного профиля** (ПРФ-23),
 * и человек видит это в карточке до нажатия.
 */
@Composable
internal fun ExportHistorySheet(ui: ExportUi, outcome: ExportOutcome?, onIntent: (ExportHistoryIntent) -> Unit) {
    var dialog by remember { mutableStateOf(ReportDateField.NONE) }
    Column(modifier = Modifier.testTag(TAG_EXPORT_SHEET)) {
        SheetHeader(
            title = R.string.history_export_title,
            success = outcome?.isSuccess == true,
            onDismiss = { onIntent(ExportHistoryIntent.Dismissed) },
        )
        if (outcome?.isSuccess == true) {
            SuccessContent(
                title = R.string.history_export_success_title,
                saved = outcome == ExportOutcome.SAVED,
                titleTag = TAG_EXPORT_SUCCESS,
                markTag = TAG_EXPORT_SUCCESS_MARK,
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SklyankaSpacing.Section)
                    .padding(top = SklyankaSpacing.Block),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Block),
            ) {
                outcome?.takeIf { !it.isSuccess }?.let { ExportOutcomeBanner(it) }
                PeriodChips(ui.preset) { onIntent(ExportHistoryIntent.PeriodChosen(it)) }
                ExportCompositionCard(ui) { field -> dialog = field }
                ExportDataNote()
            }
            SheetActions(
                shareTag = TAG_EXPORT_SHARE,
                saveTag = TAG_EXPORT_SAVE,
                onShare = { onIntent(ExportHistoryIntent.ShareRequested) },
                onSave = { onIntent(ExportHistoryIntent.SaveRequested) },
            )
        }
    }
    ReportDateDialog(
        field = dialog,
        from = ui.customFrom,
        to = ui.customTo,
        today = ui.today,
        onStart = { onIntent(ExportHistoryIntent.StartPicked(it)) },
        onEnd = { onIntent(ExportHistoryIntent.EndPicked(it)) },
        onClose = { dialog = ReportDateField.NONE },
    )
}

/**
 * Сообщение исхода выгрузки: отмена — нейтрально, отказы — предупреждением.
 *
 * «Места недостаточно» стоит отдельно от прочих отказов: это единственная причина, которую
 * человек может исправить сам, и слова у неё свои (§5.16). Ни один исход не выдаёт успех,
 * которого не было (П-7).
 */
@Composable
private fun ExportOutcomeBanner(outcome: ExportOutcome) {
    InlineOutcome(text = stringResource(outcome.message()), warning = outcome.isWarning())
}

private fun ExportOutcome.message(): Int = when (this) {
    ExportOutcome.CANCELLED -> R.string.history_export_cancelled
    ExportOutcome.FAILED_SHARE -> R.string.history_export_share_failed
    ExportOutcome.FAILED_SAVE -> R.string.history_export_save_failed
    ExportOutcome.NO_SPACE -> R.string.history_export_no_space
    ExportOutcome.SHARED, ExportOutcome.SAVED -> R.string.history_export_success_title
}

private fun ExportOutcome.isWarning(): Boolean = this != ExportOutcome.CANCELLED

/** Карточка состава файла: тот же вид «подпись — значение», что у отчёта. */
@Composable
private fun ExportCompositionCard(ui: ExportUi, onPickDate: (ReportDateField) -> Unit) {
    val colors = SklyankaTheme.colors
    val composition = ui.composition
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SklyankaShapes.Card)
            .background(colors.surface)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card)
            .padding(horizontal = SklyankaSpacing.Block)
            .testTag(TAG_EXPORT_CONTENT),
    ) {
        CompositionRow(
            label = stringResource(R.string.history_report_label_profile),
            testTag = TAG_EXPORT_PROFILE,
            value = composition.profile,
        )
        CardDivider()
        if (ui.preset == ReportPreset.CUSTOM) {
            CompositionRow(
                label = stringResource(R.string.history_report_label_start),
                testTag = TAG_EXPORT_START,
                value = composition.from,
                onValueClick = { onPickDate(ReportDateField.START) },
                numbersInLiterata = true,
            )
            CardDivider()
            CompositionRow(
                label = stringResource(R.string.history_report_label_end),
                testTag = TAG_EXPORT_END,
                value = composition.to,
                onValueClick = { onPickDate(ReportDateField.END) },
                numbersInLiterata = true,
            )
        } else {
            CompositionRow(
                label = stringResource(R.string.history_report_label_period),
                testTag = TAG_EXPORT_PERIOD,
                value = composition.period,
                numbersInLiterata = true,
            )
        }
        CardDivider()
        ExportRecordsRow(composition)
        CardDivider()
        CompositionRow(
            label = stringResource(R.string.history_report_label_format),
            testTag = TAG_EXPORT_FORMAT,
            value = composition.format,
        )
    }
}

/**
 * Строка «Записей»: число строк таблицы или фраза пустого периода (Р-33).
 *
 * Нулём пустой период не показывается: «0» читалось бы как измерение, а измерять нечего —
 * за период нет ни одной записи. Файл при этом формируется: шапка называет профиль и период,
 * и это честный ответ «записей нет».
 */
@Composable
private fun ExportRecordsRow(composition: ExportComposition) {
    val records = composition.records
    if (records == null) {
        Text(
            text = stringResource(R.string.report_document_empty),
            style = SklyankaType.BodyDefaultStrong,
            color = SklyankaTheme.colors.text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SklyankaSpacing.Large)
                .testTag(TAG_EXPORT_EMPTY),
        )
    } else {
        CompositionRow(
            label = stringResource(R.string.history_export_label_records),
            testTag = TAG_EXPORT_RECORDS,
            value = records,
            numbersInLiterata = true,
        )
    }
}

/** Тихое пояснение о медицинских данных: подпись под карточкой, а не предупреждение (НФТ-18). */
@Composable
private fun ExportDataNote() {
    Text(
        text = stringResource(R.string.history_export_note),
        style = SklyankaType.Caption,
        color = SklyankaTheme.colors.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_EXPORT_DATA_NOTE),
    )
}

/**
 * Шторка экспорта вместе с системными диалогами (SKL-119).
 *
 * Системный выбор места (SAF) и системный обмен живут здесь, а не в `ViewModel`: их результат
 * умеет получить только `composable` (НФТ-17, НФТ-46). Наружу уходит непрозрачная ссылка
 * строкой — домен про `Uri` не знает. Запуск выбора места — только при наличии обработчика
 * (SKL-46): без него запуск завершается отказом раньше, чем человек увидит хоть что-то.
 */
@Composable
fun ExportHistoryRoute(viewModel: ExportHistoryViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val mediaType = stringResource(R.string.history_export_media_type)

    val shareLauncher = rememberReportShareLauncher()
    LaunchedEffect(shareLauncher) { viewModel.attachShareLauncher(shareLauncher) }

    val saveLauncher = rememberLauncherForActivityResult(
        // Имя предлагается системе, но окончательное решение — за человеком: приложение
        // не контролирует, куда попадёт файл, и выбранный путь не сохраняет (НФТ-17).
        contract = ActivityResultContracts.CreateDocument(mediaType),
    ) { uri ->
        // Отмена системного диалога — не молчание: приложение обязано сказать человеку,
        // что файл не сохранён, и оставить след (П-7).
        val intent = uri?.let { ExportHistoryIntent.SaveTo(it.toString()) } ?: ExportHistoryIntent.SaveCancelled
        viewModel.onIntent(intent)
    }

    ExportSheetHost(
        ui = state.sheet,
        outcome = state.outcome,
        onIntent = { intent ->
            when (intent) {
                ExportHistoryIntent.SaveRequested -> state.sheet?.let { sheet ->
                    val transferable = sheet.document.asTransferable()
                    if (canCreateDocument(context, transferable)) {
                        try {
                            saveLauncher.launch(sheet.document.fileName)
                        } catch (_: ActivityNotFoundException) {
                            // Обработчик исчез между проверкой и запуском: исход тот же —
                            // честный отказ, а не падение.
                            viewModel.onIntent(ExportHistoryIntent.SaveUnavailable)
                        }
                    } else {
                        viewModel.onIntent(ExportHistoryIntent.SaveUnavailable)
                    }
                }

                else -> viewModel.onIntent(intent)
            }
        },
        modifier = modifier,
    )
}

/** Слой шторки: затемнение, движение и удержание последнего содержимого — те же, что у отчёта. */
@Composable
private fun ExportSheetHost(
    ui: ExportUi?,
    outcome: ExportOutcome?,
    onIntent: (ExportHistoryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastUi by remember { mutableStateOf<ExportUi?>(null) }
    var lastOutcome by remember { mutableStateOf<ExportOutcome?>(null) }
    LaunchedEffect(ui, outcome) {
        if (ui != null) {
            lastUi = ui
            lastOutcome = outcome
        }
    }
    BackHandler(enabled = ui != null) { onIntent(ExportHistoryIntent.Dismissed) }
    SklyankaBottomSheet(
        visible = ui != null,
        onDismiss = { onIntent(ExportHistoryIntent.Dismissed) },
        modifier = modifier,
    ) {
        lastUi?.let { shown -> ExportHistorySheet(shown, lastOutcome, onIntent) }
    }
}

const val TAG_EXPORT_SHEET = "history_export_sheet"
const val TAG_EXPORT_CONTENT = "history_export_content"
const val TAG_EXPORT_PROFILE = "history_export_profile"
const val TAG_EXPORT_PERIOD = "history_export_period"
const val TAG_EXPORT_START = "history_export_start"
const val TAG_EXPORT_END = "history_export_end"
const val TAG_EXPORT_RECORDS = "history_export_records"
const val TAG_EXPORT_EMPTY = "history_export_empty"
const val TAG_EXPORT_FORMAT = "history_export_format"
const val TAG_EXPORT_DATA_NOTE = "history_export_data_note"
const val TAG_EXPORT_SHARE = "history_export_share"
const val TAG_EXPORT_SAVE = "history_export_save"
const val TAG_EXPORT_SUCCESS = "history_export_success"
const val TAG_EXPORT_SUCCESS_MARK = "history_export_success_mark"
