package ru.stohastika.sklyanka.widget

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import ru.stohastika.sklyanka.domain.pro.ProBenefit
import ru.stohastika.sklyanka.domain.pro.ProPoint
import ru.stohastika.sklyanka.feature.pro.ProSheet
import ru.stohastika.sklyanka.feature.pro.ProShowcaseIntent
import ru.stohastika.sklyanka.feature.pro.ProShowcaseViewModel
import ru.stohastika.sklyanka.feature.widgets.open.ProShowcaseTarget
import ru.stohastika.sklyanka.navigation.PolicyPage
import javax.inject.Inject

/**
 * Слот витрины «Про» для экрана настройки виджета (§5.12, Р-67, Р-83).
 *
 * Рисуется **поверх** экрана настройки: человек не уезжает в приложение, конфигурация
 * не бросается, а после закрытия шторки тот же экран перечитывает доступ и завершает
 * установку. Поверхность покупки — та же [ProSheet], что и в остальных точках входа:
 * второй витрины продукт не заводит.
 *
 * Состояние сессии берётся у `ProShowcaseViewModel` активности: экран настройки —
 * `@AndroidEntryPoint`, и её фабрика моделей уже умеет собирать Hilt-модель. Отдельная
 * модель на каждый вход означала бы, что покупка, начатая на одном экране, на другом
 * выглядит незавершённой.
 *
 * В бесплатной сборке вместо этого класса связывается `FreeProShowcaseTarget` (SKL-153):
 * витрины там нет, и порт остаётся, чтобы экран виджета не знал о контуре.
 */
class ProShowcaseTargetImpl @Inject constructor() : ProShowcaseTarget {

    @Composable
    override fun Showcase(visible: Boolean, benefit: ProBenefit, onDismiss: () -> Unit) {
        val context = LocalContext.current
        val activity = context as? ComponentActivity ?: return
        val viewModel = remember(activity) {
            ViewModelProvider(activity)[ProShowcaseViewModel::class.java]
        }
        // Открытие — на просьбу экрана, а не на каждую сборку композиции: иначе шторка
        // поднималась бы заново после каждого поворота (§5.12).
        LaunchedEffect(visible) {
            if (visible) viewModel.onIntent(ProShowcaseIntent.Open(ProPoint.WIDGETS))
        }
        val state by viewModel.state.collectAsState()
        ProSheet(
            state = state,
            onIntent = { intent ->
                viewModel.onIntent(intent)
                // Закрытие шторки (системная «Назад», тап по затемнению) — не просто гашение
                // состояния: экран обязан перечитать доступ (§5.12).
                if (intent is ProShowcaseIntent.Dismiss) onDismiss()
                if (intent is ProShowcaseIntent.OpenPolicy) PolicyPage.open(context)
            },
        )
    }
}
