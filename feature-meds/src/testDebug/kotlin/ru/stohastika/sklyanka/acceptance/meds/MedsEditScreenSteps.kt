package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.normalized
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.form.FormMode
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIME_ROW
import ru.stohastika.sklyanka.ui.component.TAG_SCREEN_HEADER

/**
 * Экранные шаги приёмки правки лекарства (SKL-09, группа `@save`; заведены SKL-26).
 *
 * Модуль свой: сценарий говорит только о форме правки, и она живёт здесь
 * (`SKL-26/TDD.md §4.3`). Черновик заполняет `MedsFormStand.openEdit` — тем же
 * `MedicationDraft.of`, каким его заполняет `MedicationFormViewModel.open`.
 *
 * Название в шапке — название лекарства в модели (`med-N`), а не ключ сценария:
 * человеческих имён модель не хранит (НФТ-19), и перевод делает [MedsListStand.displayName],
 * как и во всех остальных шагах лекарств.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class MedsEditScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Когда("^человек открывает его правку$")
    fun thePersonOpensItsEditForm() {
        val medication = medicationToEdit()
        MedsFormStand.openEdit(world, medication)
        MedsFormStand.ask(world) { test -> test.onNodeWithTag(TAG_NAME_FIELD).assertIsDisplayed() }
    }

    @Тогда("^в шапке формы стоит название «([^»]+)»$")
    fun theFormHeaderNamesThatMedication(medication: String) {
        val shown = MedsListStand.displayName(world, medication)
        val mode = MedsFormStand.openEdit(world, medicationToEdit()).mode
        assertEquals("the form must be opened for editing", shown, (mode as FormMode.Edit).title)
        MedsFormStand.ask(world) { test ->
            val header = MedsProbe.texts(test, TAG_SCREEN_HEADER)
            assertTrue("the header must read «$shown»: $header", shown in header)
            assertTrue(
                "the edit form must not be titled as a new medication",
                context.getString(R.string.form_title_new) !in header,
            )
        }
    }

    @Тогда("^нижняя кнопка подписана «(.+)»$")
    fun theBottomButtonIsLabelled(label: String) {
        MedsFormStand.ask(world) { test ->
            val texts = MedsProbe.textsOf(test.onNodeWithTag(TAG_SAVE_BUTTON).fetchSemanticsNode())
            assertTrue("the save button must read «$label»: $texts", label in texts)
        }
    }

    @Тогда("^поля заполнены текущими значениями лекарства$")
    fun theFieldsHoldTheCurrentValues() {
        val medication = medicationToEdit()
        val expected = MedicationDraft.of(medication).normalized()
        val draft = MedsFormStand.openEdit(world, medication).draft
        assertEquals(
            "the fields must be filled from the medication (MedicationFormViewModel.open)",
            expected,
            draft,
        )
        MedsFormStand.ask(world) { test ->
            // Значение поля лежит у строки ввода внутри поля, а не у самого поля.
            test.onNode(
                hasTestTag(TAG_NAME_FIELD) and hasAnyDescendant(hasText(medication.name)),
                useUnmergedTree = true,
            ).assertExists()
            test.onAllNodesWithTag(TAG_TIME_ROW, useUnmergedTree = true)
                .assertCountEquals(medication.times.size)
        }
    }

    /**
     * Сменить профиль нельзя — это отсутствие, и проверяется оно там, где изменение было бы:
     * в черновике, в контракте намерений и на собранной форме. Модель лекарства профиль
     * после создания не меняет (ИНВ-3), и варианта «сменить профиль» нет ни одного.
     */
    @Тогда("^сменить профиль лекарства нельзя$")
    fun theProfileCannotBeChanged() {
        val medication = medicationToEdit()
        assertEquals(
            "the draft must keep the profile of the medication",
            medication.profileId,
            MedsFormStand.openEdit(world, medication).draft.profileId,
        )
        val intents = MedFormIntent::class.java.declaredClasses.map { it.simpleName }
        assertTrue(
            "the form contract can ask to change the profile: ${intents.filter { it.hasProfileWord() }}",
            intents.none { it.hasProfileWord() },
        )
        MedsFormStand.ask(world) { test ->
            val labels = MedsProbe.labelsOf(test.onRoot(useUnmergedTree = true).fetchSemanticsNode())
            assertTrue(
                "a profile control is on the form: ${labels.filter { it.hasProfileWord() }}",
                labels.none { it.hasProfileWord() },
            )
        }
    }

    /** Лекарство, о котором говорит сценарий: Предыстория завела его последним. */
    private fun medicationToEdit(): Medication = world.medications.get(
        requireNotNull(world.medications.last.takeIf { it.isNotEmpty() }) {
            "no medication was created for the edit"
        },
    )

    private fun String.hasProfileWord(): Boolean =
        contains(PROFILE_WORD, ignoreCase = true) || contains(PROFILE_WORD_EN, ignoreCase = true)

    private companion object {
        const val PROFILE_WORD = "профиль"
        const val PROFILE_WORD_EN = "profile"
    }
}
