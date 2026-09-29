package ru.stohastika.sklyanka.domain.today

import ru.stohastika.sklyanka.domain.Transactor
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.MarkRequest
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Instant

/** Чем кончилось нажатие «Отметить все три». */
sealed interface GroupMarkOutcome {

    /**
     * Группа отмечена целиком.
     *
     * [alreadyMarked] — приёмы, которые к моменту нажатия уже были отмечены: они молча
     * пропущены, и это прямое следствие идемпотентности (ПР-2), а не отдельная ветка.
     */
    data class Applied(val marked: Int, val alreadyMarked: Int) : GroupMarkOutcome

    /**
     * Не отмечено ничего: отказ на любом приёме откатывает всю группу.
     *
     * Частично отмеченная группа хуже неотмеченной: человек, нажавший «Отметить все
     * три», после половинного успеха уверен, что принял всё, и повторно не проверит.
     * Отката отметки в продукте нет (ОВ-6), поэтому частичный результат неисправим.
     */
    data class Rejected(val reason: String) : GroupMarkOutcome
}

/**
 * Групповая отметка одновременных приёмов (М-08-1, решение ОВ-32).
 *
 * Вся группа отмечается **одной транзакцией**. Внутри вызывается тот же
 * [MarkIntakeUseCase], что и при одиночной отметке: идемпотентность (ПР-2) и разрешение
 * гонки «часы против телефона» (ПР-3) не дублируются, а переиспользуются, и второго
 * пути записи отметки в приложении не появляется (ЧАС-24).
 *
 * Момент отметки берётся один на всю группу: три приёма, отмеченных одним нажатием,
 * обязаны иметь одно время действия, иначе разрешение гонки начнёт различать их между
 * собой по случайной задержке записи.
 */
class MarkIntakeGroupUseCase(
    private val markIntake: MarkIntakeUseCase,
    private val transactor: Transactor,
    private val time: TimeSource,
    private val log: OperationLog,
) {

    suspend operator fun invoke(
        intakeIds: List<String>,
        source: IntakeSource,
        result: MarkResult = MarkResult.TAKEN,
        actedAt: Instant? = null,
    ): GroupMarkOutcome {
        require(source != IntakeSource.AUTO) {
            "AUTO belongs to the sweep over expired intakes, not to a human mark"
        }
        if (intakeIds.isEmpty()) return GroupMarkOutcome.Applied(marked = 0, alreadyMarked = 0)
        val moment = actedAt ?: time.now()
        val outcome = runGroup(intakeIds, source, result, moment)
        record(intakeIds, source, outcome)
        return outcome
    }

    /**
     * Отказ ловится целиком — и отказ правила, и отказ хранилища.
     *
     * Широкий `catch` здесь не небрежность, а требование: групповая отметка обязана
     * закончиться либо «записано всё», либо «не записано ничего», и исключение,
     * пролетевшее мимо, оставило бы экран в состоянии «неизвестно». Транзакция к этому
     * моменту уже откатилась, поэтому данные не пострадали.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runGroup(
        intakeIds: List<String>,
        source: IntakeSource,
        result: MarkResult,
        moment: Instant,
    ): GroupMarkOutcome = try {
        transactor.inTransaction { mark(intakeIds, source, result, moment) }
    } catch (failure: Exception) {
        GroupMarkOutcome.Rejected(failure::class.simpleName ?: WRITE_FAILED)
    }

    private suspend fun mark(
        intakeIds: List<String>,
        source: IntakeSource,
        result: MarkResult,
        moment: Instant,
    ): GroupMarkOutcome.Applied {
        var marked = 0
        var alreadyMarked = 0
        intakeIds.forEach { intakeId ->
            val outcome = markIntake(
                MarkRequest(intakeId = intakeId, result = result, source = source, actedAt = moment),
            )
            when (outcome) {
                is MarkOutcome.Applied, is MarkOutcome.Superseded -> marked++
                is MarkOutcome.Ignored -> alreadyMarked++
                // Приёма нет: он снят перепланированием или переносом данных между
                // отрисовкой карточки и нажатием. Записывать оставшиеся — значит
                // отметить не то, что показано, поэтому вся группа откатывается.
                MarkOutcome.NotFound -> throw GroupMarkAborted(intakeId)
            }
        }
        return GroupMarkOutcome.Applied(marked = marked, alreadyMarked = alreadyMarked)
    }

    /**
     * В журнал уходят идентификаторы, число отмеченных и число пропущенных — без
     * названий лекарств и дозировок (НФТ-19, `TDD.md §11`).
     */
    private suspend fun record(intakeIds: List<String>, source: IntakeSource, outcome: GroupMarkOutcome) {
        val detail = when (outcome) {
            is GroupMarkOutcome.Applied -> "marked=${outcome.marked},already=${outcome.alreadyMarked}"
            is GroupMarkOutcome.Rejected -> "rejected=${outcome.reason}"
        }
        log.record(
            event = OpEvent.INTAKE_GROUP_MARKED,
            after = intakeIds.size.toString(),
            source = source.name,
            detail = detail,
        )
    }

    /** Внутренний сигнал отката: наружу он не выходит, снаружи его не поймать. */
    private class GroupMarkAborted(intakeId: String) : IllegalStateException("Intake $intakeId is gone")

    private companion object {
        const val WRITE_FAILED = "write_failed"
    }
}
