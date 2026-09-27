package ru.heuri.sklyanka.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * Экран настроек вместе с его состоянием.
 *
 * Все переходы подняты в [SettingsNavigation]: экран настроек — это вход почти во все
 * остальные части продукта, а `feature-settings` от других экранных модулей не зависит.
 * Решать, куда ведёт строка, обязан `app` (`TDD.md §3`).
 */
@Composable
fun SettingsRoute(viewModel: SettingsViewModel, navigation: SettingsNavigation, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    SettingsScreen(
        state = state,
        // Строка часов ведёт на экран подключения (SKL-17), которого в продукте ещё нет.
        // Пока его нет, строка показывает состояние связи и перехода не обещает: нажатие,
        // после которого ничего не происходит, человек читает как поломку.
        canOpenWatchSetup = navigation.openWatchSetup != null,
        onIntent = { intent ->
            when (intent) {
                SettingsIntent.AddProfile -> navigation.addProfile()
                SettingsIntent.OpenWatchSetup -> navigation.openWatchSetup?.invoke()
                SettingsIntent.OpenPermissions -> navigation.openDiagnostics()
                SettingsIntent.OpenHistory -> navigation.openHistory()
                SettingsIntent.OpenDataTransfer -> navigation.openDataTransfer()
                SettingsIntent.OpenToday -> navigation.openToday()
                SettingsIntent.OpenMedications -> navigation.openMedications()
                else -> viewModel.onIntent(intent)
            }
        },
        modifier = modifier,
    )
}

/**
 * Переходы экрана одним значением.
 *
 * Семь лямбд подряд в подписи маршрута путают местами на первом же вызове, а тип с именами
 * полей — нет. Значения по умолчанию ничего не делают: экран второго уровня, которого
 * в продукте ещё нет, не должен заставлять собирать заглушку на стороне вызывающего.
 */
data class SettingsNavigation(
    /** Список профилей → добавление профиля (`ОВ-3`, механика SKL-15). */
    val addProfile: () -> Unit = {},
    /**
     * «Часы Huawei» → подключение часов (SKL-17).
     *
     * `null` означает, что экрана подключения в сборке нет: строка тогда остаётся
     * состоянием связи и не притворяется переходом.
     */
    val openWatchSetup: (() -> Unit)? = null,
    /**
     * «Разрешения и автозапуск» → диагностика напоминаний.
     *
     * Решение по `ОВ-48`: сначала человек видит, **что** не так — состояние каждого
     * разрешения, журнал, пробное напоминание, — и только потом идёт это чинить
     * в онбординг. Обратный порядок (сразу в онбординг) отправлял бы человека проходить
     * цепочку заново, не сказав ему, сломано ли вообще что-нибудь.
     */
    val openDiagnostics: () -> Unit = {},
    val openHistory: () -> Unit = {},
    val openDataTransfer: () -> Unit = {},
    val openToday: () -> Unit = {},
    val openMedications: () -> Unit = {},
)
