package ru.stohastika.sklyanka.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.stohastika.sklyanka.domain.undo.UndoableAction
import ru.stohastika.sklyanka.domain.undo.UndoableDeletion
import ru.stohastika.sklyanka.ui.motion.LayerMotion

/**
 * Состояние корня приложения: вкладка нижней панели и открытый экран второго уровня.
 *
 * Вынесено из `MainActivity`, потому что у корня есть правило, проверяемое на JVM:
 * человек с живым окном отмены, оказавшийся в приложении, обязан попасть на «Сегодня»
 * с любого экрана (SKL-43, дополнение ОВ-81). Держатель живёт полем активности, а значит
 * доступен и композиции, и `onResume`, где принимается решение о возврате.
 *
 * Приоритет экранов остаётся в ветвях `Root` (история с «Сегодня» важнее раздела
 * настроек, раздел важнее вкладки); держатель отвечает только за состав состояния
 * и переходы. Библиотеки навигации нет — состояний четыре, и они выражаются четырьмя
 * значениями (`ADR-0007`).
 */
class RootNavigation {

    var tab: RootTab by mutableStateOf(RootTab.TODAY)
        private set

    var meds: MedsDestination? by mutableStateOf(null)
        private set

    var section: SettingsDestination? by mutableStateOf(null)
        private set

    var historyFromToday: Boolean by mutableStateOf(false)
        private set

    var rootMotion: LayerMotion by mutableStateOf(LayerMotion.Crossfade)
        private set

    /** Открыт ли сейчас «Сегодня»: им проверяется, куда привёл возврат (SKL-43). */
    val showsToday: Boolean
        get() = tab == RootTab.TODAY && meds == null && section == null && !historyFromToday

    fun openTab(tab: RootTab) {
        rootMotion = LayerMotion.Crossfade
        this.tab = tab
    }

    fun openMeds(destination: MedsDestination) {
        rootMotion = if (destination == MedsDestination.List || destination == MedsDestination.Archive) {
            LayerMotion.Crossfade
        } else {
            LayerMotion.Forward
        }
        meds = destination
    }

    /**
     * Открыть форму нового лекарства, если пришло действие ярлыка быстрого доступа (SKL-80).
     *
     * Единственный шов, по которому [MainActivity] разбирает входящее намерение: имя
     * действия живёт в [AddMedicationLaunch], решение о маршруте — здесь, и потому обе
     * половины проверяются на JVM без Activity.
     *
     * Живое окно удаления сильнее ярлыка (SKL-99, ОВ-124): пока человек может вернуть
     * лекарство кнопкой «Отменить», форма поверх окна его бы его лишила — вызов
     * возвращает `false`, а стек раздела не трогается.
     *
     * @return `true`, если действие было ярлыком и форма открыта.
     */
    fun openFromLaunch(action: String?, liveAction: UndoableAction? = null, medicationId: String? = null): Boolean {
        // Живое окно удаления сильнее ярлыка (SKL-99, ОВ-124): пока человек может вернуть
        // лекарство кнопкой «Отменить», форма поверх окна лишила бы его этой возможности.
        val opensTheForm = AddMedicationLaunch.matches(action) && liveAction !is UndoableDeletion
        if (opensTheForm) openMeds(MedsDestination.NewMedication)
        // Вход с плиты виджета (SKL-118, §5.11): место на экране называет содержимое плиты.
        // Живое окно удаления и здесь сильнее: оно относится к тому, что человек только что
        // сделал, и уводить его на «Историю» под ним значило бы отнять возврат.
        if (opensTheForm || liveAction is UndoableDeletion) return opensTheForm
        return openFromWidget(action, medicationId)
    }

    /**
     * Открыть место, названное плитой виджета.
     *
     * @return `true`, если действие пришло с плиты и маршрут выбран.
     */
    private fun openFromWidget(action: String?, medicationId: String?): Boolean = when (action) {
        WidgetLaunch.OPEN_TODAY -> {
            // Нажатие на плиту ведёт в приложение, а не «в приложение поверх открытого
            // экрана»: слой выбирается по приоритету выше вкладки, и без сброса карточка
            // лекарства или раздел перекрыли бы переход (ADR-0007).
            openToday()
            true
        }

        WidgetLaunch.OPEN_HISTORY -> {
            resetToRoot()
            openHistory()
            true
        }

        WidgetLaunch.OPEN_MEDICATION ->
            medicationId
                ?.takeIf { it.isNotBlank() }
                ?.let { id ->
                    resetToRoot()
                    openMeds(MedsDestination.Card(id))
                    true
                }
                ?: false

        else -> false
    }

    /** Снять верхние слои: вкладка остаётся, раздел, раздел лекарств и история закрываются. */
    private fun resetToRoot() {
        rootMotion = LayerMotion.Crossfade
        section = null
        meds = null
        historyFromToday = false
    }

    fun leaveMeds() {
        rootMotion = LayerMotion.Back
        meds = null
    }

    fun openSection(destination: SettingsDestination) {
        rootMotion = LayerMotion.Forward
        section = destination
    }

    fun leaveSection() {
        rootMotion = LayerMotion.Back
        section = null
    }

    fun openHistory() {
        rootMotion = LayerMotion.Forward
        historyFromToday = true
    }

    fun leaveHistory() {
        rootMotion = LayerMotion.Back
        historyFromToday = false
    }

    /**
     * Открыть «Сегодня», что бы ни было открыто: второй уровень, история, вкладка.
     *
     * Возврат обязан быть полным: оставленный открытым раздел показал бы человека
     * не там, куда его привело живое окно отмены, а незакрытая история — вместо «Сегодня».
     */
    fun openToday() {
        rootMotion = LayerMotion.Crossfade
        tab = RootTab.TODAY
        meds = null
        section = null
        historyFromToday = false
    }
}
