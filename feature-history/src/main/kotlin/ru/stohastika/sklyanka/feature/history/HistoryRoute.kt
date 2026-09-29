package ru.stohastika.sklyanka.feature.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Экран истории вместе с его состоянием и системным выбором места.
 *
 * Возврат поднят в параметр: «Назад» ведёт туда, откуда человек пришёл, — в настройки при
 * входе из настроек (`docs/spec/03-screens.md`, «Навигация»). На макете стрелка ведёт
 * на «Сегодня», и это расхождение устраняется здесь: решать, куда ведёт возврат, обязан
 * тот, кто собрал маршрут, а не экран.
 *
 * Системный выбор места (SAF) живёт здесь, а не в `ViewModel` и не в домене: это результат
 * чужой активности, и получить его может только composable. Наружу уходит непрозрачная
 * ссылка строкой — домен про `Uri` не знает (НФТ-46). Разрешений на внешнее хранилище
 * приложение не запрашивает (НФТ-17).
 */
@Composable
fun HistoryRoute(viewModel: HistoryViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val mediaType = stringResource(R.string.report_media_type)

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
                HistoryIntent.SaveRequested -> state.report?.let { saveLauncher.launch(it.document.fileName) }
                else -> viewModel.onIntent(intent)
            }
        },
        modifier = modifier,
    )
}
