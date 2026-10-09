package ru.stohastika.sklyanka.domain.history

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.HistoryRepository
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.YearMonth

/**
 * Приверженность за месяц активного профиля (`TDD.md §9.1`).
 *
 * Профиль берётся из контекста активного профиля, а не из аргумента экрана: это
 * единственное место, где гарантируется ПР-10, и оно обязано быть одно. Экран не умеет
 * попросить историю чужого профиля — ему нечем это выразить.
 *
 * Пересчёт идёт целиком на каждое изменение данных и на каждое переключение месяца:
 * месяц — единица расчёта, и попытка обновить сводку частично смешала бы данные двух
 * месяцев в одной плитке (`TDD.md §9.2`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveAdherenceUseCase(
    private val activeProfile: ActiveProfileRepository,
    private val history: HistoryRepository,
    private val calculator: AdherenceCalculator,
    private val time: TimeSource,
    private val log: OperationLog,
) {

    operator fun invoke(month: YearMonth): Flow<AdherencePeriod> {
        val window = HistoryWindow.of(month)
        return activeProfile.activeProfileId.flatMapLatest { profileId ->
            history.observe(profileId, window).map { slice -> calculate(slice, window) }
        }
    }

    /**
     * Расчёт с записью в журнал.
     *
     * В журнал идут профиль, период, число обработанных записей и длительность расчёта —
     * и ни одного результата: «86 %» рядом с `profile_id` это медицинские данные, просто
     * агрегированные (НФТ-19, `TDD.md §11`). Длительность нужна НФТ-25: без записи
     * подтвердить бюджет 300 мс на заполненной базе нечем.
     */
    private suspend fun calculate(slice: HistorySlice, window: HistoryWindow): AdherencePeriod {
        val startedAt = time.now()
        val period = calculator.of(slice, window, time.today(), time.zone())
        val spent = time.now().toEpochMilli() - startedAt.toEpochMilli()
        log.record(
            event = OpEvent.HISTORY_PERIOD_CALCULATED,
            profileId = slice.profileId,
            after = window.logDetail(),
            detail = "records=${slice.intakes.size};ms=$spent",
        )
        // Доли может не быть: мерить нечего (Р-33). След называет причину прямо —
        // «порог не согласован» было бы ложью, решение Р-21/Р-27 порог согласовало.
        if (period.onTimeShare is OnTimeShare.Absent) {
            log.record(
                event = OpEvent.HISTORY_ON_TIME_NOT_MEASURED,
                profileId = slice.profileId,
                after = window.logDetail(),
                detail = ON_TIME_NOT_MEASURED,
            )
        }
        return period
    }

    private companion object {
        const val ON_TIME_NOT_MEASURED = "on_time_not_measured"
    }
}

/** Период для журнала: границы окна и ничего больше — ни названий, ни чисел расчёта. */
internal fun HistoryWindow.logDetail(): String = "$from..$to"
