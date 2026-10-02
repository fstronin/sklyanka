package ru.stohastika.sklyanka.acceptance.steps.sk93

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.component.DateTimePickerDialog
import ru.stohastika.sklyanka.ui.component.InkProbe
import ru.stohastika.sklyanka.ui.component.TAG_TIME_INPUT
import ru.stohastika.sklyanka.ui.component.TAG_TIME_PICKER
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.io.File
import java.time.LocalTime

/**
 * Экранные шаги приёмки SKL-93: тайм-пикер, оптический бокс силуэтов и переводы.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 * Диалог и силуэт собираются на стенде заново в каждом шаге (как в SKL-90): сценарий
 * проверяет вёрстку заготовки, и состояние между шагами не переносится.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class Sk93CoreUiScreenSteps {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var scale: Float = 1f
    private var keyboard: Boolean = false
    private var confirmed: LocalTime? = null

    // --- Тайм-пикер ------------------------------------------------------------------

    @Допустим("^диалог «Время приёма» открыт на стенде SKL-93$")
    fun theDialogIsOnTheStand() {
        keyboard = false
        confirmed = null
    }

    @И("^системный шрифт диалога SKL-93 увеличен до (\\d+) %$")
    fun theDialogFontScale(percent: Int) {
        scale = percent / PERCENT
    }

    @Тогда("^цифра выбранного слота стоит по центру подложки 96×80 dp с допуском 1 dp$")
    fun theSlotDigitIsCentered() {
        composeAt(scale) {
            val digits = digitsUnder(TAG_TIME_PICKER)
            assertTrue("the slot must show its digits", digits.isNotEmpty())
            digits.forEach { node ->
                val layout = requireNotNull(InkProbe.textLayout(node))
                val ink = requireNotNull(InkProbe.inkBounds(layout)) { "the slot digit must have ink" }
                val plate = requireNotNull(node.parent)
                assertEquals("the slot plate must be 96×80 dp", SLOT_PLATE, plate.size)
                assertEquals(
                    "the 72 dp line must be centered in the 80 dp plate",
                    ((plate.size.height - layout.size.height) / 2).toFloat(),
                    node.boundsInRoot.top - plate.boundsInRoot.top,
                    TOLERANCE,
                )
                assertTrue(
                    "the ink must be optically centered: $ink of ${layout.size}",
                    centreGap(ink.top, ink.bottom, layout.size.height) <= 1,
                )
            }
        }
    }

    @Когда("^человек переключает ввод на клавиатурный$")
    fun thePersonSwitchesToKeyboard() {
        keyboard = true
        composeAt(scale) {
            switchToKeyboard()
            onNodeWithTag(TAG_TIME_INPUT).assertExists()
        }
    }

    @Тогда("^все чернила цифр клавиатурного ввода внутри поля 96×72 dp$")
    fun theInputDigitsAreInsideTheField() {
        composeAt(scale) {
            switchToKeyboard()
            val fields = digitsUnder(TAG_TIME_INPUT)
            assertTrue("the keyboard input must show its digits", fields.isNotEmpty())
            fields.forEach { node ->
                val layout = requireNotNull(InkProbe.textLayout(node))
                val ink = requireNotNull(InkProbe.inkBounds(layout)) { "the input digit must have ink" }
                assertEquals(FIELD_HEIGHT, node.size.height)
                assertTrue("the ink must be inside the field: $ink", ink.top >= 0 && ink.bottom < FIELD_HEIGHT)
            }
        }
    }

    @И("^зазоры над и под цифрой равны$")
    fun theInputGapsAreEqual() {
        composeAt(scale) {
            switchToKeyboard()
            digitsUnder(TAG_TIME_INPUT).forEach { node ->
                val layout = requireNotNull(InkProbe.textLayout(node))
                val ink = requireNotNull(InkProbe.inkBounds(layout)) { "the input digit must have ink" }
                assertTrue(
                    "the gaps above and below the ink must be equal: $ink of $FIELD_HEIGHT",
                    centreGap(ink.top, ink.bottom, FIELD_HEIGHT) <= 1,
                )
            }
        }
    }

    @Тогда("^кнопка переключения подписана «([^»]+)»$")
    fun theSwitchIsLabelled(label: String) {
        composeAt(scale) {
            if (keyboard) switchToKeyboard()
            onNodeWithText(label).assertExists()
        }
    }

    @Когда("^человек возвращается на циферблат$")
    fun thePersonReturnsToTheDial() {
        keyboard = false
        composeAt(scale) {
            switchToKeyboard()
            switchToDial()
            onNodeWithTag(TAG_TIME_PICKER).assertExists()
        }
    }

    @Когда("^человек подтверждает выбор$")
    fun thePersonConfirms() {
        composeAt(scale) {
            if (keyboard) switchToKeyboard()
            onNodeWithText(context.getString(R.string.action_done)).performClick()
        }
    }

    @Тогда("^диалог возвращает время 08:00$")
    fun theDialogReturnsTheTime() {
        assertEquals(LocalTime.of(8, 0), confirmed)
    }

    // --- Оптический бокс силуэтов ----------------------------------------------------

    @Допустим("^ряд «Вид таблетки» собран на стенде SKL-93$")
    fun theShapeRowIsOnTheStand() = Unit

    @Тогда("^все пять силуэтов ряда имеют одну ширину внешнего контура и общий центр$")
    fun allFiveShapesShareWidthAndCentre() {
        val boxes = TabletShape.entries.associateWith { shape ->
            requireNotNull(InkProbe.shapeBounds(shape, SHAPE_SIDE)) { "the silhouette $shape must have ink" }
        }
        val widths = boxes.values.map { it.width }.distinct()
        assertEquals("the five silhouettes must be one width: ${boxes.mapValues { it.value.width }}", 1, widths.size)
        boxes.forEach { (shape, box) ->
            assertEquals(
                "the silhouette $shape must be horizontally centred",
                (SHAPE_SIDE / 2).toDouble(),
                ((box.left + box.right + 1) / 2).toDouble(),
                1.0,
            )
            assertEquals(
                "the silhouette $shape must be vertically centred",
                (SHAPE_SIDE / 2).toDouble(),
                ((box.top + box.bottom + 1) / 2).toDouble(),
                1.0,
            )
        }
    }

    // --- Переводы --------------------------------------------------------------------

    @Допустим("^каталоги переводов «Склянки» открыты на стенде SKL-93$")
    fun theTranslationCataloguesAreOpen() = Unit

    @Тогда("^строка «([^»]+)» непустая во всех шести локалях$")
    fun theStringIsTranslatedEverywhere(russianText: String) {
        val files = translationFiles()
        val base = stringValue(files.first { it.first == "values" }.second, SWITCH_DIAL_KEY)
        assertEquals("the russian resource must read «$russianText»", russianText, base)
        files.forEach { (qualifier, file) ->
            val value = stringValue(file, SWITCH_DIAL_KEY)
            assertTrue("missing «$russianText» in $qualifier: ${file.path}", value.isNotBlank())
        }
    }

    @Но("^в нерусских локалях в ней нет кириллицы$")
    fun theForeignTranslationsHaveNoCyrillic() {
        translationFiles().filterNot { it.first in CYRILLIC_LANGUAGES }.forEach { (qualifier, file) ->
            val value = stringValue(file, SWITCH_DIAL_KEY)
            assertTrue(
                "russian text in $qualifier: «$value»",
                !CYRILLIC.containsMatchIn(value),
            )
        }
    }

    // --- Обвязка ---------------------------------------------------------------------

    private fun ComposeUiTest.switchToKeyboard() {
        onNodeWithText(context.getString(R.string.cd_switch_time_input)).performClick()
    }

    private fun ComposeUiTest.switchToDial() {
        onNodeWithText(context.getString(R.string.cd_switch_time_dial)).performClick()
    }

    private fun ComposeUiTest.digitsUnder(container: String): List<SemanticsNode> =
        onAllNodes(hasAnyAncestor(hasTestTag(container)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter(InkProbe::hasDigits)

    private fun centreGap(top: Int, bottom: Int, box: Int): Int = kotlin.math.abs((top + bottom + 1) / 2 - box / 2)

    private fun composeAt(scale: Float, question: ComposeUiTest.() -> Unit) {
        val previous = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(scale)
        try {
            runComposeUiTest {
                setContent {
                    SklyankaTheme {
                        DateTimePickerDialog(
                            title = DIALOG_TITLE,
                            initial = LocalTime.of(8, 0),
                            is24Hour = true,
                            onDismiss = {},
                            onConfirm = { confirmed = it },
                        )
                    }
                }
                question(this)
            }
        } finally {
            RuntimeEnvironment.setFontScale(previous)
        }
    }

    private fun translationFiles(): List<Pair<String, File>> {
        val root = requireNotNull(System.getProperty("sklyanka.repo.root")) {
            "sklyanka.repo.root is not set: the screen test convention must be applied"
        }
        return TRANSLATION_QUALIFIERS.map { qualifier ->
            qualifier to File(root, "core-ui/src/main/res/$qualifier/strings.xml")
        }
    }

    private fun stringValue(file: File, name: String): String {
        assertTrue("the resource file is missing: ${file.path}", file.isFile)
        val match = Regex("<string name=\"$name\">([^<]*)</string>").find(file.readText())
        return match?.groupValues?.get(1)?.trim().orEmpty()
    }

    private companion object {
        const val PERCENT = 100f
        const val DIALOG_TITLE = "Время приёма"
        const val SWITCH_DIAL_KEY = "cd_switch_time_dial"
        const val FIELD_HEIGHT = 72
        const val SHAPE_SIDE = 64
        const val TOLERANCE = 1f
        val SLOT_PLATE = IntSize(96, 80)

        val TRANSLATION_QUALIFIERS = listOf(
            "values",
            "values-en-rUS",
            "values-de",
            "values-es",
            "values-kk",
            "values-zh-rCN",
        )

        /** Казахский пишется кириллицей легально; остальные локали — нет. */
        val CYRILLIC_LANGUAGES = setOf("values", "values-kk")
        val CYRILLIC = Regex("[\\u0400-\\u04FF]")
    }
}
