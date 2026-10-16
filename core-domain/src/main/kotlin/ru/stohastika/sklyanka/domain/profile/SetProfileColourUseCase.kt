package ru.stohastika.sklyanka.domain.profile

import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.Invariants
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.repository.ProfileRepository

/** Чем закончилась правка цвета профиля. */
sealed interface SetProfileColourOutcome {

    /** @property profile профиль после правки. */
    data class Applied(val profile: Profile) : SetProfileColourOutcome

    /**
     * Профиль владельца: цвет у него не виден и потому не правится (ПРФ-16, ПРФ-27).
     *
     * Отдельный исход, а не тихое «ничего не произошло»: цвет владельца — основной акцент
     * приложения, свободный выбор акцента — платная возможность «Про» (Р-31), и попытка
     * записать цвет означает дефект того, кто позвал сценарий.
     */
    data object OwnProfile : SetProfileColourOutcome

    data object UnknownProfile : SetProfileColourOutcome

    /**
     * Цвет уже такой: повторное сохранение формы без смены цвета — не событие.
     *
     * Отдельный исход, а не тихая запись: форма правки зовёт сценарий при каждом сохранении
     * чужого профиля, и без этой ветви переименование без смены цвета оставляло бы в журнале
     * пару одинаковых значений (НФТ-19) и трогало хранилище зря.
     */
    data object Unchanged : SetProfileColourOutcome

    /** Цвет не из набора дизайн-системы (ПРФ-15): произвольный цвет снимает защиту от чужого приёма. */
    data object ColourOutsidePalette : SetProfileColourOutcome
}

/**
 * Правка цвета чужого профиля (ПРФ-15, ПРФ-27, Р-84 п. 5).
 *
 * Цвет правится **только у чужого**: у владельца он нигде не показывается как индикация,
 * и цветом владельца остаётся основной акцент приложения (ПРФ-16). Набор цветов приходит
 * параметром [palette], а не читается из дизайн-системы: домен не видит `core-ui`
 * (направление зависимостей внутрь), а проверку «цвет из закрытого набора» выполнять
 * обязан — иначе её выполняет каждый экран по-своему.
 *
 * Порядок проверок повторяет [SetMirrorRemindersUseCase]: сначала незнакомый профиль,
 * затем профиль владельца, и только потом значение. Цвет и дублирование — две правки
 * одного экрана, и расхождение в порядке отказов было бы видно человеку как разное
 * поведение соседних строк.
 *
 * Цвет в журнале — не персональные данные, поэтому before/after пишутся значениями ARGB:
 * без них вопрос «почему у мамы стал другой цвет» неразбираем (НФТ-19).
 */
class SetProfileColourUseCase(
    private val profiles: ProfileRepository,
    private val palette: Set<Int>,
    private val log: OperationLog,
) {

    suspend operator fun invoke(profileId: String, colour: Int): SetProfileColourOutcome {
        val profile = profiles.get(profileId)
        return when {
            profile == null -> SetProfileColourOutcome.UnknownProfile
            profile.isSelf -> SetProfileColourOutcome.OwnProfile
            colour !in palette -> SetProfileColourOutcome.ColourOutsidePalette
            // Повторное сохранение формы без смены цвета — не событие: ни записи,
            // ни строки в журнале (ПРФ-27).
            profile.color == colour -> SetProfileColourOutcome.Unchanged
            else -> write(profile, colour)
        }
    }

    private suspend fun write(profile: Profile, colour: Int): SetProfileColourOutcome {
        val recoloured = profile.copy(color = colour)
        Invariants.requireValid(Invariants.violations(recoloured))
        profiles.save(recoloured)
        log.record(
            event = OpEvent.PROFILE_COLOUR_CHANGED,
            profileId = profile.id,
            before = argb(profile.color),
            after = argb(colour),
        )
        return SetProfileColourOutcome.Applied(recoloured)
    }

    /** Значение цвета для журнала: ARGB в шестнадцатеричном виде, без знака и без локали. */
    private fun argb(colour: Int): String = "#" + Integer.toHexString(colour).uppercase().padStart(ARGB_HEX_LENGTH, '0')

    private companion object {
        /** Восемь знаков ARGB (`AARRGGBB`) — длина значения цвета в журнале. */
        const val ARGB_HEX_LENGTH = 8
    }
}
