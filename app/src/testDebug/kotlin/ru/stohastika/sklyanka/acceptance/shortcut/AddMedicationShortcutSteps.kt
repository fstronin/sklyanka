package ru.stohastika.sklyanka.acceptance.shortcut

import androidx.compose.runtime.mutableStateListOf
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.navigation.AddMedicationLaunch
import ru.stohastika.sklyanka.navigation.MedsDestination
import ru.stohastika.sklyanka.navigation.MedsStack
import ru.stohastika.sklyanka.navigation.RootNavigation

/**
 * Экранные шаги приёмки ярлыка «Добавить лекарство» (SKL-80, группа `@screen`).
 *
 * Полный запуск Activity в контейнере недоступен: граф Hilt поднимается только на
 * устройстве (`MainActivityAddMedicationTest`). Поэтому маршрут проверяется там, где он
 * записан: [RootNavigation.openFromLaunch] кладёт форму поверх раздела «Лекарства», а
 * [MedsStack] закрытием формы ведёт в список — ровно те две половины, из которых состоит
 * поведение ярлыка.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class AddMedicationShortcutSteps {

    private val navigation = RootNavigation()
    private var stack: MedsStack? = null

    @Допустим("^форма нового лекарства открыта запуском из ярлыка$")
    fun theNewMedicationFormWasOpenedByTheShortcut() {
        theAppIsLaunchedByTheShortcut()
    }

    @Когда("^приложение запущено ярлыком «Добавить лекарство»$")
    fun theAppIsLaunchedByTheShortcut() {
        assertTrue("the shortcut action must open the form", navigation.openFromLaunch(AddMedicationLaunch.ACTION))
        // Стек раздела так, как его заводит `MedsSection` с начальным местом «форма»:
        // список всегда на дне, форма — поверх него (SKL-80, TDD §7.2).
        stack = MedsStack(mutableStateListOf(MedsDestination.List, MedsDestination.NewMedication))
    }

    @Тогда("^открыта форма нового лекарства$")
    fun theNewMedicationFormIsOpen() {
        assertEquals(MedsDestination.NewMedication, navigation.meds)
    }

    @Тогда("^форма нового лекарства открыта$")
    fun theNewMedicationFormIsOpenWithOtherWordOrder() {
        theNewMedicationFormIsOpen()
    }

    @Но("^«Сегодня» вместо неё не показано$")
    fun todayIsNotShownInstead() {
        assertFalse("the shortcut must not land on «Сегодня»", navigation.showsToday)
    }

    @Когда("^человек закрывает форму$")
    fun thePersonClosesTheForm() {
        assertTrue("the form must close", requireNotNull(stack) { "the form must be open" }.pop())
    }

    @Тогда("^открыт список лекарств$")
    fun theMedicationListIsOpen() {
        assertEquals(MedsDestination.List, requireNotNull(stack) { "the form must have been open" }.current)
    }
}
