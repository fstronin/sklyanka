package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_PREVIEW
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_UNIT

/**
 * Экранные шаги приёмки слова единицы дозы в поле (SKL-138, норма `docs/norm/06-design.md` §5.3).
 *
 * Сценарий называет число словом языка («2 таблетки»), а проверяется то, что человек видит:
 * подпись единицы в поле — суффикс, который обязан следовать за набранным числом. Подпись
 * ищется своим тегом внутри блока, а не по всему экрану: та же форма слова стоит в превью
 * и в подсказках, и поиск по тексту нашёл бы чужой узел.
 *
 * Значения полей задаются намерениями того же вида, каким их задаёт человек на клавиатуре
 * (`MedFormIntent.StockChanged` и подобные), — той же дорогой, что у шагов SKL-126.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class Sk138UnitWordsScreenSteps(private val world: AcceptanceWorld) {

    // --- Предыстория: значения строк остатка и размера упаковки -----------------------------

    @Когда("^в форме нового лекарства задано «Сейчас есть» (.+)$")
    fun theStockIsEntered(amount: String) {
        MedsFormStand.intent(world, MedFormIntent.StockChanged(amount))
    }

    @Когда("^в форме нового лекарства задан размер упаковки (.+)$")
    fun thePackageSizeIsEntered(amount: String) {
        MedsFormStand.intent(world, MedFormIntent.PackageSizeChanged(amount))
    }

    // --- Подпись единицы в строках ----------------------------------------------------------

    @Тогда("^единицей в строке «Сейчас есть» показано «(.+)»$")
    fun theStockUnitIsShown(unit: String) {
        assertUnitLabel(TAG_STOCK_UNIT, unit)
    }

    /**
     * Единица в превью формы: слово то же, что в поле дозы.
     *
     * Проверяется слово, а не вся строка: число в превью печатается своей формой
     * («0.5 таблетки»), и утверждение всей строки закрепляло бы чужой дефект формата числа.
     */
    @Тогда("^в превью формы единицей названо «(.+)»$")
    fun thePreviewNamesTheUnit(unit: String) {
        MedsFormStand.ask(world) { test ->
            val texts = MedsProbe.texts(test, TAG_MEDICATION_PREVIEW)
            assertTrue("the preview must name the unit «$unit»: $texts", texts.any { unit in it })
        }
    }

    private fun assertUnitLabel(tag: String, expected: String) {
        MedsFormStand.ask(world) { test ->
            val label = test.onNodeWithTag(tag, useUnmergedTree = true)
            label.assertIsDisplayed()
            label.assertTextEquals(expected)
        }
    }
}
