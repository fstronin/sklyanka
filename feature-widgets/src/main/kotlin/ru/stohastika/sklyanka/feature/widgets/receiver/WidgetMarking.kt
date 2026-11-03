package ru.stohastika.sklyanka.feature.widgets.receiver

import ru.stohastika.sklyanka.domain.analytics.ActionSource
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecorder
import ru.stohastika.sklyanka.domain.analytics.IntakeAction
import ru.stohastika.sklyanka.domain.analytics.IntakeActionKind
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.MarkRequest
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.IntakeSource

/**
 * Что делает нажатие кнопки отметки на плите (ПР-22).
 *
 * Отдельной функцией, а не кодом внутри `MarkIntakeAction`: нажатие виджета обязано быть
 * проверяемым без устройства. Здесь ровно два утверждения продукта — какая просьба уходит
 * в домен (`TAKEN` с плиты, источник `PHONE`) и что после отметки плиты перерисовываются, —
 * и оба проверяются JVM-тестом.
 *
 * Успех и отказ отсюда не видно: проигравшая отметка (`Ignored`) — штатный ход событий
 * (ПР-2), и она не отличает себя от применённой; показывает это только состав данных.
 */
object WidgetMarking {

    /** Просьба отметки с плиты: тот же сценарий, что у кнопки в приложении (ПР-22). */
    fun request(intakeId: String): MarkRequest = MarkRequest(
        intakeId = intakeId,
        result = MarkResult.TAKEN,
        source = IntakeSource.PHONE,
    )

    /**
     * Отметить приём тем же сценарием, что кнопка в приложении.
     *
     * @param analytics запись события нажатия (SKL-165): одно нажатие плиты — одно событие,
     *   и только по состоявшейся отметке — проигравшая (`Ignored`) нового действия
     *   не образует.
     * @param mark единственная точка отметки продукта (`MarkIntakeUseCase`). Перерисовку
     *   плит делает он же своими последствиями (`MarkSideEffects`), а не нажатие: второй
     *   вызов на то же событие — лишний запуск сессии.
     */
    suspend fun mark(
        intakeId: String,
        analytics: AnalyticsRecorder,
        mark: suspend (MarkRequest) -> MarkOutcome,
    ): MarkOutcome {
        val outcome = mark(request(intakeId))
        if (outcome.isCompleted) {
            analytics.record(IntakeAction(IntakeActionKind.TAKEN, ActionSource.PHONE))
        }
        return outcome
    }
}
