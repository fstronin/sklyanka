package ru.stohastika.sklyanka.screen

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.feature.meds.card.TAG_COURSE_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_MEDICATION_CARD_SCREEN
import ru.stohastika.sklyanka.feature.meds.card.TAG_REFILL
import ru.stohastika.sklyanka.feature.meds.card.TAG_REFILL_CONFIRM
import ru.stohastika.sklyanka.feature.meds.card.TAG_REFILL_ROW
import ru.stohastika.sklyanka.feature.meds.card.TAG_SCHEDULE_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_VALUE
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import java.math.BigDecimal

/**
 * D — карточка лекарства: расписание, курс, остаток и пополнение (SKL-97, TDD §8.2).
 *
 * Проверка сеет курс с границами и остатком упаковки, открывает карточку из списка,
 * утверждает блоки и повторяет путь «Пополнить» → новое значение → «Сохранить».
 * Новый остаток проверяется по показанному тексту, а не по внутреннему числу.
 */
@HiltAndroidTest
class MedicationCardDeviceTest : DeviceScreenTest() {

    @Test
    fun theCardShowsScheduleCourseAndStock() {
        val name = testContext.getString(R.string.device_test_medication_card)
        seedMedication(
            id = "dev-med-card",
            name = name,
            times = listOf(DeviceTestClock.scheduledTime),
            endDate = DeviceTestClock.today.plusDays(COURSE_DAYS),
            stockLeft = BigDecimal(STOCK_BEFORE),
            packageSize = BigDecimal(PACKAGE_SIZE),
        )
        planIntakes()

        launchApp()
        awaitRoot()
        openMedications()
        scrollTo(TAG_MEDICATION_LIST, TAG_MEDICATION_CARD)
        click(TAG_MEDICATION_CARD)

        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        waitForTag(TAG_SCHEDULE_BLOCK)
        waitForTag(TAG_COURSE_BLOCK)
        waitForTag(TAG_STOCK_BLOCK)
        val stockBefore = nodeText(TAG_STOCK_VALUE)
        capture("med-card")

        scrollToAndClick(TAG_REFILL)
        waitForTag(TAG_REFILL_ROW)
        typeInto(TAG_REFILL_ROW, STOCK_AFTER)
        click(TAG_REFILL_CONFIRM)

        waitUntil(SCREEN_TIMEOUT_MS, "нового значения остатка") {
            val stock = nodeText(TAG_STOCK_VALUE)
            stock != stockBefore && stock.contains(STOCK_AFTER)
        }
        capture("med-card-refilled")
    }

    /**
     * Системный «Назад» закрывает карточку: своего шеврона у неё больше нет (SKL-158).
     * Возврат — тот же жест/кнопка платформы, каким выходит форма лекарства (Р-54).
     */
    @Test
    fun theSystemBackReturnsToTheList() {
        val name = testContext.getString(R.string.device_test_medication_card)
        seedMedication(id = "dev-med-back", name = name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()

        launchApp()
        awaitRoot()
        openMedications()
        scrollTo(TAG_MEDICATION_LIST, TAG_MEDICATION_CARD)
        click(TAG_MEDICATION_CARD)
        waitForTag(TAG_MEDICATION_CARD_SCREEN)

        pressBack()

        waitForTag(TAG_MEDICATION_LIST)
        capture("list-after-system-back")
    }

    private companion object {
        const val COURSE_DAYS = 30L
        const val STOCK_BEFORE = "9"
        const val STOCK_AFTER = "30"
        const val PACKAGE_SIZE = "60"
    }
}
