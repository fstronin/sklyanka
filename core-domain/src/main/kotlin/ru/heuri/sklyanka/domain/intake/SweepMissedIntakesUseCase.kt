package ru.heuri.sklyanka.domain.intake

import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import ru.heuri.sklyanka.domain.time.TimeSource

/**
 * Почему запущен проход по просроченным приёмам.
 *
 * Уходит в журнал служебным кодом: без причины запись «переведено четыре приёма»
 * не отвечает на вопрос, почему они появились в истории именно сейчас (`TDD.md §9`).
 */
enum class SweepReason(val code: String) {
    /** Будильник границы суток — появится вместе с SKL-13. */
    TIMER("timer"),

    /** Открытие приложения: единственное событие, которое случается наверняка. */
    APP_START("app_start"),

    /** Любое перепланирование приёмов (НАП-34). */
    REPLAN("replan"),
}

/** Сколько ожидающихся приёмов действительно переведено в «пропущено без отметки». */
@JvmInline
value class SweepResult(val expired: Int) {
    companion object {
        val NOTHING = SweepResult(0)
    }
}

/**
 * Перевод просроченных ожидающихся приёмов в «пропущено без отметки» (ПР-1).
 *
 * Опираться на таймер нельзя: устройство бывает выключено ровно в момент истечения окна
 * (`docs/spec/04-reminders.md`, сценарий И-10). Поэтому проход выполняется и по
 * расписанию, и при каждом открытии приложения, и при каждом перепланировании (НАП-34),
 * а значит обязан быть идемпотентным: повторный проход находит пустую выборку и не
 * меняет ни одной записи.
 *
 * Уведомлений проход не порождает и порождать не может — порта уведомлений у него нет
 * (НАП-34). Иначе телефон, включённый после суток простоя, выдал бы человеку очередь
 * карточек о том, что он всё пропустил.
 *
 * Проход **не ограничен активным профилем**: приёмы профиля «Мама» обязаны истекать,
 * пока на экране открыт профиль «Я». Фильтрация по профилю — свойство экранных выборок
 * (ПР-10), а не жизненного цикла записи.
 */
class SweepMissedIntakesUseCase(
    private val intakes: IntakeRepository,
    private val window: IntakeWindow,
    private val time: TimeSource,
    private val log: OperationLog,
) {

    suspend operator fun invoke(reason: SweepReason): SweepResult {
        val now = time.nowLocal()
        // Хранилище отсекает заведомо живые приёмы по индексу, домен перепроверяет
        // каждого кандидата границей его собственного окна: граница — параметр (ОВ-4),
        // и выборка не имеет права быть её вторым, независимым определением.
        val expired = intakes.expiredPending(window.candidateBound(now))
            .filter { IntakeTransition.isExpired(it, window, now) }
        if (expired.isEmpty()) return SweepResult.NOTHING

        val changed = intakes.markMissed(expired.map { it.id }, time.now())
        if (changed > 0) {
            // В журнал идут только число и причина: названий лекарств и имён профилей
            // здесь нет (НФТ-19), а по доле автоматических пропусков за неделю видно,
            // доходят ли напоминания вообще.
            log.record(
                event = OpEvent.INTAKE_AUTO_MISSED,
                after = changed.toString(),
                source = IntakeSource.AUTO.name,
                detail = reason.code,
            )
        }
        return SweepResult(changed)
    }
}
