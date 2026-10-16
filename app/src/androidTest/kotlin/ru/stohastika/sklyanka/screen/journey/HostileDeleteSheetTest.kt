package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_CONFIRM
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_SHEET
import ru.stohastika.sklyanka.feature.meds.card.TAG_MEDICATION_CARD_SCREEN
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_WORD_FIELD
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_WORD_LABEL
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import ru.stohastika.sklyanka.feature.meds.R as MedsR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Враждебный проход по шторке удаления (SKL-140, реестр походов № 17).
 *
 * Человек пробует сломать самое необратимое действие приложения на лекарстве без единой
 * отметки «принято» (удаление доступно только такому — ПР-14, ОВ-124). Приёмы похода:
 * системный «Назад» на открытой шторке, неверное слово подтверждения, двойное нажатие
 * подтверждения и двойное нажатие возврата. Каждый приём утверждает состояние, а не экран:
 * мягкое удаление и его возврат видно в журнале операций (`MEDICATION_DELETED`,
 * `MEDICATION_DELETED_UNDONE`), и «ровно один раз» — это число записей журнала и число
 * строк списка, а не только подпись на карточке.
 *
 * Приём с системным «Назад» утверждает **норму**, а не наблюдаемое: закрыта одна шторка,
 * карточка на месте (`docs/norm/04-behavior.md` §5.3, KDoc `SklyankaBottomSheet`). Сегодня
 * утверждение красное: своего обработчика возврата у шторки нет, и нажатие уносит карточку
 * целиком — поход обязан показывать дефект, а не обходить его (Р-58).
 *
 * Второе нажатие действия отдаётся лишь пока узел ещё на экране: шторка гаснет тем же
 * мгновением, что и решение, и повтор до неё не доходит. Инвариант держит
 * [JourneyTest.opLogCount] — повтор операции домен отбрасывает сам.
 */
@HiltAndroidTest
class HostileDeleteSheetTest : JourneyTest() {

    @Test
    fun thePersonFailsToBreakTheDeletionOfAMedication() {
        seedMedication(id = DELETED_ID, name = name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()

        launchApp()
        awaitRoot()
        openMedications()
        openMedicationCard(name)

        // Системный «Назад» на открытой шторке: закрыта одна шторка, карточка на месте.
        openDeleteSheet()
        capture("sheet-open")
        pressBack()
        assertSheetClosedAndCardKept()

        // Неверное слово: подтверждение недоступно, и нажатие по нему ничего не делает.
        openDeleteSheet()
        val word = ui(MedsR.string.medication_delete_word)
        enterWord(WRONG_WORD)
        val confirm = compose.onNodeWithTag(TAG_DELETE_CONFIRM, useUnmergedTree = true)
        confirm.assertIsNotEnabled()
        confirm.performClick()
        assertTrue("the sheet must stay open after a rejected word", exists(TAG_DELETE_SHEET))
        assertEquals("a rejected word must not delete", 0, opLogCount(OpEvent.MEDICATION_DELETED))
        assertEquals(
            "the sheet must name the word to confirm with",
            targetContext.getString(UiR.string.confirm_word_label, word),
            nodeText(TAG_CONFIRM_WORD_LABEL),
        )
        capture("wrong-word")

        // Верное слово, подтверждение дважды: лекарство удалено ровно один раз.
        enterWord(word)
        compose.onNodeWithTag(TAG_DELETE_CONFIRM, useUnmergedTree = true).assertIsEnabled()
        clickTwice(TAG_DELETE_CONFIRM)
        waitUntil(SCREEN_TIMEOUT_MS, "the list without the deleted medication") {
            exists(TAG_MEDICATION_LIST) && !textExists(name)
        }
        assertEquals("double confirmation must delete exactly once", 1, opLogCount(OpEvent.MEDICATION_DELETED))
        capture("deleted")

        // Возврат в коротком окне: второе нажатие не возвращает лекарство второй раз.
        val deleted = targetContext.getString(MedsR.string.meds_deleted_snackbar, name)
        waitUntil(SCREEN_TIMEOUT_MS, "the undo window") { textExists(deleted) }
        waitForTag(TAG_SNACKBAR)
        clickTwiceByText(ui(MedsR.string.meds_undo_action))
        scrollListTo(name)
        assertTrue("the undone medication must be back in the list", textExists(name))
        assertEquals(
            "double undo must return the medication exactly once",
            1,
            opLogCount(OpEvent.MEDICATION_DELETED_UNDONE),
        )
        assertEquals("the list must hold exactly one row of the medication", 1, cardRows())
        capture("undone")
    }

    private val name: String get() = testContext.getString(R.string.device_test_medication_deletion)

    /**
     * Открыть шторку удаления на карточке лекарства: карточка — та, что оставил поход.
     */
    private fun openDeleteSheet() {
        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        if (!exists(TAG_DELETE_SHEET)) click(TAG_DELETE)
        waitForTag(TAG_DELETE_SHEET)
    }

    /**
     * Норма: системная «Назад» закрывает одну шторку без изменений — карточка остаётся,
     * удаления нет (`docs/norm/04-behavior.md` §5.3, KDoc `SklyankaBottomSheet`).
     *
     * Сегодня утверждение красное: у шторки нет своего обработчика возврата, нажатие
     * перехватывает раздел «Лекарства» (`navigation/MedsSection.kt:47` —
     * `if (!stack.pop()) onLeaveSection()`) и уносит карточку целиком. Поход показывает
     * дефект, а не обходит его (Р-58).
     */
    private fun assertSheetClosedAndCardKept() {
        waitUntil(SCREEN_TIMEOUT_MS, "the delete sheet to close") { !exists(TAG_DELETE_SHEET) }
        assertEquals("the system back must not delete", 0, opLogCount(OpEvent.MEDICATION_DELETED))
        assertTrue(
            "the system back must close only the sheet: the medication card must stay (10-behavior §5.3)",
            exists(TAG_MEDICATION_CARD_SCREEN),
        )
        capture("back-kept")
    }

    /** Ввести слово в поле шторки: полотно шторки не прокручивается, поле видно целиком. */
    private fun enterWord(value: String) {
        compose.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_CONFIRM_WORD_FIELD)),
            useUnmergedTree = true,
        ).performTextReplacement(value)
        compose.waitForIdle()
    }

    /**
     * Нажать действие дважды подряд: второй раз — только если оно ещё на экране.
     *
     * Нажатие по действию, которое снимается первым нажатием (подтверждение удаления,
     * «Отменить» снэкбара), вторым разом адресовать уже некому, а слепой тап по прежней
     * точке попал бы в то, что встало на это место (SKL-119, прогон 16.10.2026: второе
     * нажатие «Отменить» открывало форму нового лекарства). Поэтому второе нажатие
     * делается только по узлу, который всё ещё на экране.
     */
    private fun clickTwice(tag: String) {
        click(tag)
        if (exists(tag)) click(tag)
    }

    /** То же для действия снэкбара: у него нет тега, только подпись действия. */
    private fun clickTwiceByText(text: String) {
        compose.onNodeWithText(text, useUnmergedTree = true).performClick()
        if (textExists(text)) compose.onNodeWithText(text, useUnmergedTree = true).performClick()
    }

    /** Сколько строк списка показывает это лекарство: повтор возврата дал бы вторую. */
    private fun cardRows(): Int = compose.onAllNodes(
        hasTestTag(TAG_MEDICATION_CARD) and hasAnyDescendant(hasText(name)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size

    private companion object {
        const val DELETED_ID = "hostile-delete-sheet"
        const val WRONG_WORD = "nope"
    }
}
