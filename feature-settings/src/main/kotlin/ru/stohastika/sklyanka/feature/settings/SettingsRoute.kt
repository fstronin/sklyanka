package ru.stohastika.sklyanka.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import ru.stohastika.sklyanka.domain.analytics.AnalyticsScreen
import ru.stohastika.sklyanka.domain.pro.ProPoint
import ru.stohastika.sklyanka.ui.analytics.TrackScreenView

/**
 * Экран настроек вместе с его состоянием.
 *
 * Все переходы подняты в [SettingsNavigation]: экран настроек — это вход почти во все
 * остальные части продукта, а `feature-settings` от других экранных модулей не зависит.
 * Решать, куда ведёт строка, обязан `app` (`TDD.md §3`).
 */
@Composable
fun SettingsRoute(viewModel: SettingsViewModel, navigation: SettingsNavigation, modifier: Modifier = Modifier) {
    // Показ экрана — один раз за вход: значение привязано к экрану, а не к композиции
    // (SKL-165, TDD §5.4).
    TrackScreenView(AnalyticsScreen.SETTINGS)
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val policyUrl = policyUrl(LocalContext.current)
    // Сигнал «открыть витрину» из блока «Оформление» (SKL-120): нажатие платной строки
    // диалога без доступа ведёт в покупку, а не меняет выбор (§5.17, Р-67 пп. 2–3).
    // Шторку хостит `app` — поверхность покупки одна на все точки входа (Р-67), и второй
    // её экземпляр в настройках был бы второй поверхностью.
    LaunchedEffect(viewModel) {
        viewModel.showcase.collect { point -> navigation.openPro(point) }
    }
    SettingsScreen(
        state = state,
        onIntent = { intent ->
            when (intent) {
                SettingsIntent.AddProfile -> navigation.addProfile()
                is SettingsIntent.EditProfile -> navigation.editProfile(intent.profileId)
                SettingsIntent.DeleteProfile -> navigation.deleteProfile()
                // Строка «Системные настройки уведомлений» ведёт в системный экран
                // уведомлений приложения (ОВ-98, Р-52):
                // платформенный `Intent` собирает `app`, экран о нём не знает.
                SettingsIntent.OpenSound -> navigation.openSound()
                SettingsIntent.OpenPermissions -> navigation.openDiagnostics()
                SettingsIntent.OpenHistory -> navigation.openHistory()
                SettingsIntent.OpenDataTransfer -> navigation.openDataTransfer()
                // «Экспорт истории» открывает шторку выгрузки: файл формируется в
                // `feature-history`, а показывает его `app` — поверх корня, как витрину.
                SettingsIntent.OpenHistoryExport -> navigation.openHistoryExport()
                // Политику открывает браузер системным действием: своего просмотрщика
                // в приложении нет (ОВ-123).
                SettingsIntent.OpenPolicy -> uriHandler.openUri(policyUrl)
                // Строка блока «Склянка Про» ведёт в ту же шторку, что и точки выбора
                // платных функций (Р-67 п. 6): поверхность покупки хостит `app`.
                SettingsIntent.OpenPro -> navigation.openPro(ProPoint.SETTINGS)
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
    /** Список профилей → добавление профиля (`ОВ-3`, ПРФ-9, SKL-121). */
    val addProfile: () -> Unit = {},
    /**
     * Тихая строка «Изменить» → экран правки профиля (§5.14, SKL-121).
     *
     * Идентификатор профиля приходит от строки списка: править можно любого — в том числе
     * неактивного, — и решать, чей профиль правится, обязан тот, кто собрал маршрут.
     */
    val editProfile: (String) -> Unit = {},
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
    /**
     * Строка «Экспорт истории» → шторка выгрузки истории (SKL-119, Р-35).
     *
     * Шторку хостит `app`: `feature-settings` не зависит от `feature-history`, и рисовать
     * поверхность соседнего модуля она не может (проверка направлений
     * `verifyModuleDependencies`).
     */
    val openHistoryExport: () -> Unit = {},
    /**
     * Витрина подписки: строка блока «Склянка Про» (SKL-122, Р-67 п. 6) и платный выбор
     * в диалогах «Оформление» без доступа (SKL-120, §5.17).
     *
     * Точку-повод называет экран: `ProPoint.SETTINGS` у строки подписки,
     * `ProPoint.ACCENT` у выбора темы и иконки, — и от неё зависит порядок состава
     * на витрине (Р-75 п. 3).
     *
     * Шторку открывает тот, кто собирает маршруты, то есть `app`: поверхность покупки одна
     * на все точки входа (Р-67), и второй её экземпляр в модуле настроек был бы второй
     * поверхностью.
     */
    val openPro: (ProPoint) -> Unit = {},
    val openToday: () -> Unit = {},
    val openMedications: () -> Unit = {},
)
