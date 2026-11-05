package ru.stohastika.sklyanka.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.feature.history.HistoryViewModel
import ru.stohastika.sklyanka.feature.settings.profile.DeleteProfileRoute
import ru.stohastika.sklyanka.feature.settings.profile.DeleteProfileViewModel
import ru.stohastika.sklyanka.feature.settings.profile.ProfileFormMode
import ru.stohastika.sklyanka.feature.settings.profile.ProfileFormRoute
import ru.stohastika.sklyanka.feature.settings.profile.ProfileFormViewModel
import ru.stohastika.sklyanka.feature.settings.transfer.DataTransferRoute
import ru.stohastika.sklyanka.feature.settings.transfer.DataTransferViewModel

/**
 * Вкладка первого уровня: три вкладки нижней панели (`docs/norm/04-behavior.md`, «Навигация»).
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
 *
 * Правка профиля несёт идентификатор: править можно любого профиля — в том числе
 * неактивного (SKL-121, ПРФ-19), и маршрут обязан назвать, чей профиль открыт.
 */
sealed interface SettingsDestination {

    /** Настройки → Данные → История приёмов (SKL-18). */
    data object History : SettingsDestination

    /** Настройки → Данные → Перенос данных (SKL-19). */
    data object Transfer : SettingsDestination

    /** Настройки → Профили → удаление активного чужого профиля (ОВ-7, SKL-36). */
    data object DeleteProfile : SettingsDestination

    /** Настройки → Профили → «Добавить профиль»: мастер «Новый профиль» (ПРФ-9, SKL-121). */
    data object AddProfile : SettingsDestination

    /** Настройки → Профили → тихое «Изменить»: экран «Профиль» этого профиля (ПРФ-27, SKL-121). */
    data class EditProfile(val profileId: String) : SettingsDestination
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
    viewModels: SettingsViewModels,
    onLeave: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        // История сама ставит перехват кнопки «Назад» и знает, откуда открыта.
        SettingsDestination.History -> HistorySection(
            viewModel = viewModels.history,
            origin = HistoryOrigin.SETTINGS,
            onLeave = { onLeave() },
            modifier = modifier,
        )

        SettingsDestination.Transfer -> {
            BackHandler(enabled = !LocalModalSurfaceOpen.current) { onLeave() }
            DataTransferRoute(
                viewModel = viewModels.transfer,
                // «После загрузки откроется тот же экран настройки» (ПЕР-31): разрешения
                // принадлежат системе, а не файлу, и на новом устройстве их выдают заново.
                onOpenPermissions = onOpenPermissions,
                onBack = onLeave,
                modifier = modifier,
            )
        }

        SettingsDestination.DeleteProfile -> {
            BackHandler(enabled = !LocalModalSurfaceOpen.current) { onLeave() }
            DeleteProfileRoute(
                viewModel = viewModels.deleteProfile,
                onLeave = onLeave,
                modifier = modifier,
            )
        }

        SettingsDestination.AddProfile -> {
            BackHandler(enabled = !LocalModalSurfaceOpen.current) { onLeave() }
            ProfileFormRoute(
                viewModel = viewModels.profileForm,
                mode = ProfileFormMode.Add,
                onLeave = onLeave,
                modifier = modifier,
            )
        }

        is SettingsDestination.EditProfile -> {
            BackHandler(enabled = !LocalModalSurfaceOpen.current) { onLeave() }
            ProfileFormRoute(
                viewModel = viewModels.profileForm,
                mode = ProfileFormMode.Edit(destination.profileId),
                onLeave = onLeave,
                modifier = modifier,
            )
        }
    }
}

/**
 * Экранные состояния второго уровня настроек одним значением.
 *
 * Семь параметров подряд путаются местами на первом же вызове, а тип с именами полей —
 * нет; тот же приём, что у [MedsViewModels].
 */
data class SettingsViewModels(
    val history: HistoryViewModel,
    val transfer: DataTransferViewModel,
    val deleteProfile: DeleteProfileViewModel,
    val profileForm: ProfileFormViewModel,
)
