package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.MedicationListRules
import ru.stohastika.sklyanka.domain.medication.doseUnit
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.feature.meds.card.MedicationCardScreen
import ru.stohastika.sklyanka.feature.meds.card.TAG_REFILL
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_BAR
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_VALUE
import ru.stohastika.sklyanka.feature.meds.list.MedicationListScreen
import ru.stohastika.sklyanka.feature.meds.list.TAG_LAST_INTAKE
import ru.stohastika.sklyanka.feature.meds.list.TAG_TRAILING_LINE
import java.math.BigDecimal
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme as AppTheme

/**
 * Экранные шаги приёмки вёрстки карточек лекарств при нехватке ширины (SKL-59, `@screen`).
 *
 * Список собирает [MedsListStand], карточку — [MedCardStand]: состояние считается доменом,
 * а проверяются слова и строки собранной композиции. Системный шрифт задаётся подменой
 * `LocalDensity` — тем же приёмом, что в `Sk93DenseScreenSteps` и `LargeFontTest`;
 * свойство «слово не разорвано» читается по `TextLayoutResult`, а не по копии подсчёта.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class MedsWidthScreenSteps(private val world: AcceptanceWorld) {

    /** Системный шрифт сценария: 100 % по умолчанию, как на телефоне. */
    private var scale: Float = 1f

    /** Лекарство, о котором говорит сценарий. */
    private var focused: String = ""

    // --- Предыстория ---------------------------------------------------------------

    @Допустим("^курс идёт с (.+?) бессрочно$")
    fun theCourseIsOpenEndedSince(date: String) {
        val name = focusedOrLast()
        world.medications.update(name) {
            it.copy(startDate = world.dateOf(date), endDate = null)
        }
    }

    @Допустим("^у профиля «([^»]+)» есть лекарство «([^»]+)» с одним приёмом$")
    fun theProfileHasAMedicationWithOneIntake(profile: String, medication: String) {
        add(profile, medication, times = listOf(MORNING))
    }

    @Допустим("^сработал порог остатка: осталось (\\d+) таблет\\S+$")
    fun theStockThresholdHasFired(units: Int) {
        val name = focusedOrLast()
        world.medications.update(name) {
            it.copy(stockLeft = BigDecimal(units), stockThresholdDays = PREMISE_THRESHOLD_DAYS)
        }
        assertTrue(
            "the premise must make the threshold fire",
            MedicationListRules.stockThresholdReached(world.medications.get(name)),
        )
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
        focused = world.medicationNamed(medication)
    }

    @Допустим("^последний приём был (.+)$")
    fun theLastIntakeWasOn(date: String) {
        val name = focusedOrLast()
        val card = world.medications.get(name)
        val day = world.dateOf(date)
        // Приём раньше начала курса — не данность продукта, а данность сценария:
        // срок курса «по потребности» отодвигается, иначе история приёма не попадёт
        // в окно, которое читает список (ЭКР-5).
        world.medications.update(name) { it.copy(startDate = day) }
        world.intakes.store(
            Intake(
                id = world.intakes.nextId(),
                medicationId = card.id,
                scheduledAt = day.atTime(LAST_INTAKE_TIME),
                status = IntakeStatus.TAKEN,
                actedAt = world.clock.momentOf(day.atTime(LAST_INTAKE_TIME)),
                source = IntakeSource.PHONE,
                actorProfileId = card.profileId,
            ),
        )
    }

    @Допустим("^у профиля «([^»]+)» есть лекарство «([^»]+)», капсулы, размер упаковки (\\d+)$")
    fun theProfileHasACapsuleMedicationWithThatPackage(profile: String, medication: String, size: Int) {
        addCapsules(profile, medication)
        world.medications.update(world.medicationNamed(medication)) {
            it.copy(packageSize = BigDecimal(size))
        }
    }

    @Допустим("^у профиля «([^»]+)» есть лекарство «([^»]+)», капсулы, остаток (\\d+) капсул$")
    fun theProfileHasACapsuleMedicationWithThatStock(profile: String, medication: String, units: Int) {
        addCapsules(profile, medication)
        world.medications.update(world.medicationNamed(medication)) {
            it.copy(packageSize = BigDecimal(units), stockLeft = BigDecimal(units))
        }
    }

    /**
     * Остаток без имени: сценарий говорит об остатке лекарства, открытого последним.
     *
     * Строка стоит в Предыстории, поэтому она задаёт значение, а не проверяет его.
     */
    @Допустим("^остаток упаковки — (\\d+) (?:таблет\\S+|капсул\\S*)$")
    fun theStockOfTheFocusedMedication(units: Int) {
        world.medications.update(focusedOrLast()) { it.copy(stockLeft = BigDecimal(units)) }
    }

    @Допустим("^системный шрифт — (\\d+) %$")
    fun theSystemFontIs(percent: Int) {
        scale = percent / PERCENT
    }

    // --- Проверки: список ------------------------------------------------------------

    @Тогда("^в карточке «([^»]+)» видны все три чипа времени$")
    fun allThreeTimesAreVisibleInTheCard(medication: String) {
        assertEquals("the scenario must use three intakes", 3, timesOf(medication).size)
        list { test -> assertTimesInTrailingLine(test, medication) }
    }

    @Тогда("^все три чипа времени на месте$")
    fun allThreeTimesAreStillThere() {
        list { test -> assertTimesInTrailingLine(test, displayName()) }
    }

    @Тогда("^подпись «(.+)» читается целыми словами$")
    fun theCaptionReadsAsWholeWords(caption: String) {
        list { test ->
            val node = trailingLine(test, displayName())
            val text = MedsProbe.textsOf(node).single()
            assertTrue(
                "the caption must read «$caption»: $text",
                text.replace(NBSP, ' ').contains(caption.replace(NBSP, ' ')),
            )
            assertWordsIntact(node, text)
        }
    }

    @Но("^ни одна буква подписи не стоит в отдельной строке$")
    fun noLetterOfTheCaptionStandsAlone() {
        list { test ->
            val node = trailingLine(test, displayName())
            assertWordsIntact(node, MedsProbe.textsOf(node).single())
        }
    }

    @Тогда("^подпись периода читается целыми словами$")
    fun thePeriodCaptionReadsAsWholeWords() {
        list { test ->
            val node = trailingLine(test, displayName())
            assertWordsIntact(node, MedsProbe.textsOf(node).single())
        }
    }

    @Тогда("^карточка показывает «(.+)» читаемым текстом$")
    fun theCardShowsThatTextReadably(expected: String) {
        list { test ->
            val node = trailingLine(test, displayName())
            val text = MedsProbe.textsOf(node).single()
            assertTrue(
                "the card must show «$expected»: $text",
                text.replace(NBSP, ' ').endsWith(expected.replace(NBSP, ' ')),
            )
            assertWordsIntact(node, text)
        }
    }

    @Тогда("^в карточке «([^»]+)» видны чип «([^»]+)», ограничение и строка последнего приёма$")
    fun theOnDemandCardHoldsItsRows(medication: String, chip: String) {
        list { test ->
            val card = test.onNode(MedsListStand.card(world, medication), useUnmergedTree = true)
                .fetchSemanticsNode()
            val texts = MedsProbe.textsOf(card)
            assertTrue("the card must carry the chip «$chip»: $texts", chip in texts)
            assertTrue(
                "the card must carry the limit: $texts",
                texts.any { NO_MORE_THAN.containsMatchIn(it) },
            )
            assertTrue(
                "the card must carry the last intake line: $texts",
                test.onAllNodesWithTag(TAG_LAST_INTAKE, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Тогда("^кнопка «([^»]+)» на месте$")
    fun theButtonIsThere(label: String) {
        list { test ->
            val card = test.onNode(MedsListStand.card(world, displayName()), useUnmergedTree = true)
                .fetchSemanticsNode()
            assertTrue(
                "the card must offer «$label»: ${MedsProbe.textsOf(card)}",
                label in MedsProbe.textsOf(card),
            )
        }
    }

    // --- Проверки: блок остатка карточки ----------------------------------------------

    @Тогда("^в блоке «Остаток» написано «(.+)» одной строкой$")
    fun theStockBlockReadsOnOneLine(expected: String) {
        card { test ->
            val node = stockValue(test)
            val text = MedsProbe.textsOf(node).single()
            assertEquals(
                "the stock block must read «$expected»",
                expected.replace(NBSP, ' '),
                text.replace(NBSP, ' '),
            )
            assertFigureKeepsItsWord(node, text)
        }
    }

    @И("^слово «(.+)» не переносится на отдельную строку$")
    fun thatWordIsNotMovedToItsOwnLine(word: String) {
        card { test ->
            val node = stockValue(test)
            val text = MedsProbe.textsOf(node).single()
            assertTrue("the word «$word» must stay with the value: $text", word in text)
            assertFigureKeepsItsWord(node, text)
        }
    }

    @Тогда("^«(.+)» читается одной строкой$")
    fun thatTextReadsOnOneLine(expected: String) {
        card { test ->
            val node = stockValue(test)
            val text = MedsProbe.textsOf(node).single()
            assertEquals(
                "the stock block must read «$expected»",
                expected.replace(NBSP, ' '),
                text.replace(NBSP, ' '),
            )
            assertFigureKeepsItsWord(node, text)
        }
    }

    @И("^кнопка «(.+)» расположена ниже значения, во всю ширину$")
    fun theButtonStandsUnderTheValueFullWidth(label: String) {
        card { test ->
            val value = stockValue(test)
            val button = test.onNodeWithTag(TAG_REFILL, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                "«$label» must stand under the value: ${button.boundsInRoot} after ${value.boundsInRoot}",
                button.boundsInRoot.top >= value.boundsInRoot.bottom,
            )
            assertEquals(
                "«$label» must take the full width",
                value.boundsInRoot.width,
                button.boundsInRoot.width,
                WIDTH_TOLERANCE,
            )
        }
    }

    @Тогда("^полоса остатка показана$")
    fun theStockBarIsShown() {
        card { test -> test.onNodeWithTag(TAG_STOCK_BAR, useUnmergedTree = true).assertExists() }
    }

    // --- Обвязка --------------------------------------------------------------------

    private fun add(profile: String, medication: String, times: List<LocalTime>) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            times = times,
        )
        focused = world.medicationNamed(medication)
    }

    private fun addCapsules(profile: String, medication: String) {
        add(profile, medication, listOf(MORNING))
        world.medications.update(focused) {
            it.copy(form = MedicationForm.CAPSULE, doseUnit = MedicationForm.CAPSULE.doseUnit().code)
        }
    }

    private fun focusedOrLast(): String = focused.ifEmpty { world.medications.last }

    private fun displayName(): String = focusedOrLast()

    private fun timesOf(medication: String): List<LocalTime> =
        world.medications.get(world.medicationNamed(medication)).times.sorted()

    private fun list(question: (ComposeUiTest) -> Unit) = atScale {
        runComposeUiTest {
            setContent { AtFontScale { MedicationListScreen(state = MedsListStand.state(world), onIntent = {}) } }
            question(this)
        }
    }

    private fun card(question: (ComposeUiTest) -> Unit) = atScale {
        runComposeUiTest {
            setContent {
                AtFontScale {
                    MedicationCardScreen(state = MedCardStand.state(world, displayName()), onIntent = {})
                }
            }
            question(this)
        }
    }

    private fun atScale(action: () -> Unit) {
        val previous = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(scale)
        try {
            action()
        } finally {
            RuntimeEnvironment.setFontScale(previous)
        }
    }

    @Composable
    private fun AtFontScale(content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = scale)) {
            AppTheme { content() }
        }
    }

    private fun trailingLine(test: ComposeUiTest, medication: String): SemanticsNode = test.onNode(
        hasTestTag(TAG_TRAILING_LINE) and hasAnyAncestor(MedsListStand.card(world, medication)),
        useUnmergedTree = true,
    ).fetchSemanticsNode()

    private fun assertTimesInTrailingLine(test: ComposeUiTest, medication: String) {
        val node = trailingLine(test, medication)
        val text = MedsProbe.textsOf(node).single()
        timesOf(medication).forEach { time ->
            val shown = time.format(TIME_FORMAT)
            assertTrue("the card must show $shown: $text", shown in text)
        }
    }

    private fun stockValue(test: ComposeUiTest): SemanticsNode =
        test.onNodeWithTag(TAG_STOCK_VALUE, useUnmergedTree = true).fetchSemanticsNode()

    /** Ни одно слово не разорвано по строкам: каждая строка целиком содержит своё слово. */
    private fun assertWordsIntact(node: SemanticsNode, text: String) {
        val lines = textLines(node, text)
        text.split(' ', NBSP).filter { it.isNotBlank() && it != SEPARATOR }.forEach { word ->
            assertTrue("the word «$word» is broken across lines: $lines", lines.any { word in it })
        }
    }

    /**
     * Число остатка не разрывается со своей единицей (SKL-59).
     *
     * Перенос перед числом («осталось / 60 капсул») нарушением не считается — ровно как
     * в `MedicationCardScreenTest.theStockUnitNeverStartsALineAfterTheNumber`; нарушение —
     * строка, кончающаяся числом, за которой строка начинается буквой.
     */
    private fun assertFigureKeepsItsWord(node: SemanticsNode, text: String) {
        val lines = textLines(node, text)
        val split = lines.zipWithNext().any { (first, second) ->
            first.trimEnd().lastOrNull()?.isDigit() == true && second.trimStart().firstOrNull()?.isLetter() == true
        }
        assertFalse("the stock is broken between number and unit: $lines", split)
        assertTrue("the stock did not fit even in two lines: $lines", lines.size <= 2)
    }

    private fun textLines(node: SemanticsNode, text: String): List<String> {
        val layout = requireNotNull(node.textLayout()) { "the text node has no layout result" }
        return (0 until layout.lineCount).map { index ->
            text.substring(layout.getLineStart(index), layout.getLineEnd(index))
        }
    }

    private fun SemanticsNode.textLayout(): TextLayoutResult? {
        val results = mutableListOf<TextLayoutResult>()
        config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.firstOrNull()
    }

    private companion object {
        const val PERCENT = 100f
        const val WIDTH_TOLERANCE = 0.5f
        const val NBSP = '\u00A0'
        const val SEPARATOR = "·"

        /** Порог Предыстории: заведомо больше запаса на 9 дней, чтобы порог сработал. */
        const val PREMISE_THRESHOLD_DAYS = 14

        val MORNING: LocalTime = LocalTime.of(8, 0)
        val LAST_INTAKE_TIME: LocalTime = LocalTime.of(10, 0)
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** «не чаще 3 раз в сутки» — ограничение в описании карточки «по потребности». */
        val NO_MORE_THAN = Regex("не чаще \\d+ раз в сутки")
    }
}
