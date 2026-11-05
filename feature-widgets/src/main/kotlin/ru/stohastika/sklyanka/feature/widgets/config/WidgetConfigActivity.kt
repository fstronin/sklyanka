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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.ObserveAccentThemeUseCase
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetType
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import javax.inject.Inject

/**
 * Экран настройки виджета (§5.12).
 *
 * Платформа показывает этот экран при добавлении виджета и **не добавляет** виджет, если
 * экран вернул отмену. Отсюда правило: завершение доступно только тогда, когда выбор
 * закончен — у типа «по лекарству» лекарство обязано быть выбрано, а системный «Назад»
 * и есть отмена установки.
 *
 * Отмена ставится результатом **до** первой композиции: человек может уйти системным
 * «Назад» в любой момент, и виджет не должен появиться ни от одного такого ухода.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    /**
     * Акцентная тема (§2.5): экран обязан нести выбор человека, а не базовую «оливу» —
     * тем же швом, каким его читает корневая активность (SKL-120).
     */
    @Inject
    lateinit var observeAccentTheme: ObserveAccentThemeUseCase

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
                WidgetConfigScreen(
                    state = state,
                    onSelectType = viewModel::selectType,
                    onSelectMedication = viewModel::selectMedication,
                    onComplete = ::complete,
                )
            }
        }
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
