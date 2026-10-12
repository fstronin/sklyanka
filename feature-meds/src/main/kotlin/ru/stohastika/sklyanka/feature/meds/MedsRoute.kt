package ru.stohastika.sklyanka.feature.meds

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormRoute
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormViewModel
import ru.stohastika.sklyanka.feature.meds.list.MedicationListScreen
import ru.stohastika.sklyanka.feature.meds.list.MedicationListViewModel
import ru.stohastika.sklyanka.feature.meds.list.MedsIntent
import ru.stohastika.sklyanka.feature.meds.list.MedsTab
import ru.stohastika.sklyanka.ui.component.BottomBar
import ru.stohastika.sklyanka.ui.component.BottomBarTab
import ru.stohastika.sklyanka.ui.motion.AnimatedLayer
import ru.stohastika.sklyanka.ui.motion.LayerMotion

/**
 * Раздел «Лекарства»: список и форма.
 *
 * Внутренний переход у раздела один — список ↔ форма, и он живёт здесь: форма
 * открывается круглой кнопкой добавления, закрывается «Отменой» и закрывается сама
 * после успешного сохранения. Сама форма собрана отдельным маршрутом: вход в неё не
 * один — из карточки лекарства открывается та же форма в режиме правки (SKL-10).
 *
 * Переходы наружу — карточка лекарства (SKL-10) и архив (SKL-11) — подняты в
 * параметры: `feature-meds` не зависит от других экранных модулей, и решать, куда
 * ведут эти переходы, обязан тот, кто собирает маршруты, то есть `app`.
 */
@Suppress("LongParameterList")
@Composable
fun MedsRoute(
    listViewModel: MedicationListViewModel,
    formViewModel: MedicationFormViewModel,
    onOpenMedicationCard: (String) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenToday: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState by listViewModel.state.collectAsState()
    // Открытость формы переживает пересоздание активности: поворот не имеет права
    // выбрасывать человека из формы в список (SKL-142). Уход из раздела состояние
    // отпускает: форма и место принадлежат заходу, а не приложению целиком.
    var formOpen by rememberSaveable { mutableStateOf(false) }
    // Уход с экрана закрывает окно удаления (SKL-30, ОВ-124): точка выхода одна.
    DisposableEffect(Unit) {
        onDispose { listViewModel.onScreenLeft() }
    }

    AnimatedLayer(
        layer = formOpen,
        motion = if (formOpen) LayerMotion.Forward else LayerMotion.Back,
    ) { open ->
        if (open) {
            MedicationFormRoute(
                viewModel = formViewModel,
                medicationId = null,
                onDone = { formOpen = false },
                modifier = modifier,
            )
        } else {
            // Нижняя панель — общий компонент (ОВ-91). Список получает остаток высоты, поэтому
            // круглая кнопка добавления остаётся над панелью, а сама панель стоит на корне
            // вкладки. Переходы наружу задаёт `app`.
            Column(modifier = modifier) {
                MedicationListScreen(
                    state = listState,
                    // Аватар в чужом профиле ведёт туда же, куда вкладка настроек:
                    // список профилей живёт в настройках (ПРФ-19, §5.8).
                    onOpenProfiles = onOpenSettings,
                    onIntent = { intent ->
                        when (intent) {
                            MedsIntent.AddMedication -> formOpen = true
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
    }
}
