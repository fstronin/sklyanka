package ru.stohastika.sklyanka.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
     * @return `true`, если действие было ярлыком и форма открыта.
     */
    fun openFromLaunch(action: String?): Boolean {
        if (!AddMedicationLaunch.matches(action)) return false
        openMeds(MedsDestination.NewMedication)
        return true
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
