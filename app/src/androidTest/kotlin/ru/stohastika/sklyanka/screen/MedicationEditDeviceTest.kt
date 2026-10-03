package ru.stohastika.sklyanka.screen

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.feature.meds.card.TAG_EDIT
import ru.stohastika.sklyanka.feature.meds.card.TAG_MEDICATION_CARD_SCREEN
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIME_ROW
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R

/**
 * F — правка лекарства с черновиком (SKL-97, TDD §8.2).
 *
 * Форма правки открывается заполненным черновиком: название и строка времени не пусты.
 * После смены названия и сохранения человек возвращается в карточку — и карточка
 * показывает новое значение, а не прежнее.
 */
@HiltAndroidTest
class MedicationEditDeviceTest : DeviceScreenTest() {

    @Test
    fun theEditReturnsToTheCardWithNewValues() {
        val before = testContext.getString(R.string.device_test_medication_edit)
        val after = testContext.getString(R.string.device_test_medication_edit_renamed)
        seedMedication(id = "dev-med-edit", name = before, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()

        launchApp()
        awaitRoot()
        openMedications()
        click(TAG_MEDICATION_CARD)

        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        click(TAG_EDIT)

        waitForTag(TAG_MEDICATION_FORM)
        waitUntil(SCREEN_TIMEOUT_MS, "черновика с прежним названием") { textExists(before) }
        waitForTag(TAG_TIME_ROW)
        capture("med-edit-form")

        replaceText(TAG_NAME_FIELD, after)
        click(TAG_SAVE_BUTTON)

        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        waitUntil(SCREEN_TIMEOUT_MS, "карточки с новым названием") { textExists(after) }
        capture("med-card-edited")
    }
}
