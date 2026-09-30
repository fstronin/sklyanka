package ru.stohastika.sklyanka.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

    /** Открыт ли сейчас «Сегодня»: им проверяется, куда привёл возврат (SKL-43). */
    val showsToday: Boolean
        get() = tab == RootTab.TODAY && meds == null && section == null && !historyFromToday

    fun openTab(tab: RootTab) {
        this.tab = tab
    }

    fun openMeds(destination: MedsDestination) {
        meds = destination
    }

    fun leaveMeds() {
        meds = null
    }

    fun openSection(destination: SettingsDestination) {
        section = destination
    }

    fun leaveSection() {
        section = null
    }

    fun openHistory() {
        historyFromToday = true
    }

    fun leaveHistory() {
        historyFromToday = false
    }

    /**
     * Открыть «Сегодня», что бы ни было открыто: второй уровень, история, вкладка.
     *
     * Возврат обязан быть полным: оставленный открытым раздел показал бы человека
     * не там, куда его привело живое окно отмены, а незакрытая история — вместо «Сегодня».
     */
    fun openToday() {
        tab = RootTab.TODAY
        meds = null
        section = null
        historyFromToday = false
    }
}
