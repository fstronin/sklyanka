package ru.stohastika.sklyanka.domain.analytics

/**
 * Гейт согласия перед SDK (SKL-82, ПМ-19/ПМ-25/ПМ-28).
 *
 * Правило отправки доменное, потому что это продуктовое правило, а не деталь вендора:
 * три условия в одном месте — решение принято, версия предмета согласия текущая, сборка
 * не debug. Пока условие не выполнено, `send` не доходит до sink вовсе — «до согласия
 * ни одного события» становится свойством кода, а не договорённостью.
 *
 * Активация выполняется до первого события и только по факту успеха: событие, ушедшее
 * в неактивированный SDK, теряется молча. Сбой активации (нет HMS Core, нет файла
 * вендора) не выходит наружу — контур просто молчит, приложение работает (НФТ-39).
 *
 * Класс чистый и живёт на JVM: и юнит-тесты, и сценарная приёмка проверяют его
 * с подменённым sink, без устройства и без вендора.
 */
class ConsentedAnalytics(
    private val sink: AnalyticsSink,
    private val debuggable: Boolean,
    private val log: AnalyticsLog,
) : Analytics {

    @Volatile
    private var allowed = false

    override fun track(event: AnalyticsEvent) {
        if (!allowed) return
        runCatching { sink.send(event) }.onFailure { log.sendFailed(it::class.simpleName.orEmpty()) }
    }

    override suspend fun applyConsent(consent: AnalyticsConsent) {
        val consented = consent.sendingAllowed(AnalyticsConsentVersion.MATERIAL)
        val next = consented && !debuggable
        if (debuggable && consented) log.debugSuppressed()
        if (next == allowed) return

        if (next) {
            runCatching { sink.activate() }
                .onSuccess {
                    allowed = true
                    log.activated()
                }
                .onFailure { log.unavailable(it::class.simpleName.orEmpty()) }
        } else {
            allowed = false
            runCatching { sink.deactivate() }
                .onFailure { log.unavailable(it::class.simpleName.orEmpty()) }
        }
    }
}
