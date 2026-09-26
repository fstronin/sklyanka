package ru.heuri.sklyanka.feature.meds.archive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * Вкладка «Архив» вместе с её состоянием.
 *
 * Переходы наружу — к списку активных и в карточку лекарства — подняты в параметры по той
 * же причине, что и у остальных экранов раздела: `feature-meds` не знает, из чего собран
 * граф навигации.
 */
@Composable
fun ArchiveRoute(
    viewModel: ArchiveViewModel,
    onSelectActive: () -> Unit,
    onOpenMedicationCard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    ArchiveScreen(
        state = state,
        onIntent = { intent ->
            when (intent) {
                ArchiveIntent.SelectActive -> onSelectActive()
                is ArchiveIntent.OpenMedication -> onOpenMedicationCard(intent.medicationId)
                else -> viewModel.onIntent(intent)
            }
        },
        modifier = modifier,
    )
}
