package ru.stohastika.sklyanka.feature.settings.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.feature.settings.transfer.MIME_JSON
import ru.stohastika.sklyanka.feature.settings.transfer.withPicker

/**
 * Экран удаления профиля вместе с системным выбором места выгрузки (ОВ-7, SKL-36).
 *
 * Системный выбор места (SAF) живёт здесь, а не в ViewModel и не в домене: это результат
 * чужой активности, и получить его может только composable. Наружу уходит непрозрачная
 * ссылка строкой — домен про `Uri` не знает (НФТ-46). Контракт и обработчик отсутствия
 * выбора файлов — те же, что у переноса (SKL-19): выгрузка одна и та же.
 *
 * Уход с экрана шлёт [DeleteProfileIntent.Leave]: состояние не переживает уход, и при
 * возвращении экран начинается с нового профиля (того же правила, что у переноса).
 * Повторное открытие возможно и для другого чужого профиля — состояние обязано быть
 * чистым, а профиль — прочитан заново.
 */
@Composable
fun DeleteProfileRoute(viewModel: DeleteProfileViewModel, onLeave: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    val saveLauncher = rememberLauncherForActivityResult(
        // Имя предлагается системе, но окончательное решение — за человеком: приложение
        // не контролирует, куда попадёт файл, и не сохраняет выбранный путь (ПЕР-36).
        contract = ActivityResultContracts.CreateDocument(MIME_JSON),
    ) { uri -> uri?.let { viewModel.onIntent(DeleteProfileIntent.ExportTargetChosen(it.toString())) } }

    DisposableEffect(Unit) {
        onDispose { viewModel.onIntent(DeleteProfileIntent.Leave) }
    }

    DeleteProfileScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onSaveFile = {
            withPicker(onMissing = { viewModel.onIntent(DeleteProfileIntent.NoFilePicker) }) {
                saveLauncher.launch(state.exportFileName)
            }
        },
        onDone = onLeave,
        modifier = modifier,
    )
}
