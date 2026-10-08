package ru.stohastika.sklyanka.feature.meds.card

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Карточка лекарства вместе с её состоянием.
 *
 * Переходы наружу подняты в параметры: `feature-meds` не зависит от других экранных
 * модулей, и решать, куда ведут «Назад» и «Изменить», обязан тот, кто собирает маршруты.
 *
 * Архивация возвращает человека в список лекарств (`docs/v2/10-behavior.md`), и это
 * делает маршрут, а не экран: сам экран о существовании списка не знает.
 *
 * Мягкое удаление (SKL-99, ОВ-124) закрывает карточку двумя путями, и оба ведут сюда:
 * подтверждение выставляет `closed`, а сама запись исчезает из активных и архивных
 * выборок — состояние становится `null`. Память «карточка уже открывалась» нужна
 * затем, чтобы второй путь не оставил поверх списка пустой слой: закрывать его было бы
 * некому. До первого состояния идёт чтение, и назад уходить рано.
 */
@Composable
fun MedicationCardRoute(
    medicationId: String,
    viewModel: MedicationCardViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(medicationId) { viewModel.open(medicationId) }
    val state by viewModel.state.collectAsState()

    var wasOpen by remember(medicationId) { mutableStateOf(false) }
    val current = state
    if (current != null) {
        wasOpen = true
    } else {
        // Карточка чужого профиля не открывается вовсе (ПР-10), и назад уходить некуда:
        // состояние так и не появилось. Уже открытая карточка, потерявшая запись, —
        // другое дело: человек удалил лекарство, и его возвращают в список.
        if (wasOpen) LaunchedEffect(medicationId) { onBack() }
        return
    }
    LaunchedEffect(current.closed) { if (current.closed) onBack() }

    MedicationCardScreen(
        state = current,
        onIntent = { intent ->
            when (intent) {
                MedCardIntent.Back -> onBack()
                MedCardIntent.Edit -> onEdit(current.medicationId)
                else -> viewModel.onIntent(intent)
            }
        },
        modifier = modifier,
    )
}
