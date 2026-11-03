package ru.stohastika.sklyanka.domain.analytics

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.analytics.port.AnalyticsConsentStore

/**
 * Решение человека об аналитике (ПМ-18, ПМ-25).
 *
 * Три состояния, а не флаг: «ещё не спрашивали» — не то же самое, что «отказался».
 * Первое означает «показать экран», второе — «не показывать и ничего не отправлять» (АН-2).
 */
enum class AnalyticsConsentState {
    NOT_ASKED,
    ALLOWED,
    DECLINED,
}

/** Откуда открыт экран согласия: первый запуск или раздел данных в настройках (ПМ-25, Р-96). */
enum class AnalyticsConsentSource {
    FIRST_LAUNCH,
    SETTINGS,
}

/**
 * Показывать ли экран согласия на аналитику.
 *
 * Правило то же, что у политики конфиденциальности: пока решения нет, экран показывается.
 * Отдельного состояния «первый запуск» не заводится — оно выражено отсутствием решения.
 */
class ShouldShowAnalyticsConsentUseCase(private val store: AnalyticsConsentStore) {

    suspend operator fun invoke(): Boolean = store.consent.first() == AnalyticsConsentState.NOT_ASKED
}

/** Текущее и последующие решения: строка настроек меняется без перезахода на экран. */
class ObserveAnalyticsConsentUseCase(private val store: AnalyticsConsentStore) {

    operator fun invoke(): Flow<AnalyticsConsentState> = store.consent
}

/**
 * Решение о согласии: единственное место, где согласие превращается в записи и отправку.
 *
 * Принятие: решение → запись выбора → открытие передачи → отправка накопленного. Порядок
 * именно такой: `consent_shown`, накопленный до согласия, уходит вместе с выбором, а не
 * отдельным проходом раньше него. Повторный вход из настроек пишет и показ
 * (`source = settings`, Р-96), и включение (`analytics_enabled`).
 *
 * Отказ: решение → закрытие передачи → очистка очереди. Ни одного события и ни одной
 * отправки — и при следующих запусках тоже. `not_now` не отправляется никогда: при отказе
 * его запись стирается вместе с очередью (АН-2, TDD §7.4).
 */
class DecideAnalyticsConsentUseCase(
    private val store: AnalyticsConsentStore,
    private val recorder: AnalyticsRecorder,
) {

    suspend operator fun invoke(
        decision: AnalyticsConsentState,
        source: AnalyticsConsentSource = AnalyticsConsentSource.FIRST_LAUNCH,
    ) {
        require(decision != AnalyticsConsentState.NOT_ASKED) {
            "a decision is either approval or refusal"
        }
        if (decision == AnalyticsConsentState.DECLINED) {
            decline()
            return
        }
        store.decide(AnalyticsConsentState.ALLOWED)
        if (source == AnalyticsConsentSource.SETTINGS) {
            recorder.recordConsent(ConsentShown(ConsentShownSource.SETTINGS))
        }
        recorder.recordConsent(ConsentChoice(ConsentAnswer.ACCEPTED))
        if (source == AnalyticsConsentSource.SETTINGS) {
            recorder.recordConsent(AnalyticsEnabled)
        }
        recorder.onConsentGranted()
    }

    private suspend fun decline() {
        store.decide(AnalyticsConsentState.DECLINED)
        recorder.onConsentDeclined()
    }
}
