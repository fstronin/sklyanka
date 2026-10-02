package ru.stohastika.sklyanka.acceptance.steps.sk93

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.today.IntakeItem
import ru.stohastika.sklyanka.domain.today.IntakePresentation
import ru.stohastika.sklyanka.feature.today.DenseIntakeRow
import ru.stohastika.sklyanka.feature.today.TAG_DENSE_ROW_TIME
import ru.stohastika.sklyanka.feature.today.TAG_PRN_CARD
import ru.stohastika.sklyanka.feature.today.TodayFixtures
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.feature.today.denseTimeColumnWidth
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_GLYPH
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_PICTOGRAM
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Экранные шаги приёмки SKL-93: плотная строка и силуэт лекарства на «Сегодня».
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class Sk93DenseScreenSteps {

    private var scale: Float = 1f

    @Допустим("^плотный день открыт на стенде SKL-93$")
    fun theDenseDayIsOnTheStand() {
        scale = 1f
    }

    @И("^системный шрифт плотного дня SKL-93 увеличен до (\\d+) %$")
    fun theDenseFontScale(percent: Int) {
        scale = percent / PERCENT
    }

    @Тогда("^время строки — одна строка без переноса$")
    fun theTimeIsOneLine() {
        denseAt(scale) {
            val times = denseTimes()
            assertTrue("the dense day must show its times", times.isNotEmpty())
            times.forEach { node ->
                val layout = requireNotNull(node.textLayout())
                assertEquals("the machine time must be one line", 1, layout.lineCount)
                assertTrue("the machine time must not be cut", !layout.didOverflowHeight)
            }
        }
    }

    @И("^колонка времени шире 44 dp$")
    fun theTimeColumnIsWiderThanTheMinimum() {
        denseAt(scale) {
            val minimum = with(density) { MIN_TOUCH_TARGET.toPx().toInt() }
            denseTimes().forEach { node ->
                assertTrue("the time column must grow: ${node.size.width}", node.size.width > minimum)
            }
        }
    }

    @И("^карточка начинается после колонки времени$")
    fun theCardStartsAfterTheTime() {
        denseAt(scale) {
            val time = denseTimes().first()
            val card = onAllNodesWithTag(TAG_INTAKE_CARD, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .first()
            assertTrue(
                "the card must follow the time column: ${card.boundsInRoot} after ${time.boundsInRoot}",
                card.boundsInRoot.left >= time.boundsInRoot.right,
            )
        }
    }

    @Тогда("^времена строк группы выровнены$")
    fun theTimeColumnsAreAligned() {
        denseAt(scale) {
            val times = denseTimes()
            assertEquals("the lane must show two rows", 2, times.size)
            assertEquals(
                "the time columns must share one left edge",
                times[0].boundsInRoot.left,
                times[1].boundsInRoot.left,
                0f,
            )
            assertEquals("the time columns must share one width", times[0].size.width, times[1].size.width)
        }
    }

    @Тогда("^строка приёма несёт силуэт своего лекарства$")
    fun theIntakeRowCarriesItsMedicationSilhouette() {
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    TodayScreen(state = TodayFixtures.plainDay(), onIntent = {})
                }
            }
            val cards = onAllNodesWithTag(TAG_INTAKE_CARD, useUnmergedTree = true).fetchSemanticsNodes().size +
                onAllNodesWithTag(TAG_PRN_CARD, useUnmergedTree = true).fetchSemanticsNodes().size
            onAllNodesWithTag(TAG_INTAKE_GLYPH, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .let { glyphs -> assertEquals("each intake row must carry its silhouette", cards, glyphs.size) }
            onAllNodesWithTag(TAG_INTAKE_PICTOGRAM, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .let { pictograms -> assertTrue("the neutral pictogram must be gone", pictograms.isEmpty()) }
        }
    }

    private fun SemanticsNode.textLayout(): TextLayoutResult? {
        val results = mutableListOf<TextLayoutResult>()
        config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.firstOrNull()
    }

    private fun ComposeUiTest.denseTimes(): List<SemanticsNode> =
        onAllNodesWithTag(TAG_DENSE_ROW_TIME, useUnmergedTree = true).fetchSemanticsNodes()

    private fun denseAt(scale: Float, question: ComposeUiTest.() -> Unit) {
        val previous = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(scale)
        try {
            runComposeUiTest {
                setContent { DenseStand(scale) }
                question(this)
            }
        } finally {
            RuntimeEnvironment.setFontScale(previous)
        }
    }

    @Composable
    private fun DenseStand(scale: Float) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = scale),
        ) {
            SklyankaTheme {
                val items = listOf(
                    row("i-1", LocalTime.of(18, 0)),
                    row("i-2", LocalTime.of(20, 30)),
                )
                val timeWidth = denseTimeColumnWidth(items)
                Column(
                    modifier = Modifier
                        .width(WIDTH)
                        .background(SklyankaTheme.colors.background),
                ) {
                    items.forEach { item -> DenseIntakeRow(item, {}, timeWidth) }
                }
            }
        }
    }

    private fun row(id: String, time: LocalTime): IntakeItem {
        val medication = Medication(
            id = "m-$id",
            profileId = PROFILE_ID,
            name = "Лекарство",
            form = MedicationForm.TABLET,
            shape = TabletShape.ROUND,
            doseAmount = BigDecimal.ONE,
            doseUnit = "tablet",
            strength = null,
            strengthUnit = null,
            foodRelation = FoodRelation.ANY,
            scheduleMode = ScheduleMode.SCHEDULED,
            maxPerDay = null,
            times = listOf(time),
            startDate = DATE.minusMonths(1),
            endDate = null,
            stockLeft = null,
            stockThresholdDays = null,
            archivedAt = null,
            archiveReason = null,
        )
        return IntakeItem(
            intake = Intake(
                id = id,
                medicationId = medication.id,
                scheduledAt = LocalDateTime.of(DATE, time),
                status = IntakeStatus.PENDING,
                actedAt = null,
                source = null,
                actorProfileId = null,
            ),
            medication = medication,
            presentation = IntakePresentation.UPCOMING,
            markedAt = null,
        )
    }

    private companion object {
        const val PERCENT = 100f
        const val PROFILE_ID = "profile-1"
        val DATE: LocalDate = LocalDate.of(2026, 10, 2)
        val WIDTH = 390.dp
        val MIN_TOUCH_TARGET = 44.dp
    }
}
