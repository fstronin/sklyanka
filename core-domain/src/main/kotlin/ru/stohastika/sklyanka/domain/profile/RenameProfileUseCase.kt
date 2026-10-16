package ru.stohastika.sklyanka.domain.profile

import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.Invariants
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.repository.ProfileRepository

/** Чем закончилась правка имени профиля. */
sealed interface RenameProfileOutcome {

    /** @property profile профиль после правки: экрану нужно имя для подтверждения. */
    data class Applied(val profile: Profile) : RenameProfileOutcome

    /** Имя пустое после обрезки пробелов: профиль без имени неотличим от другого в списке. */
    data object BlankName : RenameProfileOutcome

    data object UnknownProfile : RenameProfileOutcome
}

/**
 * Правка имени профиля (ПРФ-27, Р-84 п. 5).
 *
 * Имя правится у **любого** профиля, включая владельца, и проверки `isSelf` здесь нет
 * намеренно: имя владельца — единственное, что у него вообще правится (у чужого к нему
 * добавляются цвет и дублирование, ПРФ-26), и оно уходит в отчёт врачу и файл переноса.
 * Отсутствие проверки — это и есть правило, а не её забывчивость.
 *
 * В журнал попадают идентификатор и факт правки, а имени там нет (НФТ-19): имя профиля —
 * такие же персональные данные, как название лекарства, и прежнее значение в журнале
 * пережило бы правку.
 */
class RenameProfileUseCase(private val profiles: ProfileRepository, private val log: OperationLog) {

    suspend operator fun invoke(profileId: String, name: String): RenameProfileOutcome {
        val profile = profiles.get(profileId)
        val trimmed = name.trim()
        return when {
            profile == null -> RenameProfileOutcome.UnknownProfile
            trimmed.isEmpty() -> RenameProfileOutcome.BlankName
            else -> write(profile, trimmed)
        }
    }

    private suspend fun write(profile: Profile, name: String): RenameProfileOutcome {
        val renamed = profile.copy(name = name)
        Invariants.requireValid(Invariants.violations(renamed))
        profiles.save(renamed)
        log.record(event = OpEvent.PROFILE_RENAMED, profileId = profile.id)
        return RenameProfileOutcome.Applied(renamed)
    }
}
