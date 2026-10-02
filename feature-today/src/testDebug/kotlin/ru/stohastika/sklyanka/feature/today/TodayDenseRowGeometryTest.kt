package ru.stohastika.sklyanka.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.today.IntakeItem
import ru.stohastika.sklyanka.domain.today.IntakePresentation
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Плотная строка: машинное время `HH:mm` атомарно и растёт со шрифтом (ОВ-117).
 *
 * Дефект ручной проверки: колонка 44 dp была фиксированной, и при 130/200 % время
 * рвалось по двоеточию («20: / 30»). Проверка утверждает свойство разметки: время —
 * одна строка без переноса, слот растёт по значению, карточка получает остаток,
 * а колонки строк группы стоят по одной вертикали.
 */
@RunWith(AndroidJUnit4::class)
class TodayDenseRowGeometryTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theTimeIsOneLineAndTheColumnGrowsAtTwoHundredPercent() {
        show(scale = SCALE_200, items = listOf(item("i-1", LocalTime.of(20, 30))))

        val time = onlyTimeNode()
        val layout = requireNotNull(time.textLayout())
        assertEquals("the time must be one line", 1, layout.lineCount)
        assertTrue("the time must not be cut", !layout.didOverflowHeight)
        assertTrue(
            "the time column must grow wider than the 44 dp minimum",
            time.size.width > minTouchTargetPx(),
        )
    }

    @Test
    fun theCardKeepsTheRemainingWidthAndDoesNotOverlapTheTime() {
        show(scale = SCALE_200, items = listOf(item("i-1", LocalTime.of(20, 30))))

        val time = onlyTimeNode()
        val card = composeRule
            .onAllNodesWithTag(TAG_INTAKE_CARD, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .single()
        assertTrue(
            "the card must start after the time column: time ${time.boundsInRoot}, card ${card.boundsInRoot}",
            card.boundsInRoot.left >= time.boundsInRoot.right,
        )
    }

    @Test
    fun theTimeColumnsOfAGroupShareOneVertical() {
        show(
            scale = SCALE_200,
            items = listOf(
                item("i-1", LocalTime.of(18, 0)),
                item("i-2", LocalTime.of(20, 30)),
            ),
        )

        val times = composeRule
            .onAllNodesWithTag(TAG_DENSE_ROW_TIME, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(2, times.size)
        assertEquals(
            "the columns of a group must be left-aligned",
            times[0].boundsInRoot.left,
            times[1].boundsInRoot.left,
            0f,
        )
        assertEquals(
            "the columns of a group must be one width",
            times[0].size.width,
            times[1].size.width,
        )
    }

    private fun onlyTimeNode(): SemanticsNode = composeRule
        .onAllNodesWithTag(TAG_DENSE_ROW_TIME, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .single()

    private fun SemanticsNode.textLayout(): TextLayoutResult? {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult]?.action?.invoke(results)
        return results.firstOrNull()
    }

    private fun minTouchTargetPx(): Int = with(composeRule.density) { MIN_TOUCH_TARGET.toPx().toInt() }

    private fun show(scale: Float, items: List<IntakeItem>) {
        composeRule.setContent {
            Scaled(scale) {
                SklyankaTheme {
                    Column(
                        modifier = Modifier
                            .width(WIDTH)
                            .background(SklyankaTheme.colors.background),
                    ) {
                        val timeWidth = denseTimeColumnWidth(items)
                        items.forEach { item -> DenseIntakeRow(item, {}, timeWidth) }
                    }
                }
            }
        }
    }

    @Composable
    private fun Scaled(scale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = scale),
            content = content,
        )
    }

    private fun item(id: String, time: LocalTime): IntakeItem {
        val medication = Medication(
            id = "m-$id",
            profileId = PROFILE_ID,
            name = "Лекарство",
            form = MedicationForm.TABLET,
            shape = ru.stohastika.sklyanka.domain.model.TabletShape.ROUND,
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
        val DATE: LocalDate = LocalDate.of(2026, 10, 2)
        val WIDTH = 390.dp
        val MIN_TOUCH_TARGET = 44.dp
        const val PROFILE_ID = "profile-1"
        const val SCALE_200 = 2f
    }
}
