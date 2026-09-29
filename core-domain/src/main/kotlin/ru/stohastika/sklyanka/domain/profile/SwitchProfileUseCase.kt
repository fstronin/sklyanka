package ru.stohastika.sklyanka.domain.profile

import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository

/** Чем закончилась попытка сменить активный профиль. */
sealed interface SwitchOutcome {

    data class Switched(val from: String, val to: ProfileContext) : SwitchOutcome

    /** Повторный выбор того же профиля событием не является и в журнал не пишется. */
    data object AlreadyActive : SwitchOutcome

    data object UnknownProfile : SwitchOutcome
}

/**
 * Смена активного профиля (ПРФ-17…ПРФ-21).
 *
 * **Будильники здесь не трогаются — и трогаться не могут.** Состав напоминаний
 * определяется свойствами профилей, а не тем, какой экран открыт (НАП-12, ПРФ-6),
 * поэтому у этого сценария нет ни зависимости на перепланирование, ни способа её
 * получить: утверждение проверяется конструкцией, а не тестом на внимательность.
 * Перепланирование вызывают только сценарии, меняющие **состав** профилей
 * ([ProfileSetReplanner]).
 *
 * Возврата в прежний профиль здесь тоже нет ни в каком виде: переключение, вызванное
 * открытием напоминания, остаётся в силе после отметки (ПРФ-21). Тихий возврат создал
 * бы состояние, о котором человек не знает, — то же зло, что и отсутствие индикации,
 * только с другой стороны.
 */
class SwitchProfileUseCase(private val profiles: ProfileRepository, private val active: ActiveProfileRepository) {

    suspend operator fun invoke(profileId: String, reason: SwitchReason): SwitchOutcome {
        val profile = profiles.get(profileId)
        // Текущий профиль читается только когда есть куда переключаться: без единого
        // профиля в базе чтение активного — аварийная ситуация, а не ответ «нет».
        val before = profile?.let { active.current() }
        return when {
            profile == null -> SwitchOutcome.UnknownProfile
            before == profileId -> SwitchOutcome.AlreadyActive
            else -> {
                active.switchTo(profileId, reason)
                SwitchOutcome.Switched(from = requireNotNull(before), to = ProfileContext.of(profile))
            }
        }
    }
}
