package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.TAG_DOSE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_STRENGTH_FIELD
import ru.stohastika.sklyanka.ui.component.TAG_LIST_PICKER_OPTION
import ru.stohastika.sklyanka.ui.component.TAG_TEXT_FIELD_UNIT
import ru.stohastika.sklyanka.ui.component.TAG_TEXT_FIELD_UNIT_PICKER

/**
 * Экранные шаги приёмки единиц измерения (SKL-126, решение Р-45).
 *
 * Сценарий называет единицу словом («мг», «МЕ»), а проверяется то, что человек видит:
 * подпись единицы в поле дозы, подпись единицы в поле дозировки и состав списка, которым
 * эта единица выбирается. Код, которым единица ложится в запись, экрану не виден —
 * его проверяют тесты домена и формы; здесь проверяется витрина.
 *
 * Доменный шаг «человек выбирает единицу дозировки «…»» кладёт единицу в черновик без
 * нажатия и остаётся сценариям правил; здесь стоит своё слово — «человек выбирает в списке
 * единицу «…»», — потому что проверяется нажатие по списку, а не значение.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class Sk126UnitsScreenSteps(private val world: AcceptanceWorld) {

    // --- Предыстория: значения на собранной форме ------------------------------------------

    @Допустим("^в форме нового лекарства задано количество дозы (.+)$")
    fun theDoseIsEntered(amount: String) {
        MedsFormStand.intent(world, MedFormIntent.DoseAmountChanged(amount))
    }

    @Допустим("^в форме нового лекарства задана дозировка (.+)$")
    fun theStrengthIsEntered(amount: String) {
        MedsFormStand.intent(world, MedFormIntent.StrengthChanged(amount))
    }

    // --- Выбор единицы дозировки ------------------------------------------------------------

    @Когда("^человек открывает выбор единицы дозировки$")
    fun thePersonOpensTheUnitPicker() {
        MedsFormStand.act(world) { test -> test.onUnitPicker().performClick() }
    }

    @Тогда("^в списке предложены единицы «(.+)», «(.+)», «(.+)», «(.+)», «(.+)» и «(.+)»$")
    fun thePickerOffersThoseUnits(first: String, second: String, third: String, fourth: String, fifth: String, sixth: String) {
        val expected = listOf(first, second, third, fourth, fifth, sixth)
        // Список открывается в том же проходе композиции, что и читается: открытое
        // состояние живёт в разметке экрана, и отдельная сборка показала бы форму без него.
        MedsFormStand.ask(world) { test ->
            test.onUnitPicker().performClick()
            test.waitForIdle()
            val options = test.onAllNodesWithTag(TAG_LIST_PICKER_OPTION, useUnmergedTree = true)
            options.assertCountEquals(expected.size)
            expected.forEach { unit ->
                // Вариант ищется по тегу и подписи: та же подпись стоит единицей поля.
                test.onNode(hasTestTag(TAG_LIST_PICKER_OPTION) and hasText(unit)).assertExists()
            }
            assertEquals("the closed list is exactly the six units", expected.distinct().size, expected.size)
        }
    }

    /**
     * Выбор единицы в открытом списке: нажатие на вариант и есть выбор.
     *
     * Шаг отдельный от доменного «человек выбирает единицу дозировки «…»»: тот кладёт
     * единицу в черновик без нажатия, а здесь проверяется именно нажатие по списку.
     */
    @Когда("^человек выбирает в списке единицу «([^»]+)»$")
    fun thePersonChoosesTheUnitFromTheList(label: String) {
        chooseUnit(label)
    }

    // --- Подпись единицы в полях -------------------------------------------------------------

    @Тогда("^единицей в блоке «Доза на один приём» показано «(.+)»$")
    fun theDoseUnitIsShown(unit: String) {
        assertUnitLabel(TAG_DOSE_FIELD, unit)
    }

    @Тогда("^единицей в блоке «Дозировка препарата» показано «(.+)»$")
    fun theStrengthUnitIsShown(unit: String) {
        assertUnitLabel(TAG_STRENGTH_FIELD, unit)
    }

    // --- Обвязка -----------------------------------------------------------------------------

    /**
     * Нажатие по списку единиц в одной композиции.
     *
     * Два нажатия — по единице в поле и по варианту списка — обязаны пройти в одном
     * проходе композиции: открытое состояние списка живёт в разметке экрана, и следующая
     * сборка стенда показала бы форму уже без него.
     */
    private fun chooseUnit(label: String) {
        MedsFormStand.act(world) { test ->
            test.onUnitPicker().performClick()
            test.waitForIdle()
            test.onNode(hasTestTag(TAG_LIST_PICKER_OPTION) and hasText(label)).performClick()
        }
    }

    private fun assertUnitLabel(fieldTag: String, expected: String) {
        MedsFormStand.ask(world) { test ->
            val label = test.onNode(
                hasTestTag(TAG_TEXT_FIELD_UNIT) and hasAnyAncestor(hasTestTag(fieldTag)),
                useUnmergedTree = true,
            )
            label.assertIsDisplayed()
            label.assertTextEquals(expected)
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onUnitPicker() = onNode(
        hasTestTag(TAG_TEXT_FIELD_UNIT_PICKER) and hasAnyAncestor(hasTestTag(TAG_STRENGTH_FIELD)),
        useUnmergedTree = true,
    )
}
