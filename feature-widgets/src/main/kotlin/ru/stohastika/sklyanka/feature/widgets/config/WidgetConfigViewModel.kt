package ru.stohastika.sklyanka.feature.widgets.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.medication.MedicationListOrder
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.profile.ProfileContextProvider
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.widget.WidgetSetupGate
import ru.stohastika.sklyanka.feature.widgets.bindings.WidgetBindingStore
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetType
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import javax.inject.Inject

/**
 * Состояние экрана настройки виджета (§5.12).
 *
 * @param medications лекарства **активного** профиля в порядке списка лекарств: у экземпляра
 *   виджета профиль не выбирается (ПР-24), а второго порядка лекарств продукт не заводит.
 */
data class WidgetConfigState(
    val type: WidgetType = WidgetType.TODAY,
    val medications: List<Medication> = emptyList(),
    val selectedMedicationId: String? = null,
    val profile: ProfileChromeState = ProfileChromeState.Owner,
) {

    /** Кнопка завершения доступна: правило установки, а не условие места (§5.12). */
    val printable: Boolean
        get() = WidgetSetupGate.canComplete(
            medicationRequired = type == WidgetType.MEDICATION,
            medicationChosen = selectedMedicationId != null,
        )

    /** Строка лекарства показывается только у типа «по лекарству» (§5.12). */
    val showMedicationRow: Boolean get() = type == WidgetType.MEDICATION

    /** У профиля нет ни одного лекарства: строка не нажимается, диалог не открывается. */
    val hasNoMedications: Boolean get() = showMedicationRow && medications.isEmpty()
}

/**
 * Экран настройки виджета: выбор типа и лекарства (§5.12).
 *
 * Порядок лекарств — тот же, что в списке лекарств ([MedicationListOrder]): второго порядка
 * продукт не заводит, а архивные курсы в выбор не входят — плита показывает приёмы
 * действующего курса (§5.12).
 *
 * Установка завершается только законченным выбором: у типа «по лекарству» лекарство
 * обязательно ([WidgetSetupGate]).
 */
@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    private val medications: MedicationRepository,
    private val profiles: ProfileContextProvider,
    private val bindings: WidgetBindingStore,
    private val time: TimeSource,
) : ViewModel() {

    private val mutable = MutableStateFlow(WidgetConfigState())
    val state: StateFlow<WidgetConfigState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            val context = profiles.snapshot()
            if (context != null) {
                mutable.value = mutable.value.copy(
                    profile = ProfileChromeState.of(
                        name = context.name,
                        colorArgb = context.color,
                        isSelf = context.isSelf,
                    ),
                    medications = MedicationListOrder.sorted(
                        medications = medications.observeActive(context.id).first(),
                        now = time.nowLocal(),
                    ),
                )
            }
        }
    }

    fun selectType(type: WidgetType) {
        mutable.value = mutable.value.copy(type = type)
    }

    fun selectMedication(medicationId: String) {
        mutable.value = mutable.value.copy(selectedMedicationId = medicationId)
    }

    /**
     * Завершить установку: запомнить привязку и разрешить платформе добавить плиту.
     *
     * @return `false`, если завершать нечего: лекарство не выбрано.
     *   Отмена в этом случае — не заглушка, а исход: виджет не появляется (§5.12).
     */
    suspend fun complete(widgetId: Int): Boolean {
        val current = mutable.value
        if (!current.printable) return false
        current.selectedMedicationId?.let { bindings.bind(widgetId, it) }
        return true
    }
}
