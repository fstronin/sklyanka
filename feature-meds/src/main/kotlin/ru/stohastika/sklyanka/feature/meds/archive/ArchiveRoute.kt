package ru.stohastika.sklyanka.feature.meds.archive

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.ui.component.BottomBar
import ru.stohastika.sklyanka.ui.component.BottomBarTab

/**
 * Вкладка «Архив» вместе с её состоянием.
 *
 * Переходы наружу — к списку активных и в карточку лекарства — подняты в параметры по той
 * же причине, что и у остальных экранов раздела: `feature-meds` не знает, из чего собран
 * граф навигации. «Архив» — корень вкладки «Лекарства» и несёт ту же нижнюю панель (ОВ-91).
 *
 * Уход с экрана закрывает окно удаления: точка выхода одна на все пути навигации,
 * как у «Сегодня» (SKL-30, ОВ-124).
 */
@Composable
fun ArchiveRoute(
    viewModel: ArchiveViewModel,
    onSelectActive: () -> Unit,
    onOpenMedicationCard: (String) -> Unit,
    onOpenToday: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenLeft() }
    }
    Column(modifier = modifier) {
        ArchiveScreen(
            state = state,
            // Аватар в чужом профиле ведёт в список профилей настроек (ПРФ-19, §5.8).
            onOpenProfiles = onOpenSettings,
            onIntent = { intent ->
                when (intent) {
                    ArchiveIntent.SelectActive -> onSelectActive()
                    is ArchiveIntent.OpenMedication -> onOpenMedicationCard(intent.medicationId)
                    else -> viewModel.onIntent(intent)
                }
            },
            modifier = Modifier.weight(1f),
        )
        BottomBar(
            selected = BottomBarTab.Meds,
            onSelect = { tab ->
                when (tab) {
                    BottomBarTab.Today -> onOpenToday()
                    BottomBarTab.Settings -> onOpenSettings()
                    BottomBarTab.Meds -> Unit
                }
            },
        )
    }
}
