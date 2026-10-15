package ru.stohastika.sklyanka.feature.pro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.pro.ProSheetSession
import ru.stohastika.sklyanka.domain.pro.ProSheetState
import javax.inject.Inject

/**
 * Экран витрины «Склянки Про» — тонкая обёртка над сессией шторки (SKL-122).
 *
 * Ни одного правила здесь нет намеренно: правила переходов живут в
 * [ProSheetSession], потому что их проверяет приёмка без Android
 * (`docs/tasks/SKL-122/TDD.md` §2), а экран обязан показывать ровно то состояние,
 * которое проверил прогон. Обязанность `ViewModel` одна — пережить поворот экрана
 * и увести работу сессии в `viewModelScope`.
 */
@HiltViewModel
class ProShowcaseViewModel @Inject constructor(private val session: ProSheetSession) : ViewModel() {

    val state: StateFlow<ProSheetState> = session.state

    fun onIntent(intent: ProShowcaseIntent) {
        when (intent) {
            is ProShowcaseIntent.Open -> viewModelScope.launch { session.open(intent.point) }
            is ProShowcaseIntent.SelectPeriod -> session.select(intent.period)
            ProShowcaseIntent.Buy -> viewModelScope.launch { session.buy() }
            ProShowcaseIntent.Restore -> viewModelScope.launch { session.restore() }
            ProShowcaseIntent.Manage -> viewModelScope.launch { session.manage() }
            ProShowcaseIntent.ExpandDisclosure -> session.expandDisclosure()
            ProShowcaseIntent.Dismiss -> session.dismiss()
            // Открытие документа — дело того, кто собирает маршруты (`app`): здесь намерение
            // только гасится, чтобы сопоставление осталось исчерпывающим.
            ProShowcaseIntent.OpenPolicy -> Unit
        }
    }
}
