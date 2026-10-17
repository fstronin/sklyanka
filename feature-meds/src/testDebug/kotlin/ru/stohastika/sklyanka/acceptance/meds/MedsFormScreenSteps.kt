package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.TAG_COUNTER_CARD
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.TAG_DOSE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_END_DATE
import ru.stohastika.sklyanka.feature.meds.form.TAG_FOOD_RELATION
import ru.stohastika.sklyanka.feature.meds.form.TAG_INDEFINITE
import ru.stohastika.sklyanka.feature.meds.form.TAG_MAX_PER_DAY
import ru.stohastika.sklyanka.feature.meds.form.TAG_MODE_SWITCH
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_PACKAGE_SIZE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_PACKAGE_SIZE_HINT
import ru.stohastika.sklyanka.feature.meds.form.TAG_PACKAGE_SIZE_UNIT
import ru.stohastika.sklyanka.feature.meds.form.TAG_RELEASE_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_START_DATE
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_STRENGTH_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_TABLET_SHAPE_ROW
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIMES_BLOCK
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIME_ROW
import java.time.LocalTime
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки состава формы лекарства (SKL-09, группа `@form`; заведены SKL-26).
 *
 * Состояние формы собирает [MedsFormStand] из черновика домена, а нажатия уходят
 * намерениями: проверяется то, что человек видит, а не копия разметки. Порядок блоков
 * сверяется по позициям собранных узлов — это контракт формы, зафиксированный
 * отступлением от макета Д-2 (`TDD.md §5.6`).
 *
 * Предыстория сценариев 141, 148, 155 и 177 записана доменными строками
 * (`MedicationFormSteps`), которые для экранного прогона остаются бездейственными:
 * они меняют черновик в своём классе шагов. Стенд собирает то же состояние сам —
 * через настоящий черновик, — и это отмечено у каждого шага, где это важно.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class MedsFormScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // --- Порядок и состав блоков ---------------------------------------------------

    @Когда("^человек открывает форму нового лекарства$")
    fun thePersonOpensTheNewMedicationForm() {
        MedsFormStand.open(world)
        MedsFormStand.ask(world) { test -> test.onNodeWithTag(TAG_NAME_FIELD).assertIsDisplayed() }
    }

    @Тогда("^блоки идут в порядке: «(.+)»$")
    fun theBlocksGoInThatOrder(order: String) {
        val expected = ORDER_TOKENS.findAll(order).map { match ->
            val name = match.groupValues[1].ifEmpty { match.groupValues[2] }
            requireNotNull(BLOCK_TAGS[name]) { "unknown form block in the order: $name" }
        }.flatMap { it }.toList()
        MedsFormStand.ask(world) { test ->
            val positions = expected.map { tag ->
                val nodes = test.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
                assertTrue("the form must show the block $tag", nodes.isNotEmpty())
                nodes.minOf { it.boundsInRoot.top }
            }
            positions.zipWithNext().forEach { (upper, lower) ->
                assertTrue("blocks are out of order: $upper stands not above $lower", upper < lower)
            }
        }
    }

    @Тогда("^в блоке «Форма выпуска» доступны «(.+)»$")
    fun theReleaseFormOffersThoseValues(values: String) {
        val expected = values.split(VALUES_SEPARATOR).map { it.trim().trim('«', '»') }
        MedsFormStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_RELEASE_FORM, useUnmergedTree = true).assertCountEquals(expected.size)
            // Подпись плитки адресуется внутри блока: «Таблетка» звучит ещё и в заголовке
            // блока-превью (ОВ-94), и поиск по всему экрану нашёл бы два узла.
            expected.forEach { label ->
                test.onNode(
                    hasText(label) and hasAnyAncestor(hasTestTag(TAG_RELEASE_FORM)),
                    useUnmergedTree = true,
                ).assertIsDisplayed()
            }
        }
    }

    // --- Строка «Размер упаковки» (SKL-34) ------------------------------------------

    @Тогда("^в блоке «Остаток в упаковке» есть строка «Сейчас есть» и под ней строка «Размер упаковки»$")
    fun thePackageSizeRowStandsUnderTheStock() {
        MedsFormStand.ask(world) { test ->
            test.onNodeWithText(context.getString(R.string.form_stock_now)).assertIsDisplayed()
            val stockY = test.onNodeWithTag(TAG_STOCK_FIELD, useUnmergedTree = true)
                .fetchSemanticsNode().positionInRoot.y
            val packageY = test.onNodeWithTag(TAG_PACKAGE_SIZE_FIELD, useUnmergedTree = true)
                .fetchSemanticsNode().positionInRoot.y
            assertTrue(
                "the package size row must stand under «Сейчас есть»: $packageY < $stockY",
                packageY > stockY,
            )
        }
    }

    @Тогда("^единицей в строке «Размер упаковки» показано «(.+)»$")
    fun thePackageSizeUnitIs(unit: String) {
        MedsFormStand.ask(world) { test ->
            test.onNodeWithTag(TAG_PACKAGE_SIZE_UNIT, useUnmergedTree = true).assertTextEquals(unit)
        }
    }

    @Тогда("^под ней показана подсказка «(.+)»$")
    fun thePackageSizeHintIs(hint: String) {
        MedsFormStand.ask(world) { test ->
            test.onNodeWithTag(TAG_PACKAGE_SIZE_HINT, useUnmergedTree = true).assertIsDisplayed()
            assertTrue(
                "the hint must read «$hint»",
                test.onAllNodesWithText(hint, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Допустим("^человек выбрал форму выпуска «([^»]+)»$")
    fun thePersonHasChosenTheReleaseForm(label: String) {
        MedsFormStand.open(world)
        val intents = MedsFormStand.act(world) { test ->
            test.onNode(
                hasText(label) and hasAnyAncestor(hasTestTag(TAG_RELEASE_FORM)),
                useUnmergedTree = true,
            ).performClick()
        }
        assertTrue(
            "the release form tile «$label» must choose the form: $intents",
            intents.any { it is MedFormIntent.ReleaseFormChanged && it.form == formOf(label) },
        )
        assertEquals(formOf(label), MedsFormStand.open(world).draft.form)
    }

    @Когда("^он смотрит на блок «Вид таблетки»$")
    fun thePersonLooksAtTheTabletShapeBlock() {
        MedsFormStand.ask(world) { test -> test.onNodeWithTag(TAG_TABLET_SHAPE_ROW).assertIsDisplayed() }
    }

    @Тогда("^доступны «(.+)»$")
    fun theTabletShapesAreAvailable(values: String) {
        val expected = values.split(VALUES_SEPARATOR).map { it.trim().trim('«', '»') }
        MedsFormStand.ask(world) { test ->
            val descriptions = test.onAllNodes(
                hasAnyAncestor(hasTestTag(TAG_TABLET_SHAPE_ROW)) and
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().map { node ->
                node.config[SemanticsProperties.ContentDescription].joinToString(" ")
            }
            assertEquals("the shape row must offer exactly $expected", expected, descriptions)
        }
    }

    @Тогда("^каждая кнопка подписана для чтения с экрана этим же словом$")
    fun everyShapeButtonIsLabelledWithItsWord() {
        SHAPE_LABELS.forEach { label ->
            MedsFormStand.ask(world) { test ->
                test.onNodeWithContentDescription(context.getString(label), useUnmergedTree = true).assertExists()
            }
        }
    }

    // --- Режим приёма ----------------------------------------------------------------

    @Когда("^он выбирает режим «(По расписанию|По потребности)»$")
    fun thePersonChoosesTheMode(mode: String) {
        // Предыстория сценария записана доменной строкой «человек заполняет форму нового
        // лекарства»: для экранного прогона форма открывается здесь, и это то же
        // состояние — новый черновик.
        MedsFormStand.open(world)
        val intents = MedsFormStand.act(world) { test ->
            test.onNode(
                hasText(mode) and hasAnyAncestor(hasTestTag(TAG_MODE_SWITCH)),
                useUnmergedTree = true,
            ).performClick()
        }
        assertTrue("the mode switch must report «$mode»: $intents", intents.any { it is MedFormIntent.ModeChanged })
    }

    @Тогда("^показаны блоки «Приёмов в сутки» и «Время приёма»$")
    fun theScheduleBlocksAreShown() {
        MedsFormStand.ask(world) { test ->
            test.onNodeWithText(context.getString(R.string.form_label_intakes_per_day)).assertIsDisplayed()
            // Надпись блока набирается капителью (`SectionLabel`) — это типографика,
            // и адресуется блок тегом, а не строчной формулировкой сценария.
            test.onNodeWithTag(TAG_TIMES_BLOCK).assertIsDisplayed()
        }
    }

    @Тогда("^показана подпись «(.+)»$")
    fun theCaptionIsShown(caption: String) {
        MedsFormStand.ask(world) { test -> test.onNodeWithText(caption).assertIsDisplayed() }
    }

    @Тогда("^блоки расписания заменяются вводом ограничения «(.+)»$")
    fun theScheduleBlocksAreReplacedWithTheLimit(limit: String) {
        assertTrue("the scenario must speak of the daily limit", limit.startsWith(NO_MORE_THAN))
        MedsFormStand.ask(world) { test ->
            test.onNodeWithTag(TAG_MAX_PER_DAY).assertExists()
            test.onNodeWithTag(TAG_TIMES_BLOCK).assertDoesNotExist()
            test.onNodeWithText(context.getString(R.string.form_label_max_per_day)).assertIsDisplayed()
        }
    }

    @Тогда("^показано пояснение «(.+)»$")
    fun theExplanationIsShown(text: String) {
        MedsFormStand.ask(world) { test -> test.onNodeWithText(text).assertIsDisplayed() }
    }

    // --- Связь с едой в режиме «По потребности» (SKL-49, ОВ-83) ----------------------

    @Допустим("^человек выбрал связь с едой «([^»]+)»$")
    fun thePersonHasChosenTheFoodRelation(label: String) {
        MedsFormStand.open(world)
        val intents = MedsFormStand.act(world) { test ->
            test.onNode(
                hasText(label) and hasAnyAncestor(hasTestTag(TAG_FOOD_RELATION)),
                useUnmergedTree = true,
            ).performClick()
        }
        assertTrue(
            "the food relation tile «$label» must choose the relation: $intents",
            intents.any { it is MedFormIntent.FoodRelationChanged && it.relation == foodRelationOf(label) },
        )
        assertEquals(foodRelationOf(label), MedsFormStand.open(world).draft.foodRelation)
    }

    @Тогда("^блока «Связь с едой» на форме нет$")
    fun theFoodRelationBlockIsGone() {
        MedsFormStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_FOOD_RELATION, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Но("^блок «Остаток в упаковке» показан$")
    fun theStockBlockIsShown() {
        MedsFormStand.ask(world) { test -> test.onNodeWithTag(TAG_STOCK_FIELD).assertIsDisplayed() }
    }

    @Тогда("^блок «Связь с едой» снова показан$")
    fun theFoodRelationBlockIsBack() {
        // Блок — ряд из четырёх плиток; тег стоит на каждой, и проверка адресует состав.
        MedsFormStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_FOOD_RELATION, useUnmergedTree = true).assertCountEquals(4)
        }
    }

    @Тогда("^блок «Связь с едой» показан$")
    fun theFoodRelationBlockIsShown() {
        MedsFormStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_FOOD_RELATION, useUnmergedTree = true).assertCountEquals(4)
        }
    }

    @И("^выбранной связью с едой остаётся «([^»]+)»$")
    fun theFoodRelationChoiceStays(label: String) {
        // Режим делает выбор нередактируемым, а не отменяет его: возврат к расписанию
        // показывает прежнюю связь с едой (SKL-49).
        assertEquals(
            "the mode switch must not lose the choice",
            foodRelationOf(label),
            MedsFormStand.open(world).draft.foodRelation,
        )
    }

    // --- Счётчик приёмов и список времён ----------------------------------------------

    @Когда("^человек нажимает «Увеличить»$")
    fun thePersonPressesIncrease() {
        // Предыстория сценария задаёт времена «08:00» и «20:00» доменными строками
        // `MedicationFormSteps`; экранному стенду они не видны, и стенд собирает то же
        // состояние через черновик домена.
        MedsFormStand.open(world, times = TWO_TIMES)
        val intents = MedsFormStand.act(world) { test ->
            test.onNodeWithContentDescription(context.getString(R.string.cd_increase), useUnmergedTree = true)
                .performClick()
        }
        assertTrue("«Увеличить» must add a time: $intents", MedFormIntent.TimeAdded in intents)
    }

    @Тогда("^в списке времён появляется третья строка, а число приёмов становится (\\d+)$")
    fun theThirdTimeRowAppears(intakes: Int) {
        assertEquals(intakes, MedsFormStand.open(world).draft.intakesPerDay)
        MedsFormStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_TIME_ROW, useUnmergedTree = true).assertCountEquals(intakes)
            assertTrue(
                "the counter must read $intakes",
                MedsProbe.texts(test, TAG_COUNTER_CARD).any { it == intakes.toString() },
            )
        }
    }

    @Когда("^человек удаляет время «(\\d{2}:\\d{2})»$")
    fun thePersonDeletesThatTime(time: String) {
        val deleted = LocalTime.parse(time)
        val intents = MedsFormStand.act(world) { test ->
            test.onNodeWithContentDescription(
                context.getString(R.string.cd_delete_time, time),
                useUnmergedTree = true,
            ).performClick()
        }
        assertTrue(
            "the delete button of $time must remove it: $intents",
            intents.any {
                it is MedFormIntent.TimeRemoved
            },
        )
        assertFalse(
            "the time $time must leave the draft",
            deleted in MedsFormStand.open(world).draft.times,
        )
    }

    @Тогда("^в списке остаются две строки, а число приёмов становится (\\d+)$")
    fun theTwoTimeRowsStay(intakes: Int) {
        assertEquals(intakes, MedsFormStand.open(world).draft.intakesPerDay)
        MedsFormStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_TIME_ROW, useUnmergedTree = true).assertCountEquals(intakes)
            assertTrue(
                "the counter must read $intakes",
                MedsProbe.texts(test, TAG_COUNTER_CARD).any { it == intakes.toString() },
            )
        }
    }

    // --- Бессрочность и окончание --------------------------------------------------------

    @Когда("^переключатель «Принимать бессрочно» включён$")
    fun theIndefiniteSwitchIsOn() {
        MedsFormStand.open(world)
        if (MedsFormStand.open(world).draft.endDate != null) {
            MedsFormStand.act(world) { test -> test.onNodeWithTag(TAG_INDEFINITE).performClick() }
        }
        assertNull("the course must be open-ended (ПР-6)", MedsFormStand.open(world).draft.endDate)
        MedsFormStand.ask(world) { test ->
            val node = test.onNodeWithTag(TAG_INDEFINITE).fetchSemanticsNode()
            assertEquals(ToggleableState.On, node.config.getOrNull(SemanticsProperties.ToggleableState))
        }
    }

    @Тогда("^строка «Окончание» приглушена и показывает «(.+)»$")
    fun theEndDateRowIsMutedAndShows(value: String) {
        assertNull(MedsFormStand.open(world).draft.endDate)
        MedsFormStand.ask(world) { test ->
            val node = test.onNodeWithTag(TAG_END_DATE).fetchSemanticsNode()
            assertTrue("the muted end date must read «$value»", value in MedsProbe.textsOf(node))
            val actions = node.config.toList().map { it.key.name }
            assertFalse("the muted end date must accept no tap: $actions", actions.any { it.contains(ON_CLICK) })
        }
    }

    @Когда("^человек выключает «Принимать бессрочно»$")
    fun thePersonTurnsTheIndefiniteSwitchOff() {
        val intents = MedsFormStand.act(world) { test -> test.onNodeWithTag(TAG_INDEFINITE).performClick() }
        assertTrue(
            "the indefinite switch must be turned off: $intents",
            MedFormIntent.IndefiniteToggled(false) in intents,
        )
        val draft = MedsFormStand.open(world).draft
        assertEquals("the end date must be suggested from the start (ПР-6)", draft.startDate, draft.endDate)
    }

    @Тогда("^строка «Окончание» становится активной и требует даты$")
    fun theEndDateRowBecomesActiveAndAsksForADate() {
        assertNotNull("the course must hold an end date now", MedsFormStand.open(world).draft.endDate)
        MedsFormStand.ask(world) { test ->
            val node = test.onNodeWithTag(TAG_END_DATE).fetchSemanticsNode()
            val actions = node.config.toList().map { it.key.name }
            assertTrue("the active end date must accept a tap: $actions", actions.any { it.contains(ON_CLICK) })
            assertFalse(
                "the active end date must show a date, not the muted dash",
                MedsProbe.textsOf(node).any { it == MUTED_END_DATE },
            )
        }
    }

    // --- Единственное сохранение -----------------------------------------------------------

    @Тогда("^внизу показана кнопка «(.+)»$")
    fun theSaveButtonIsAtTheBottom(label: String) {
        MedsFormStand.ask(world) { test ->
            val node = test.onNodeWithTag(TAG_SAVE_BUTTON).fetchSemanticsNode()
            assertTrue("the save button must read «$label»", label in MedsProbe.textsOf(node))
        }
    }

    /**
     * Р-54: слова «Отмена» на форме нет — ни в шапке, ни в теле.
     *
     * Слово перестало быть обязательным у вида `Modal` и ушло с формы вместе с действием:
     * выход даёт системный «Назад», сохранение — нижняя кнопка (Д-3). Слово интерфейса
     * не удалялось: оно осталось у шторки отчёта врачу, где действие есть.
     */
    @Тогда("^в шапке нет слова «Отмена»$")
    fun theHeaderKeepsNoCancelWord() {
        MedsFormStand.ask(world) { test ->
            test.onAllNodesWithText(context.getString(UiR.string.action_cancel)).assertCountEquals(0)
        }
    }

    @Тогда("^кнопки «Готово» в шапке нет$")
    fun thereIsNoDoneButtonInTheHeader() {
        // Отступление от макета по Д-3: сохранение одно, и дублирующей кнопки нет вовсе.
        MedsFormStand.ask(world) { test ->
            test.onAllNodesWithText(DONE_LABEL, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // --- Обвязка -------------------------------------------------------------------------

    private fun formOf(label: String): MedicationForm =
        MedicationForm.entries.first { context.getString(formLabel(it)) == label }

    private fun foodRelationOf(label: String): FoodRelation =
        FoodRelation.entries.first { context.getString(foodLabel(it)) == label }

    private fun foodLabel(relation: FoodRelation): Int = when (relation) {
        FoodRelation.ANY -> R.string.food_relation_any
        FoodRelation.BEFORE_MEAL -> R.string.food_relation_before
        FoodRelation.WITH_MEAL -> R.string.food_relation_with
        FoodRelation.AFTER_MEAL -> R.string.food_relation_after
    }

    private fun formLabel(form: MedicationForm): Int = when (form) {
        MedicationForm.TABLET -> R.string.med_form_tablet
        MedicationForm.CAPSULE -> R.string.med_form_capsule
        MedicationForm.SPRAY -> R.string.med_form_spray
        MedicationForm.SYRUP -> R.string.med_form_syrup
        MedicationForm.DROPS -> R.string.med_form_drops
        MedicationForm.INJECTION -> R.string.med_form_injection
    }

    private companion object {
        /** Порядок блоков: здесь «расписание» — это и счётчик, и список времён. */
        val BLOCK_TAGS: Map<String, List<String>> = mapOf(
            "Название" to listOf(TAG_NAME_FIELD),
            "Форма выпуска" to listOf(TAG_RELEASE_FORM),
            "Вид таблетки" to listOf(TAG_TABLET_SHAPE_ROW),
            "Доза на один приём" to listOf(TAG_DOSE_FIELD),
            "Дозировка препарата" to listOf(TAG_STRENGTH_FIELD),
            "Режим приёма" to listOf(TAG_MODE_SWITCH),
            "расписание" to listOf(TAG_COUNTER_CARD, TAG_TIMES_BLOCK),
            "Связь с едой" to listOf(TAG_FOOD_RELATION),
            "Остаток в упаковке" to listOf(TAG_STOCK_FIELD),
            "Период приёма" to listOf(TAG_START_DATE),
        )

        /** «Название», «Форма выпуска» … и неподписанное слово «расписание». */
        val ORDER_TOKENS = Regex("«([^»]+)»|(расписание)")
        const val VALUES_SEPARATOR = ","
        const val NO_MORE_THAN = "не чаще"
        const val ON_CLICK = "OnClick"
        const val DONE_LABEL = "Готово"
        const val MUTED_END_DATE = "—"

        val TWO_TIMES: List<LocalTime> = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))

        /** Подписи силуэтов для чтения с экрана — те же пять слов, что в сценарии. */
        val SHAPE_LABELS = listOf(
            R.string.tablet_shape_round,
            R.string.tablet_shape_round_scored,
            R.string.tablet_shape_oval,
            R.string.tablet_shape_oblong,
            R.string.tablet_shape_triangular,
        )
    }
}
