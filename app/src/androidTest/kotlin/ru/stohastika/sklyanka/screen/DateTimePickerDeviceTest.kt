package ru.stohastika.sklyanka.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.feature.meds.form.TAG_ADD_TIME
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIME_ROW
import ru.stohastika.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.stohastika.sklyanka.ui.component.TAG_DATE_TIME_PICKER
import ru.stohastika.sklyanka.ui.component.TAG_TIME_INPUT
import ru.stohastika.sklyanka.ui.component.TAG_TIME_PICKER
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Тайм-пикер на настоящем устройстве (SKL-96, переработано SKL-97, TDD §9).
 *
 * Диалог приложения живёт отдельным окном — ровно то, чего JVM-стенд касается
 * механизмом, но не настоящим окном. Проверка идёт на тестовом графе: данные образа
 * на неё не влияют.
 */
@HiltAndroidTest
class DateTimePickerDeviceTest : DeviceScreenTest() {

    @Test
    fun theTimeDialogOffersClockAndKeyboardInput() {
        launchApp()
        awaitRoot()
        openMedications()
        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)

        // Новый черновик времени не содержит: строка появляется после «Добавить время».
        compose.onNodeWithTag(TAG_ADD_TIME, useUnmergedTree = true).performScrollTo().performClick()

        val timeRow = waitForTag(TAG_TIME_ROW)
        timeRow.performScrollTo()
        timeRow.performClick()

        waitForTag(TAG_DATE_TIME_PICKER)
        compose.onNodeWithTag(TAG_TIME_PICKER, useUnmergedTree = true).assertIsDisplayed()
        capture("time-picker", compose.onNode(isDialog()))

        compose.onNodeWithText(targetContext.getString(UiR.string.cd_switch_time_input)).performClick()
        waitForTag(TAG_TIME_INPUT).assertIsDisplayed()
        capture("time-picker-input", compose.onNode(isDialog()))
    }
}
