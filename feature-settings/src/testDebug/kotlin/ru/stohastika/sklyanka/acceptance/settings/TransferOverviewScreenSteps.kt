package ru.stohastika.sklyanka.acceptance.settings

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.TransferFileBuilder
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.transfer.TransferMode
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.transfer.DataTransferScreen
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_MODE_MERGE
import ru.stohastika.sklyanka.feature.settings.transfer.TAG_MODE_REPLACE
import ru.stohastika.sklyanka.feature.settings.transfer.TransferIntent
import ru.stohastika.sklyanka.feature.settings.transfer.TransferStep
import ru.stohastika.sklyanka.ui.component.TAG_CHIP
import ru.stohastika.sklyanka.ui.component.TAG_SCREEN_HEADER
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDate
import java.time.LocalTime
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки состава, чипов и шапки «Переноса данных» (SKL-60, `@screen`).
 *
 * Состояние экрана собирает [TransferScreenProbe] — настоящий `DataTransferViewModel`
 * на мире сценария; шаги только нажимают и читают. Свежесть состава проверяется
 * маршрутом: шаг «вернулся в настройки» шлёт тот же `Leave`, что `DisposableEffect`
 * маршрута, а «снова открывает» — `Refresh`, который посылает `LaunchedEffect` появления.
 * Свойств здесь нет: перечитывание выполняет ViewModel.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class TransferOverviewScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private var scale: Float = 1f

    /** Лекарство, заведённое последним: им задаётся история приёмов. */
    private var focused: String = ""

    private var added: Int = 0

    // --- Предыстория: состав устройства -------------------------------------------------

    @Допустим("^на устройстве одно лекарство$")
    fun theDeviceHasOneMedication() {
        addMedication()
    }

    @Допустим("^на устройстве три активных лекарства и одно в архиве$")
    fun theDeviceHasThreeActiveAndOneArchived() {
        repeat(3) { addMedication() }
        addMedication(archived = true)
    }

    @Допустим("^на устройстве пять лекарств, двенадцать в архиве, два профиля$")
    fun theDeviceHasThatComposition() {
        repeat(5) { addMedication() }
        repeat(12) { addMedication(archived = true) }
        world.profiles.get(SECOND_PROFILE)
    }

    @Допустим("^история приёмов началась (.+)$")
    fun theHistoryStartsOn(date: String) {
        storeHistoryAt(world.dateOf(date))
    }

    @Допустим("^история приёмов — восемь месяцев$")
    fun theHistoryIsEightMonthsLong() {
        storeHistoryAt(world.clock.today().minusMonths(8))
    }

    @Допустим("^системный шрифт — (\\d+) %$")
    fun theSystemFontIs(percent: Int) {
        scale = percent / PERCENT
    }

    // --- Действия ------------------------------------------------------------------------

    @Допустим("^человек один раз открыл «Перенос данных» и вернулся в настройки$")
    fun thePersonOpenedAndLeftTheScreen() {
        // Открытие: ViewModel читает состав в init. Уход: маршрут шлёт Leave — сброс
        // начатой загрузки и перечитывание состава.
        TransferScreenProbe.model(world)
        TransferScreenProbe.onIntent(world, TransferIntent.Leave)
    }

    @Когда("^человек заводит ещё два лекарства$")
    fun thePersonAddsTwoMoreMedications() {
        repeat(2) { addMedication() }
    }

    @Когда("^человек открывает «Перенос данных»$")
    fun thePersonOpensTheScreen() {
        openOverview()
    }

    @Когда("^снова открывает «Перенос данных»$")
    fun thePersonOpensTheScreenAgain() {
        openOverview()
    }

    @Когда("^человек выбирает файл для загрузки$")
    fun thePersonChoosesAFile() {
        prepareFile()
        TransferScreenProbe.chooseFile(world)
    }

    // --- Проверки: чипы состава -----------------------------------------------------------

    @Тогда("^чип состава говорит «(.+)»$")
    fun theCompositionChipSays(chip: String) {
        assertTrue(
            "the screen must show the chip «$chip»",
            TransferScreenProbe.hasText(TransferScreenProbe.state(world), chip),
        )
    }

    @Тогда("^чипы говорят (.+)$")
    fun theChipsSay(chips: String) {
        val expected = quoted(chips)
        assertTrue("the scenario must name the chips", expected.isNotEmpty())
        expected.forEach { chip ->
            assertTrue(
                "the screen must show the chip «$chip»",
                TransferScreenProbe.hasText(TransferScreenProbe.state(world), chip),
            )
        }
    }

    @Тогда("^видны все пять чипов: (.+)$")
    fun allFiveChipsAreVisible(chips: String) {
        val expected = quoted(chips)
        assertEquals("the scenario must name five chips", 5, expected.size)
        expected.forEach { chip ->
            assertTrue(
                "the screen must show the chip «$chip»",
                TransferScreenProbe.hasText(TransferScreenProbe.state(world), chip),
            )
        }
        overview { test ->
            assertEquals(
                "the composition must hold exactly five chips",
                5,
                test.onAllNodesWithTag(TAG_CHIP, useUnmergedTree = true).fetchSemanticsNodes().size,
            )
        }
    }

    @Но("^ни один чип не выведен по одной букве в столбик$")
    fun noChipIsDrawnAsAColumnOfLetters() {
        overview { test -> assertChipsAreWhole(test) }
    }

    @Тогда("^каждый чип показан целиком, переносы — между чипами, а не внутри чипа$")
    fun everyChipIsShownWhole() {
        overview { test -> assertChipsAreWhole(test) }
    }

    // --- Проверки: шапка -------------------------------------------------------------------

    @Тогда("^подпись «(.+)» стоит строкой выше заголовка$")
    fun theBackLabelStandsAboveTheTitle(label: String) {
        overview { test ->
            val back = test.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            val title = test.onNodeWithText(context.getString(R.string.transfer_title)).fetchSemanticsNode()
            assertTrue(
                "«$label» must stand above the title: back=${back.boundsInRoot}, title=${title.boundsInRoot}",
                back.boundsInRoot.bottom <= title.boundsInRoot.top,
            )
        }
    }

    @Тогда("^заголовок «(.+)» начинается от края контента экрана$")
    fun theTitleStartsAtTheContentEdge(label: String) {
        overview { test ->
            // Край контента — левая граница самой шапки: она уже стоит на отступе экрана,
            // и заголовок обязан начинаться с неё, а не с места после кнопки.
            val header = test.onNodeWithTag(TAG_SCREEN_HEADER).fetchSemanticsNode()
            val title = test.onNodeWithText(label).fetchSemanticsNode()
            assertEquals(
                "the title must start at the content edge: header=${header.boundsInRoot}, title=${title.boundsInRoot}",
                header.boundsInRoot.left,
                title.boundsInRoot.left,
                EDGE_TOLERANCE,
            )
        }
    }

    @И("^у него нет висячего отступа от кнопки$")
    fun theTitleHasNoHangingIndentFromTheButton() {
        overview { test ->
            val back = test.onNodeWithText(context.getString(UiR.string.action_back)).fetchSemanticsNode()
            val title = test.onNodeWithText(context.getString(R.string.transfer_title)).fetchSemanticsNode()
            assertTrue(
                "the title must not hang after the button: title=${title.boundsInRoot}, back=${back.boundsInRoot}",
                title.boundsInRoot.left <= back.boundsInRoot.left,
            )
        }
    }

    // --- Проверки: порядок шагов -------------------------------------------------------------

    @Тогда("^он видит сводку файла$")
    fun thePersonSeesTheFileSummary() {
        val state = TransferScreenProbe.state(world)
        assertEquals("choosing a file must lead to the summary", TransferStep.SUMMARY, state.step)
        TransferScreenProbe.ask(world, state) {
            onNodeWithText(context.getString(R.string.transfer_summary_title)).assertExists()
        }
    }

    @И("^по-прежнему выбирает режим «Добавить к текущим» или «Заменить всё»$")
    fun thePersonStillPicksAMode() {
        // Порядок шагов ПЕР-14/ПЕР-15 не изменился: режим по-прежнему выбирается
        // на обзоре, а сводка — шаг между выбором файла и решением.
        val overview = TransferScreenProbe.state(world).copy(step = TransferStep.OVERVIEW)
        TransferScreenProbe.ask(world, overview) {
            onNodeWithTag(TAG_MODE_MERGE).assertExists()
            onNodeWithTag(TAG_MODE_REPLACE).assertExists()
        }
        TransferScreenProbe.selectMode(world, TransferMode.MERGE)
        assertEquals(
            "merge mode must still be selectable",
            TransferMode.MERGE,
            TransferScreenProbe.state(world).mode,
        )
        TransferScreenProbe.selectMode(world, TransferMode.REPLACE_ALL)
        assertEquals(
            "replace mode must still be selectable",
            TransferMode.REPLACE_ALL,
            TransferScreenProbe.state(world).mode,
        )
        // И выбранный режим ведёт прежним путём: «добавить к текущим» — через совпадения.
        TransferScreenProbe.selectMode(world, TransferMode.MERGE)
        TransferScreenProbe.onIntent(world, TransferIntent.ContinueFromSummary)
        assertEquals(
            "merge mode must still go through the matches step",
            TransferStep.MATCHES,
            TransferScreenProbe.state(world).step,
        )
    }

    // --- Обвязка ---------------------------------------------------------------------------

    private fun openOverview() {
        // Появление экрана перечитывает состав — то же намерение, что шлёт маршрут.
        TransferScreenProbe.onIntent(world, TransferIntent.Refresh)
        val state = TransferScreenProbe.state(world)
        assertEquals("the screen must open on the overview", TransferStep.OVERVIEW, state.step)
        TransferScreenProbe.ask(world, state) {
            onNodeWithText(context.getString(R.string.transfer_title)).assertExists()
        }
    }

    private fun addMedication(archived: Boolean = false) {
        val name = "med-${++added}"
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = name,
            times = listOf(MORNING),
        )
        focused = name
        if (archived) {
            world.medications.archive(name, at = world.clock.momentOf(world.clock.today().atTime(MORNING)))
        }
    }

    private fun storeHistoryAt(date: LocalDate) {
        val name = focused.ifEmpty { world.medications.last }
        val card = world.medications.get(name)
        world.intakes.store(
            Intake(
                id = world.intakes.nextId(),
                medicationId = card.id,
                scheduledAt = date.atTime(MORNING),
                status = IntakeStatus.TAKEN,
                actedAt = world.clock.momentOf(date.atTime(MORNING)),
                source = IntakeSource.PHONE,
                actorProfileId = card.profileId,
            ),
        )
    }

    /** Файл для выбора: непустой снимок, который настоящий кодек выложит в выбранное место. */
    private fun prepareFile() {
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        val medication = world.medications.all().firstOrNull()
        builder.medication(
            key = medication?.name ?: FILE_MEDICATION,
            profileKey = FILE_SELF,
            name = medication?.name ?: FILE_MEDICATION,
            times = listOf(MORNING),
            startDate = world.clock.today(),
        )
        world.transfer.prepare(builder.build(exportedAt = world.clock.now()))
    }

    private fun overview(question: (ComposeUiTest) -> Unit) {
        val state = TransferScreenProbe.state(world)
        val previous = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(scale)
        try {
            runComposeUiTest {
                setContent {
                    val base = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = base.density, fontScale = scale),
                    ) {
                        SklyankaTheme {
                            DataTransferScreen(
                                state = state,
                                onIntent = {},
                                onSave = {},
                                onPickFile = {},
                                onBack = {},
                            )
                        }
                    }
                }
                question(this)
            }
        } finally {
            RuntimeEnvironment.setFontScale(previous)
        }
    }

    /** Чип переносится целиком: подпись одной строкой и не обрезана (SKL-60, дефект пилота). */
    private fun assertChipsAreWhole(test: ComposeUiTest) {
        val chips = test.onAllNodesWithTag(TAG_CHIP, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("the composition must show its chips", chips.isNotEmpty())
        chips.forEach { chip ->
            val layouts = textLayoutsOf(chip)
            assertTrue("a chip without text: ${chip.boundsInRoot}", layouts.isNotEmpty())
            layouts.forEach { layout ->
                assertEquals("a chip must read as one whole label: ${chip.boundsInRoot}", 1, layout.lineCount)
            }
        }
    }

    private fun textLayoutsOf(node: SemanticsNode): List<TextLayoutResult> {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        node.children.forEach { child -> layouts += textLayoutsOf(child) }
        return layouts
    }

    private fun quoted(text: String): List<String> = QUOTED.findAll(text).map { it.groupValues[1] }.toList()

    private companion object {
        const val PERCENT = 100f
        const val EDGE_TOLERANCE = 1f
        const val FILE_SELF = "file-self"
        const val FILE_MEDICATION = "med-file"
        const val SECOND_PROFILE = "Мама"

        val MORNING: LocalTime = LocalTime.of(8, 0)
        val QUOTED = Regex("«([^»]+)»")
    }
}
