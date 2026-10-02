package ru.stohastika.sklyanka.acceptance.steps.sk64

import androidx.compose.runtime.mutableStateListOf
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import ru.stohastika.sklyanka.navigation.MedsDestination
import ru.stohastika.sklyanka.navigation.MedsStack
import ru.stohastika.sklyanka.navigation.RootNavigation
import ru.stohastika.sklyanka.navigation.RootTab
import ru.stohastika.sklyanka.navigation.SettingsDestination
import ru.stohastika.sklyanka.ui.motion.LayerMotion

class Sk64NavigationScreenSteps {

    private var navigation = RootNavigation()
    private var medsStack: MedsStack? = null
    private var motion: () -> LayerMotion = { navigation.rootMotion }

    @Допустим("^корень приложения собран на стенде SKL-64$")
    fun theRootIsOnTheStand() {
        navigation = RootNavigation()
        medsStack = null
        motion = { navigation.rootMotion }
    }

    @Когда("^человек открывает карточку лекарства с «Сегодня»$")
    fun theMedicationCardOpens() {
        navigation.openMeds(MedsDestination.Card("med-1"))
        motion = { navigation.rootMotion }
    }

    @Когда("^человек открывает «Перенос данных» из настроек$")
    fun theTransferOpensFromSettings() {
        navigation.openTab(RootTab.SETTINGS)
        navigation.openSection(SettingsDestination.TRANSFER)
        motion = { navigation.rootMotion }
    }

    @Когда("^человек возвращается назад$")
    fun thePersonGoesBack() {
        navigation.leaveSection()
        motion = { navigation.rootMotion }
    }

    @Когда("^человек переключается на вкладку «Настройки»$")
    fun thePersonSwitchesToSettings() {
        navigation.openTab(RootTab.SETTINGS)
        motion = { navigation.rootMotion }
    }

    @Когда("^человек открывает архив лекарств$")
    fun thePersonOpensTheMedicationArchive() {
        val stack = MedsStack(mutableStateListOf<MedsDestination>(MedsDestination.List))
        stack.push(MedsDestination.Archive)
        medsStack = stack
        motion = { stack.motion }
    }

    @Тогда("^движение перехода — «(вперёд|назад|рядом)»$")
    fun theMotionIs(name: String) {
        val expected = when (name) {
            "вперёд" -> LayerMotion.Forward
            "назад" -> LayerMotion.Back
            else -> LayerMotion.Crossfade
        }
        assertEquals(expected, motion())
    }
}
