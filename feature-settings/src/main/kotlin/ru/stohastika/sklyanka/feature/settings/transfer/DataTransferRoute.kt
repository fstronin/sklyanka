package ru.stohastika.sklyanka.feature.settings.transfer

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.domain.transfer.TransferCounts
import ru.stohastika.sklyanka.domain.transfer.TransferFormat

/**
 * Раздел «Перенос данных»: шаги одного экрана и системный выбор файла.
 *
 * Системный выбор места и файла (SAF) живёт здесь, а не в `ViewModel` и не в домене:
 * это результат чужой активности, и получить его может только composable. Наружу уходит
 * непрозрачная ссылка строкой — домен про `Uri` не знает (НФТ-46). Разрешений на внешнее
 * хранилище приложение не запрашивает (НФТ-17).
 *
 * Переход на экран настройки разрешений поднят в параметр: его делает SKL-14, а
 * `feature-settings` от других экранных модулей не зависит. Точка расширения задана
 * типом, поэтому подключение маршрута не потребует правок экрана.
 */
@Composable
fun DataTransferRoute(
    viewModel: DataTransferViewModel,
    onOpenPermissions: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    val saveLauncher = rememberLauncherForActivityResult(
        // Имя предлагается системе, но окончательное решение — за человеком: приложение
        // не контролирует, куда попадёт файл, и не сохраняет выбранный путь (ПЕР-36).
        contract = ActivityResultContracts.CreateDocument(MIME_JSON),
    ) { uri -> uri?.let { viewModel.onIntent(TransferIntent.SaveTo(it.toString())) } }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onIntent(TransferIntent.FileChosen(it.toString())) } }

    // Загрузка не переживает уход с экрана (ПЕР-17): при возвращении перенос начинается
    // сначала, и разобранный файл не остаётся висеть в памяти вместе с историей человека.
    DisposableEffect(Unit) {
        onDispose { viewModel.onIntent(TransferIntent.Leave) }
    }

    val overview: @Composable () -> Unit = {
        DataTransferScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onSave = { withPicker(viewModel) { saveLauncher.launch(state.exportFileName) } },
            onPickFile = { withPicker(viewModel) { pickLauncher.launch(arrayOf(MIME_JSON, MIME_ANY)) } },
            onBack = onBack,
            modifier = modifier,
        )
    }

    when (state.step) {
        TransferStep.OVERVIEW -> overview()

        TransferStep.SUMMARY -> TransferSummaryScreen(
            counts = state.fileCounts ?: EMPTY_COUNTS,
            state = state,
            zone = state.zone,
            onContinue = { viewModel.onIntent(TransferIntent.ContinueFromSummary) },
            onCancel = { viewModel.onIntent(TransferIntent.Leave) },
            modifier = modifier,
        )

        TransferStep.MATCHES -> MatchReviewScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onCancel = { viewModel.onIntent(TransferIntent.Leave) },
            modifier = modifier,
        )

        TransferStep.REPLACE_CONFIRM -> ReplaceConfirmationStep(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = modifier,
        )

        // Отчёт без отчёта невозможен, но состояние это допускает, и падать здесь нельзя:
        // экран просто возвращается к исходному шагу.
        TransferStep.REPORT -> state.report?.let { report ->
            TransferReportScreen(
                report = report,
                medicationNames = state.medicationNames,
                safetyCopyName = state.safetyCopyName,
                // После отчёта — экран настройки разрешений (ПЕР-25): разрешения файлом
                // не переносятся, и без этого шага человек получит молчащее приложение
                // и уверенность, что перенос удался.
                onContinue = {
                    viewModel.onIntent(TransferIntent.AcknowledgeReport)
                    onOpenPermissions()
                },
                modifier = modifier,
            )
        } ?: overview()
    }
}

/**
 * Шаг подтверждения замены: числа, слово и опасное действие (ПЕР-16, ОВ-67).
 *
 * Вынесен из [DataTransferRoute] отдельной сборкой: маршрут держит все шаги сразу,
 * и его длина — про число шагов, а не про устройство одного из них.
 */
@Composable
private fun ReplaceConfirmationStep(state: TransferUiState, onIntent: (TransferIntent) -> Unit, modifier: Modifier) {
    ReplaceConfirmScreen(
        device = state.deviceCounts ?: EMPTY_COUNTS,
        file = state.fileCounts ?: EMPTY_COUNTS,
        safetyCopyName = TransferFormat.SAFETY_COPY_NAME,
        confirmationWord = state.confirmationWord,
        onConfirmationWordChange = { onIntent(TransferIntent.ConfirmationWordChanged(it)) },
        onConfirm = { onIntent(TransferIntent.Apply) },
        onCancel = { onIntent(TransferIntent.Leave) },
        modifier = modifier,
    )
}

/**
 * Запуск системного выбора файлов на устройстве, где его может не быть.
 *
 * Выбор места и файла — чужая активность, и на устройстве без неё `launch` бросает
 * `ActivityNotFoundException` прямо в главный поток: приложение умирает по нажатию кнопки.
 * Так и происходило на образе без системного выбора файлов — то есть отказ этот не
 * умозрительный. Человеку вместо падения говорится, что выбрать файл нечем.
 */
private inline fun withPicker(viewModel: DataTransferViewModel, launch: () -> Unit) {
    try {
        launch()
    } catch (_: ActivityNotFoundException) {
        // Исключение — единственный способ узнать об отсутствии активности заранее:
        // опрос PackageManager отвечает на другой вопрос и на части прошивок отвечает неверно.
        viewModel.onIntent(TransferIntent.NoFilePicker)
    }
}

/** Пустая сводка: состояние допускает шаг без прочитанного файла, и падать на нём нельзя. */
private val EMPTY_COUNTS = TransferCounts(profiles = 0, medications = 0, archived = 0, intakes = 0)

private const val MIME_JSON = "application/json"

/**
 * Второй тип для выбора файла.
 *
 * Файл, пришедший из мессенджера или из облачного клиента, нередко отдаётся как
 * `application/octet-stream`: ограничение выбора одним типом означало бы, что человек
 * видит свою резервную копию серой и не может её выбрать.
 */
private const val MIME_ANY = "*/*"
