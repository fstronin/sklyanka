package ru.stohastika.sklyanka.feature.meds

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.TrailingLine
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.feature.meds.list.AsNeededCardUi
import ru.stohastika.sklyanka.feature.meds.list.MedicationCardUi
import ru.stohastika.sklyanka.feature.meds.list.MedicationListScreen
import ru.stohastika.sklyanka.feature.meds.list.MedsUiState
import ru.stohastika.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.stohastika.sklyanka.feature.meds.list.TAG_LAST_INTAKE
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.feature.meds.list.TAG_TRAILING_LINE
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экран «Лекарства»: счётчики, состав карточек, кнопка добавления.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
// Высота с запасом: список начинается с шапки и сегмента, и проверкам нужно, чтобы
// последняя карточка попала в окно, а не осталась незакомпонованной (SKL-105).
@Config(qualifiers = "w390dp-h1200dp-mdpi")
class MedicationListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theHeaderCarriesTheScreenTitle() {
        show(MedsUiState(loading = false, items = listOf(scheduledCard())))

        composeRule.onNodeWithText(context.getString(R.string.meds_title)).assertIsDisplayed()
    }

    @Test
    fun theSegmentedControlShowsBothCounters() {
        show(
            MedsUiState(
                activeCount = 5,
                archiveCount = 12,
                items = listOf(scheduledCard()),
                loading = false,
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.meds_tab_active, 5)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.meds_tab_archive, 12)).assertIsDisplayed()
    }

    @Test
    fun anAsNeededMedicationIsListedWhileTheActiveCounterIgnoresIt() {
        // ЭКР-3: чип говорит «Активные · 5», а карточек шесть — шестая «по потребности».
        show(
            MedsUiState(
                activeCount = 5,
                items = List(5) { index -> scheduledCard(id = "scheduled-$index") } + asNeededCard(),
                loading = false,
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.meds_tab_active, 5)).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_MEDICATION_CARD).assertCountEquals(6)
    }

    @Test
    fun anAsNeededCardCarriesTheChipTheLastIntakeAndTheTakeButton() {
        show(MedsUiState(items = listOf(asNeededCard()), loading = false))

        // Карточка объявлена одной кнопкой и сливает семантику потомков, поэтому её
        // внутренние узлы ищутся по неслитому дереву.
        composeRule
            .onNodeWithText(context.getString(R.string.meds_as_needed_chip), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_LAST_INTAKE, useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(UiR.string.action_take), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /**
     * Чип «по потребности» не съедает название (находка визревью SKL-91).
     *
     * Длинный переведённый чип оставлял заголовку ширину в несколько пикселей, и имя
     * схлопывалось в «…» — список «по потребности» становился неразличим. Название
     * обязано остаться читаемым целиком: чип переносится под него.
     */
    @Test
    @Config(qualifiers = "ru")
    fun anAsNeededNameIsNotSwallowedByTheChip() {
        show(MedsUiState(items = listOf(asNeededCard(title = "med-c")), loading = false))

        val title = composeRule
            .onNodeWithText("med-c", useUnmergedTree = true)
            .fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        title.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.first()

        assertFalse(
            "имя «med-c» скрыто чипом: видно ${layout.getLineEnd(0, true)} символов",
            layout.isLineEllipsized(0),
        )
    }

    /**
     * Дата «с …» берёт исходный падеж месяца (находка визревью SKL-91).
     *
     * Послелог «бастап» требует исходного падежа, которого ICU для казахских месяцев
     * не даёт: суффикс приходит из ресурсного массива языка.
     */
    @Test
    @Config(qualifiers = "kk")
    fun aKazakhOpenCourseNamesTheMonthInTheAblative() {
        show(
            MedsUiState(
                items = listOf(
                    scheduledCard(trailingLine = TrailingLine.Period(LocalDate.of(2026, 9, 12), null)),
                ),
                loading = false,
            ),
        )

        composeRule
            .onNodeWithText("12 қыркүйектен бастап", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun anExhaustedDailyLimitDisablesTheTakeButton() {
        show(MedsUiState(items = listOf(asNeededCard(takeEnabled = false)), loading = false))

        composeRule.onNodeWithText(context.getString(UiR.string.action_take)).assertIsNotEnabled()
    }

    @Test
    fun aTriggeredStockThresholdReplacesThePeriodWithTheStockLeft() {
        show(
            MedsUiState(
                items = listOf(
                    scheduledCard(
                        trailingLine = TrailingLine.Stock(BigDecimal("9"), DoseUnit.TABLET),
                    ),
                ),
                loading = false,
            ),
        )

        composeRule.onNodeWithTag(TAG_TRAILING_LINE, useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * Три чипа времени не выдавливают уточнение из строки (SKL-59, дефект пилота).
     *
     * Уточнение — дата курса или остаток. Если рядом с чипами ему не хватило ширины,
     * оно переносится под чипы целиком; колонка из одной буквы — это разрыв слова,
     * а не перенос.
     */
    @Test
    fun aCardWithThreeIntakeTimesKeepsThePeriodReadable() {
        show(
            MedsUiState(
                items = listOf(
                    scheduledCard(
                        times = listOf(LocalTime.of(8, 0), LocalTime.of(13, 30), LocalTime.of(18, 0)),
                    ),
                ),
                loading = false,
            ),
        )

        val node = composeRule
            .onNodeWithTag(TAG_TRAILING_LINE, useUnmergedTree = true)
            .fetchSemanticsNode()
        val width = with(composeRule.density) { node.size.width.toDp().value }
        val height = with(composeRule.density) { node.size.height.toDp().value }
        assertTrue("уточнение сжато в колонку: width=$width dp", width >= MIN_TRAILING_WIDTH_DP)
        assertTrue("уточнение разложено на $height dp", height <= MAX_TRAILING_HEIGHT_DP)
    }

    @Test
    fun theAddButtonIsLabelledForScreenReaders() {
        show(MedsUiState(items = listOf(scheduledCard()), loading = false))

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.cd_add_medication))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ADD_MEDICATION).assertIsDisplayed()
    }

    @Test
    fun anEmptyActiveListInvitesToAddAMedication() {
        show(MedsUiState(loading = false))

        composeRule.onNodeWithText(context.getString(R.string.meds_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.meds_empty_action)).assertIsDisplayed()
    }

    private fun show(state: MedsUiState) {
        composeRule.setContent {
            SklyankaTheme {
                MedicationListScreen(state = state, onIntent = {})
            }
        }
    }

    private fun scheduledCard(
        id: String = "scheduled",
        trailingLine: TrailingLine = TrailingLine.Period(LocalDate.of(2026, 3, 12), null),
        times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    ) = MedicationCardUi(
        medicationId = id,
        title = "med-$id",
        form = MedicationForm.TABLET,
        shape = TabletShape.ROUND_SCORED,
        strength = "50",
        strengthUnit = "mg",
        timesPerDay = times.size,
        times = times,
        trailingLine = trailingLine,
        asNeeded = null,
    )

    private fun asNeededCard(takeEnabled: Boolean = true, title: String = "med-as-needed") = MedicationCardUi(
        medicationId = "as-needed",
        title = title,
        form = MedicationForm.CAPSULE,
        shape = null,
        strength = "200",
        strengthUnit = "mg",
        timesPerDay = 0,
        times = emptyList(),
        trailingLine = TrailingLine.Period(LocalDate.of(2026, 3, 12), null),
        asNeeded = AsNeededCardUi(
            maxPerDay = 3,
            lastIntakeDate = LocalDate.of(2026, 9, 19),
            takeEnabled = takeEnabled,
        ),
    )

    private companion object {
        /** Уточнение уже́ самого длинного слова («сентября») не бывает: это разрыв слова. */
        const val MIN_TRAILING_WIDTH_DP = 80f

        /** Три строки уточнения — предел; колонка из букв даёт десятки строк. */
        const val MAX_TRAILING_HEIGHT_DP = 80f
    }
}
