package ru.stohastika.sklyanka.feature.onboarding.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.analytics.AcceptAnalyticsFromSettingsUseCase
import ru.stohastika.sklyanka.domain.analytics.ConsentSource
import ru.stohastika.sklyanka.domain.analytics.DeclineAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.GrantAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.SetAnalyticsEnabledUseCase
import javax.inject.Inject

/**
 * Решение человека на экране согласия (ПМ-19, ПМ-25, ПМ-27).
 *
 * Модель не решает, «да» это или «нет», — она выбирает сценарий по источнику показа:
 * первый запуск и повторное информирование пишут решение и отмечают исход парой
 * `consent_shown` → `consent_choice`; вход из настроек включает контур и отмечает
 * `consent_shown(source: settings)` → `analytics_enabled` (уточнение к ПМ-25).
 *
 * Повторный вход сбрасывает решение: `start` зовётся маршрутом на каждый показ, а экран
 * может быть открыт дважды за жизнь процесса — при первом запуске и позже из настроек.
 */
@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val grant: GrantAnalyticsConsentUseCase,
    private val decline: DeclineAnalyticsConsentUseCase,
    private val acceptFromSettings: AcceptAnalyticsFromSettingsUseCase,
    private val setEnabled: SetAnalyticsEnabledUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ConsentUiState())
    val state: StateFlow<ConsentUiState> = _state.asStateFlow()

    private val _decided = MutableStateFlow(false)

    /** Решение принято: хозяин экрана уводит человека дальше или возвращает в настройки. */
    val decided: StateFlow<Boolean> = _decided.asStateFlow()

    private var deciding = false

    /** Начать показ с названного источника: повторный вход — новое решение. */
    fun start(source: ConsentSource) {
        deciding = false
        _decided.value = false
        _state.value = ConsentUiState(source)
    }

    fun onAllow() = decide {
        if (_state.value.fromSettings) acceptFromSettings() else grant(_state.value.source)
    }

    fun onDecline() = decide {
        if (_state.value.fromSettings) {
            // Включение из настроек отменено: решение «отказано» записывается,
            // отправка молчит, события об этом не заводятся (ПМ-26).
            setEnabled(false)
        } else {
            decline(policyUpdate = _state.value.source == ConsentSource.POLICY_UPDATE)
        }
    }

    private fun decide(action: suspend () -> Unit) {
        if (deciding) return
        deciding = true
        viewModelScope.launch {
            action()
            _decided.value = true
        }
    }
}
