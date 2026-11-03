package ru.stohastika.sklyanka.feature.settings

import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.pro.ProStatus
import ru.stohastika.sklyanka.domain.pro.ProSubscription
import ru.stohastika.sklyanka.domain.profile.ProfileCreationGate
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.ui.token.ProfileColor
import java.time.LocalDate

/**
 * Состояния экрана настроек для экранных проверок и снимков.
 *
 * Мир тот же, что в `acceptance.feature`: свой профиль активен, у профиля «Мама» четыре
 * лекарства с дублированием напоминаний, одно разрешение не выдано. Часов в сборке
 * нет — шов вырезан (Р-3, SKL-106).
 *
 * Строка «Добавить профиль» несёт исход гейта на **следующее** заведение (SKL-121):
 * он считается тем же [ProfileCreationGate] и по тем же числам, что у `SettingsViewModel`, —
 * число строк и доступ. Иначе фикстура показывала бы состояние, которого экран не увидит.
 */
internal object SettingsScreenFixtures {

    const val FOREIGN_NAME = "Мама"
    const val FOREIGN_MEDICATIONS = 4
    const val VERSION = "0.1.0"

    /** Доступ бесплатной сборки: подписки нет, но доступ есть всегда (Р-86, ADR-0016). */
    val FREE_ACCESS: ProStatus = ProStatus(ProSubscription.Active(productId = null, until = LocalDate.MAX))

    /** Имена чужих профилей сверх первого: пять профилей — владелец и четыре чужих (ПРФ-32). */
    private val FOREIGN_NAMES = listOf(FOREIGN_NAME, "Папа", "Бабушка", "Сестра")

    /** Цвета чужих профилей из закрытого набора (ПРФ-15), по порядку строк. */
    private val FOREIGN_COLOURS = listOf(
        ProfileColor.Terracotta,
        ProfileColor.Olive,
        ProfileColor.Plum,
        ProfileColor.Indigo,
    )

    /**
     * @param foreignProfiles сколько чужих профилей сверх владельца: 0 — один профиль,
     *   4 — предел пяти (ПРФ-32). По умолчанию — «Мама», как в остальных фикстурах модуля.
     * @param pro доступ сборки: по умолчанию — бесплатная, где доступ есть всегда
     *   (`ProSubscription.Active(null, LocalDate.MAX)`), поэтому гейт закрывает создание
     *   только на пределе пяти (Р-86, ПРФ-32).
     * @param analytics решение об аналитике: по умолчанию «ещё не спрашивали», и строка
     *   показывает «Выключено» — отправки нет ни в этом состоянии, ни при отказе (АН-2).
     * @param analyticsPromptVisible открыт ли вопрос о согласии поверх настроек (ПМ-25 п. 4).
     */
    fun state(
        denied: Int = 1,
        manual: Int = 1,
        reminders: ReminderSettingsValues = ReminderSettingsValues(persistentEnabled = true),
        activeIsSelf: Boolean = true,
        snoozePickerVisible: Boolean = false,
        foreignProfiles: Int = 1,
        pro: ProStatus = FREE_ACCESS,
        analytics: AnalyticsConsentState = AnalyticsConsentState.NOT_ASKED,
        analyticsPromptVisible: Boolean = false,
    ): SettingsUiState {
        val profiles = listOf(ownerRow(activeIsSelf)) + foreignRows(activeIsSelf, foreignProfiles)
        return SettingsUiState(
            profiles = profiles,
            // Как у ViewModel: удаляемым считается активный чужой профиль, и только он.
            deletableProfile = profiles.firstOrNull { it.isActive && !it.isSelf },
            reminders = reminders,
            snoozePickerVisible = snoozePickerVisible,
            permissions = PermissionsSummary(denied = denied, manual = manual),
            version = VERSION,
            pro = pro,
            // Тем же правилом, что `SettingsViewModel`: по числу строк и доступу (ПРФ-33, ПРФ-34).
            profileCreation = ProfileCreationGate.decide(profiles.size, pro.included),
            analytics = analytics,
            analyticsPromptVisible = analyticsPromptVisible,
        )
    }

    private fun ownerRow(active: Boolean): ProfileRowState = ProfileRowState(
        id = "self",
        name = "Владелец",
        colorArgb = ProfileColor.Teal.argb,
        isSelf = true,
        isActive = active,
        medicationCount = 2,
        mirrorReminders = false,
    )

    private fun foreignRows(activeIsSelf: Boolean, count: Int): List<ProfileRowState> = List(count) { index ->
        val name = FOREIGN_NAMES.getOrElse(index) { "Профиль ${index + 1}" }
        ProfileRowState(
            id = if (index == 0) "mother" else "foreign-$index",
            name = name,
            colorArgb = FOREIGN_COLOURS[index % FOREIGN_COLOURS.size].argb,
            isSelf = false,
            isActive = !activeIsSelf && index == 0,
            medicationCount = if (index == 0) FOREIGN_MEDICATIONS else 1,
            mirrorReminders = true,
        )
    }
}
