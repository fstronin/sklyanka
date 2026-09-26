package ru.heuri.sklyanka.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * Экран «Сегодня» вместе с его состоянием.
 *
 * Переходы наружу подняты в параметры: `feature-today` не зависит от других экранных
 * модулей, и решать, куда ведут «Купить», «Добавить лекарство» и нажатие на строку
 * приёма, обязан тот, кто собирает маршруты, то есть `app` (`TDD.md §8.2`).
 */
@Composable
fun TodayRoute(viewModel: TodayViewModel, navigation: TodayNavigation, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    TodayScreen(
        state = state,
        onIntent = { intent ->
            when (intent) {
                is TodayIntent.OpenMedication -> navigation.openMedicationCard(intent.medicationId)
                is TodayIntent.OpenStock -> navigation.openMedicationStock(intent.medicationId)
                TodayIntent.AddMedication -> navigation.addMedication()
                TodayIntent.OpenMedicationList -> navigation.openMedicationList()
                TodayIntent.OpenSettings -> navigation.openSettings()
                else -> viewModel.onIntent(intent)
            }
        },
        modifier = modifier,
    )
}

/**
 * Переходы экрана одним значением.
 *
 * Пять лямбд подряд в подписи маршрута путают местами на первом же вызове, а тип
 * с именами полей — нет.
 */
data class TodayNavigation(
    /** Строка приёма — карточка лекарства (SKL-10). */
    val openMedicationCard: (String) -> Unit = {},
    /** «Купить» — карточка лекарства на блоке остатка (SKL-12). */
    val openMedicationStock: (String) -> Unit = {},
    /** «Добавить лекарство» — форма нового лекарства (SKL-09). */
    val addMedication: () -> Unit = {},
    val openMedicationList: () -> Unit = {},
    val openSettings: () -> Unit = {},
)
