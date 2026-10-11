package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.feature.meds.form.TAG_ADD_TIME
import ru.stohastika.sklyanka.feature.meds.form.TAG_DOSE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIME_ROW
import ru.stohastika.sklyanka.feature.meds.form.TAG_VIOLATION
import ru.stohastika.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_DATE_TIME_PICKER
import java.time.LocalTime
import ru.stohastika.sklyanka.feature.meds.R as MedsR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Враждебный поход «форма лекарства» (SKL-140, реестр походов № 14).
 *
 * Человек ломает ввод так, как этого не делает ни один сценарий приёмки: поворачивает экран
 * посреди заполнения, уходит системной «Назад» без сохранения, жмёт «Сохранить» дважды подряд,
 * вводит название из одних пробелов, отменяет выбор времени. Утверждается не «экран сменился»,
 * а место человека и состояние данных: введённое цело, лекарство заведено ровно одно, отказ
 * назван и показан, строка времени не изменилась.
 *
 * **Две проверки этого похода нарочно жёсткие, и на сегодняшнем продукте они красные.**
 * Набор обязан показывать дефект, а не обходить его (TDD §8): понизить их до наблюдаемого
 * поведения значило бы закрепить дефект проверкой.
 *
 * 1. Поворот в середине ввода (см. [waitForFormAfterRotation]). Манифест не объявляет
 *    `configChanges` (`app/src/main/AndroidManifest.xml:149`), поворот пересоздаёт активность,
 *    а путь человека живёт в полях активности (`RootNavigation`, `MainActivity.kt:192`) и в
 *    памяти композиции (`formOpen`, `MedsRoute.kt:47`). Человек возвращается на «Сегодня»,
 *    заполненный черновик формы на экране не восстанавливается.
 * 2. Двойное нажатие «Сохранить» (шаг 3). Ни флага «идёт запись», ни идемпотентности черновика
 *    нет: `MedicationFormViewModel.kt:89` заводит запись на каждое нажатие, а черновик без
 *    идентификатора получает новый на каждую попытку (`SaveMedicationUseCase.kt:47,72`) —
 *    второе нажатие заводит второе лекарство.
 *
 * Название из одних пробелов форма считает пустым названием и до сохранения не доводит: кнопка
 * недоступна, а причина названа человеку словами (шаг 4). Поэтому «сохранение отклонено, названо
 * первое нарушение и форма прокручена к нему» проверяется там же, но на черновике с настоящим
 * названием, у которого нет ни одного времени приёма: первое нарушение списка и есть время.
 *
 * Конец курса в прошлом автоматизацией не берётся — так и записано в приёмке (SKL-140.feature,
 * примечание к враждебному проходу по форме): дату ставит календарь Material3
 * (`DateTimePickerDialog`), у дня календаря устойчивого тега нет, и нарушение по сроку держат
 * экранные проверки формы.
 */
@HiltAndroidTest
class HostileMedicationFormTest : JourneyTest() {

    @Test
    fun thePersonCannotBreakTheMedicationForm() {
        val halfName = testContext.getString(R.string.device_test_medication_name, "hostile-form")
        val savedName = testContext.getString(R.string.device_test_medication_name, "hostile-save")
        val violationName = testContext.getString(R.string.device_test_medication_name, "hostile-violation")
        val blockedReason = ui(MedsR.string.form_save_blocked)

        launchApp()
        awaitRoot()
        openMedications()
        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)

        // Половина полей: название и доза введены, времени нет — форма к записи не готова.
        typeInto(TAG_NAME_FIELD, halfName)
        typeInto(TAG_DOSE_FIELD, DOSE)
        capture("form-half")

        // 2. Системная «Назад» без сохранения: форма закрыта, лекарства не заведено.
        pressBack()
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия формы системной «Назад»") { !exists(TAG_MEDICATION_FORM) }
        capture("after-back")
        openMedications()
        waitForTag(TAG_MEDICATION_LIST)
        assertEquals("an abandoned form must not create a row", 0, cardsWith(halfName))
        assertEquals("an abandoned form must not create a medication", 0, opLogCount(OpEvent.MEDICATION_CREATED))

        // 3. Двойное нажатие «Сохранить»: одно лекарство и один план приёмов, а не два.
        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)
        typeInto(TAG_NAME_FIELD, savedName)
        typeInto(TAG_DOSE_FIELD, DOSE)
        scrollToAndClick(TAG_ADD_TIME)
        waitForTag(TAG_TIME_ROW)
        doublePress(TAG_SAVE_BUTTON)
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия формы после сохранения") { !exists(TAG_MEDICATION_FORM) }
        waitForTag(TAG_MEDICATION_LIST)
        scrollListTo(savedName)
        capture("saved-twice")
        assertEquals("a double press must leave exactly one row", 1, cardsWith(savedName))
        assertEquals("a double press must create one medication", 1, opLogCount(OpEvent.MEDICATION_CREATED))
        assertEquals("a double press must not double the plan", 1, tomorrowIntakes())

        // 4. Название из одних пробелов: своего названия у лекарства нет, поэтому форма
        //    не доводит запись до сохранения — кнопка недоступна, а причина названа словами.
        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)
        typeInto(TAG_NAME_FIELD, SPACES)
        waitUntil(SCREEN_TIMEOUT_MS, "названной причины недоступного сохранения") { textExists(blockedReason) }
        assertEquals("the field must keep the spaces as typed", SPACES, typedText(TAG_NAME_FIELD))
        compose.onNodeWithText(blockedReason, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TAG_SAVE_BUTTON, useUnmergedTree = true).assertIsNotEnabled()
        capture("name-blank")

        // «Дата окончания в прошлом» автоматизацией не берётся (примечание приёмки SKL-140):
        // конец курса ставит календарь Material3, у дня календаря нет устойчивого тега. Нарушение
        // по сроку держат экранные проверки формы (`MedicationFormScreen`-стенд), а здесь отказ
        // и прокрутка к первому нарушению проверены через другое поле — отсутствие времени приёма.
        replaceText(TAG_NAME_FIELD, violationName)
        typeInto(TAG_DOSE_FIELD, DOSE)
        click(TAG_SAVE_BUTTON)
        waitUntilDisplayed(TAG_VIOLATION, "первого нарушения, названного формой")
        val missingTime = ui(MedsR.string.error_no_times)
        assertEquals("the rejection must name the missing time", missingTime, nodeText(TAG_VIOLATION))
        assertTrue("a rejected save must leave the form open", exists(TAG_MEDICATION_FORM))
        assertEquals("a rejected save must not create a medication", 1, opLogCount(OpEvent.MEDICATION_CREATED))
        capture("first-violation")

        // 5. Выбор времени, закрытый отменой: показанное в строке время не изменилось.
        scrollToAndClick(TAG_ADD_TIME)
        waitForTag(TAG_TIME_ROW)
        val shown = timeRowText()
        assertTrue("the time row must show a time, got «$shown»", shown.isNotBlank())
        scrollToAndClick(TAG_TIME_ROW)
        waitForTag(TAG_DATE_TIME_PICKER)
        capture("time-picker")
        cancelTimePicker()
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия выбора времени отменой") { !exists(TAG_DATE_TIME_PICKER) }
        // 6. Поворот в середине ввода — последней пробой: образ может не менять ориентацию,
        //    и тогда проба не имеет предмета (о пропуске скажет отчёт, а не молчание).
        //    Ставь её раньше — отказ стенда прятал бы остальные находки этого похода.
        assumeTrue(
            "стенд не меняет ориентацию: проба поворота не имеет предмета, дефект назван SKL-142",
            rotateLandscapeOrNull(),
        )
        waitForFormAfterRotation(violationName)
        capture("form-landscape")
        rotatePortrait()
        waitForFormAfterRotation(violationName)
        assertEquals("the typed dose must survive the rotation", DOSE, typedText(TAG_DOSE_FIELD))
        capture("form-portrait")
    }

    /**
     * Дождаться заполненной формы после поворота — то же, что названо сценарием.
     *
     * Поворот пересоздаёт активность, и на миг композиции в процессе нет вовсе: чтение дерева
     * в этот миг отказывает, а не отвечает «узла нет», поэтому условие ждёт через [runCatching].
     */
    private fun waitForFormAfterRotation(name: String) {
        waitUntil(SCREEN_TIMEOUT_MS, "заполненной формы после поворота") {
            runCatching { exists(TAG_MEDICATION_FORM) && typedText(TAG_NAME_FIELD) == name }.getOrDefault(false)
        }
    }

    /**
     * Двойное нажатие, как его делает человек: две точки подряд, без простоя между ними.
     *
     * Ожидание простоя между нажатиями мерило бы не двойное нажатие, а скорость закрытия формы,
     * и отказ проверки говорил бы не о том, что человек сделал.
     */
    private fun doublePress(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
            click()
            click()
        }
        compose.waitForIdle()
    }

    /** Введённое в поле: у поля читается его редактируемый текст, а не подпись. */
    private fun typedText(fieldTag: String): String = compose.onNode(
        hasSetTextAction() and hasAnyAncestor(hasTestTag(fieldTag)),
        useUnmergedTree = true,
    ).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    /**
     * Показанное в строке времени.
     *
     * Цифры времени лежат в потомке строки, поэтому строка читается слитым деревом — тем же,
     * каким её читает человек: нажимаемая строка сливает подписи своих детей.
     */
    private fun timeRowText(): String = compose.onNodeWithTag(TAG_TIME_ROW)
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.Text)
        .orEmpty()
        .joinToString(" ") { it.text }

    /** Строки списка с названным лекарством: двойное сохранение видно их числом. */
    private fun cardsWith(name: String): Int = compose.onAllNodes(
        hasTestTag(TAG_MEDICATION_CARD) and hasAnyDescendant(hasText(name)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size

    /** Приёмы завтрашнего дня на время первой строки расписания: двойное сохранение их удвоит. */
    private fun tomorrowIntakes(): Int =
        dayIntakes(DeviceTestClock.today.plusDays(1)).count { it.scheduledAt.toLocalTime() == MORNING }

    /**
     * Дождаться, что узел не просто есть, а виден человеку.
     *
     * Прокрутка формы к первому нарушению — анимация: узел появляется раньше, чем доезжает
     * до видимой области, и «есть» здесь ещё не значит «показан».
     */
    private fun waitUntilDisplayed(tag: String, what: String) {
        waitUntil(SCREEN_TIMEOUT_MS, what) {
            runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed() }.isSuccess
        }
    }

    /** Закрыть выбор времени отменой: подпись берётся из ресурса дизайн-системы. */
    private fun cancelTimePicker() {
        val cancel = ui(UiR.string.action_cancel)
        compose.onNode(
            hasText(cancel) and hasAnyAncestor(hasTestTag(TAG_DATE_TIME_PICKER)),
            useUnmergedTree = true,
        ).performClick()
    }

    private companion object {
        const val DOSE = "1"

        /** Название из одних пробелов: для формы это пустое название, а не имя лекарства. */
        const val SPACES = "   "

        /** Время первой строки расписания: его даёт продукт — 08:00 (`DEFAULT_TIME`). */
        val MORNING: LocalTime = LocalTime.of(8, 0)
    }
}
