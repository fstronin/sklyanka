package ru.stohastika.sklyanka.screen.journey

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.feature.meds.card.TAG_COURSE_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_SCHEDULE_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_BLOCK
import ru.stohastika.sklyanka.feature.meds.form.TAG_ADD_TIME
import ru.stohastika.sklyanka.feature.meds.form.TAG_DOSE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIME_ROW
import ru.stohastika.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import java.time.LocalTime

/**
 * Поход «Завести лекарство» (SKL-140, реестр походов № 2).
 *
 * Человек заводит лекарство руками — форма, доза, остаток, два времени, — и доходит
 * до дня и до карточки. Настоящее хранилище здесь обязательно: последняя треть похода
 * проверяет, что лекарство и его приёмы пережили выход из приложения.
 *
 * Два времени, а не одно, по правилу продукта: курс начинается сегодня, и время, уже
 * прошедшее на дне начала (09:01 для 08:00), сегодняшнего приёма не порождает
 * (`ScheduleExpander.skipPastTimesOnCourseStart`) — человек, заводящий лекарство утром,
 * утренний приём не пропускал.
 */
@HiltAndroidTest
class MedicationCreationJourneyTest : JourneyTest() {

    @Test
    fun thePersonCreatesAMedicationAndFindsItInTheDay() {
        val name = testContext.getString(R.string.device_test_medication_name, "journey-creation")
        launchApp()
        awaitRoot()
        openMedications()

        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)
        typeInto(TAG_NAME_FIELD, name)
        typeInto(TAG_DOSE_FIELD, DOSE)
        typeInto(TAG_STOCK_FIELD, STOCK_LEFT)
        // «Добавить время» дважды: продукт даёт 08:00, второе время — на четыре часа позже.
        scrollToAndClick(TAG_ADD_TIME)
        waitForTag(TAG_TIME_ROW)
        scrollToAndClick(TAG_ADD_TIME)
        capture("form-filled")

        click(TAG_SAVE_BUTTON)

        waitForTag(TAG_MEDICATION_LIST)
        scrollListTo(name)
        capture("list")

        openMedicationCard(name)
        waitForTag(TAG_SCHEDULE_BLOCK)
        waitForTag(TAG_COURSE_BLOCK)
        waitForTag(TAG_STOCK_BLOCK)
        capture("card")

        pressBack()
        waitForTag(TAG_MEDICATION_LIST)
        openToday()
        waitUntil(SCREEN_TIMEOUT_MS, "лекарства «$name» на «Сегодня»") { textExists(name) }
        capture("today")

        // Состояние, а не картинка: сегодня ждут приём на 12:00, а прошедшее 08:00
        // на дне начала курса приёма не породило; завтра ждут оба времени.
        val today = dayIntakes()
        assertEquals(
            "today must hold exactly the noon intake, got ${today.map { it.scheduledAt }}",
            1,
            today.count { it.scheduledAt.toLocalTime() == NOON },
        )
        assertEquals(
            "a time already past on the course start day must not create an intake",
            0,
            today.count { it.scheduledAt.toLocalTime() == MORNING },
        )
        assertEquals(
            "the full schedule must be planned for tomorrow",
            listOf(MORNING, NOON),
            dayIntakes(DeviceTestClock.today.plusDays(1)).map { it.scheduledAt.toLocalTime() }.sorted(),
        )

        relaunchApp()
        awaitRoot()
        waitUntil(SCREEN_TIMEOUT_MS, "лекарства «$name» после перезапуска") { textExists(name) }
        openMedications()
        scrollListTo(name)
        assertTrue("the medication must survive the relaunch", textExists(name))
    }

    private companion object {
        const val DOSE = "1"
        const val STOCK_LEFT = "9"
        val MORNING: LocalTime = LocalTime.of(8, 0)
        val NOON: LocalTime = LocalTime.of(12, 0)
    }
}
