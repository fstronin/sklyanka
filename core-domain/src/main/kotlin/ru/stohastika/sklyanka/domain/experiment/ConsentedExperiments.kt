package ru.stohastika.sklyanka.domain.experiment

import kotlinx.coroutines.withTimeoutOrNull
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentVersion
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLog
import ru.stohastika.sklyanka.domain.analytics.ExperimentExposed
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Гейт согласия и fail-safe перед AGC (SKL-83, ПМ-20/ПМ-29; TDD §8).
 *
 * Правило применения доменное, потому что это продуктовое правило, а не деталь вендора:
 * три условия в одном месте — решение принято, версия предмета согласия текущая, сборка
 * не debug. Пока условие не выполнено, вендор не вызывается вовсе, а любая поверхность
 * получает базовый вариант.
 *
 * Fail-safe: обновление вариантов идёт с верхней границей ожидания и не блокирует
 * вызывающего; ошибка или таймаут не выходят наружу и не отменяют уже полученный снимок.
 * Снимок прошлого запуска применяется сразу при включении, поэтому вариант переживает
 * перезапуск. Выключение согласия обнуляет снимок и прекращает запросы.
 *
 * Экспонирование: первое чтение не-base варианта за процесс отправляет
 * `experiment_exposed` через общий [Analytics] — то есть тоже через гейт согласия.
 * Класс чистый и живёт на JVM: и юнит-тесты, и сценарная приёмка проверяют его
 * с подменёнными вендором и кэшем, без устройства и без вендора.
 */
class ConsentedExperiments(
    private val vendor: ExperimentVendor,
    private val cache: ExperimentCacheStore,
    private val analytics: Analytics,
    private val debuggable: Boolean,
    private val log: AnalyticsLog,
    private val timeout: Duration = DEFAULT_TIMEOUT,
) : Experiments {

    @Volatile
    private var allowed = false

    private val active = AtomicReference<Map<ExperimentId, VariantKey>>(emptyMap())
    private val exposed = ConcurrentHashMap.newKeySet<Pair<ExperimentId, VariantKey>>()

    override fun variantOf(experiment: ExperimentId): VariantKey {
        if (!allowed) return experiment.base
        val variant = active.get()[experiment] ?: experiment.base
        if (variant != experiment.base && exposed.add(experiment to variant)) {
            analytics.track(ExperimentExposed(experiment, variant))
        }
        return variant
    }

    override suspend fun applyConsent(consent: AnalyticsConsent) {
        val next = consent.sendingAllowed(AnalyticsConsentVersion.MATERIAL) && !debuggable
        if (next == allowed) return
        allowed = next
        // Включение: снимок прошлого запуска применяется сразу, обновление — асинхронно.
        if (next) {
            val cached = runCatching { cache.read() }.getOrDefault(emptyMap())
            active.set(acceptedVariants(cached))
        } else {
            active.set(emptyMap())
        }
    }

    override suspend fun refresh() {
        if (!allowed) return
        when (val outcome = fetchOutcome()) {
            FetchOutcome.TimedOut -> log.experimentsUnavailable("timeout")
            is FetchOutcome.Failed -> log.experimentsUnavailable(outcome.reason)
            is FetchOutcome.Received -> applyFetched(outcome.variants)
        }
    }

    private suspend fun applyFetched(fetched: Map<ExperimentId, VariantKey>) {
        // Согласие могли выключить, пока шёл запрос: результат не применяется.
        if (!allowed) return
        val accepted = acceptedVariants(fetched)
        runCatching { cache.write(accepted) }
            .onFailure { log.experimentsUnavailable(it::class.simpleName.orEmpty()) }
        active.set(accepted)
        log.experimentsUpdated(accepted.size)
    }

    private suspend fun fetchOutcome(): FetchOutcome =
        runCatching { withTimeoutOrNull(timeout) { vendor.fetchVariants() } }.fold(
            onSuccess = { variants ->
                if (variants == null) FetchOutcome.TimedOut else FetchOutcome.Received(variants)
            },
            onFailure = { failure ->
                // Отмена корутины — не деградация: она не логируется и не превращается в вариант.
                if (failure is CancellationException) throw failure
                FetchOutcome.Failed(failure::class.simpleName.orEmpty())
            },
        )

    /**
     * Отбросить пары, которых нет в каталоге.
     *
     * Защита от повреждённого кэша и чужого значения: вариант, не разрешённый
     * экспериментом, не существует — действует базовый (ПМ-26: значения закрыты).
     */
    private fun acceptedVariants(variants: Map<ExperimentId, VariantKey>): Map<ExperimentId, VariantKey> =
        variants.filter { (experiment, variant) -> variant in experiment.variants }

    /** Исход получения вариантов: успех, таймаут или сбой — без исключений наружу. */
    private sealed interface FetchOutcome {

        data object TimedOut : FetchOutcome

        data class Failed(val reason: String) : FetchOutcome

        data class Received(val variants: Map<ExperimentId, VariantKey>) : FetchOutcome
    }

    companion object {

        /** Верхняя граница ожидания вендора: получение неблокирующее (НФТ-16а). */
        val DEFAULT_TIMEOUT: Duration = 3.seconds
    }
}
