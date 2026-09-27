package ru.heuri.sklyanka.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.heuri.sklyanka.feature.history.HistoryViewModel
import ru.heuri.sklyanka.feature.settings.transfer.DataTransferRoute
import ru.heuri.sklyanka.feature.settings.transfer.DataTransferViewModel

/**
 * Вкладка первого уровня: три вкладки нижней панели (`docs/spec/03-screens.md`, «Навигация»).
 *
 * Раздел «Лекарства» вкладкой не является — он открывается поверх и возвращает туда, откуда
 * открыт, поэтому в перечислении его нет. Место, с которого он открыт, хранится значением
 * этого типа: константа вместо значения означала бы, что человек, ушедший в лекарства
 * из настроек, возвращается на «Сегодня».
 */
enum class RootTab {
    TODAY,
    SETTINGS,
}

/**
 * Экраны второго уровня, открываемые из настроек.
 *
 * Подключение часов (SKL-17) здесь отсутствует намеренно: экрана в продукте нет, и строка
 * настроек перехода не обещает.
 */
enum class SettingsDestination {

    /** Настройки → Данные → История приёмов (SKL-18). */
    HISTORY,

    /** Настройки → Данные → Перенос данных (SKL-19). */
    TRANSFER,
}

/**
 * Экраны второго уровня настроек внутри навигации приложения.
 *
 * Маршруты собраны здесь, а не в экранных модулях: `feature-*` друг от друга не зависят,
 * и знание о том, куда ведёт возврат, принадлежит `app`. Возврат с любого из них ведёт
 * в настройки — туда, откуда человек пришёл (критерий приёмки 15).
 *
 * Системная кнопка «Назад» и стрелка на экране ведут в одно место. Две разные ветки здесь
 * означали бы, что вышедший кнопкой телефона попадает не туда, куда вышел бы стрелкой,
 * и поймать это ревью не может: каждая ветка по отдельности выглядит правильной.
 */
@Composable
fun SettingsSection(
    destination: SettingsDestination,
    history: HistoryViewModel,
    transfer: DataTransferViewModel,
    onLeave: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        // История сама ставит перехват кнопки «Назад» и знает, откуда открыта.
        SettingsDestination.HISTORY -> HistorySection(
            viewModel = history,
            origin = HistoryOrigin.SETTINGS,
            onLeave = { onLeave() },
            modifier = modifier,
        )

        SettingsDestination.TRANSFER -> {
            BackHandler(enabled = true) { onLeave() }
            DataTransferRoute(
                viewModel = transfer,
                // «После загрузки откроется тот же экран настройки» (ПЕР-31): разрешения
                // принадлежат системе, а не файлу, и на новом устройстве их выдают заново.
                onOpenPermissions = onOpenPermissions,
                onBack = onLeave,
                modifier = modifier,
            )
        }
    }
}
