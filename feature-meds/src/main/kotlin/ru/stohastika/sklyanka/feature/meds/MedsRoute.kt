package ru.stohastika.sklyanka.feature.meds

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.domain.analytics.AnalyticsScreen
import ru.stohastika.sklyanka.feature.meds.list.MedicationListScreen
import ru.stohastika.sklyanka.feature.meds.list.MedicationListViewModel
import ru.stohastika.sklyanka.feature.meds.list.MedsIntent
import ru.stohastika.sklyanka.feature.meds.list.MedsTab
import ru.stohastika.sklyanka.ui.analytics.TrackScreenView
import ru.stohastika.sklyanka.ui.component.BottomBar
import ru.stohastika.sklyanka.ui.component.BottomBarTab

/**
 * Раздел «Лекарства»: список активных лекарств и нижняя панель.
 *
 * Формы здесь нет: она — место в стеке раздела наравне с карточкой и архивом, и поднимает
 * его тот, кто собирает маршруты, то есть `app` (SKL-150). Пока открытость формы жила
 * флагом этого маршрута, стек раздела оставался на списке, и системная «Назад» с формы,
 * открытой круглой кнопкой, уводила человека из раздела на «Сегодня» — тогда как та же
 * форма, открытая с «Сегодня», возвращала в список: два входа вели себя противоположно
 * (`docs/norm/04-behavior.md` §5.1 — системная «Назад» повторяет возврат).
 *
 * Переходы наружу — форма нового лекарства, карточка лекарства (SKL-10) и архив (SKL-11) —
 * приходят параметрами: `feature-meds` не зависит от других экранных модулей, и решать,
 * куда ведут эти переходы, обязан `app`.
 */
@Suppress("LongParameterList")
@Composable
fun MedsRoute(
    listViewModel: MedicationListViewModel,
    onOpenMedicationCard: (String) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenNewMedication: () -> Unit,
    onOpenToday: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Показ экрана — один раз на вход: повторная композиция события не плодит (TDD §5.4).
    TrackScreenView(AnalyticsScreen.MEDICATIONS)
    val listState by listViewModel.state.collectAsState()
    // Уход с экрана закрывает окно удаления (SKL-30, ОВ-124): точка выхода одна.
    DisposableEffect(Unit) {
        onDispose { listViewModel.onScreenLeft() }
    }

    // Нижняя панель — общий компонент (ОВ-91). Список получает остаток высоты, поэтому
    // круглая кнопка добавления остаётся над панелью, а сама панель стоит на корне вкладки.
    // Переходы наружу задаёт `app`.
    Column(modifier = modifier) {
        MedicationListScreen(
            state = listState,
            // Аватар в чужом профиле ведёт туда же, куда вкладка настроек:
            // список профилей живёт в настройках (ПРФ-19, §5.8).
            onOpenProfiles = onOpenSettings,
            onIntent = { intent ->
                when (intent) {
                    MedsIntent.AddMedication -> onOpenNewMedication()
                    is MedsIntent.OpenMedication -> onOpenMedicationCard(intent.medicationId)
                    is MedsIntent.SelectTab -> {
                        if (intent.tab == MedsTab.ARCHIVE) onOpenArchive() else listViewModel.onIntent(intent)
                    }

                    is MedsIntent.TakeNow -> listViewModel.onIntent(intent)
                    is MedsIntent.UndoDeletion -> listViewModel.onIntent(intent)
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
