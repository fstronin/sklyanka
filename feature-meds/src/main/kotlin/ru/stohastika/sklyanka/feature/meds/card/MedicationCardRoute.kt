package ru.stohastika.sklyanka.feature.meds.card

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * Карточка лекарства вместе с её состоянием.
 *
 * Переходы наружу подняты в параметры: `feature-meds` не зависит от других экранных
 * модулей, и решать, куда ведут «Назад» и «Изменить», обязан тот, кто собирает маршруты.
 *
 * Архивация возвращает человека в список лекарств (`docs/spec/03-screens.md`), и это
 * делает маршрут, а не экран: сам экран о существовании списка не знает.
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

    val current = state ?: return
    // Карточка чужого профиля не открывается: состояние остаётся пустым, и экран
    // не рисуется вовсе (ПР-10). Уход с экрана — дело того, кто собрал маршрут.
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
