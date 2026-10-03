package ru.stohastika.sklyanka.analytics

import kotlinx.coroutines.flow.collectLatest
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentStore
import ru.stohastika.sklyanka.domain.experiment.Experiments
import javax.inject.Inject

/**
 * Синхронизация A/B с согласием при старте приложения (SKL-83, TDD §11).
 *
 * Единственная точка, где решение согласия применяется к экспериментам: поток хранилища
 * эмитит и при первом запуске, и при включении/выключении в настройках, поэтому use cases
 * SKL-82 не меняются. Обновление вариантов идёт той же корутиной и только при разрешении;
 * `collectLatest` отменяет его, если решение сменилось, — результат, пришедший после
 * выключения, не применяется.
 *
 * `run()` не возвращается: поток живёт столько же, сколько процесс. Вызывается из
 * `SklyankaApp` отдельной корутиной, чтобы получение вариантов не удлиняло путь
 * планирования напоминаний (НФТ-16а).
 */
class ExperimentsBootstrap @Inject constructor(
    private val consent: AnalyticsConsentStore,
    private val experiments: Experiments,
) {

    /** Применять решение согласия и обновлять варианты, пока живёт процесс. */
    suspend fun run() {
        consent.observe().collectLatest { decision ->
            experiments.applyConsent(decision)
            experiments.refresh()
        }
    }
}
