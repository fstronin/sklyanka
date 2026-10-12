package ru.stohastika.sklyanka.feature.history

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CompletableDeferred
import ru.stohastika.sklyanka.domain.history.ReportShare
import ru.stohastika.sklyanka.domain.history.ReportShareLauncher
import ru.stohastika.sklyanka.domain.history.ReportShareResult

/**
 * Экран истории вместе с его состоянием и системными диалогами.
 *
 * Возврат поднят в параметр: «Назад» ведёт туда, откуда человек пришёл, — в настройки при
 * входе из настроек (`docs/norm/04-behavior.md`, «Навигация»). На макете стрелка ведёт
 * на «Сегодня», и это расхождение устраняется здесь: решать, куда ведёт возврат, обязан
 * тот, кто собрал маршрут, а не экран.
 *
 * Системный выбор места (SAF) живёт здесь, а не в `ViewModel` и не в домене: это результат
 * чужой активности, и получить его может только composable. Наружу уходит непрозрачная
 * ссылка строкой — домен про `Uri` не знает (НФТ-46). Разрешений на внешнее хранилище
 * приложение не запрашивает (НФТ-17).
 *
 * **Запуск системного выбора места — только при наличии обработчика** (SKL-46): без него
 * запуск завершается `ActivityNotFoundException` раньше, чем человек увидит хоть что-то,
 * и честный отказ «Отчёт не сохранён» остаётся недостижимым.
 *
 * **Системный обмен получателя тоже живёт здесь.** Успех передачи ставится только по
 * `RESULT_OK` системного выбора, а не в момент его запуска: пока диалог открыт, исход
 * неизвестен (SKL-101, дефект 4).
 */
@Composable
fun HistoryRoute(viewModel: HistoryViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val mediaType = stringResource(R.string.report_media_type)

    val shareLauncher = rememberReportShareLauncher()
    // Показ системного выбора получателя подставляется состоянию разметкой: домен о нём
    // не знает, а получить результат диалога умеет только экран.
    LaunchedEffect(shareLauncher) { viewModel.attachShareLauncher(shareLauncher) }

    val saveLauncher = rememberLauncherForActivityResult(
        // Имя предлагается системе, но окончательное решение — за человеком: приложение
        // не контролирует, куда попадёт файл, и выбранный путь не сохраняет (НФТ-17).
        contract = ActivityResultContracts.CreateDocument(mediaType),
    ) { uri ->
        // Отмена системного диалога — не молчание: приложение обязано сказать человеку,
        // что отчёт не сохранён, и оставить след (`TDD.md §11`).
        val intent = uri?.let { HistoryIntent.SaveTo(it.toString()) } ?: HistoryIntent.SaveCancelled
        viewModel.onIntent(intent)
    }

    HistoryScreen(
        state = state,
        onIntent = { intent ->
            when (intent) {
                HistoryIntent.Back -> onBack()
                HistoryIntent.SaveRequested -> state.report?.let { report ->
                    if (canCreateDocument(context, report.document)) {
                        try {
                            saveLauncher.launch(report.document.fileName)
                        } catch (_: ActivityNotFoundException) {
                            // Обработчик исчез между проверкой и запуском: исход тот же —
                            // честный отказ, а не падение.
                            viewModel.onIntent(HistoryIntent.SaveUnavailable)
                        }
                    } else {
                        viewModel.onIntent(HistoryIntent.SaveUnavailable)
                    }
                }

                else -> viewModel.onIntent(intent)
            }
        },
        modifier = modifier,
    )
}

/**
 * Показ системного выбора получателя отчёта.
 *
 * Успех — только `RESULT_OK`; всё остальное, включая закрытие диалога, — отмена.
 * `ActivityNotFoundException` означает, что системного обмена в устройстве нет:
 * это отказ, а не молчание (SKL-101, дефект 4). `IntentSender`, который принимает
 * `createChooser`, не заводится: его результат тоже пришлось бы ждать через экран,
 * а `StartActivityForResult` доносит то же решение без лишнего контура.
 */
@Composable
private fun rememberReportShareLauncher(): ReportShareLauncher {
    val pending = remember { PendingShare() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pending.complete(
            if (result.resultCode == Activity.RESULT_OK) ReportShareResult.CHOSEN else ReportShareResult.DISMISSED,
        )
    }
    return remember(launcher) {
        ReportShareLauncher { share ->
            val decision = CompletableDeferred<ReportShareResult>()
            pending.await = decision
            try {
                launcher.launch(shareChooser(share))
            } catch (_: ActivityNotFoundException) {
                pending.await = null
                return@ReportShareLauncher ReportShareResult.UNAVAILABLE
            }
            decision.await()
        }
    }
}

/**
 * Системное намерение обмена: PDF-файл с доступом ровно к одному документу и только
 * на чтение. Флаги и `ClipData` — тот же способ, что у `FileProvider`-выдачи: получатель
 * не получает доступ к каталогу кеша целиком (НФТ-17).
 */
private fun shareChooser(share: ReportShare): Intent {
    val uri = Uri.parse(share.handle)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = share.mediaType
        putExtra(Intent.EXTRA_TITLE, share.fileName)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(share.fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, null)
}

/** Ожидание решения системного выбора: переживает рекомпозицию, но не уход экрана. */
private class PendingShare {
    var await: CompletableDeferred<ReportShareResult>? = null

    fun complete(result: ReportShareResult) {
        await?.complete(result)
        await = null
    }
}
