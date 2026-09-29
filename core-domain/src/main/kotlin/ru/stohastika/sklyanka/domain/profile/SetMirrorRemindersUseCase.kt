package ru.stohastika.sklyanka.domain.profile

import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.repository.ProfileRepository

/** Чем закончилось переключение дублирования напоминаний профиля. */
sealed interface MirrorRemindersOutcome {

    data class Applied(val before: Boolean, val after: Boolean) : MirrorRemindersOutcome

    /**
     * Профиль владельца: флаг для него смысла не имеет (ПРФ-26).
     *
     * Отдельный исход, а не тихое «ничего не произошло»: строка тумблера у своего профиля
     * не показывается вовсе, и попытка записать значение означает дефект экрана, который
     * обязан быть виден.
     */
    data object OwnProfile : MirrorRemindersOutcome

    data object UnknownProfile : MirrorRemindersOutcome

    /** Значение уже такое: повторное нажатие тумблера в ту же сторону — не событие. */
    data object Unchanged : MirrorRemindersOutcome
}

/**
 * Дублирование напоминаний чужого профиля (ПРФ-5, ПРФ-6, ПРФ-7, НАП-12).
 *
 * Флаг отвечает на один вопрос: должен ли телефон звонить, когда наступает время приёма
 * чужого профиля. Всё остальное у профиля работает независимо от него — приёмы
 * генерируются, переходят в пропущенные и попадают в историю. Не дублируется только
 * **напоминание**, а не учёт.
 *
 * Переключение — событие перепланирования (НАП-11): включение обязано добавить будильники
 * профиля в окно немедленно, выключение — снять их. «Немедленно» здесь не украшение:
 * человек, выключивший дублирование, ждёт тишины сейчас, а не после следующего
 * якорного будильника.
 */
class SetMirrorRemindersUseCase(
    private val profiles: ProfileRepository,
    private val replan: ProfileSetReplanner,
    private val log: OperationLog,
) {

    suspend operator fun invoke(profileId: String, enabled: Boolean): MirrorRemindersOutcome {
        val profile = profiles.get(profileId)
        return when {
            profile == null -> MirrorRemindersOutcome.UnknownProfile
            profile.isSelf -> MirrorRemindersOutcome.OwnProfile
            // Повторное нажатие тумблера в ту же сторону — не событие: ни записи,
            // ни перепланирования, ни строки в журнале.
            profile.mirrorReminders == enabled -> MirrorRemindersOutcome.Unchanged
            else -> write(profile, enabled)
        }
    }

    private suspend fun write(profile: Profile, enabled: Boolean): MirrorRemindersOutcome {
        profiles.save(profile.copy(mirrorReminders = enabled))
        log.record(
            event = OpEvent.PROFILE_MIRROR_CHANGED,
            profileId = profile.id,
            before = profile.mirrorReminders.toString(),
            after = enabled.toString(),
        )
        replan.onProfileSetChanged()
        return MirrorRemindersOutcome.Applied(before = profile.mirrorReminders, after = enabled)
    }
}
