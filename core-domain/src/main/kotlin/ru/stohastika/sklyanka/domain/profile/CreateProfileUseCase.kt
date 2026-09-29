package ru.stohastika.sklyanka.domain.profile

import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.Invariants
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.repository.ProfileRepository

/** Чем закончилось создание профиля. */
sealed interface CreateProfileOutcome {

    data class Created(val profile: ProfileContext) : CreateProfileOutcome

    /** Имя пустое после обрезки пробелов: профиль без имени неотличим от другого в списке. */
    data object BlankName : CreateProfileOutcome

    /** Цвет не из набора дизайн-системы (ПРФ-15): произвольный цвет ломает защиту. */
    data object ColourOutsidePalette : CreateProfileOutcome
}

/**
 * Заведение профиля, за которым присматривают (ПРФ-9).
 *
 * **Экрана добавления профиля нет** — он не описан ни в макетах, ни в спецификации,
 * и помечен открытым вопросом `ОВ-3`. Сценарий всё равно нужен: без второго профиля
 * половина приёмки непроверяема, а проверять её на данных, заведённых в обход домена,
 * значит проверять не то приложение, которое работает у человека.
 *
 * `mirrorReminders` — **обязательный параметр без значения по умолчанию**, и это прямой
 * ответ на открытую часть `ОВ-3`. Значение по умолчанию, поставленное здесь, молча
 * закрыло бы вопрос, который заказчик ещё не решил: от него зависит, зазвонит ли телефон
 * владельца в 09:00. Предложение к согласованию — спрашивать при создании, по умолчанию
 * включено (присмотр без напоминаний себе смысла почти не имеет), но выбор делает тот,
 * кто вызывает сценарий, и делает явно.
 *
 * Допустимый набор цветов приходит параметром [palette], а не читается из дизайн-системы:
 * домен не видит `core-ui` (направление зависимостей внутрь), а проверку «цвет из
 * ограниченного набора» (ПРФ-15) выполнять всё равно обязан — иначе её выполняет экран,
 * то есть каждый экран по-своему.
 */
class CreateProfileUseCase(
    private val profiles: ProfileRepository,
    private val palette: Set<Int>,
    private val ids: ProfileIdSource,
    private val replan: ProfileSetReplanner,
    private val log: OperationLog,
) {

    suspend operator fun invoke(name: String, color: Int, mirrorReminders: Boolean): CreateProfileOutcome {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> CreateProfileOutcome.BlankName
            color !in palette -> CreateProfileOutcome.ColourOutsidePalette
            else -> write(trimmed, color, mirrorReminders)
        }
    }

    private suspend fun write(name: String, color: Int, mirrorReminders: Boolean): CreateProfileOutcome {
        val profile = Profile(
            id = ids.newId(),
            name = name,
            color = color,
            // Второго профиля владельца не бывает (ИНВ-1), и завести его этим путём нельзя.
            isSelf = false,
            mirrorReminders = mirrorReminders,
        )
        Invariants.requireValid(Invariants.violations(profile))
        profiles.save(profile)
        log.record(
            event = OpEvent.PROFILE_CREATED,
            profileId = profile.id,
            after = mirrorReminders.toString(),
        )
        // Состав профилей изменился: приёмы нового профиля появятся в окне, как только
        // у него заведут лекарства, а флаг дублирования решает, зазвонит ли телефон (ПРФ-6).
        replan.onProfileSetChanged()
        return CreateProfileOutcome.Created(ProfileContext.of(profile))
    }
}
