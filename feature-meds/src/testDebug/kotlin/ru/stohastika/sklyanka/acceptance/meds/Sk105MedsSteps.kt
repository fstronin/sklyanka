package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.DraftField
import ru.stohastika.sklyanka.domain.medication.StrengthUnit
import ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft
import ru.stohastika.sklyanka.domain.medication.doseUnit
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.undo.UndoableActionStore
import ru.stohastika.sklyanka.domain.undo.UndoableDeletion
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.TAG_COUNTER_CARD
import ru.stohastika.sklyanka.feature.meds.card.MedCardIntent
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_CONFIRM
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_SHEET
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_SHEET_TITLE
import ru.stohastika.sklyanka.feature.meds.card.TAG_WEEK_BLOCK
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.MedFormUiState
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormScreen
import ru.stohastika.sklyanka.feature.meds.form.TAG_DOSE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_END_DATE
import ru.stohastika.sklyanka.feature.meds.form.TAG_FOOD_RELATION
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_PREVIEW
import ru.stohastika.sklyanka.feature.meds.form.TAG_MODE_SWITCH
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_PERIOD_PRESETS
import ru.stohastika.sklyanka.feature.meds.form.TAG_RELEASE_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_START_DATE
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_STRENGTH_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_TABLET_SHAPE_ROW
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIMES_BLOCK
import ru.stohastika.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.ui.component.TAG_ADHERENCE_DAY
import ru.stohastika.sklyanka.ui.component.TAG_ADHERENCE_LEGEND
import ru.stohastika.sklyanka.ui.component.TAG_ADHERENCE_WEEK
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_WORD_LABEL
import ru.stohastika.sklyanka.ui.component.TAG_PINNED_HEADER_BAR
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import ru.stohastika.sklyanka.ui.component.TAG_TEXT_FIELD_ERROR
import ru.stohastika.sklyanka.ui.component.TAG_TEXT_FIELD_INPUT
import ru.stohastika.sklyanka.ui.component.matchesConfirmationWord
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки SKL-105 в модуле лекарств (группа `@meds`).
 *
 * Сценарии задачи говорят о том, что человек видит: сжатая неделя карточки, два названных
 * блока дозы, подпись превью, слово-подтверждение удаления и нижние поля прокрутки. Шаги
 * стоят на том же стенде, что и остальные экранные шаги лекарств ([MedCardStand],
 * [MedsFormStand], [MedsListStand]): состояния собираются тем же путём, что продуктовые
 * ViewModel, а нажатия уходят намерениями и применяются доменом. Общее берётся у соседних
 * классов шагов вызовом, а не вторым объявлением строки: дубль выражения внутри прогона
 * ломает сборку (`:acceptance:verifyAcceptanceSteps`).
 *
 * Три места стендом не выразить, и там шаг собирает композицию сам, называя почему:
 * **попытка сохранения** (стенд формы ведёт черновик, но не путь сохранения — перечень
 * нарушений отдаёт доменный `ValidateMedicationDraft`, то же правило, что у
 * `SaveMedicationUseCase`), **подпись превью** (её собирает `feature-meds` из ресурсов
 * и черновика) и **нижние поля прокрутки** (их задают геометрия и панель, а не модель).
 *
 * Прогон идёт в окне высотой 3000 dp: сценарии говорят о нижних блоках карточки и формы,
 * а `assertIsDisplayed` унаследованных шагов требует видимости.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class Sk105MedsSteps(
    private val world: AcceptanceWorld,
    /** Шаги шторки SKL-99: «Отменить» в снэкбаре нажимается их же путём, а не вторым. */
    private val deletions: MedDeletionScreenSteps,
) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Лекарство, о котором говорит сценарий: карточку и шторку открывают шаги с именем. */
    private var focused: String = ""

    // --- Предыстория: ряды и недели --------------------------------------------------------

    /**
     * Десять лекарств с приёмом в одно время — список длиннее окна телефона.
     *
     * Имена сценарные и без имени в документе: сценарий говорит о числе карточек, а не
     * о том, как они называются, — как у `ScenarioMedications.ensureCount`.
     */
    @Допустим("^у профиля «([^»]+)» есть десять лекарств с приёмом в (\\d{2}:\\d{2})$")
    fun theProfileHasTenMedicationsWithAnIntakeAt(profile: String, time: String) {
        val at = LocalTime.parse(time)
        (1..TEN_MEDICATIONS).forEach { number ->
            world.medications.add(
                profile = world.profiles,
                profileName = profile,
                name = "$UNNAMED_MEDICATION $number",
                times = listOf(at),
            )
        }
        assertEquals(
            "the premise requires ten medications",
            TEN_MEDICATIONS,
            world.medications.names().size,
        )
    }

    /**
     * Четыре приёма на неделе, три из них — в завершённых днях.
     *
     * Один приём в день: `taken` завершённых дней отмечены принятыми, остальные —
     * пропущены, а сегодняшний ждёт своего часа (ЭКР-6 — незавершённые сутки в исход
     * не входят). Курс начинается до первого из этих дней, иначе день стоял бы
     * «до начала курса» пустым.
     */
    @Допустим("^у лекарства «([^»]+)» на неделе (\\d+) приёма, из них (\\d+) приняты в завершённых днях$")
    fun thatMedicationHasThatManyIntakesInTheWeek(medication: String, planned: Int, taken: Int) {
        focused = medication
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = medication,
            times = listOf(MORNING),
            startDate = world.clock.today().minusDays(planned.toLong()),
        )
        val card = world.medications.get(medication)
        (0 until planned).forEach { back ->
            val day = world.clock.today().minusDays(back.toLong())
            val status = when {
                back == 0 -> IntakeStatus.PENDING
                back <= taken -> IntakeStatus.TAKEN
                else -> IntakeStatus.MISSED
            }
            store(card.id, day.atTime(MORNING), status)
        }
        assertEquals(
            "the premise requires taken intakes in completed days",
            taken,
            world.intakes.of(medication).count { it.status == IntakeStatus.TAKEN },
        )
    }

    // --- Предыстория: заполненная форма ----------------------------------------------------

    @Допустим("^человек заполнил в форме нового лекарства только название «([^»]+)»$")
    fun thePersonFilledOnlyTheName(name: String) {
        MedsFormStand.open(world)
        MedsFormStand.intent(world, MedFormIntent.NameChanged(name))
    }

    /**
     * Заполненная форма нового лекарства: название, форма, вид, доза и дозировка.
     *
     * Название сценарий не называет — оно здесь представительное: проверяются числа
     * и слова превью, а не имя. Форма и вид идут намерениями — тем же путём, каким их
     * выбирает `MedicationFormViewModel.reduce`.
     */
    @Допустим(
        "^человек заполнил в форме нового лекарства название, выбрал форму «([^»]+)», " +
            "вид «([^»]+)», дозу (\\S+) и дозировку (\\S+)$",
    )
    fun thePersonFilledTheWholeForm(form: String, shape: String, dose: String, strength: String) {
        MedsFormStand.open(world)
        MedsFormStand.intent(world, MedFormIntent.NameChanged(REPRESENTATIVE_NAME))
        MedsFormStand.intent(world, MedFormIntent.ReleaseFormChanged(formOf(form)))
        MedsFormStand.intent(world, MedFormIntent.ShapeChanged(shapeOf(shape)))
        MedsFormStand.intent(world, MedFormIntent.DoseAmountChanged(dose))
        MedsFormStand.intent(world, MedFormIntent.StrengthChanged(strength))
    }

    // --- Сжатая неделя (V-1, §5.2) ---------------------------------------------------------

    @Тогда("^в блоке «Последние 7 дней» стоят заголовок и «([^»]+)»$")
    fun theWeekBlockCarriesTheHeadingAndThatPhrase(phrase: String) {
        assertEquals(
            "the scenario must quote the resource",
            phrase,
            context.getString(R.string.card_week_empty),
        )
        val heading = context.getString(R.string.card_section_week)
        MedCardStand.ask(world, focusedMedication()) { test ->
            val texts = MedsProbe.texts(test, TAG_WEEK_BLOCK)
            // Надпись блока набрана капителью (`SectionLabel`): сверяется слово, а не регистр.
            assertTrue(
                "the week block must be titled «$heading»: $texts",
                texts.any { it.equals(heading, ignoreCase = true) },
            )
            assertTrue("the week block must say «$phrase»: $texts", phrase in texts)
        }
    }

    /**
     * Ряд, кружок «сегодня» и легенда на сжатой неделе не рисуются (V-1, §5.2).
     *
     * Кружок сегодняшнего дня проверяется по доступному имени — тем же, каким его читает
     * программа чтения с экрана: он один называет место в неделе, а не исход.
     */
    @Но("^ряда столбиков недели, кружка сегодняшнего дня и легенды на экране нет$")
    fun theWeekStripTheTodayCircleAndTheLegendAreAbsent() {
        val today = context.getString(R.string.cd_adherence_today, fullWeekday())
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onAllNodesWithTag(TAG_ADHERENCE_WEEK, useUnmergedTree = true).assertCountEquals(0)
            test.onAllNodesWithTag(TAG_ADHERENCE_DAY, useUnmergedTree = true).assertCountEquals(0)
            test.onAllNodesWithTag(TAG_ADHERENCE_LEGEND, useUnmergedTree = true).assertCountEquals(0)
            test.onAllNodesWithContentDescription(today, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Но("^дня без данных в легенде нет$")
    fun theLegendHasNoItemAboutMissingData() {
        val legend = listOf(
            UiR.string.adherence_legend_taken,
            UiR.string.adherence_legend_missed,
            UiR.string.adherence_legend_today,
        ).map { context.getString(it) }
        MedCardStand.ask(world, focusedMedication()) { test ->
            val texts = MedsProbe.texts(test, TAG_ADHERENCE_LEGEND)
            // Легенда объясняет исходы, а не отсутствие данных: три пункта — и ни одного
            // сверх них. Четвёртый («день без данных») не влезал бы в строку на 200 % ни
            // в одной из шести локалей (§5.2).
            assertEquals("the legend must explain the outcomes only: $texts", legend.sorted(), texts.sorted())
        }
    }

    // --- Ряд дозы: два названных блока (V-3, §5.3) -----------------------------------------

    @Тогда("^сверху вниз стоят блок «Доза на один приём» и блок «Дозировка препарата»$")
    fun theDoseAndTheStrengthStandAsTwoNamedBlocks() {
        val dose = context.getString(R.string.form_label_dose)
        val strength = context.getString(R.string.form_label_strength)
        MedsFormStand.ask(world) { test ->
            val doseTop = topOfLabel(test, dose, TAG_DOSE_FIELD)
            val strengthTop = topOfLabel(test, strength, TAG_STRENGTH_FIELD)
            assertTrue(
                "the dose block must stand above the strength block: $doseTop < $strengthTop",
                doseTop < strengthTop,
            )
        }
    }

    @И("^под блоком дозировки сказано, что поле необязательное и в расчётах не участвует$")
    fun underTheStrengthBlockTheFieldIsCalledOptionalAndOutOfCalculations() {
        val hint = context.getString(R.string.form_strength_hint)
        // Пояснение обязано назвать и необязательность, и то, что поле не участвует
        // в расчётах (§5.3): одно без другого читается как «заполни всё равно».
        assertTrue("the hint must call the field optional: $hint", hint.lowercase().contains(OPTIONAL_WORD))
        assertTrue("the hint must keep it out of calculations: $hint", hint.lowercase().contains(OUT_OF_CALCULATIONS))
        MedsFormStand.ask(world) { test ->
            val strengthTop = topOfLabel(test, context.getString(R.string.form_label_strength), TAG_STRENGTH_FIELD)
            val hintNode = test.onAllNodesWithText(hint, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()
            assertTrue(
                "the hint must stand under the strength block",
                hintNode != null && hintNode.boundsInRoot.top > strengthTop,
            )
        }
    }

    @Тогда("^оба поля блока дозы пусты$")
    fun bothFieldsOfTheDoseBlockAreEmpty() {
        MedsFormStand.ask(world) { test ->
            // Сверяется поле ввода, а не блок: в блоке стоит ещё и подпись единицы («шт»),
            // и пустой блок — это пустое поле, а не отсутствие слова.
            assertEquals("the dose field must be empty", "", inputTextOf(test, TAG_DOSE_FIELD))
            assertEquals("the strength field must be empty", "", inputTextOf(test, TAG_STRENGTH_FIELD))
        }
    }

    /**
     * Недоступность кнопки — только из-за названия (10-behavior §5.3).
     *
     * Проверка с обеих сторон: без названия кнопка недоступна при пустых полях дозы,
     * а с названием — доступна, хотя оба поля дозы по-прежнему пусты.
     */
    @И("^кнопка сохранения недоступна только из-за пустого названия$")
    fun theSaveButtonIsDisabledBecauseOfTheEmptyNameOnly() {
        MedsFormStand.ask(world) { test ->
            test.onNodeWithTag(TAG_SAVE_BUTTON, useUnmergedTree = true).assertIsNotEnabled()
        }
        MedsFormStand.intent(world, MedFormIntent.NameChanged(REPRESENTATIVE_NAME))
        val state = MedsFormStand.open(world)
        assertNull("the dose must stay empty", state.draft.doseAmount)
        assertNull("the strength must stay empty", state.draft.strength)
        MedsFormStand.ask(world) { test ->
            test.onNodeWithTag(TAG_SAVE_BUTTON, useUnmergedTree = true).assertIsEnabled()
        }
    }

    // --- Отказ в сохранении (ИНВ-4, ИНВ-5) -------------------------------------------------

    @Когда("^человек нажимает «Сохранить»$")
    fun thePersonPressesSave() {
        val intents = MedsFormStand.act(world) { test ->
            test.onNodeWithTag(TAG_SAVE_BUTTON, useUnmergedTree = true).performClick()
        }
        assertTrue("the save button must report the attempt: $intents", MedFormIntent.Save in intents)
    }

    @Тогда("^нарушение показано под блоком «Доза на один приём»$")
    fun theViolationIsShownUnderTheDoseBlock() {
        val message = context.getString(R.string.error_dose_not_positive)
        askAfterFailedSave { test ->
            val error = test.onNode(
                hasTestTag(TAG_TEXT_FIELD_ERROR) and hasAnyAncestor(hasTestTag(TAG_DOSE_FIELD)),
                useUnmergedTree = true,
            ).fetchSemanticsNode()
            assertTrue(
                "the dose violation must be printed under its own block: ${MedsProbe.textsOf(error)}",
                message in MedsProbe.textsOf(error),
            )
        }
    }

    /**
     * Экран прокручен к первому нарушению.
     *
     * Проверяется то, ради чего прокрутка и заведена: сообщение не осталось за пределами
     * видимой области и не спряталось под закреплённым рядом. Блок — первый нарушенный
     * (у пустой дозы поле идёт раньше расписания).
     *
     * Композиция сжата до телефонного окна: в окне стенда (3000 dp) форма помещается
     * целиком, прокрутки не случается, и обе границы сходились бы при любом поведении.
     */
    @И("^форма прокручена к первому нарушению$")
    fun theFormIsScrolledToTheFirstViolation() {
        val state = MedsFormStand.open(world)
        assertEquals(
            "the first violation must be the dose (ИНВ-4)",
            DraftField.DOSE,
            ValidateMedicationDraft()(state.draft).first().field,
        )
        askAfterFailedSave(formSize = PHONE_FORM) { test ->
            val viewport = test.onNode(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val dose = test.onNodeWithTag(TAG_DOSE_FIELD, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val bar = test
                .onNodeWithTag(TAG_PINNED_HEADER_BAR, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue(
                "the first violation must stand below the pinned row: $dose vs $bar",
                dose.top >= bar.bottom - SLACK,
            )
            assertTrue(
                "the first violation must stay inside the visible area: $dose vs $viewport",
                dose.bottom <= viewport.bottom + SLACK,
            )
        }
    }

    @Но("^под блоком «Дозировка препарата» нарушения нет$")
    fun thereIsNoViolationUnderTheStrengthBlock() {
        val state = MedsFormStand.open(world)
        assertTrue(
            "the optional strength must carry no violation (ИНВ-5): ${state.violations}",
            ValidateMedicationDraft()(state.draft).none { it.field == DraftField.STRENGTH },
        )
        askAfterFailedSave { test ->
            test.onNode(
                hasTestTag(TAG_TEXT_FIELD_ERROR) and hasAnyAncestor(hasTestTag(TAG_STRENGTH_FIELD)),
                useUnmergedTree = true,
            ).assertDoesNotExist()
        }
    }

    // --- Превью: форма, вид и числа (§4.16, §5.3) -------------------------------------------

    /**
     * Подпись превью — по тому же правилу, что `medicationPreviewNumbers` (ЭКР-7).
     *
     * Числа берутся у черновика, а слова — у ресурсов: «2 шт · 48 мг». Сценарий называет
     * числа «2 таблетки · 48 мг» словами нормы, а единицу количества печатает продукт
     * (`dose_suffix_piece` у штучных форм), поэтому сверяется собранная строка, а числа
     * сценария обязаны быть в ней названы.
     */
    @Тогда("^превью называет «(.+)»$")
    fun thePreviewNames(caption: String) {
        val state = MedsFormStand.open(world)
        val title = expectedPreviewTitle(state)
        val numbers = expectedPreviewNumbers(state)
        val fromScenario = NUMBERS.findAll(caption).map { it.value }.toList()
        MedsFormStand.ask(world) { test ->
            val texts = MedsProbe.texts(test, TAG_MEDICATION_PREVIEW)
            assertTrue("the preview must name the form and the shape «$title»: $texts", title in texts)
            assertTrue("the preview must name the numbers «$numbers»: $texts", numbers in texts)
            fromScenario.forEach { number ->
                assertTrue("the numbers must name $number: $numbers", number in numbers)
            }
        }
    }

    @И("^при пустой дозировке в строке превью остаются форма и вид$")
    fun withAnEmptyStrengthThePreviewKeepsTheFormAndTheShape() {
        MedsFormStand.intent(world, MedFormIntent.StrengthChanged(""))
        val state = MedsFormStand.open(world)
        assertNull("the strength must be empty now", state.draft.strength)
        val title = expectedPreviewTitle(state)
        val numbers = expectedPreviewNumbers(state)
        MedsFormStand.ask(world) { test ->
            val texts = MedsProbe.texts(test, TAG_MEDICATION_PREVIEW)
            assertTrue("the preview must keep the form and the shape «$title»: $texts", title in texts)
            assertTrue("the numbers must keep only the dose «$numbers»: $texts", numbers in texts)
            val dangling = texts.any { text ->
                val trimmed = text.trim()
                trimmed.startsWith(PREVIEW_NUMBER_SEPARATOR.trim()) ||
                    trimmed.endsWith(PREVIEW_NUMBER_SEPARATOR.trim())
            }
            assertTrue("no separator may be left without a number: $texts", dangling.not())
        }
    }

    // --- Шторка удаления: слово-подтверждение (V-4, §5.7) -----------------------------------

    @Допустим("^человек открыл шторку удаления лекарства «([^»]+)»$")
    fun thePersonHasOpenedTheDeletionSheet(medication: String) {
        focused = medication
        MedCardStand.ask(world, medication, deleteConfirmation = true) { test ->
            test.onNodeWithTag(TAG_DELETE_SHEET, useUnmergedTree = true).assertExists()
        }
    }

    @Когда("^человек открывает шторку удаления лекарства «([^»]+)»$")
    fun thePersonOpensTheDeletionSheet(medication: String) {
        focused = medication
        val intents = MedCardStand.act(world, medication) { test ->
            test.onNodeWithTag(TAG_DELETE, useUnmergedTree = true).performClick()
        }
        assertTrue("«Удалить» must open the confirmation sheet: $intents", MedCardIntent.DeleteRequested in intents)
        thePersonHasOpenedTheDeletionSheet(medication)
    }

    @Тогда("^шторка просит ввести слово «([^»]+)»$")
    fun theSheetAsksToTypeTheWord(word: String) {
        val expected = context.getString(R.string.medication_delete_word)
        assertEquals("the scenario must quote the word from resources", word, expected)
        val label = context.getString(UiR.string.confirm_word_label, expected)
        MedCardStand.ask(world, focusedMedication(), deleteConfirmation = true) { test ->
            val texts = MedsProbe.texts(test, TAG_CONFIRM_WORD_LABEL)
            assertTrue("the sheet must ask «$label»: $texts", label in texts)
        }
    }

    @Но("^лекарство ещё не удалено$")
    fun theMedicationIsNotDeletedYet() {
        assertFalse(
            "the sheet must not delete anything by itself",
            world.medications.get(world.medicationNamed(focusedMedication())).isDeleted,
        )
    }

    @И("^название лекарства в шторке названо$")
    fun theSheetNamesTheMedication() {
        val name = MedsListStand.displayName(world, focusedMedication())
        MedCardStand.ask(world, focusedMedication(), deleteConfirmation = true) { test ->
            val texts = MedsProbe.texts(test, TAG_DELETE_SHEET_TITLE)
            assertTrue("the sheet must name «$name»: $texts", texts.any { name in it })
        }
    }

    /**
     * Несовпавшее слово кнопку не включает.
     *
     * Слово сценария набирает объявленный выше шаг ввода, а проверка набирает своё —
     * тем же приёмом, что `MedDeletionScreenSteps.theConfirmButtonIsAvailable`: композиция
     * стенда собирается заново на каждый шаг, и поле держит слово локально.
     */
    @Тогда("^кнопка «Удалить» недоступна$")
    fun theConfirmButtonIsNotAvailable() {
        val word = context.getString(R.string.medication_delete_word)
        val wrong = word.dropLast(1)
        assertFalse("the check must type a word that does not match", matchesConfirmationWord(wrong, word))
        MedCardStand.ask(world, focusedMedication(), deleteConfirmation = true) { test ->
            test.onNodeWithTag(TAG_TEXT_FIELD_INPUT, useUnmergedTree = true).performTextReplacement(wrong)
            test.onNodeWithTag(TAG_DELETE_CONFIRM, useUnmergedTree = true).assertIsNotEnabled()
        }
    }

    /**
     * Под полем нет ни объяснения, ни состояния ошибки (§5.7).
     *
     * Неверный ввод виден недоступной кнопкой: пока слово не совпало, второго ответа
     * на вопрос «подтверждено ли» быть не должно — ни текста под полем, ни состояния
     * ошибки у самого поля.
     */
    @И("^под полем нет ни пояснения, ни состояния ошибки$")
    fun thereIsNoExplanationAndNoErrorStateUnderTheField() {
        val word = context.getString(R.string.medication_delete_word)
        val wrong = word.dropLast(1)
        val actions = listOf(context.getString(R.string.card_delete), context.getString(UiR.string.action_cancel))
        MedCardStand.ask(world, focusedMedication(), deleteConfirmation = true) { test ->
            test.onNodeWithTag(TAG_TEXT_FIELD_INPUT, useUnmergedTree = true).performTextReplacement(wrong)
            test.onAllNodesWithTag(TAG_TEXT_FIELD_ERROR, useUnmergedTree = true).assertCountEquals(0)
            val input = test.onNodeWithTag(TAG_TEXT_FIELD_INPUT, useUnmergedTree = true).fetchSemanticsNode()
            assertNull(
                "the field must carry no error state: ${input.config}",
                input.config.getOrNull(SemanticsProperties.Error),
            )
            val sheet = test.onNodeWithTag(TAG_DELETE_SHEET, useUnmergedTree = true).fetchSemanticsNode()
            val under = textsBelow(sheet, input.boundsInRoot.bottom)
            assertTrue(
                "only the actions may stand under the field: $under",
                under.all { it in actions },
            )
        }
    }

    // --- Удаление и окно отмены (ПР-14, ПМ-24) ---------------------------------------------

    @Тогда("^лекарство исчезает из списка лекарств$")
    fun theMedicationDisappearsFromTheList() {
        val name = focusedMedication()
        val card = world.medications.get(world.medicationNamed(name))
        assertTrue("the deletion must take effect", card.isDeleted)
        MedsListStand.ask(world) { test ->
            assertTrue(
                "the list must not carry the deleted medication",
                MedsListStand.state(world).items.none { it.medicationId == card.id },
            )
            test.onAllNodesWithText(card.name, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @И("^на экране живёт окно «Отменить» со сроком (\\d+) секунд$")
    fun theUndoWindowIsLiveForThatManySeconds(seconds: Int) {
        assertEquals(
            "the scenario must quote the window",
            seconds.toLong(),
            UndoableActionStore.WINDOW.seconds,
        )
        val live = world.undoActions.live()
        assertTrue("the window must be about the deletion: $live", live is UndoableDeletion)
        assertEquals(
            "the window must expire in $seconds seconds",
            Duration.ofSeconds(seconds.toLong()),
            Duration.between(world.clock.now(), requireNotNull(live).expiresAt),
        )
        val name = MedsListStand.displayName(world, focusedMedication())
        val text = context.getString(R.string.meds_deleted_snackbar, name)
        MedsListStand.ask(world) { test ->
            val snackbar = test.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).fetchSemanticsNode()
            val texts = MedsProbe.textsOf(snackbar)
            assertTrue("the snackbar must read «$text»: $texts", text in texts)
        }
    }

    /**
     * «Отменить» возвращает лекарство вместе с приёмами и будильниками.
     *
     * Нажатие — объявленным шагом SKL-99: строка SKL-105 говорит о том же действии
     * человека, и второе правило нажатия было бы вторым ответом на вопрос «как отменяют».
     * Возврат лекарства, приёмов и будильников делает домен — шаг проверяет следствие.
     */
    @И("^«Отменить» возвращает лекарство вместе с приёмами и будильниками$")
    fun theUndoReturnsTheMedicationWithItsIntakesAndAlarms() {
        val name = focusedMedication()
        val card = world.medications.get(world.medicationNamed(name))
        deletions.thePersonUndoesInTheSnackbar()
        assertFalse(
            "the medication must come back active",
            world.medications.get(world.medicationNamed(name)).isDeleted,
        )
        val intakes = world.intakes.of(name)
        assertTrue("its intakes must come back: $intakes", intakes.any { it.isPending })
        val alarms = world.reminders.scheduled().keys
            .filter { intakeId -> world.intakes.get(intakeId)?.medicationId == card.id }
        assertTrue("the alarms must come back: ${world.reminders.scheduled()}", alarms.isNotEmpty())
    }

    // --- Нижние поля прокрутки (V-10) ------------------------------------------------------

    @Когда("^человек прокручивает содержимое формы до последнего блока$")
    fun thePersonScrollsTheFormToTheLastBlock() {
        MedsFormStand.ask(world) { test ->
            // Последний блок формы — «Период приёма», а в нём ниже всего стоит ряд пресетов
            // срока окончания: по нему и видно, упирается ли содержимое в панель (V-10).
            test.onNodeWithTag(TAG_PERIOD_PRESETS, useUnmergedTree = true).performScrollTo()
            test.waitForIdle()
        }
    }

    @Тогда("^между последним блоком формы и панелью кнопки сохранения есть отступ$")
    fun betweenTheLastBlockAndTheSavePanelThereIsAGap() {
        MedsFormStand.ask(world) { test ->
            val last = test.onNodeWithTag(TAG_PERIOD_PRESETS, useUnmergedTree = true).fetchSemanticsNode()
            val button = test.onNodeWithTag(TAG_SAVE_BUTTON, useUnmergedTree = true).fetchSemanticsNode()
            val gap = with(button.layoutInfo.density) {
                (button.boundsInRoot.top - last.boundsInRoot.bottom).toDp()
            }
            // Поле прокрутки формы равно шагу поля экрана (§4.1): без него последний блок
            // упирался в панель вплотную и читался обрезанным (V-10).
            assertTrue(
                "the gap between the last block and the save panel is $gap, below ${SklyankaSpacing.Screen}",
                gap.value + SLACK_DP >= SklyankaSpacing.Screen.value,
            )
        }
    }

    @И("^ни один блок формы панелью не обрезан$")
    fun noFormBlockIsCutByThePanel() {
        MedsFormStand.ask(world) { test ->
            val button = test.onNodeWithTag(TAG_SAVE_BUTTON, useUnmergedTree = true).fetchSemanticsNode()
            val viewport = test.onNode(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNode()
            // Панель — сосед прокрутки, а не наложение: её верх не заходит в видимую
            // область прокрутки, и потому ни один блок под ней не прячется.
            assertTrue(
                "the save panel must not overlap the scroll viewport: ${viewport.boundsInRoot}",
                viewport.boundsInRoot.bottom <= button.boundsInRoot.top + SLACK,
            )
            FORM_BLOCK_TAGS.forEach { tag ->
                test.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().forEach { block ->
                    assertTrue(
                        "the block $tag must not be cut off by the panel: ${block.boundsInRoot}",
                        block.boundsInRoot.bottom <= viewport.boundsInRoot.bottom + SLACK,
                    )
                }
            }
        }
    }

    @Когда("^человек прокручивает список лекарств до конца$")
    fun thePersonScrollsTheMedicationListToTheEnd() {
        MedsListStand.ask(world) { test ->
            // Список длиной в десять карточек прокручивается до конца: прокрутка идёт
            // свитками, а не нажатием, потому что «до конца» — это движение, а не выбор.
            repeat(SCROLL_GESTURES) {
                test.onNode(hasScrollAction(), useUnmergedTree = true).performTouchInput { swipeUp() }
            }
            test.waitForIdle()
        }
    }

    @Тогда("^последняя карточка видна целиком$")
    fun theLastCardIsFullyVisible() {
        val last = testCard()
        MedsListStand.ask(world) { test ->
            val viewport = test.onNode(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val card = test.onNode(last, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(
                "the last card must stay inside the list viewport: $card vs $viewport",
                card.top >= viewport.top - SLACK && card.bottom <= viewport.bottom + SLACK,
            )
            test.onNode(last, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @И("^поле прокрутки снизу не меньше высоты круглой кнопки и двух боковых полей$")
    fun theScrollFieldBelowIsNotSmallerThanTheRoundButtonAndTwoSideFields() {
        val last = testCard()
        MedsListStand.ask(world) { test ->
            val viewport = test.onNode(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNode()
            val card = test.onNode(last, useUnmergedTree = true).fetchSemanticsNode()
            val button = test.onNodeWithTag(TAG_ADD_MEDICATION, useUnmergedTree = true).fetchSemanticsNode()
            // Меньшее поле — дефект: последняя карточка недостижима под кнопкой (§4.1).
            // Считается поле прокрутки снизу, а не запас разметки: у конца списка оно равно
            // подложке `FloatingActionInset` — высоте наложения плюс два боковых поля.
            val field = with(viewport.layoutInfo.density) {
                (viewport.boundsInRoot.bottom - card.boundsInRoot.bottom).toDp()
            }
            assertTrue(
                "the field below the last card is $field, below ${SklyankaSizes.FloatingActionInset}",
                field.value + SLACK_DP >= SklyankaSizes.FloatingActionInset.value,
            )
            assertTrue(
                "the round button must not cover the last card: ${button.boundsInRoot}",
                card.boundsInRoot.bottom <= button.boundsInRoot.top + SLACK,
            )
        }
    }

    // --- Обвязка ---------------------------------------------------------------------------

    private fun focusedMedication(): String = focused.ifEmpty { world.medications.last }

    /** Карточка последнего лекарства списка: порядок задаёт сам экран, а не сценарий. */
    private fun testCard(): SemanticsMatcher {
        val id = MedsListStand.state(world).items.last().medicationId
        val name = requireNotNull(world.medications.byId(id)) { "unknown medication id: $id" }.name
        return hasTestTag(TAG_MEDICATION_CARD) and hasAnyDescendant(hasText(name))
    }

    /**
     * Собрать форму с нарушениями последней попытки сохранить.
     *
     * Стенд ведёт черновик, но не путь сохранения: перечень нарушений в продукте отдаёт
     * `SaveMedicationUseCase`, а он — то же доменное `ValidateMedicationDraft`. Здесь
     * берётся то же правило, иначе появилось бы второе «какое поле нарушено».
     *
     * Нарушения приходят второй фазой, после первой раскладки: так их появление — то же
     * событие, что нажатие «Сохранить», и прокрутка к первому нарушению запускается при
     * уже измеренных якорях блоков. [formSize] сжимает композицию до телефонного окна:
     * окно стенда (3000 dp) вмещает форму целиком, и без сжатия прокрутки не случается.
     */
    private fun <T> askAfterFailedSave(formSize: DpSize? = null, question: (ComposeUiTest) -> T): T {
        val state = MedsFormStand.open(world)
        val failed = state.copy(violations = ValidateMedicationDraft()(state.draft))
        val live = mutableStateOf(state)
        var answer: T? = null
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    MedicationFormScreen(
                        state = live.value,
                        onIntent = {},
                        onCancel = {},
                        onPickDate = {},
                        onPickTime = { _, _ -> },
                        modifier = formSize?.let { Modifier.size(it) } ?: Modifier,
                    )
                }
            }
            waitForIdle()
            live.value = failed
            waitForIdle()
            answer = question(this)
        }
        @Suppress("UNCHECKED_CAST")
        return answer as T
    }

    /** Верх надписи блока: надпись набрана капителью, поэтому ищется по слову без регистра. */
    private fun topOfLabel(test: ComposeUiTest, label: String, fieldTag: String): Float {
        val labelNode = test.onAllNodes(hasText(label, ignoreCase = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull { node -> node.boundsInRoot.top < testTopOf(test, fieldTag) }
        requireNotNull(labelNode) { "the form must name the block «$label»" }
        return labelNode.boundsInRoot.top
    }

    private fun testTopOf(test: ComposeUiTest, fieldTag: String): Float =
        test.onNodeWithTag(fieldTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    /** Подпись превью: форма и вид — тем же правилом, что `medicationPreviewTitle` (§4.16). */
    private fun expectedPreviewTitle(state: MedFormUiState): String {
        val form = context.getString(formLabelOf(state.draft.form))
        val shape = state.draft.shape?.let { chosen ->
            val label = context.getString(shapeLabelOf(chosen))
            " " + label.replaceFirstChar { it.lowercase(locale()) }
        }.orEmpty()
        return form + shape
    }

    /**
     * Числа превью: «2 таблетки · 48 мг» — тем же правилом, что `medicationPreviewNumbers` (§5.3).
     *
     * Порядок ЭКР-7: доза на приём, затем дозировка препарата, через « · ». Доза печатается
     * словом единицы со склонением по числу (Р-45), дозировка — словом выбранной единицы;
     * пустая часть в строку не попадает.
     */
    private fun expectedPreviewNumbers(state: MedFormUiState): String {
        val dose = state.draft.doseAmount?.let { amount ->
            val count = amount.toInt()
            context.resources.getQuantityString(
                previewDoseTemplate(state.draft.form.doseUnit()),
                count,
                amount.stripTrailingZeros().toPlainString(),
            )
        }
        val strength = state.draft.strength?.let { amount ->
            val unit = state.draft.strengthUnit ?: return@let null
            "${amount.stripTrailingZeros().toPlainString()} ${context.getString(strengthUnitTemplate(unit))}"
        }
        return listOfNotNull(dose?.takeIf { it.isNotBlank() }, strength?.takeIf { it.isNotBlank() })
            .joinToString(PREVIEW_NUMBER_SEPARATOR)
    }

    /** Семейство склонений дозы — та же шестёрка, что у `MedsText.previewDoseLabel` (Р-45). */
    private fun previewDoseTemplate(unit: DoseUnit): Int = when (unit) {
        DoseUnit.TABLET -> R.plurals.preview_dose_tablets
        DoseUnit.CAPSULE -> R.plurals.preview_dose_capsules
        DoseUnit.MILLILITRE -> R.plurals.preview_dose_millilitres
        DoseUnit.DROP -> R.plurals.preview_dose_drops
        DoseUnit.SPRAY -> R.plurals.preview_dose_sprays
        DoseUnit.INJECTION -> R.plurals.preview_dose_injections
    }

    /** Слово единицы дозировки — та же шестёрка, что у `MedsText.StrengthUnit.label` (Р-45). */
    private fun strengthUnitTemplate(unit: StrengthUnit): Int = when (unit) {
        StrengthUnit.MILLIGRAM -> R.string.strength_unit_milligram
        StrengthUnit.MICROGRAM -> R.string.strength_unit_microgram
        StrengthUnit.GRAM -> R.string.strength_unit_gram
        StrengthUnit.MILLILITRE -> R.string.strength_unit_millilitre
        StrengthUnit.INTERNATIONAL_UNIT -> R.string.strength_unit_international_unit
        StrengthUnit.PERCENT -> R.string.strength_unit_percent
    }

    private fun formLabelOf(form: MedicationForm): Int = when (form) {
        MedicationForm.TABLET -> R.string.med_form_tablet
        MedicationForm.CAPSULE -> R.string.med_form_capsule
        MedicationForm.SPRAY -> R.string.med_form_spray
        MedicationForm.SYRUP -> R.string.med_form_syrup
        MedicationForm.DROPS -> R.string.med_form_drops
        MedicationForm.INJECTION -> R.string.med_form_injection
    }

    private fun shapeLabelOf(shape: TabletShape): Int = when (shape) {
        TabletShape.ROUND -> R.string.tablet_shape_round
        TabletShape.ROUND_SCORED -> R.string.tablet_shape_round_scored
        TabletShape.OVAL -> R.string.tablet_shape_oval
        TabletShape.OBLONG -> R.string.tablet_shape_oblong
        TabletShape.TRIANGULAR -> R.string.tablet_shape_triangular
    }

    private fun formOf(label: String): MedicationForm =
        MedicationForm.entries.first { context.getString(formLabelOf(it)).equals(label, ignoreCase = true) }

    private fun shapeOf(label: String): TabletShape =
        TabletShape.entries.first { context.getString(shapeLabelOf(it)).equals(label, ignoreCase = true) }

    private fun locale() = context.resources.configuration.locales[0]

    /** Полное название дня недели — тем же шаблоном, каким его собирает экран. */
    private fun fullWeekday(): String = world.clock.today().format(
        DateTimeFormatter.ofPattern(
            context.getString(R.string.date_pattern_weekday_full),
            locale(),
        ),
    )

    /** Текст поля ввода: пустое поле не называет ни одного знака (ни `Text`, ни `EditableText`). */
    private fun inputTextOf(test: ComposeUiTest, fieldTag: String): String {
        val node = test.onNode(
            hasTestTag(TAG_TEXT_FIELD_INPUT) and hasAnyAncestor(hasTestTag(fieldTag)),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val editable = node.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        val shown = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
        return editable + shown
    }

    private fun store(medicationId: String, moment: LocalDateTime, status: IntakeStatus) {
        world.intakes.store(
            Intake(
                id = world.intakes.nextId(),
                medicationId = medicationId,
                scheduledAt = moment,
                status = status,
                actedAt = if (status == IntakeStatus.PENDING) null else world.clock.momentOf(moment),
                source = if (status == IntakeStatus.PENDING) null else IntakeSource.PHONE,
                actorProfileId = null,
            ),
        )
    }

    /** Тексты узлов, стоящих ниже [boundary]: так проверяется, что под полем ничего нет. */
    private fun textsBelow(node: SemanticsNode, boundary: Float): List<String> {
        val here = if (node.boundsInRoot.top >= boundary) {
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        } else {
            emptyList()
        }
        return here + node.children.flatMap { textsBelow(it, boundary) }
    }

    private companion object {
        /** Разделитель чисел превью — точка-пауза из макета (`medicationPreviewNumbers`). */
        const val PREVIEW_NUMBER_SEPARATOR = " · "

        /** Числа сценария в подписи превью: «2 таблетки · 48 мг» называет 2 и 48. */
        val NUMBERS = Regex("\\d+")

        const val TEN_MEDICATIONS = 10
        const val UNNAMED_MEDICATION = "лекарство"
        const val REPRESENTATIVE_NAME = "Магний B6"
        const val SCROLL_GESTURES = 3

        /** Допуск сравнения координат и размеров: одна точка на округление раскладки. */
        const val SLACK = 1f
        const val SLACK_DP = 0.5f

        /**
         * Телефонное окно проверки прокрутки: 390 dp ширины, 600 dp высоты.
         *
         * Окно стенда (3000 dp) форму вмещает целиком, и здесь нужен размер, при котором
         * содержимое выше видимой области, — иначе прокрутка к нарушению не случается.
         */
        val PHONE_FORM = DpSize(390.dp, 600.dp)

        /** Слова пояснения под блоком дозировки — требование §5.3, а не оформление. */
        const val OPTIONAL_WORD = "необязательно"
        const val OUT_OF_CALCULATIONS = "не участвует"

        val MORNING: LocalTime = LocalTime.of(8, 0)

        /** Блоки формы: панель кнопки не имеет права накрыть ни один из них (V-10). */
        val FORM_BLOCK_TAGS = listOf(
            TAG_NAME_FIELD,
            TAG_MEDICATION_PREVIEW,
            TAG_RELEASE_FORM,
            TAG_TABLET_SHAPE_ROW,
            TAG_DOSE_FIELD,
            TAG_STRENGTH_FIELD,
            TAG_MODE_SWITCH,
            TAG_COUNTER_CARD,
            TAG_TIMES_BLOCK,
            TAG_FOOD_RELATION,
            TAG_STOCK_FIELD,
            TAG_START_DATE,
            TAG_END_DATE,
            TAG_PERIOD_PRESETS,
        )
    }
}
