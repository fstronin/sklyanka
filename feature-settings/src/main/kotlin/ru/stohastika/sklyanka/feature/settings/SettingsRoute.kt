package ru.stohastika.sklyanka.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource

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
    val uriHandler = LocalUriHandler.current
    val policyUrl = stringResource(R.string.settings_policy_url)
    SettingsScreen(
        state = state,
        onIntent = { intent ->
            when (intent) {
                SettingsIntent.AddProfile -> navigation.addProfile()
                SettingsIntent.DeleteProfile -> navigation.deleteProfile()
                // Строка «Системные настройки уведомлений» ведёт в системный экран
                // уведомлений приложения (ОВ-98, Р-52):
                // платформенный `Intent` собирает `app`, экран о нём не знает.
                SettingsIntent.OpenSound -> navigation.openSound()
                SettingsIntent.OpenPermissions -> navigation.openDiagnostics()
                SettingsIntent.OpenHistory -> navigation.openHistory()
                SettingsIntent.OpenDataTransfer -> navigation.openDataTransfer()
                // Политику открывает браузер системным действием: своего просмотрщика
                // в приложении нет (ОВ-123).
                SettingsIntent.OpenPolicy -> uriHandler.openUri(policyUrl)
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
     * «Удалить профиль «Имя»» → экран подтверждения удаления (`ОВ-7`, SKL-36).
     *
     * Строка появляется только у активного чужого профиля: сюда ведёт ровно одно
     * действие, и оно необратимо.
     */
    val deleteProfile: () -> Unit = {},
    /**
     * «Системные настройки уведомлений» → системный экран уведомлений приложения (ОВ-98, Р-52).
     *
     * Звук и важность — свойство неизменяемого канала уведомлений (`ADR-0004`), и выбрать
     * их приложение не может: оно только приводит человека в системный экран каналов.
     */
    val openSound: () -> Unit = {},
    /**
     * «Разрешения и автозапуск» → диагностика напоминаний.
     *
     * Решение по `ОВ-48`: сначала человек видит, **что** не так — состояние каждого
     * разрешения, журнал, «Проверить сейчас», — и только потом идёт это чинить
     * в онбординг. Обратный порядок (сразу в онбординг) отправлял бы человека проходить
     * цепочку заново, не сказав ему, сломано ли вообще что-нибудь.
     */
    val openDiagnostics: () -> Unit = {},
    val openHistory: () -> Unit = {},
    val openDataTransfer: () -> Unit = {},
    val openToday: () -> Unit = {},
    val openMedications: () -> Unit = {},
)
