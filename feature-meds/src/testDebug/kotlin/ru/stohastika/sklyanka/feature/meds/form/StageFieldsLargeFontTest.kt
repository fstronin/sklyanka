package ru.stohastika.sklyanka.feature.meds.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDate

/**
 * Два поля этапа при системном шрифте 100 % и 200 % (§5.10, §6.3, НФТ-31).
 *
 * Порог раскладки — измеренная ширина поля, а не `fontScale`: при 100 % поля делят строку
 * пополам, при 200 % слово единицы («таблетки») перестаёт помещаться в половину, и поля
 * встают столбиком. Проверка — по положению узлов в корне: у столбика поля стоят на одной
 * вертикали и на разных строках, у ряда — на одной строке и на разных вертикалях.
 *
 * Ширина корня — экранная (390 dp, как у прочих проверок формы), высота — с запасом:
 * карточка этапа обязана быть видимой целиком, иначе границы узлов обрезаются прокруткой
 * и сравнение положений теряет смысл.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h6000dp-mdpi")
class StageFieldsLargeFontTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theStageFieldsStandInAColumnAtTwoHundredPercent() {
        show(SCALE_200)

        val dose = boundsOf(TAG_STAGE_DOSE)
        val days = boundsOf(TAG_STAGE_DAYS)

        assertTrue(
            "the days field must stand under the dose field at 200 %, but both are on top $dose",
            days.top >= dose.bottom,
        )
        assertEquals("the stacked fields must share the left edge", dose.left, days.left, EDGE_TOLERANCE)
    }

    @Test
    fun theStageFieldsShareTheRowAtOneHundredPercent() {
        show(SCALE_100)

        val dose = boundsOf(TAG_STAGE_DOSE)
        val days = boundsOf(TAG_STAGE_DAYS)

        assertEquals("the fields of one row must share the top edge", dose.top, days.top, EDGE_TOLERANCE)
        assertTrue(
            "the days field must stand to the right of the dose field at 100 %, but it is at $days",
            days.left >= dose.right,
        )
    }

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** Форма в виде «По этапам» с одним этапом: доза 0,5 и длительность 7 (§5.10). */
    private fun show(scale: Float) {
        val draft = MedicationDraft(
            id = null,
            profileId = PROFILE_ID,
            name = NAME,
            form = MedicationForm.TABLET,
            scheduleMode = ScheduleMode.SCHEDULED,
            startDate = LocalDate.of(2026, 9, 25),
        )
        composeRule.setContent {
            Scaled(scale) {
                SklyankaTheme {
                    MedicationFormScreen(
                        state = MedFormUiState.of(
                            draft = draft,
                            mode = FormMode.Create,
                            inputs = FormInputs(stageDoses = listOf("0,5"), stageDays = listOf("7")),
                            scheduleKind = ScheduleKind.STAGES,
                        ),
                        onIntent = {},
                        onPickDate = {},
                        onPickTime = { _, _ -> },
                    )
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

    private companion object {
        const val SCALE_100 = 1f
        const val SCALE_200 = 2f
        const val EDGE_TOLERANCE = 0.5f
        const val NAME = "med-a"
        const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
    }
}
