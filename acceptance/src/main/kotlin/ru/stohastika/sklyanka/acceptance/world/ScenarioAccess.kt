package ru.stohastika.sklyanka.acceptance.world

import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import ru.stohastika.sklyanka.domain.pro.ProStatus
import ru.stohastika.sklyanka.domain.pro.ProSubscription
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionCache
import java.time.LocalDate

/**
 * Доступ к прежним платным возможностям в мире сценария (SKL-153, Р-86, ADR-0016).
 *
 * Продукт бесплатный: доступа не покупают, и состояние подписки не приходит из магазина.
 * Здесь повторено ровно одно следствие бесплатной сборки — кэш всегда отвечает действующей
 * записью `Active(productId = null, until = LocalDate.MAX)` (`docs/tasks/SKL-153/TDD.md` §3).
 * Правила доступа не пересказываются: их решают настоящие гейты состава
 * ([ru.stohastika.sklyanka.domain.pro.ComplexScheduleGate],
 * [ru.stohastika.sklyanka.domain.profile.ProfileCreationGate],
 * [ru.stohastika.sklyanka.domain.widget.WidgetSetupGate],
 * [ru.stohastika.sklyanka.domain.appearance.AppearanceGate]) и настоящий
 * [ObserveProStatusUseCase].
 *
 * Магазина, шторки и проверки при запуске здесь нет вовсе: платёжная приёмка удалена вместе
 * с платным контуром (Р-86 п. 3), а код магазина остаётся за сборочным флагом `pro`.
 */
class ScenarioAccess(clock: ScenarioClock) {

    /**
     * Кэш бесплатной сборки: чтение всегда даёт действующий доступ, запись ничего не меняет.
     *
     * Так же, как `FreeProSubscriptionCache` в `app/src/free`: сеть и магазин в поставляемой
     * сборке отсутствуют, а доменный шов «доступ» сохранён ради будущего `pro`-варианта.
     */
    private val cache: ProSubscriptionCache = object : ProSubscriptionCache {

        override suspend fun read(): ProSubscription = ProSubscription.Active(productId = null, until = LocalDate.MAX)

        override suspend fun write(subscription: ProSubscription) = Unit
    }

    /** Чтение доступа тем же сценарием, что у экрана и гейтов состава (Р-86). */
    val statusUseCase: ObserveProStatusUseCase = ObserveProStatusUseCase(cache, clock.time)

    /** Состояние доступа так, как его видит строка настроек и гейты состава. */
    fun status(): ProStatus = runBlocking { statusUseCase() }
}
