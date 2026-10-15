package ru.stohastika.sklyanka.feature.widgets.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.pro.ProBenefit
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetType
import ru.stohastika.sklyanka.feature.widgets.open.ProShowcaseTarget
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import javax.inject.Inject

/**
 * Экран настройки виджета — точка гейта «Склянки Про» (Р-32, ПР-23, §5.12).
 *
 * Платформа показывает этот экран при добавлении виджета и **не добавляет** виджет, если
 * экран вернул отмену. Отсюда правило: без действующей подписки завершение недоступно
 * (кнопка ведёт в витрину), а системный «Назад» — и есть отмена установки. После истечения
 * подписки установленный виджет продолжает работать: закрыта установка, а не показ
 * и не отметка (Р-32).
 *
 * Отмена ставится результатом **до** первой композиции: человек может уйти системным
 * «Назад» в любой момент, и виджет не должен появиться ни от одного такого ухода.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var showcase: ProShowcaseTarget

    private val viewModel: WidgetConfigViewModel by viewModels()

    private val widgetId: Int
        get() = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: Activity.RESULT_CANCELED

    /** Язык интерфейса закрепляется до первого обращения к ресурсам (НФТ-43а, ADR-0008). */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withInterfaceLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED, widgetResultIntent())
        // Тип плиты называет провайдер, который человек выбрал в системном списке (§5.12):
        // без этого правила экран открывался бы всегда на «Сегодня».
        widgetType()?.let(viewModel::selectType)
        setContent {
            // Экран ставится в оформление активного профиля (§5.12): человек видит, из чьего
            // профиля выбирает лекарство, и признаки чужого профиля рисуют компоненты.
            val state by viewModel.state.collectAsState()
            ProfileChrome(state = state.profile) {
                SklyankaTheme {
                    // Витрина рисуется поверх экрана и не уводит с него (Р-83, §5.12):
                    // по её закрытии доступ перечитывается, и установка продолжается тем же
                    // экраном, если подписка появилась.
                    var showcaseOpen by remember { mutableStateOf(false) }
                    WidgetConfigScreen(
                        state = state,
                        onSelectType = viewModel::selectType,
                        onSelectMedication = viewModel::selectMedication,
                        onComplete = ::complete,
                        onOpenPro = { showcaseOpen = true },
                    )
                    showcase.Showcase(visible = showcaseOpen, benefit = ProBenefit.WIDGETS) {
                        showcaseOpen = false
                        viewModel.refreshAccess()
                    }
                }
            }
        }
        // Возврат из витрины перечитывает доступ: подписка могла появиться, и тот же экран
        // завершает установку без второго входа (Р-67, §5.12).
        viewModel.refreshAccess()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccess()
    }

    /** Тип плиты по объявленному провайдеру: хост привязывает виджет до открытия настройки. */
    private fun widgetType(): WidgetType? = AppWidgetManager.getInstance(this)
        .getAppWidgetInfo(widgetId)
        ?.provider
        ?.className
        ?.let(WidgetType::ofProvider)

    private fun complete() {
        lifecycleScope.launch {
            if (viewModel.complete(widgetId)) {
                setResult(RESULT_OK, widgetResultIntent())
                finish()
            }
        }
    }

    /**
     * Намерение-результат с идентификатором плиты: без него платформа не знает, какую плиту
     * добавлять (штатный контракт конфигурационной активности).
     */
    private fun widgetResultIntent(): Intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
