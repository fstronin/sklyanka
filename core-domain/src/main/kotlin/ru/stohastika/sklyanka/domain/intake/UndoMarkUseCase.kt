package ru.stohastika.sklyanka.domain.intake

import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.profile.ActorResolver
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository

/**
 * Просьба отменить состоявшуюся отметку — то, что приходит с экрана «Сегодня» (SKL-30).
 *
 * Идентификаторы — приёмы одного действия человека: одиночная отметка называет один
 * приём, групповая — всю группу, отменяемую целиком.
 */
data class UndoRequest(
    val intakeIds: List<String>,
    /** Отмена приходит только с телефона; AUTO зарезервирован за проходом по просроченным. */
    val source: IntakeSource = IntakeSource.PHONE,
)

/** Чем кончилась отмена. */
sealed interface UndoOutcome {

    /** Отменено [undone] отметок; ноль — отменять было нечего (приёмы уже ожидаются). */
    data class Applied(val undone: Int) : UndoOutcome

    /** Хотя бы один приём действия не найден: не отменено ничего. */
    data object NotFound : UndoOutcome
}

/**
 * Полностью разрешённая отмена, готовая к записи: исполнитель уже вычислен.
 *
 * У отмены нет ни результата, ни момента действия, ни источника отметки (ПР-3): она
 * не участвует в правиле первенства и не выбирает исход. Поэтому это не [MarkCommand].
 */
data class UndoCommand(
    val intakeIds: List<String>,
    val source: IntakeSource,
    /** Профиль того, кто отменил; null — владелец профиля приёма (ПР-12, ИНВ-18). */
    val actorProfileId: String?,
)

/**
 * Отмена отметки — явная операция человека, а не замещение отметки (ОВ-6/ОВ-81).
 *
 * Отдельный сценарий, а не метод [MarkIntakeUseCase]: у отметки есть результат и момент
 * действия, у отмены их нет, и слияние дало бы команду с полями, не участвующими
 * в половине исходов. Единственная точка отметки остаётся единственной.
 *
 * Работает ровно так же, как отметка, по границе транзакции: здесь — только то, что
 * нужно знать заранее (профиль исполнителя), решение принимается внутри
 * [IntakeRepository.undoAtomically] по её собственному чтению.
 */
class UndoMarkUseCase(
    private val intakes: IntakeRepository,
    private val medications: MedicationRepository,
    private val profiles: ProfileRepository,
) {

    suspend operator fun invoke(request: UndoRequest): UndoOutcome {
        require(request.source != IntakeSource.AUTO) {
            "AUTO belongs to the sweep over expired intakes, not to a human undo"
        }
        return if (request.intakeIds.isEmpty()) UndoOutcome.Applied(undone = 0) else undo(request)
    }

    private suspend fun undo(request: UndoRequest): UndoOutcome {
        // Приёмы и их лекарства читаются до транзакции: от них нужен только профиль
        // владельца лекарства, и от исхода отмены он не зависит (ПР-12). Решение же,
        // что делать с записями, принимается внутри транзакции — по её собственному чтению.
        val stored = request.intakeIds.map { intakes.get(it) }
        val owned = stored.map { intake -> intake?.let { medications.get(it.medicationId) } }
        if (stored.contains(null) || owned.contains(null)) return UndoOutcome.NotFound
        val medicationsOf = owned.filterNotNull()
        // Кто отменил — тот же ActorResolver, что у отметки (ПРФ-14): отмену нажал
        // владелец телефона, и в чужом профиле журнал обязан подписать её им.
        val actorProfileId = ActorResolver.actorFor(
            intakeProfileId = medicationsOf.first().profileId,
            source = request.source,
            selfProfileId = profiles.getSelf()?.id,
        )
        return intakes.undoAtomically(
            UndoCommand(
                intakeIds = request.intakeIds,
                source = request.source,
                actorProfileId = actorProfileId,
            ),
        )
    }
}
