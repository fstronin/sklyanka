package ru.stohastika.sklyanka.feature.settings.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.domain.analytics.AnalyticsScreen
import ru.stohastika.sklyanka.ui.analytics.TrackScreenView

/**
 * Форма профиля вместе с её состоянием: мастер и правка (SKL-121).
 *
 * Открытие — один раз на заход, а не на сборку композиции: поворот пересобирает композицию
 * целиком, и безусловное `open()` затирало бы набранное имя — оно живёт в ViewModel, которую
 * пересоздание переживает, а признак «форма уже открыта» лежит в сохранённом состоянии
 * композиции (образец формы лекарства, SKL-142).
 *
 * Пока причина — платная граница, то же нажатие просит витрину ([ProfileFormViewModel.showcase]):
 * поверхность покупки одна на все точки входа (Р-67), и показывает её `app`. После закрытия
 * витрины `app` зовёт [ProfileFormViewModel.refreshProAccess], и создание завершает та же
 * кнопка — второго входа в мастер не требуется (§5.13, Р-84 п. 2).
 *
 * @param mode `Add` — мастер; `Edit` — правка названного профиля.
 * @param onLeave уход с экрана: системный «Назад» и успешное сохранение ведут в настройки.
 */
@Composable
fun ProfileFormRoute(
    viewModel: ProfileFormViewModel,
    mode: ProfileFormMode,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPro: () -> Unit = {},
) {
    // Мастер и правка — один экран профилей: показ пишется один раз за вход
    // (SKL-165, TDD §5.4).
    TrackScreenView(AnalyticsScreen.PROFILES)
    var opened by rememberSaveable(mode) { mutableStateOf(false) }
    LaunchedEffect(mode) {
        if (!opened || viewModel.state.value == null) {
            opened = true
            viewModel.onIntent(ProfileFormIntent.Open(mode))
        }
    }
    val state by viewModel.state.collectAsState()

    // Успешное создание или сохранение закрывает форму: список профилей пересобирается
    // потоком, и перечитывать его отдельно не нужно. Событие не повторяется при новой
    // подписке, поэтому открытие формы после сохранения закрыться от него не может (SKL-57).
    LaunchedEffect(Unit) { viewModel.closed.collect { onLeave() } }
    LaunchedEffect(Unit) { viewModel.showcase.collect { onOpenPro() } }

    val current = state ?: return
    ProfileFormScreen(state = current, onIntent = viewModel::onIntent, modifier = modifier)
}
