package ru.stohastika.sklyanka.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import dagger.hilt.android.testing.HiltAndroidTest
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

/**
 * Форма лекарства на настоящем устройстве (SKL-96, переработано SKL-97, TDD §9).
 *
 * Самый длинный экран продукта: настоящая платформа проверяет ввод, вставки, окно
 * и ленивый список. Лекарство создаётся в базе в памяти тестового графа, имя —
 * из ресурсов тестового APK; случайный суффикс не нужен, столкновений нет.
 */
@HiltAndroidTest
class MedicationFormDeviceTest : DeviceScreenTest() {

    @Test
    fun theFormOpensFromTheMedicationList() {
        launchApp()
        awaitRoot()
        openMedications()

        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM).assertIsDisplayed()

        capture("medication-form")
    }

    @Test
    fun theTypedMedicationIsSavedAndAppearsInTheList() {
        launchApp()
        awaitRoot()
        openMedications()
        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)

        // Новый черновик пуст: без названия и дозы сохранение не проходит, без времени
        // «по расписанию» черновик невалиден (ИНВ-4, ИНВ-6). Заполняем минимум.
        val name = testContext.getString(R.string.device_test_medication_name, "form")
        typeInto(TAG_NAME_FIELD, name)
        typeInto(TAG_DOSE_FIELD, DOSE)
        scrollToAndClick(TAG_ADD_TIME)
        waitForTag(TAG_TIME_ROW)
        click(TAG_SAVE_BUTTON)

        waitForTag(TAG_MEDICATION_LIST)
        assertMedicationInList(name)

        capture("medication-list")
    }

    /**
     * Созданное лекарство ищется в ленивом списке: нижние карточки не компонуются,
     * пока к ним не прокрутили, поэтому проверка прокручивает список до имени.
     */
    private fun assertMedicationInList(name: String) {
        waitUntil(SCREEN_TIMEOUT_MS, "лекарства «$name» в списке") {
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
        const val DOSE = "1"
    }
}
