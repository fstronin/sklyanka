package ru.stohastika.sklyanka.acceptance.today

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.navigation.MedsDestination
import ru.stohastika.sklyanka.navigation.RootNavigation
import ru.stohastika.sklyanka.navigation.RootTab
import ru.stohastika.sklyanka.navigation.UndoReturn

/**
 * Экранные шаги приёмки возврата на «Сегодня» под живым окном отмены (SKL-43).
 *
 * Прогон идёт в `app`, потому что сценарию нужны и корень приложения, и «Сегодня»,
 * и карточка напоминания: `RootNavigation` живёт в `app`, «Сегодня» — в `feature-today`,
 * карточка — в `feature-reminder` (`SKL-26/TDD.md §4.3`). Состояние корня — экземпляр
 * сценария: Cucumber создаёт класс шагов на каждый сценарий, и переходы одного сценария
 * не протекают в следующий.
 *
 * Карточку, отметку и показ снэкбара дают существующие шаги и стенд SKL-30
 * (`UndoCompositionSteps`, `UndoAppProbe`); здесь — только шаги маршрута. Повтора
 * правила возврата в шагах нет: зовётся тот же [UndoReturn], что из `MainActivity.onResume`.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class ReturnToTodaySteps(private val world: AcceptanceWorld) {

    private val navigation = RootNavigation()

    // --- Предыстория ----------------------------------------------------------------------

    @Допустим("^приложение оставлено на экране «(Лекарства|Настройки)»$")
    fun theAppIsLeftOnTheScreen(screen: String) {
        when (screen) {
            "Лекарства" -> navigation.openMeds(MedsDestination.List)
            "Настройки" -> navigation.openTab(RootTab.SETTINGS)
        }
        assertFalse("the app must not be on Today yet", navigation.showsToday)
    }

    // --- Действия -------------------------------------------------------------------------

    @Когда("^(.+?) в (\\d{2}:\\d{2}(?::\\d{2})?) человек возвращается в приложение$")
    fun thePersonComesBackToTheApp(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        // Жизненный цикл Activity стенд не воспроизводит: роль «человек оказался
        // в приложении» играет та же точка, которую зовёт `MainActivity.onResume` (SKL-43).
        UndoReturn.onAppeared(world.undoActions, navigation)
    }

    // --- Проверки -------------------------------------------------------------------------

    @Тогда("^человек видит «Сегодня»$")
    fun thePersonSeesToday() {
        assertTrue("the root must open Today", navigation.showsToday)
    }

    @Тогда("^человек остаётся на экране «(Лекарства|Настройки)»$")
    fun thePersonStaysOnTheScreen(screen: String) {
        when (screen) {
            "Лекарства" -> assertEquals(MedsDestination.List, navigation.meds)
            "Настройки" -> {
                assertEquals(RootTab.SETTINGS, navigation.tab)
                assertNull(navigation.section)
            }
        }
        assertFalse("the root must not switch to Today", navigation.showsToday)
    }
}
