package ru.stohastika.sklyanka.feature.widgets.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.ObserveAccentThemeUseCase
import ru.stohastika.sklyanka.domain.pro.ProBenefit
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetType
import ru.stohastika.sklyanka.feature.widgets.open.ProShowcaseTarget
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.pro.LocalProUiEnabled
import ru.stohastika.sklyanka.ui.pro.ProContour
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import javax.inject.Inject

/**
 * Экран настройки виджета — точка гейта «Склянки Про» в платном контуре (Р-32, ПР-23, §5.12).
 *
 * Платформа показывает этот экран при добавлении виджета и **не добавляет** виджет, если
 * экран вернул отмену. Отсюда правило: в `pro` без действующей подписки завершение недоступно
 * (кнопка ведёт в витрину), а системный «Назад» — и есть отмена установки. После истечения
 * подписки установленный виджет продолжает работать: закрыта установка, а не показ
 * и не отметка (Р-32). В бесплатном продукте оплаты нет (Р-86): гейт проходит, метки «Про»
 * и слова о подписке не рисуются, а завершение доступно сразу после выбора.
 *
 * Отмена ставится результатом **до** первой композиции: человек может уйти системным
 * «Назад» в любой момент, и виджет не должен появиться ни от одного такого ухода.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var showcase: ProShowcaseTarget

    /**
     * Акцентная тема «Про» (Р-31, §2.5): экран обязан нести выбор человека, а не базовую
     * «оливу» — тем же швом, каким его читает корневая активность (SKL-120).
     */
    @Inject
    lateinit var observeAccentTheme: ObserveAccentThemeUseCase

    /**
     * Платный контур (Р-86): в `free` — `false`, и экран не рисует ни меток «Про», ни слов
     * о подписке. Activity отдельная от главного экрана, поэтому признак приходит из Hilt,
     * а не из `ProHost`: биндинг контура даёт сборочный вариант.
     */
    @Inject
    lateinit var proContour: ProContour

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
            // Среда и тема — те же, что у всего приложения (Р-23, §2.5): вложенной обёртки
            // с умолчаниями нет, иначе выбранный «Аметист» до экрана настройки не доходил бы.
            val accentTheme by observeAccentTheme().collectAsState(initial = AccentTheme.DEFAULT)
            ProfileChrome(state = state.profile, theme = accentTheme, dark = isSystemInDarkTheme()) {
                // Признак контура — тот же, что у главного экрана: в free метки «Про»
                // и слова о подписке не рисуются, в pro остаются (Р-86).
                CompositionLocalProvider(LocalProUiEnabled provides proContour.uiEnabled) {
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
