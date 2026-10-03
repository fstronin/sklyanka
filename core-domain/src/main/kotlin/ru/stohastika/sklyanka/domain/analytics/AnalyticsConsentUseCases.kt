package ru.stohastika.sklyanka.domain.analytics

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Принять согласие: записать решение, включить контур и отметить исход (ПМ-19, ПМ-26).
 *
 * Порядок шагов обязателен: сначала запись, затем активация, и только потом события
 * решения — иначе событие ушло бы в выключенный контур. Исход отправляется парой
 * `consent_shown` → `consent_choice`, потому что «показ без решения» продукту не нужен:
 * доля отказа читается по решениям, а не по брошенным экранам.
 */
class GrantAnalyticsConsentUseCase(private val consent: AnalyticsConsentStore, private val analytics: Analytics) {

    suspend operator fun invoke(source: ConsentSource) {
        consent.record(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)
        analytics.applyConsent(consent.read())
        analytics.track(AnalyticsEvent.ConsentShown(source))
        analytics.track(AnalyticsEvent.ConsentChoice(ConsentOutcome.ACCEPTED))
    }
}

/**
 * Отказаться: записать решение и выключить контур (ПМ-25, ПМ-27).
 *
 * Первичный отказ не отправляет ничего: до согласия контур не активирован. Повторный
 * отказ после существенного изменения предмета согласия (`policyUpdate = true`) — другое
 * дело: контур ещё активен, и исход повторного показа успевает уйти парой
 * `consent_shown(policy_update)` → `consent_choice(not_now)` до выключения.
 */
class DeclineAnalyticsConsentUseCase(private val consent: AnalyticsConsentStore, private val analytics: Analytics) {

    suspend operator fun invoke(policyUpdate: Boolean = false) {
        val wasActive = consent.read().sendingAllowed(AnalyticsConsentVersion.MATERIAL)
        consent.record(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)
        if (wasActive && policyUpdate) {
            analytics.track(AnalyticsEvent.ConsentShown(ConsentSource.POLICY_UPDATE))
            analytics.track(AnalyticsEvent.ConsentChoice(ConsentOutcome.NOT_NOW))
        }
        analytics.applyConsent(consent.read())
    }
}

/**
 * Переключатель «Аналитика» в настройках (ПМ-18, ПМ-25).
 *
 * Включение — такое же информированное решение, как экран первого запуска: оно пишется
 * с текущей версией предмета согласия и отмечается событием. Выключение события
 * не отправляет (ПМ-26) и прекращает отправку немедленно.
 */
class SetAnalyticsEnabledUseCase(private val consent: AnalyticsConsentStore, private val analytics: Analytics) {

    suspend operator fun invoke(enabled: Boolean) {
        if (enabled) {
            consent.record(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)
            analytics.applyConsent(consent.read())
            analytics.track(AnalyticsEvent.AnalyticsEnabled(AnalyticsEnabledSource.SETTINGS))
        } else {
            consent.record(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)
            analytics.applyConsent(consent.read())
        }
    }
}

/**
 * Включение аналитики из настроек: тот же экран согласия, другое решение (ПМ-25 п. 4).
 *
 * Запись решения и активация — как при первом согласии, но каталог называет этот вход
 * иначе: `consent_shown(source: settings)` фиксирует показ экрана из настроек,
 * `analytics_enabled(source: settings)` — что аналитика включена позже, осознанным
 * действием в настройках (ПМ-26, уточнение к ПМ-25 от 03.10.2026). `consent_choice`
 * здесь не отправляется: исход этого входа несёт `analytics_enabled`, а доля отказа
 * первого запуска считается парой `consent_shown` → `consent_choice`.
 */
class AcceptAnalyticsFromSettingsUseCase(
    private val consent: AnalyticsConsentStore,
    private val analytics: Analytics,
) {

    suspend operator fun invoke() {
        consent.record(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)
        analytics.applyConsent(consent.read())
        analytics.track(AnalyticsEvent.ConsentShown(ConsentSource.SETTINGS))
        analytics.track(AnalyticsEvent.AnalyticsEnabled(AnalyticsEnabledSource.SETTINGS))
    }
}

/**
 * Состояние переключателя аналитики для настроек и старта.
 *
 * Отдаётся решением [AnalyticsConsent.sendingAllowed], а не сырым флагом: устаревшая
 * версия согласия — это «выключено», и переключатель обязан показывать то же, что
 * решает отправку.
 */
class ObserveAnalyticsConsentUseCase(private val consent: AnalyticsConsentStore) {

    operator fun invoke(): Flow<Boolean> = consent.observe().map { it.sendingAllowed(AnalyticsConsentVersion.MATERIAL) }
}
