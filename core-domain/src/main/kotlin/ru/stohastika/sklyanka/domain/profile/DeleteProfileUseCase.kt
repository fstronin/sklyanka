package ru.stohastika.sklyanka.domain.profile

import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository

/** Чем закончилось удаление профиля. */
sealed interface DeleteProfileOutcome {

    /** @property profile удалённый профиль: экрану нужно его имя для итоговой строки (ПРФ-28, ПРФ-31). */
    data class Deleted(val profile: Profile) : DeleteProfileOutcome

    /**
     * Профиль владельца: правило ПРФ-28.
     *
     * Отдельный исход, а не тихое «ничего не произошло»: экран удаления своего профиля
     * не предлагает, и попытка означает дефект того, кто позвал сценарий.
     */
    data object OwnProfile : DeleteProfileOutcome

    data object UnknownProfile : DeleteProfileOutcome
}

/**
 * Удаление чужого профиля вместе с его данными (ОВ-7, ПРФ-28, ПРФ-30, ПРФ-31).
 *
 * Порядок шагов — часть контракта:
 *
 * 1. **стирание** одной транзакцией — приёмы, лекарства, профиль ([ProfileEraser]);
 * 2. **след в журнале** — после успешной транзакции, как у переноса
 *    (`ApplyTransferUseCase`): запись не может говорить об откатившейся операции;
 * 3. **переключение на владельца** — стирание идёт до переключения: обратный порядок
 *    при отказе стирания оставил бы человека в своём профиле с целым чужим,
 *    а причину переключения — ложной. Окно между шагами 1 и 3 закрыт структурой:
 *    сохранённая ссылка на исчезнувший профиль приводится к владельцу самим
 *    чтением активного профиля (ПРФ-31);
 * 4. **перепланирование** — единственный законный повод «состав профилей изменился»
 *    (ПРФ-30, НАП-12): будильники удалённого профиля снимаются немедленно.
 *
 * Медицинских данных в журнале нет: только идентификатор профиля и факт (НФТ-19).
 * Второго источника активного профиля сценарий не заводит: владелец читается у того же
 * [ProfileRepository], что и у всего приложения.
 */
class DeleteProfileUseCase(
    private val profiles: ProfileRepository,
    private val eraser: ProfileEraser,
    private val active: ActiveProfileRepository,
    private val replan: ProfileSetReplanner,
    private val log: OperationLog,
) {

    suspend operator fun invoke(profileId: String): DeleteProfileOutcome {
        val profile = profiles.get(profileId)
        return when {
            profile == null -> DeleteProfileOutcome.UnknownProfile
            profile.isSelf -> DeleteProfileOutcome.OwnProfile
            else -> erase(profile)
        }
    }

    private suspend fun erase(profile: Profile): DeleteProfileOutcome {
        eraser.erase(profile.id)
        log.record(event = OpEvent.PROFILE_DELETED, profileId = profile.id)
        active.switchTo(ownerId(), SwitchReason.PROFILE_DELETED)
        replan.onProfileSetChanged()
        return DeleteProfileOutcome.Deleted(profile)
    }

    /** Владелец обязан существовать (INV-1): его отсутствие — не исход удаления, а отказ базы. */
    private suspend fun ownerId(): String = requireNotNull(profiles.getSelf()) {
        "INV-1: exactly one self profile must exist"
    }.id
}
