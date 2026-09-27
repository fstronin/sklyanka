package ru.heuri.sklyanka.domain.profile

import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.repository.ProfileRepository

/**
 * Напоминание диктует профиль (ПРФ-8, ПРФ-20, ПРФ-21).
 *
 * Карточка напоминания открывается в профиле того приёма, о котором напоминает, —
 * независимо от того, какой профиль был активен. Иначе человек, нажавший «Принято»
 * на карточке маминого приёма из своего профиля, совершил бы ровно ту ошибку, от которой
 * защищает вся задача.
 *
 * Профиль берётся через лекарство (ПР-10): собственного поля профиля у приёма нет,
 * и второй источник правды о принадлежности приёма здесь не появится.
 *
 * Возврата после закрытия карточки нет и не будет (ПРФ-21): переключение видно и обратимо
 * руками, а не откатывается само.
 */
class OpenReminderProfileUseCase(
    private val medications: MedicationRepository,
    private val profiles: ProfileRepository,
    private val switch: SwitchProfileUseCase,
) {

    /**
     * Переключиться в профиль приёма, о котором пришло напоминание.
     *
     * @param medicationId лекарство приёма: подсистема напоминаний работает
     *   идентификаторами, профиля она не знает (НФТ-19)
     * @return профиль, в котором открывается карточка, либо `null`, если лекарства
     *   или его профиля больше нет
     */
    suspend operator fun invoke(medicationId: String): ProfileContext? {
        val profileId = medications.get(medicationId)?.profileId ?: return null
        return when (switch(profileId, SwitchReason.REMINDER_OPENED)) {
            is SwitchOutcome.Switched,
            // Профиль напоминания уже активен: переключать нечего, а индикация на месте.
            SwitchOutcome.AlreadyActive,
            -> profiles.get(profileId)?.let(ProfileContext::of)

            SwitchOutcome.UnknownProfile -> null
        }
    }
}
