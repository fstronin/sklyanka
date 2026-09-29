package ru.stohastika.sklyanka.acceptance.history

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.AdherencePeriod
import ru.stohastika.sklyanka.domain.history.ObserveAdherenceUseCase
import java.time.YearMonth

/**
 * Приверженность за месяц из мира сценария приёмки — тем же путём, что на экране.
 *
 * Состояние собирает настоящий [ObserveAdherenceUseCase]: профиль берётся из контекста
 * активного профиля (ПР-10), срез — из `HistoryRepository` мира, расчёт — из
 * `AdherenceCalculator` с тем же правилом «вовремя», что зарегистрировано в продукте
 * (`OnTimeRule.NotAgreed`, прочерк вместо доли). Выдуманное состояние проверяло бы
 * разметку, а приёмка SKL-18 говорит о продукте.
 */
internal object HistoryFromWorld {

    fun period(world: AcceptanceWorld, month: YearMonth): AdherencePeriod {
        val useCase = ObserveAdherenceUseCase(
            activeProfile = world.profileSession.activeStore,
            history = world.history,
            calculator = AdherenceCalculator(),
            time = world.clock.time,
            log = world.log,
        )
        return runBlocking { useCase(month).first() }
    }
}
