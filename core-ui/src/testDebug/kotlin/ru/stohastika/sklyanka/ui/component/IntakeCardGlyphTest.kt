package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Силуэт строки приёма — форма самого лекарства, а не нейтральная пиктограмма.
 *
 * Находка ручной проверки: на «Сегодня» все строки показывали капсулу независимо
 * от формы лекарства. Строка с известной формой обязана нести `MedicationGlyph`
 * («форма + вид», §4.15), и только карточка без лекарства вовсе (каталог, заготовка)
 * остаётся с нейтральной пиктограммой.
 */
@RunWith(AndroidJUnit4::class)
class IntakeCardGlyphTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun aKnownFormShowsTheMedicationSilhouette() {
        show(form = MedicationForm.TABLET, shape = TabletShape.ROUND)

        composeTestRule.onNodeWithTag(TAG_INTAKE_GLYPH, useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag(TAG_INTAKE_PICTOGRAM, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aCapsuleKeepsItsOwnForm() {
        show(form = MedicationForm.CAPSULE, shape = null)

        composeTestRule.onNodeWithTag(TAG_INTAKE_GLYPH, useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag(TAG_INTAKE_PICTOGRAM, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aCardWithoutMedicationKeepsTheNeutralPictogram() {
        show(form = null, shape = null)

        composeTestRule.onNodeWithTag(TAG_INTAKE_PICTOGRAM, useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag(TAG_INTAKE_GLYPH, useUnmergedTree = true).assertDoesNotExist()
    }

    private fun show(form: MedicationForm?, shape: TabletShape?) {
        composeTestRule.setContent {
            SklyankaTheme {
                IntakeCard(
                    state = IntakeCardState.Pending,
                    labels = IntakeCardLabels(medication = "Л", dose = "1 таблетка"),
                    form = form,
                    shape = shape,
                )
            }
        }
    }
}
