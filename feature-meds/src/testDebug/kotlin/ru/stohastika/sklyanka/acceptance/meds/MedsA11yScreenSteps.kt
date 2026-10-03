package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.TAG_DOSE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_INDEFINITE
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_REMINDER
import ru.stohastika.sklyanka.feature.meds.form.TAG_STRENGTH_FIELD
import java.time.LocalTime

/**
 * Шаги приёмки подписей формы для программы чтения с экрана (SKL-09, группа `@a11y`).
 *
 * Проверяется то, что читает программа: подпись берётся у собранного узла — из описания
 * содержания или из текста, которым узел сливает подпись (`НФТ-30`). Слово, которое
 * программа обязана произнести, называет сценарий; шаг сверяет его с узлом.
 *
 * Строки времени в свежей форме нет вовсе: первая строка — «08:00», и появляется она
 * тем же действием «Добавить время», что у человека. Без этого читать было бы нечего,
 * и сценарий остался бы непроверенным.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class MedsA11yScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Что программа произнесла последним: сверяется со словом сценария. */
    private var spoken: List<String> = emptyList()

    @Когда("^программа чтения с экрана читает (.+)$")
    fun theScreenReaderReads(element: String) {
        val draft = MedsFormStand.open(world).draft
        if (element.startsWith(DELETE_TIME_PREFIX) && DELETED_TIME !in draft.times) {
            MedsFormStand.intent(world, MedFormIntent.TimeAdded)
        }
        spoken = MedsFormStand.ask(world) { test -> labels(element, test) }
        assertTrue("the element «$element» must carry a label", spoken.isNotEmpty())
    }

    @Тогда("^она произносит «(.+)»$")
    fun itPronounces(label: String) {
        assertTrue("the screen reader must pronounce «$label», it says $spoken", label in spoken)
    }

    private fun labels(element: String, test: ComposeUiTest): List<String> = when {
        element.startsWith(DECREASE) -> labelsOf(
            test.onNodeWithContentDescription(context.getString(R.string.cd_decrease), useUnmergedTree = true),
        )

        element.startsWith(INCREASE) -> labelsOf(
            test.onNodeWithContentDescription(context.getString(R.string.cd_increase), useUnmergedTree = true),
        )

        element.startsWith(DELETE_TIME_PREFIX) -> {
            val time = requireNotNull(QUOTED_TIME.find(element)) { "no time in «$element»" }.groupValues[1]
            labelsOf(
                test.onNodeWithContentDescription(
                    context.getString(R.string.cd_delete_time, time),
                    useUnmergedTree = true,
                ),
            )
        }

        element == DOSE_AMOUNT -> labelsOf(test.onNodeWithTag(TAG_DOSE_FIELD, useUnmergedTree = true))
        element == STRENGTH -> labelsOf(test.onNodeWithTag(TAG_STRENGTH_FIELD, useUnmergedTree = true))
        element == STOCK_REMINDER -> labelsOf(test.onNodeWithTag(TAG_STOCK_REMINDER, useUnmergedTree = true))
        element == INDEFINITE -> labelsOf(test.onNodeWithTag(TAG_INDEFINITE, useUnmergedTree = true))
        else -> error("unknown a11y element: $element")
    }

    private fun labelsOf(interaction: SemanticsNodeInteraction): List<String> =
        MedsProbe.labelsOf(interaction.fetchSemanticsNode())

    private companion object {
        const val DECREASE = "кнопку уменьшения числа приёмов"
        const val INCREASE = "кнопку увеличения числа приёмов"
        const val DELETE_TIME_PREFIX = "кнопку удаления времени"
        const val DOSE_AMOUNT = "поле количества дозы"
        const val STRENGTH = "поле дозировки"
        const val STOCK_REMINDER = "переключатель порога остатка"
        const val INDEFINITE = "переключатель бессрочности"

        val DELETED_TIME: LocalTime = LocalTime.of(8, 0)
        val QUOTED_TIME = Regex("«(\\d{2}:\\d{2})»")
    }
}
