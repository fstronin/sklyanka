package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal

/**
 * Единицы дозы и дозировки в строке напоминания (Р-45, SKL-126).
 *
 * Правило то же, что на «Сегодня» и в карточке лекарства: доза печатается словом своей
 * формы со склонением по числу, а единица дозировки — словом языка, а не кодом.
 * У модулей свои копии строк намеренно (`feature-*` не зависят друг от друга), поэтому
 * и проверка у каждого своя: опечатка в копии иначе не видна до кадра.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class ReminderUnitTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyDoseUnitIsPrintedWithItsOwnWord() {
        // Число и его единица не разрываются (SKL-59 TDD §2.2): в ожидании \u00A0, не пробел.
        val lines = listOf(
            MedicationForm.TABLET to "2\u00A0таблетки",
            MedicationForm.CAPSULE to "2\u00A0капсулы",
            MedicationForm.SYRUP to "5\u00A0мл",
            MedicationForm.DROPS to "3\u00A0капли",
            MedicationForm.SPRAY to "2\u00A0впрыска",
            MedicationForm.INJECTION to "2\u00A0инъекции",
        )
        val amounts = listOf("2", "2", "5", "3", "2", "2")

        composeRule.setContent {
            SklyankaTheme {
                Column {
                    lines.forEachIndexed { index, (form, _) ->
                        Text(row(form, BigDecimal(amounts[index])).doseLine())
                    }
                }
            }
        }

        lines.forEach { (_, expected) -> composeRule.onNodeWithText(expected).assertExists() }
    }

    @Test
    fun theStrengthUnitIsPrintedByItsWordNotByItsCode() {
        composeRule.setContent {
            SklyankaTheme { Text(row(MedicationForm.TABLET, BigDecimal("2"), strengthUnit = "mcg").doseLine()) }
        }

        composeRule.onNodeWithText("2\u00A0таблетки · 50 мкг").assertExists()
    }

    /**
     * Дробная доза берёт форму дроби: «0,5 таблетки», а не «0,5 таблеток» (SKL-138).
     *
     * Проверяется слово, а не вся строка: число печатается своей формой, а разделитель дроби —
     * отдельный дефект формата числа (НФТ-43), и утверждение всей строки закрепило бы его здесь.
     */
    @Test
    fun aFractionalDoseTakesTheFractionForm() {
        composeRule.setContent {
            SklyankaTheme { Text(row(MedicationForm.TABLET, BigDecimal("0.5")).doseLine()) }
        }

        composeRule.onNodeWithText("таблетки", substring = true).assertExists()
    }

    private fun row(form: MedicationForm, amount: BigDecimal, strengthUnit: String? = null): ReminderRow = ReminderRow(
        intakeId = "intake-1",
        name = "med-a",
        form = form,
        doseAmount = amount,
        strength = strengthUnit?.let { BigDecimal("50") },
        strengthUnit = strengthUnit,
    )
}
