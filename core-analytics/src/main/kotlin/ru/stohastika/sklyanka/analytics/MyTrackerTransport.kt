package ru.stohastika.sklyanka.analytics

import android.app.Activity
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecord
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsTransport
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Адаптер провайдера за портом (SKL-165, TDD §2.3, АН-4).
 *
 * Здесь только три решения и ни одного доменного знания:
 *
 * 1. **Можно ли вообще.** `available` — есть ли ключ SDK. Нет ключа — сборка живёт,
 *    согласие работает как обычно, отправлять нечего.
 * 2. **Открыта ли передача.** SDK инициализируется только в [start], и только после
 *    согласия: до него поставщика в процессе нет вовсе (АН-4). После [stop] SDK
 *    больше не зовётся — это наш «стоп-кран», потому что своего у поставщика нет.
 * 3. **Батч.** Одна пачка — по одному вызову на запись и один `flush()`. Отказ SDK
 *    (исключение) — это `false`, а не падение: записи остаются в очереди до следующего раза.
 *
 * Имена событий и значения свойств берутся из каталога домена как есть: адаптер ничего
 * не придумывает и ничего не дописывает, поэтому доменная строка в событие попасть не может.
 */
@Singleton
class MyTrackerTransport @Inject constructor(private val sdk: MyTrackerSdk, @MyTrackerKey private val key: String) :
    AnalyticsTransport,
    AnalyticsLaunchAnchor {

    override val available: Boolean = key.isNotBlank()

    /** SDK инициализирован удачно. `initTracker` зовётся один раз за процесс. */
    @Volatile
    private var initialized = false

    /** Передача открыта: согласие дано и не отозвано. */
    @Volatile
    private var active = false

    override fun start() {
        if (!available) return
        active = true
        if (initialized) return
        initialized = runCatching {
            sdk.init(key)
            sdk.configure(
                bufferingPeriodSeconds = BUFFERING_PERIOD_SECONDS,
                trackingLaunchEnabled = TRACKING_LAUNCH_ENABLED,
                debug = BuildConfig.DEBUG,
            )
        }.isSuccess
    }

    override fun stop() {
        active = false
    }

    override fun deliver(records: List<AnalyticsRecord>): Boolean {
        if (!available || !active || records.isEmpty()) return false
        return runCatching {
            records.forEach { record ->
                sdk.trackEvent(
                    name = record.name.wire,
                    properties = record.properties.mapKeys { (property, _) -> property.wire },
                )
            }
            sdk.flush()
        }.isSuccess
    }

    override fun attach(activity: Activity) {
        if (!available || !active) return
        runCatching { sdk.trackLaunchManually(activity) }
    }

    private companion object {
        /**
         * Секунды. Минимум, который допускает поставщик: между нами и SDK не должно
         * оставаться накопленного запаса к моменту отзыва согласия (TDD §14, риск 1).
         */
        const val BUFFERING_PERIOD_SECONDS = 1

        /** Первая сессия и установка видны в консоли поставщика. */
        const val TRACKING_LAUNCH_ENABLED = true
    }
}
