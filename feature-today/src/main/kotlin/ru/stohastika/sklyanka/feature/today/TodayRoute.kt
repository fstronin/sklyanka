package ru.stohastika.sklyanka.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.domain.analytics.AnalyticsScreen
import ru.stohastika.sklyanka.ui.analytics.TrackScreenView

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
    // Показ экрана — один раз на вход: повторная композиция события не плодит (TDD §5.4).
    TrackScreenView(AnalyticsScreen.TODAY)
    // Уход с экрана любым путём — конец окна отмены (SKL-30): один механизм на все
    // маршруты вместо пяти вызовов по местам навигации, где шестой о нём забыл бы.
    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenLeft() }
    }
    TodayScreen(
        state = state,
        onIntent = { intent ->
            when (intent) {
                is TodayIntent.OpenMedication -> navigation.openMedicationCard(intent.medicationId)
                is TodayIntent.OpenStock -> navigation.openMedicationStock(intent.medicationId)
                TodayIntent.AddMedication -> navigation.addMedication()
                TodayIntent.OpenMedicationList -> navigation.openMedicationList()
                TodayIntent.OpenSettings -> navigation.openSettings()
                TodayIntent.OpenPermissions -> navigation.openPermissions()
                TodayIntent.OpenHistory -> navigation.openHistory()
                else -> viewModel.onIntent(intent)
            }
        },
        modifier = modifier,
    )
}

/**
 * Переходы экрана одним значением.
 *
 * Лямбды подряд в подписи маршрута путают местами на первом же вызове, а тип
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
    /** «Настроить» в предупреждении о ненадёжности — экран разрешений (SKL-29). */
    val openPermissions: () -> Unit = {},
    /** «История» в строке прогресса дня — экран истории (ОВ-84, SKL-31). */
    val openHistory: () -> Unit = {},
)
