package ru.heuri.sklyanka.feature.settings

import ru.heuri.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.heuri.sklyanka.domain.watch.WatchLinkState
import ru.heuri.sklyanka.ui.token.ProfileColor

/**
 * Состояния экрана настроек для экранных проверок и снимков.
 *
 * Мир тот же, что в `acceptance.feature`: свой профиль активен, у профиля «Мама» четыре
 * лекарства с дублированием напоминаний, часы GT 6 Pro подключены, одно разрешение
 * не выдано.
 */
internal object SettingsScreenFixtures {

    const val WATCH_MODEL = "GT 6 Pro"
    const val FOREIGN_NAME = "Мама"
    const val FOREIGN_MEDICATIONS = 4
    const val VERSION = "0.1.0"

    fun state(
        watch: WatchLinkState = WatchLinkState.Connected(WATCH_MODEL),
        denied: Int = 1,
        manual: Int = 1,
        reminders: ReminderSettingsValues = ReminderSettingsValues(persistentEnabled = true),
        activeIsSelf: Boolean = true,
    ) = SettingsUiState(
        profiles = listOf(
            ProfileRowState(
                id = "self",
                name = "Владелец",
                colorArgb = ProfileColor.Teal.argb,
                isSelf = true,
                isActive = activeIsSelf,
                medicationCount = 2,
                mirrorReminders = false,
            ),
            ProfileRowState(
                id = "mother",
                name = FOREIGN_NAME,
                colorArgb = ProfileColor.Terracotta.argb,
                isSelf = false,
                isActive = !activeIsSelf,
                medicationCount = FOREIGN_MEDICATIONS,
                mirrorReminders = true,
            ),
        ),
        reminders = reminders,
        watch = watch,
        permissions = PermissionsSummary(denied = denied, manual = manual),
        version = VERSION,
    )
}
