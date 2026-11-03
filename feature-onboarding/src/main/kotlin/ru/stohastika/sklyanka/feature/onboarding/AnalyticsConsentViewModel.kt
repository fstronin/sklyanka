package ru.stohastika.sklyanka.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentSource
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.AnalyticsRecorder
import ru.stohastika.sklyanka.domain.analytics.ConsentShown
import ru.stohastika.sklyanka.domain.analytics.ConsentShownSource
import ru.stohastika.sklyanka.domain.analytics.DecideAnalyticsConsentUseCase
import ru.stohastika.sklyanka.domain.analytics.ShouldShowAnalyticsConsentUseCase
import javax.inject.Inject

/**
 * Экран согласия на аналитику (SKL-165, ПМ-25, АН-1).
 *
 * Модель отвечает на два вопроса: показывать ли экран и что записать после решения.
 * Правило «показывать» — доменное ([ShouldShowAnalyticsConsentUseCase]), а не флаг экрана:
 * решение хранится одним ключом и повторно при запуске не спрашивается (АН-2).
 *
 * `consent_shown` пишется в момент показа, а не после нажатия: иначе воронка «показали →
 * согласились» считала бы только согласившихся. Запись живёт в очереди согласия и уходит
 * вместе с первым принятым согласием, а при отказе стирается целиком (АН-4).
 *
 * Экран не запирает: и «Разрешить», и «Сейчас не надо» ведут к одной лямбде `onDone`
 * у маршрута, и хозяин навигации сам решает, что открыть дальше.
 */
@HiltViewModel
class AnalyticsConsentViewModel @Inject constructor(
    private val shouldShow: ShouldShowAnalyticsConsentUseCase,
    private val decideConsent: DecideAnalyticsConsentUseCase,
    private val recorder: AnalyticsRecorder,
) : ViewModel() {

    /**
     * Виден ли экран: `null` — вопрос ещё не решён, `false` — показывать нечего.
     *
     * Тройное состояние, а не пара «показывать/нет»: пустая композиция до ответа домена
     * не должна выглядеть как «экран закрыт» и вызывать `onDone` раньше времени.
     */
    private val _visible = MutableStateFlow<Boolean?>(null)
    val visible: StateFlow<Boolean?> = _visible.asStateFlow()

    private var started = false
    private var deciding = false

    /**
     * Решить, показывать ли экран, и записать показ.
     *
     * Повторный вызов из-за рекомпозиции ничего не делает: `consent_shown` одного источника
     * не дублируется ни здесь, ни в очереди (Р-96).
     */
    fun show(source: AnalyticsConsentSource = AnalyticsConsentSource.FIRST_LAUNCH) {
        if (started) return
        started = true
        viewModelScope.launch {
            if (source == AnalyticsConsentSource.FIRST_LAUNCH && !shouldShow()) {
                _visible.value = false
                return@launch
            }
            recorder.recordConsent(ConsentShown(source.shownSource()))
            _visible.value = true
        }
    }

    /**
     * Нажатие одного из двух действий.
     *
     * Повторное нажатие (двойной тап, нажатие после «Назад») не принимается: решение
     * записывается один раз, и очередь не очищается после того, как согласие уже открыло
     * передачу.
     */
    fun choose(decision: AnalyticsConsentState, source: AnalyticsConsentSource) {
        if (deciding || decision == AnalyticsConsentState.NOT_ASKED) return
        deciding = true
        viewModelScope.launch {
            decideConsent(decision, source)
            _visible.value = false
        }
    }
}

/** Источник показа в терминах каталога событий (ПМ-26, Р-96). */
private fun AnalyticsConsentSource.shownSource(): ConsentShownSource = when (this) {
    AnalyticsConsentSource.FIRST_LAUNCH -> ConsentShownSource.FIRST_LAUNCH
    AnalyticsConsentSource.SETTINGS -> ConsentShownSource.SETTINGS
}
