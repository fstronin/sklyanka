package ru.stohastika.sklyanka.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.junit.Test
import ru.stohastika.sklyanka.feature.meds.form.TAG_ADD_TIME
import ru.stohastika.sklyanka.feature.meds.form.TAG_DOSE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIME_ROW
import ru.stohastika.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.test.R
import java.util.UUID

/**
 * Форма лекарства на настоящем устройстве (SKL-96, TDD §4.4–4.5).
 *
 * Самый длинный экран продукта: здесь настоящая платформа проверяет ввод, вставки,
 * окно и ленивый список. Лекарство получает уникальное нейтральное имя из ресурсов
 * тестового APK — медицинских данных нет, прежние записи не мешают.
 */
class MedicationFormDeviceTest : DeviceScreenTest() {

    @Test
    fun theFormOpensFromTheMedicationList() {
        awaitRoot()
        openMedications()

        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM).assertIsDisplayed()

        capture("medication-form")
    }

    @Test
    fun theTypedMedicationIsSavedAndAppearsInTheList() {
        awaitRoot()
        openMedications()
        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)

        // Новый черновик пуст: без названия и дозы сохранение не проходит, без времени
        // «по расписанию» черновик невалиден (ИНВ-4, ИНВ-6). Заполняем минимум.
        val name = medicationName()
        typeInto(TAG_NAME_FIELD, name)
        typeInto(TAG_DOSE_FIELD, DOSE)
        scrollToAndClick(TAG_ADD_TIME)
        waitForTag(TAG_TIME_ROW)
        click(TAG_SAVE_BUTTON)

        waitForTag(TAG_MEDICATION_LIST)
        assertMedicationInList(name)

        capture("medication-list")
    }

    private fun medicationName(): String =
        testContext.getString(R.string.device_test_medication_name, UUID.randomUUID().toString().take(UNIQUE_SUFFIX))

    /** Поле адресуется обёрткой и действием ввода: на форме не одно поле ввода. */
    private fun typeInto(fieldTag: String, value: String) {
        val input = compose.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(fieldTag)),
            useUnmergedTree = true,
        )
        input.performScrollTo()
        input.performTextInput(value)
        compose.waitForIdle()
    }

    private fun scrollToAndClick(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().performClick()
    }

    /**
     * Созданное лекарство ищется в ленивом списке: нижние карточки не компонуются,
     * пока к ним не прокрутили, поэтому проверка прокручивает список до имени.
     */
    private fun assertMedicationInList(name: String) {
        compose.waitUntil(SCREEN_TIMEOUT_MS) {
            if (textExists(name)) {
                true
            } else {
                scrollToList(name) && textExists(name)
            }
        }
        compose.onNodeWithText(name, useUnmergedTree = true).assertExists()
    }

    private fun scrollToList(name: String): Boolean = runCatching {
        compose.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag(TAG_MEDICATION_LIST)),
            useUnmergedTree = true,
        ).performScrollToNode(hasText(name))
    }.isSuccess

    private companion object {
        /** Длина случайного суффикса имени: различим в списке и не сталкивается между прогонами. */
        const val UNIQUE_SUFFIX = 8
        const val DOSE = "1"
    }
}
