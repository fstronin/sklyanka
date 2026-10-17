package ru.stohastika.sklyanka.pro

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.pro.ProPoint
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionRefreshDueUseCase
import ru.stohastika.sklyanka.domain.pro.RefreshProSubscriptionUseCase
import ru.stohastika.sklyanka.feature.pro.ProShowcaseIntent
import ru.stohastika.sklyanka.feature.pro.ProShowcaseRoute
import ru.stohastika.sklyanka.feature.pro.ProShowcaseViewModel
import ru.stohastika.sklyanka.navigation.LocalModalSurfaceOpen
import ru.stohastika.sklyanka.ui.pro.LocalProUiEnabled
import ru.stohastika.sklyanka.ui.pro.ProContour
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Витрина «Про» платной сборки (SKL-122, SKL-153, `ADR-0016` п. 2).
 *
 * Единственное место `app`, которое знает `feature-pro`: здесь живут `ViewModel` шторки,
 * маршрут, возврат, модальный признак и проверка подписки. `MainActivity` получает только
 * `Content` и `openPro` — поэтому free-сборка компилируется без платёжного модуля.
 *
 * **Проверка подписки.** Первый `ON_RESUME` — это запуск: проверка идёт всегда, как раньше
 * в `SklyankaApp`. Последующие возвращения спрашивают магазин только при просроченной записи
 * в кэше (`ProSubscriptionRefreshDueUseCase`), иначе платящий человек, чью подписку магазин
 * молча продлил, увидел бы витрину вместо управления (Р-68 п. 7). Отказ проверки ничего
 * не меняет (П-6).
 *
 * **Возврат.** Пока шторка идёт или открыта, возврат принадлежит ей: экранные обработчики
 * спрашивают [LocalModalSurfaceOpen], а в окне открытия возврат закрывает витрину. Признак
 * складывается с уже выставленным снаружи (шторка экспорта истории), а не затирает его.
 *
 * **Закрытие.** `onClosed` вызывается на переходе «была видна → закрыта», а не на первом
 * кадре: настройки, форма лекарства, мастер профиля и отчёт перечитывают доступ после
 * покупки, не теряя набранное (Р-84, Р-85).
 */
@Singleton
class ProHostImpl @Inject constructor(
    private val contour: ProContour,
    private val refreshProSubscription: RefreshProSubscriptionUseCase,
    private val refreshDue: ProSubscriptionRefreshDueUseCase,
) : ProHost {

    override val enabled: Boolean = true

    @Composable
    override fun Content(
        onPolicy: () -> Unit,
        onClosed: () -> Unit,
        content: @Composable (openPro: (ProPoint) -> Unit) -> Unit,
    ) {
        val activity = LocalContext.current as? ComponentActivity
        val viewModel = activity?.let { owner ->
            remember(owner) { ViewModelProvider(owner)[ProShowcaseViewModel::class.java] }
        }
        if (viewModel == null) {
            // Не активность (предпросмотр или чужой контекст): шторке негде жить, и открыватель
            // остаётся пустым — экран в этом случае рисуется без витрины, как в free.
            CompositionLocalProvider(LocalProUiEnabled provides contour.uiEnabled) {
                content { }
            }
            return
        }

        val state by viewModel.state.collectAsState()
        val visible = state.visible
        val opening = state.opening
        val scope = rememberCoroutineScope()
        var startupCheckDone by remember { mutableStateOf(false) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            // Признак ставится в самом событии, а не в корутине: два подряд ON_RESUME
            // не должны превратиться в две проверки подписки.
            if (!startupCheckDone) {
                startupCheckDone = true
                scope.launch { refreshProSubscription() }
            } else {
                scope.launch { if (refreshDue()) refreshProSubscription() }
            }
        }
        var wasVisible by remember { mutableStateOf(false) }
        LaunchedEffect(visible) {
            if (wasVisible && !visible) onClosed()
            wasVisible = visible
        }
        BackHandler(enabled = opening) {
            viewModel.onIntent(ProShowcaseIntent.Dismiss)
        }
        val modalSurfaceOpen = LocalModalSurfaceOpen.current
        CompositionLocalProvider(
            LocalProUiEnabled provides contour.uiEnabled,
            LocalModalSurfaceOpen provides (modalSurfaceOpen || visible || opening),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                content { point -> viewModel.onIntent(ProShowcaseIntent.Open(point)) }
                ProShowcaseRoute(viewModel = viewModel, onOpenPolicy = onPolicy)
            }
        }
    }
}
