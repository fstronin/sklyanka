package ru.heuri.sklyanka.domain.intake

import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import java.time.Instant
import java.time.LocalDateTime

/**
 * Правила жизненного цикла приёма: что происходит при отметке и по истечении окна.
 *
 * Чистые функции без хранилища и без часов — намеренно. Правило первенства по времени
 * действия (ПР-3) и граница окна (ПР-1) — единственное место, где приложение решает
 * судьбу записи, и проверяться оно обязано на JVM, а не на устройстве с эмулятором.
 *
 * Назначенное время не переписывается ни одним переходом (НАП-31): история «вовремя или
 * с опозданием» считается по расхождению отметки и плана, и правка плана стёрла бы её.
 */
object IntakeTransition {

    /**
     * Что сделать с приёмом, которому пришла отметка.
     *
     * Правило одно и то же для телефона, часов и карточки напоминания:
     * - приём ожидается — отметка записывается;
     * - приём отмечен, а пришедшая отметка не раньше записанной — отметка отбрасывается
     *   молча (ПР-2, ИНВ-19). Это же правило делает переходы необратимыми: повторное
     *   нажатие не исправляет ошибку. **Ограничение временное** — до решения ОВ-6, а не
     *   свойство предметной области;
     * - приём отмечен, а пришедшая отметка сделана раньше записанной — поля отметки
     *   замещаются (ПР-3, ЧАС-22). Заместить может только уже случившееся нажатие,
     *   опоздавшее в доставке, а не новое решение человека.
     */
    fun decide(current: Intake, command: MarkCommand): MarkDecision {
        require(current.id == command.intakeId) {
            "Mark command addresses another intake: ${command.intakeId} != ${current.id}"
        }
        val recorded = current.actedAt
        return when {
            current.isPending -> write(current, command, superseded = false)
            // ИНВ-10 нарушен в самих данных: отмеченный приём без момента отметки.
            // Сравнивать нечем, поэтому запись не трогаем — молчаливая порча истории
            // хуже потерянной отметки.
            recorded == null -> MarkDecision.Keep(current, IgnoreReason.ALREADY_MARKED)
            command.actedAt < recorded -> write(current, command, superseded = true)
            else -> MarkDecision.Keep(current, ignoreReason(current, command))
        }
    }

    /**
     * Истекло ли окно приёма (ПР-1).
     *
     * Ровно на границе приём ещё ожидается: в 23:59 своих суток он жив, в 00:00 следующих
     * — уже нет. Сравнение идёт с границей окна **конкретного приёма**, а не с «прошло
     * N часов от проверки», поэтому перевод системных часов вперёд не делает пропущенным
     * приём, окно которого не истекло (НФТ-35).
     */
    fun isExpired(intake: Intake, window: IntakeWindow, now: LocalDateTime): Boolean =
        intake.isPending && !now.isBefore(window.deadlineFor(intake.scheduledAt))

    /**
     * Перевод в «пропущено без отметки»: подпись приложения, а не человека.
     *
     * Остаток не трогается (ПР-5, согласовано с дизайном SKL-12): автоматический пропуск
     * означает, что таблетку, скорее всего, не приняли, а списание сделало бы остаток
     * неотличимым от честно принятой дозы. Того, кто отметил, здесь нет по определению
     * (ИНВ-11 наоборот: отметка есть, а человека за ней нет).
     */
    fun expire(intake: Intake, at: Instant): Intake = intake.copy(
        status = IntakeStatus.MISSED,
        actedAt = at,
        source = IntakeSource.AUTO,
        actorProfileId = null,
    )

    private fun write(current: Intake, command: MarkCommand, superseded: Boolean): MarkDecision.Write =
        MarkDecision.Write(
            intake = current.copy(
                status = command.result.status,
                actedAt = command.actedAt,
                source = command.source,
                actorProfileId = command.actorProfileId,
            ),
            stock = stockEffect(current.status, command.result.status),
            superseded = superseded,
        )

    /**
     * Остаток приводится к результату победившей отметки, а не накапливает списания
     * (ЧАС-23): замещение не имеет права списать дозу второй раз.
     */
    private fun stockEffect(before: IntakeStatus, after: IntakeStatus): StockEffect = when {
        after == before -> StockEffect.NONE
        after == IntakeStatus.TAKEN -> StockEffect.DECREMENT
        before == IntakeStatus.TAKEN -> StockEffect.RESTORE
        else -> StockEffect.NONE
    }

    /**
     * Повторное нажатие того же источника и проигравшая отметка соседнего устройства
     * различаются в журнале: устойчиво высокое число вторых означает, что карточка на
     * часах не отзывается после отметки (дефект SKL-16), а не забывчивость человека.
     */
    private fun ignoreReason(current: Intake, command: MarkCommand): IgnoreReason =
        if (current.source == command.source) IgnoreReason.ALREADY_MARKED else IgnoreReason.RACE_LOSER
}
