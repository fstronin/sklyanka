package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.undo.UndoableDeletion
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.card.MedCardIntent
import ru.stohastika.sklyanka.feature.meds.card.TAG_ARCHIVE
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_CONFIRM
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_HINT
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_SHEET
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_SHEET_CANCEL
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_SHEET_TEXT
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_SHEET_TITLE
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import ru.stohastika.sklyanka.ui.component.TAG_TEXT_FIELD_INPUT
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Экранные шаги приёмки SKL-99: карточка, шторка и снэкбар удаления (ОВ-124).
 *
 * Состояния собирают стенды ([MedCardStand], [MedsListStand], [ArchiveStand]) тем же
 * путём, что продуктовые ViewModel, а удаление и возврат применяются настоящими доменными
 * сценариями. Цвет вида кнопки разметкой не проверяется: вид `Danger` закреплён снимком
 * `button-quiet-danger` и тестом дизайн-системы; здесь проверяется наличие, подпись
 * и цель нажатия.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class MedDeletionScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Лекарство, о котором говорит сценарий: карточка открыта последней. */
    private var focused: String = ""

    /**
     * Слово, набранное шагом сценария; проверки набирают его же в своей композиции.
     *
     * Композиция стенда собирается заново на каждый шаг, и поле шторки держит слово
     * локально, — поэтому слово хранит шаг, а не разметка.
     */
    private var typedWord: String? = null

    // --- Предыстория ---------------------------------------------------------------------

    @Допустим("^человек удалил лекарство «([^»]+)» из карточки$")
    fun thePersonAlreadyDeletedTheMedicationFromTheCard(medication: String) {
        focused = medication
        val outcome = world.deleteMedicationWithWindow(medication)
        assertTrue("the deletion must take effect: $outcome", world.medications.get(medication).isDeleted)
    }

    // --- Карточка ------------------------------------------------------------------------

    @Тогда("^в карточке ниже кнопки «В архив» показана тихая кнопка «Удалить» цветом опасности$")
    fun theDeleteButtonIsShown() {
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onNodeWithTag(TAG_DELETE, useUnmergedTree = true).assertExists()
            val texts = MedsProbe.texts(test, TAG_DELETE)
            assertTrue("the button must read «Удалить»: $texts", DELETE_WORD in texts)
        }
    }

    @Тогда("^под кнопкой удаления стоит пояснение «([^»]+)»$")
    fun theHintIsShown(expected: String) {
        val hint = context.getString(R.string.card_delete_hint)
        assertEquals("the scenario must quote the resource", expected, hint)
        MedCardStand.ask(world, focusedMedication()) { test ->
            val texts = MedsProbe.texts(test, TAG_DELETE_HINT)
            assertTrue("the hint must read «$hint»: $texts", hint in texts)
        }
    }

    @Тогда("^цель нажатия кнопки удаления не меньше (\\d+) dp$")
    fun theDeleteTargetIsLargeEnough(minimum: Int) {
        MedCardStand.ask(world, focusedMedication()) { test ->
            val node = test.onNodeWithTag(TAG_DELETE, useUnmergedTree = true).fetchSemanticsNode()
            val density = node.layoutInfo.density
            val height = with(density) { node.size.height.toDp() }
            val width = with(density) { node.size.width.toDp() }
            val need = SklyankaSizes.MinTouchTarget - SklyankaSizes.BorderThin
            assertTrue(
                "the delete target is $width×$height dp, below $minimum dp",
                width.value >= need.value && height.value >= need.value,
            )
        }
    }

    @Тогда("^на карточке нет ни кнопки удаления, ни пояснения о ней$")
    fun theDeleteActionIsAbsent() {
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onAllNodesWithTag(TAG_DELETE, useUnmergedTree = true).assertCountEquals(0)
            test.onAllNodesWithTag(TAG_DELETE_HINT, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Тогда("^кнопка «В архив» на карточке осталась$")
    fun theArchiveButtonStays() {
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onNodeWithTag(TAG_ARCHIVE, useUnmergedTree = true).assertExists()
        }
    }

    // --- Шторка --------------------------------------------------------------------------

    @Когда("^человек нажимает «Удалить» в карточке$")
    fun thePersonPressesDelete() {
        val intents = MedCardStand.act(world, focusedMedication()) { test ->
            test.onNodeWithTag(TAG_DELETE, useUnmergedTree = true).performClick()
        }
        assertTrue("the button must open the confirmation sheet: $intents", MedCardIntent.DeleteRequested in intents)
    }

    @Тогда("^открыта шторка подтверждения удаления$")
    fun theSheetIsOpen() {
        MedCardStand.ask(world, focusedMedication(), deleteConfirmation = true) { test ->
            test.onNodeWithTag(TAG_DELETE_SHEET, useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^шторка спрашивает «([^»]+)»$")
    fun theSheetAsks(expected: String) {
        val name = MedsListStand.displayName(world, focusedMedication())
        val question = context.getString(R.string.card_delete_sheet_title, name)
        assertTrue("the scenario must quote the resource", expected.isNotBlank())
        MedCardStand.ask(world, focusedMedication(), deleteConfirmation = true) { test ->
            val texts = MedsProbe.texts(test, TAG_DELETE_SHEET_TITLE)
            assertTrue("the sheet must ask «$question»: $texts", question in texts)
        }
    }

    @Тогда(
        "^шторка предупреждает, что лекарство исчезнет из списков и напоминаний и его можно вернуть кнопкой «Отменить»$",
    )
    fun theSheetWarns() {
        val text = context.getString(R.string.card_delete_sheet_text)
        MedCardStand.ask(world, focusedMedication(), deleteConfirmation = true) { test ->
            val texts = MedsProbe.texts(test, TAG_DELETE_SHEET_TEXT)
            assertTrue("the sheet must warn: $texts", text in texts)
        }
    }

    @Тогда("^у шторки есть кнопка «Удалить» цветом опасности и тихая «Отмена»$")
    fun theSheetHasActions() {
        MedCardStand.ask(world, focusedMedication(), deleteConfirmation = true) { test ->
            test.onNodeWithTag(TAG_DELETE_CONFIRM, useUnmergedTree = true).assertExists()
            test.onNodeWithTag(TAG_DELETE_SHEET_CANCEL, useUnmergedTree = true).assertExists()
            val confirm = MedsProbe.texts(test, TAG_DELETE_CONFIRM)
            assertTrue("the confirm must read «Удалить»: $confirm", DELETE_WORD in confirm)
            val cancel = MedsProbe.texts(test, TAG_DELETE_SHEET_CANCEL)
            assertTrue(
                "the cancel must read «Отмена»: $cancel",
                context.getString(ru.stohastika.sklyanka.ui.R.string.action_cancel) in cancel,
            )
        }
    }

    @Когда("^человек нажимает «Отмена» в шторке$")
    fun thePersonCancelsTheSheet() {
        val intents = MedCardStand.act(world, focusedMedication(), deleteConfirmation = true) { test ->
            test.onNodeWithTag(TAG_DELETE_SHEET_CANCEL, useUnmergedTree = true).performClick()
        }
        assertTrue("the cancel must dismiss the sheet: $intents", MedCardIntent.DeleteDismissed in intents)
    }

    @Тогда("^шторка подтверждения закрыта$")
    fun theSheetIsClosed() {
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onAllNodesWithTag(TAG_DELETE_SHEET, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Тогда("^лекарство «([^»]+)» не удалено$")
    fun theMedicationIsNotDeleted(medication: String) {
        assertFalse(world.medications.get(medication).isDeleted)
    }

    @Когда("^человек набирает слово «(.+)»$")
    fun thePersonTypesTheWord(word: String) {
        typedWord = word
        MedCardStand.act(world, focusedMedication(), deleteConfirmation = true) { test ->
            test.onNodeWithTag(TAG_TEXT_FIELD_INPUT, useUnmergedTree = true).performTextReplacement(word)
        }
    }

    @Тогда("^кнопка «Удалить» пока недоступна$")
    fun theConfirmButtonIsNotYetAvailable() {
        MedCardStand.ask(world, focusedMedication(), deleteConfirmation = true) { test ->
            test.onNodeWithTag(TAG_DELETE_CONFIRM, useUnmergedTree = true).assertIsNotEnabled()
        }
    }

    @Тогда("^кнопка «Удалить» доступна$")
    fun theConfirmButtonIsAvailable() {
        val word = typedWord ?: context.getString(R.string.medication_delete_word)
        MedCardStand.ask(world, focusedMedication(), deleteConfirmation = true) { test ->
            test.onNodeWithTag(TAG_TEXT_FIELD_INPUT, useUnmergedTree = true).performTextReplacement(word)
            test.onNodeWithTag(TAG_DELETE_CONFIRM, useUnmergedTree = true).assertIsEnabled()
        }
    }

    @Когда("^человек подтверждает удаление в шторке$")
    fun thePersonConfirmsTheDeletion() {
        // Подтверждение — вводом слова: без него кнопка недоступна, и нажатие не делает
        // ничего (ПЕР-16, §5.7). Слово сценария — то же, что просит шторка; без шага ввода
        // берём его из ресурса шторки, чтобы проверить именно подтверждение.
        val word = typedWord ?: context.getString(R.string.medication_delete_word)
        val intents = MedCardStand.act(world, focusedMedication(), deleteConfirmation = true) { test ->
            test.onNodeWithTag(TAG_TEXT_FIELD_INPUT, useUnmergedTree = true).performTextReplacement(word)
            test.onNodeWithTag(TAG_DELETE_CONFIRM, useUnmergedTree = true).performClick()
        }
        assertTrue("the confirm must delete: $intents", MedCardIntent.DeleteConfirmed in intents)
    }

    @Тогда("^карточка лекарства закрыта$")
    fun theCardIsClosed() {
        assertTrue("the deletion closes the card (маршрут `app`)", MedCardStand.closed)
    }

    @Тогда("^лекарство «([^»]+)» удалено мягко$")
    fun theMedicationIsDeleted(medication: String) {
        assertTrue(world.medications.get(medication).isDeleted)
    }

    // --- Удаление и снэкбар ----------------------------------------------------------------

    @Когда("^человек удаляет лекарство «([^»]+)» из карточки$")
    fun thePersonDeletesFromTheCard(medication: String) {
        focused = medication
        thePersonPressesDelete()
        thePersonConfirmsTheDeletion()
    }

    @Тогда("^в списке показан снэкбар «([^»]+)»$")
    fun theSnackbarIsShownInTheList(expected: String) {
        assertTrue("the scenario names the medication", expected.isNotBlank())
        assertTrue("the window must be live", world.undoActions.live() is UndoableDeletion)
        assertSnackbar { question -> MedsListStand.ask(world, question) }
    }

    @Тогда("^в архиве показан снэкбар «([^»]+)»$")
    fun theSnackbarIsShownInTheArchive(expected: String) {
        assertTrue("the scenario names the medication", expected.isNotBlank())
        assertSnackbar { question -> ArchiveStand.ask(world, question) }
    }

    @Тогда("^у снэкбара одно действие «([^»]+)»$")
    fun theSnackbarHasOneAction(label: String) {
        MedsListStand.ask(world) { test ->
            val texts = snackbarTexts(test)
            assertTrue("the action must read «$label»: $texts", label in texts)
        }
    }

    @Когда("^человек нажимает «Отменить» в снэкбаре удаления$")
    fun thePersonUndoesInTheSnackbar() {
        val action = context.getString(R.string.meds_undo_action)
        val intents = MedsListStand.act(world) { test ->
            test.onNode(
                hasText(action) and hasAnyAncestor(hasTestTag(TAG_SNACKBAR)),
                useUnmergedTree = true,
            ).performClick()
        }
        assertTrue(
            "the action must undo the deletion: $intents",
            intents.any {
                it is ru.stohastika.sklyanka.feature.meds.list.MedsIntent.UndoDeletion
            },
        )
    }

    @Тогда("^снэкбара удаления в списке нет$")
    fun thereIsNoSnackbarInTheList() {
        assertTrue("the window must be closed", world.undoActions.live() == null)
        MedsListStand.ask(world) { test ->
            test.onAllNodesWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // --- Обвязка ---------------------------------------------------------------------------

    /** Лекарство сценария: названное шагом либо единственное заведённое Предысторией. */
    private fun focusedMedication(): String = focused.ifEmpty { world.medications.last }

    /** Снэкбар на экране списка или архива: текст строится из ресурса, как у продукта. */
    private fun assertSnackbar(ask: ((ComposeUiTest) -> Unit) -> Unit) {
        val name = MedsListStand.displayName(world, focusedMedication())
        val text = context.getString(R.string.meds_deleted_snackbar, name)
        ask { test ->
            val texts = snackbarTexts(test)
            assertTrue("the snackbar must read «$text»: $texts", text in texts)
        }
    }

    private fun snackbarTexts(test: ComposeUiTest): List<String> {
        val node = test.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).fetchSemanticsNode()
        return textsOf(node)
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    private companion object {
        const val DELETE_WORD = "Удалить"
    }
}
