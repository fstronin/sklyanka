package ru.stohastika.sklyanka.acceptance.today

import androidx.compose.ui.test.ExperimentalTestApi
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.today.IntakeItem
import ru.stohastika.sklyanka.feature.today.TAG_DAY_PROGRESS
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.ui.component.TAG_SCREEN_HEADER

/**
 * Экранные шаги приёмки границы суток (SKL-08, группа `@midnight @time`; заведены
 * SKL-26).
 *
 * Экран открыт один раз, дальше меняется только момент: `Сегодня` берёт сутки из
 * состояния, а состояние — из часов сценария ([TodayStand]). Пересборки на новом
 * моменте и есть модель того, что на устройстве делает тик `TodayViewModel`: сам тик
 * за пределами стенда, и это остаётся работой устройства.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TodayMidnightScreenSteps(private val world: AcceptanceWorld) {

    /** Лента, о которой говорил предыдущий шаг: «у группы стоит метка…». */
    private var lastLaneTime: String? = null

    // --- Предыстория и действия ------------------------------------------------

    @Допустим("^человек открыл экран «Сегодня» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonHasOpenedToday(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        TodayScreenProbe.ask(world) { }
    }

    // --- Проверки: состав суток ------------------------------------------------

    @Допустим("^в списке показаны приёмы (.+)$")
    fun theListShowsTheIntakesOf(date: String) {
        val day = world.dateOf(date)
        val dates = shownItems().map { it.intake.scheduledAt.toLocalDate() }.toSet()
        assertEquals("the list of «$date» must show exactly its own intakes", setOf(day), dates)
    }

    @Тогда("^в шапке показана дата «([^»]+)»$")
    fun theHeaderShowsTheDate(date: String) {
        TodayScreenProbe.ask(world) { test ->
            val header = TodayScreenProbe.texts(test, TAG_SCREEN_HEADER)
            assertTrue("no date «$date» in the header: $header", header.any { it.equals(date, ignoreCase = true) })
        }
    }

    @Тогда("^подпись прогресса дня показывает «([^»]+)»$")
    fun theDayProgressShows(caption: String) {
        TodayScreenProbe.ask(world) { test ->
            val shown = TodayScreenProbe.texts(test, TAG_DAY_PROGRESS)
            assertTrue("the day progress must read «$caption»: $shown", caption in shown)
        }
    }

    @Тогда("^приложение не перезапускается$")
    fun theApplicationIsNotRestarted() {
        // Стенд держит один мир на сценарий и пересобирает состояние из него же: между
        // «23:58» и «00:00» шага «открыть экран» нет, и сутки на экране сменились сами.
        // Чего стенд не проверяет — тика, который на устройстве будит `TodayViewModel`
        // (`TDD.md §10`): это часть продукта вне стенда и остаётся работой устройства.
        val state = TodayStand.state(world)
        assertTrue(
            "the open screen must follow the clock, not cold-start: $state",
            state is TodayUiState.Content && state.date == world.clock.today(),
        )
    }

    // --- Проверки: состав списка -----------------------------------------------

    @Тогда("^приёма «([^»]+)» за (.+?) в списке нет$")
    fun theIntakeOfThatDateIsGone(medication: String, date: String) {
        val id = world.medications.get(world.medicationNamed(medication)).id
        val day = world.dateOf(date)
        assertTrue(
            "the intake of «$medication» on $date must be gone",
            shownItems().none { it.medicationId == id && it.intake.scheduledAt.toLocalDate() == day },
        )
    }

    @Тогда("^показан приём «([^»]+)» на (.+?), (\\d{2}:\\d{2}) в состоянии ожидания$")
    fun theIntakeIsShownPending(medication: String, date: String, time: String) {
        val name = world.medicationNamed(medication)
        val moment = world.localMoment(date, time)
        val item = shownItems().firstOrNull {
            it.medicationId == world.medications.get(name).id && it.intake.scheduledAt == moment
        }
        assertTrue("the intake of «$medication» at $moment must be on the screen", item != null)
        assertEquals("the shown intake must be pending", IntakeStatus.PENDING, item!!.intake.status)
    }

    @Тогда("^приём «([^»]+)» на (.+?), (\\d{2}:\\d{2}) показан в группе «(\\d{2}:\\d{2})»$")
    fun theIntakeIsShownInThatLane(medication: String, date: String, time: String, group: String) {
        // Приём сценария существует и назначен на названное время, а на экране он стоит
        // под своей лентой — проверяются обе половины строки.
        world.intakes.at(world.medicationNamed(medication), world.localMoment(date, time))
        lastLaneTime = group
        TodayScreenProbe.ask(world) { test -> TodayScreenProbe.assertCardInLane(world, test, medication, group) }
    }

    @Тогда("^у группы стоит метка «([^»]+)»$")
    fun theGroupCarriesThatLabel(label: String) {
        val time = requireNotNull(lastLaneTime) { "the scenario never named the group" }
        TodayScreenProbe.ask(world) { test ->
            assertEquals("the lane $time must carry «$label»", label, TodayScreenProbe.laneLabelOf(test, time))
        }
    }

    // --- Обвязка ---------------------------------------------------------------

    /** Приёмы, которые экран показывает сейчас: из той же повестки, что рисует разметка. */
    private fun shownItems(): List<IntakeItem> = TodayStand.agenda(world).layout.groups.flatMap { it.items }
}
