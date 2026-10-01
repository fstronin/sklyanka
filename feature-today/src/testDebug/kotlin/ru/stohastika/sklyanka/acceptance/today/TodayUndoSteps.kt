package ru.stohastika.sklyanka.acceptance.today

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.feature.today.R
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_BAR
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import java.time.LocalTime
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки снэкбара отмены на «Сегодня» (SKL-30, группа `@screen`).
 *
 * Шаги живут в `feature-today`, потому что говорят о собранном экране «Сегодня» и его
 * ресурсах (`SKL-26/TDD.md §4.3`). Нажатия идут по композиции, а намерения применяет
 * к миру [TodayScreenProbe]: окно отмены публикуется тем же предикатом `isCompleted`,
 * что и в `TodayViewModel`.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class TodayUndoSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // --- Предыстория ----------------------------------------------------------------------

    // «у профиля «П» есть приём «Л» на …, …» объявлен в ProfileMarkingSteps:
    // та же строка стоит в сценариях изоляции профилей.

    @Допустим("^на (.+?), (\\d{2}:\\d{2}) ожидается (\\d+) приём\\S*$")
    fun thatManyIntakesAreExpected(date: String, time: String, count: Int) {
        val moment = world.localMoment(date, time)
        repeat(count) { index ->
            val name = "Одновременное ${index + 1}"
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = name,
                times = listOf(moment.toLocalTime()),
            )
            world.intakes.pending(name, moment)
        }
    }

    @Допустим("^у профиля «([^»]+)» есть лекарство «([^»]+)» по потребности, не чаще (\\d+) раз в сутки$")
    fun theProfileHasAnOnDemandMedication(profile: String, medication: String, limit: Int) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            mode = ScheduleMode.PRN,
            maxPerDay = limit,
        )
    }

    // «сегодня «Л» ещё не принимали» объявлен в TodayPrnSteps: та же строка стоит
    // в сценариях приёма «по потребности» SKL-08.

    @Допустим("^человек отметил приём «([^»]+)» принятым — показан снэкбар$")
    fun thePersonMarkedItTakenAndTheSnackbarIsShown(medication: String) {
        // Сценарий говорит о замене снэкбара, а не о вёрстке карточки: отметка идёт
        // тем же намерением экрана, что у нажатия, но без поиска карточки по имени —
        // приёмы одного времени показаны строками по одной (ОВ-32).
        val intake = requireNotNull(
            world.intakes.pendingOf(world.medicationNamed(medication))
                .firstOrNull { it.scheduledAt.toLocalDate() == world.clock.today() },
        ) { "no pending intake of $medication today" }
        TodayScreenProbe.apply(world, TodayIntent.MarkTaken(intake.id))
        assertSnackbarPresent()
    }

    @Допустим("^человек нажал «(Принято|Пропустить)» в карточке «([^»]+)»$")
    fun thePersonPressedTheButtonInTheCard(button: String, medication: String) {
        // Нажатие было раньше проверок: сценарий структуры смотрит только на срок
        // жизни снэкбара, а не на то, что он появился.
        pressInCard(medication, if (button == "Принято") UiR.string.action_taken else UiR.string.action_skip)
    }

    @Допустим("^на телефоне включено чтение с экрана$")
    fun theScreenReaderIsOn() {
        // Чтение с экрана — свойство среды, а не продукта: проверяется семантика
        // собранного снэкбара, а не наличие службы доступности на стенде.
    }

    // --- Действия --------------------------------------------------------------------------

    @Когда("^человек отмечает все приёмы времени «(\\d{2}:\\d{2})» принятыми одним действием$")
    fun thePersonMarksAllIntakesOfThatTime(time: String) {
        val ids = world.intakes.ofProfile(world.profiles, world.profiles.active)
            .filter { it.isPending && it.scheduledAt.toLocalTime() == LocalTime.parse(time) }
            .map { it.id }
        assertTrue("the scenario needs more than one pending intake at $time", ids.size > 1)
        TodayScreenProbe.apply(world, TodayIntent.MarkGroupTaken(ids))
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}(?::\\d{2})?) (?:человек )?нажимает «Отменить»(?: в снэкбаре)?$")
    fun thePersonPressesUndoInTheSnackbar(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        pressUndo()
    }

    // Вариант без момента: SKL-31 говорит «нажимает „Отменить“ в снэкбаре», и часы
    // уже стоят там, где нужно сценарию (TDD §14.3). Отдельным объявлением, а не
    // необязательной группой: Cucumber считает аргументы по группам захвата.
    @Когда("^(?:человек )?нажимает «Отменить» в снэкбаре$")
    fun thePersonPressesUndoInTheSnackbarWithoutMoment() = pressUndo()

    @Когда("^(.+?) в (\\d{2}:\\d{2}(?::\\d{2})?) человек отмечает приём «([^»]+)» пропущенным$")
    fun thePersonMarksThatIntakeSkipped(date: String, time: String, medication: String) {
        world.clock.moveTo(world.localMoment(date, time))
        val intake = requireNotNull(
            world.intakes.pendingOf(world.medicationNamed(medication))
                .firstOrNull { it.scheduledAt.toLocalDate() == world.clock.today() },
        ) { "no pending intake of $medication" }
        // Отметка идёт намерением экрана: приёмы одного времени показаны строками
        // по одной, и сценарий говорит о снэкбаре, а не о вёрстке группы.
        TodayScreenProbe.apply(world, TodayIntent.MarkSkipped(intake.id))
        assertEquals("intake of $medication", IntakeStatus.SKIPPED, world.intakes.get(intake.id)?.status)
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}:\\d{2}) тот же приём отметили принятым ещё раз с часов$")
    fun theSameIntakeIsMarkedFromTheWatch(date: String, time: String) {
        world.rememberState()
        val moment = world.localMoment(date, time)
        val intake = world.intakes.currentOrScheduled()
        world.clock.moveTo(moment)
        // Нажатие с часов идёт доменным путём, без стенда «Сегодня»: повторная отметка
        // отброшена, и окно отмены не перезапускается.
        world.mark(intake.id, MarkResult.TAKEN, IntakeSource.WATCH, world.clock.momentOf(moment))
    }

    // «человек нажимает «Принять» в карточке «Л»» объявлен в TodayPrnScreenSteps:
    // карточка «по потребности» ищется по своему тегу, а не по тегу карточки приёма.

    // --- Проверки: снэкбар ------------------------------------------------------------------

    @Тогда("^поверх содержимого показывается снэкбар «([^»]+)»$")
    fun theSnackbarAppearsOverTheContent(text: String) = assertSnackbarText(text)

    @Тогда("^показывается снэкбар «([^»]+)»$")
    fun theSnackbarIsShown(text: String) = assertSnackbarText(text)

    @Тогда("^снэкбар показывает «([^»]+)»$")
    fun theSnackbarShows(text: String) = assertSnackbarText(text)

    @Тогда("^снэкбар показывает прежний текст «([^»]+)»$")
    fun theSnackbarKeepsShowing(text: String) = assertSnackbarText(text)

    @И("^в нём есть действие «([^»]+)»$")
    fun theSnackbarOffersTheAction(action: String) {
        assertTrue(
            "the snackbar must offer «$action»: ${snackbarTexts()}",
            action in snackbarTexts(),
        )
    }

    @И("^снэкбар стоит над нижней панелью, не закрывая её$")
    fun theSnackbarStandsAboveTheBottomBar() {
        TodayScreenProbe.ask(world) { test ->
            val snackbar = test.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).fetchSemanticsNode()
            val bar = test.onNodeWithTag(TAG_BOTTOM_BAR).fetchSemanticsNode()
            assertTrue(
                "the snackbar must stand above the bar: ${snackbar.boundsInRoot.bottom} > ${bar.boundsInRoot.top}",
                snackbar.boundsInRoot.bottom <= bar.boundsInRoot.top,
            )
        }
    }

    @И("^прежнего текста про «([^»]+)» в снэкбаре нет$")
    fun theOldSnackbarTextIsGone(medication: String) {
        val modelName = TodayScreenProbe.displayName(world, medication)
        val texts = snackbarTexts()
        assertTrue("the old text about $modelName is still shown: $texts", texts.none { it.contains(modelName) })
    }

    @Тогда("^снэкбара на экране нет$")
    fun thereIsNoSnackbar() = assertSnackbarAbsent()

    // «снэкбар исчезает» и «снэкбар ещё показан» — одна строка структуры сценария:
    // два объявления на одну строку сделали бы шаг неоднозначным.
    @Тогда("^снэкбар (ещё показан|исчезает)$")
    fun theSnackbarIsStillShownOrGone(state: String) {
        if (state == STILL_SHOWN) assertSnackbarPresent() else assertSnackbarAbsent()
    }

    @Тогда("^снэкбара с действием «Отменить» не появляется$")
    fun noUndoSnackbarAppears() = assertSnackbarAbsent()

    @И("^в карточке «([^»]+)» нет действия «Отменить»$")
    fun theCardOffersNoUndo(medication: String) {
        TodayScreenProbe.ask(world) { test ->
            test.onAllNodes(
                hasText(context.getString(R.string.today_undo_action)) and
                    TodayScreenProbe.insideCard(world, medication),
                useUnmergedTree = true,
            ).assertCountEquals(0)
        }
    }

    @Тогда("^приём «([^»]+)» снова ожидается с кнопками «([^»]+)» и «([^»]+)»$")
    fun theIntakeIsPendingWithItsButtons(medication: String, first: String, second: String) {
        val name = world.medicationNamed(medication)
        assertEquals("intake of $medication", IntakeStatus.PENDING, world.intakes.of(name).single().status)
        TodayScreenProbe.ask(world) { test ->
            listOf(first, second).forEach { label ->
                test.onNode(
                    hasText(label) and TodayScreenProbe.insideCard(world, medication),
                    useUnmergedTree = true,
                ).assertIsDisplayed()
            }
        }
    }

    // --- Проверки: приём «по потребности» ---------------------------------------------------

    @Тогда("^появляется принятый приём «([^»]+)»$")
    fun aTakenOnDemandIntakeAppears(medication: String) {
        val outcome = world.intakes.lastPrnOutcome
        assertTrue("expected a new on-demand intake, got $outcome", outcome is PrnOutcome.Created)
        assertEquals(IntakeStatus.TAKEN, (outcome as PrnOutcome.Created).intake.status)
        assertEquals(world.medications.get(world.medicationNamed(medication)).id, outcome.intake.medicationId)
    }

    // --- Проверки: доступность --------------------------------------------------------------

    @Тогда("^программа чтения объявляет текст «([^»]+)»$")
    fun theScreenReaderAnnouncesTheText(text: String) {
        val expected = screenText(text)
        TodayScreenProbe.ask(world) { test ->
            val node = test.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).fetchSemanticsNode()
            // Сообщение временное и фокус чтения не переводится: объявляется Polite
            // (08-design-system.md §4.12).
            assertEquals(LiveRegionMode.Polite, node.config.getOrNull(SemanticsProperties.LiveRegion))
            assertTrue(
                "the snackbar must carry «$expected»: ${TodayScreenProbe.textsOf(node)}",
                expected in TodayScreenProbe.textsOf(node),
            )
        }
    }

    @И("^объявляет действие «([^»]+)»$")
    fun theScreenReaderAnnouncesTheAction(action: String) {
        assertTrue("the announced action must be «$action»: ${snackbarTexts()}", action in snackbarTexts())
    }

    // --- Обвязка ---------------------------------------------------------------------------

    /** «Отменить» в снэкбаре: экран отдаёт намерение, стенд применяет его к миру. */
    private fun pressUndo() {
        TodayScreenProbe.act(world) { test ->
            test.onNodeWithText(context.getString(R.string.today_undo_action), useUnmergedTree = true).performClick()
        }
    }

    /** Нажатие кнопки в карточке: экран отдаёт намерение, стенд применяет его к миру. */
    private fun pressInCard(medication: String, label: Int) {
        TodayScreenProbe.act(world) { test ->
            test.onNode(
                hasText(context.getString(label)) and TodayScreenProbe.insideCard(world, medication),
                useUnmergedTree = true,
            ).performClick()
        }
    }

    private fun assertSnackbarText(text: String) {
        val expected = screenText(text)
        TodayScreenProbe.ask(world) { test ->
            val texts = TodayScreenProbe.textsOf(
                test.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).fetchSemanticsNode(),
            )
            assertTrue("the snackbar must read «$expected»: $texts", expected in texts)
        }
    }

    private fun assertSnackbarPresent() {
        TodayScreenProbe.ask(world) { test ->
            // Отсутствующий узел здесь — отказ: fetchSemanticsNode его и называет.
            test.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).fetchSemanticsNode()
        }
    }

    private fun assertSnackbarAbsent() {
        TodayScreenProbe.ask(world) { test ->
            test.onAllNodesWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    private fun snackbarTexts(): List<String> = TodayScreenProbe.textsOnScreen(world, TAG_SNACKBAR)

    /**
     * Текст снэкбара в терминах экрана: сценарий называет лекарство по-русски, а модель —
     * `med-N` (НФТ-19), и подстановка идёт той же функцией разрешения падежей, что
     * у остальных шагов: «Эутирокс» в проверке и «Эутирокса» в Предыстории — одно лекарство.
     */
    private fun screenText(scenarioText: String): String {
        if (" — " !in scenarioText) return scenarioText
        val name = scenarioText.substringBefore(" — ")
        val resolved = runCatching { world.medicationNamed(name) }.getOrNull() ?: return scenarioText
        return scenarioText.replaceFirst(name, TodayScreenProbe.displayName(world, resolved))
    }

    private companion object {
        const val STILL_SHOWN = "ещё показан"
    }
}
