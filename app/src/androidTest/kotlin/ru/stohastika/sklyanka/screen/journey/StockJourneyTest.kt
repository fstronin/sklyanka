package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.feature.meds.card.TAG_EDIT
import ru.stohastika.sklyanka.feature.meds.card.TAG_MEDICATION_CARD_SCREEN
import ru.stohastika.sklyanka.feature.meds.card.TAG_REFILL
import ru.stohastika.sklyanka.feature.meds.card.TAG_REFILL_CONFIRM
import ru.stohastika.sklyanka.feature.meds.card.TAG_REFILL_ROW
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_VALUE
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_REMINDER
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_THRESHOLD_OPTIONS
import ru.stohastika.sklyanka.feature.today.TAG_STOCK_BANNER
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import java.math.BigDecimal
import ru.stohastika.sklyanka.feature.meds.R as MedsR
import ru.stohastika.sklyanka.feature.today.R as TodayR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Поход «Остаток, порог покупки и пополнение» (SKL-140, реестр походов № 4).
 *
 * Человек заводит лекарство с остатком, отмечает приём (доза списывается), доводит остаток
 * до порога покупки, пополняет упаковку — и предупреждение уходит. Поход идёт на настоящем
 * файле базы: остаток — это запись, а не состояние экрана, и он обязан жить там же, где живёт
 * у человека.
 *
 * **Три расхождения со сценарием, названные честно.**
 *
 * 1. Время приёма — [DeviceTestClock.scheduledTime] (09:00), а не «08:00» сценария: это
 *    посеянное время волны, и базовый момент стоит на минуту позже него, поэтому приём
 *    сегодня уже наступил и ещё не считается несостоявшимся (`OutageDetector.GRACE`).
 *    Курс начинается вчера (правило сида), иначе сегодняшнего приёма не существовало бы.
 * 2. Порог покупки сид задать не умеет, а без порога предупреждения не существует вовсе
 *    (`StockWarning` требует порог). Поэтому порог выбирает сам человек — в форме, где
 *    «Напомнить о покупке» открывает набор `StockThresholds`. Выбраны 10 дней, а не порог
 *    по умолчанию 7: после отметки остатка хватает на 8 дней, и с семью днями плашка
 *    не появилась бы — сравнение с порогом строгое (`daysLeft < thresholdDays`).
 * 3. «Остаток падает ниже порога» здесь — пара (остаток 8, порог 10), а не падение само
 *    по себе: одного списания дозы для этого не хватает.
 *
 * **Чего поход не утверждает.** Снятия предупреждения в журнале
 * (`OpEvent.STOCK_WARNING_CLEARED`) здесь нет: поток предупреждения холодный, а его
 * наблюдатель — экран «Сегодня» — создаётся заново при возвращении на день. Попадёт ли
 * переход в журнал, зависит от того, был ли наблюдатель жив в момент пополнения, а не от
 * правила продукта. Исчезновение плашки засвидетельствовано экраном и блоком остатка.
 */
@HiltAndroidTest
class StockJourneyTest : JourneyTest() {

    private val name: String get() = testContext.getString(R.string.store_medication_magnesium)

    @Test
    fun thePersonLosesStockGetsTheWarningAndRefills() {
        seedJourney()
        launchApp()
        awaitRoot()
        waitUntil(SCREEN_TIMEOUT_MS, "приёма «$name» на «Сегодня»") { textExists(name) }

        markTakenInTheDay()
        seeTheWriteOffAndSetTheThreshold()
        seeTheWarningAndRefillThePackage()
    }

    /** Сид похода: лекарство с остатком 9 и упаковкой 60, приёмы спланированы. */
    private fun seedJourney() {
        seedMedication(
            id = MEDICATION_ID,
            name = name,
            times = listOf(DeviceTestClock.scheduledTime),
            stockLeft = BigDecimal(STOCK_LEFT),
            packageSize = BigDecimal(PACKAGE_SIZE),
        )
        planIntakes()
    }

    /** Отметка приёма на «Сегодня»: окно отмены ждём и даём уйти самому, без сна. */
    private fun markTakenInTheDay() {
        compose.onNodeWithText(ui(UiR.string.action_taken), useUnmergedTree = true).performClick()
        waitUntil(SCREEN_TIMEOUT_MS, "окна отмены отметки") { exists(TAG_SNACKBAR) }
        capture("today-marked")
        // Окно отмены уходит само; ждём закрытия, чтобы нижняя панель была свободна для человека.
        waitUntil(SCREEN_TIMEOUT_MS, "ухода окна отмены") { !exists(TAG_SNACKBAR) }

        assertEquals("the mark must be stored as taken", 1, takenToday())
        assertTrue("the taken dose must be written off", opLogHas(OpEvent.STOCK_DECREMENTED))
    }

    /** Человек видит списание в карточке и открывает в форме порог покупки. */
    private fun seeTheWriteOffAndSetTheThreshold() {
        openMedications()
        openMedicationCard(name)
        scrollTo(TAG_MEDICATION_CARD_SCREEN, TAG_STOCK_VALUE)
        waitUntil(SCREEN_TIMEOUT_MS, "остатка «$UNITS_AFTER_TAKE из $PACKAGE_SIZE»") {
            exists(TAG_STOCK_VALUE) && nodeText(TAG_STOCK_VALUE) == stockText(UNITS_AFTER_TAKE)
        }
        capture("card-after-intake")

        click(TAG_EDIT)
        waitForTag(TAG_MEDICATION_FORM)
        scrollToAndClick(TAG_STOCK_REMINDER)
        waitForTag(TAG_STOCK_THRESHOLD_OPTIONS)
        chooseThreshold(THRESHOLD_DAYS)
        waitUntil(SCREEN_TIMEOUT_MS, "подписи выбранного порога") {
            textExists(thresholdCaption(THRESHOLD_DAYS))
        }
        capture("form-purchase-reminder")
        // Кнопка сохранения — в закреплённой нижней панели, соседе прокрутки, а не в ней:
        // у неё нет предка с действием прокрутки, и `performScrollTo` на ней падает
        // («no parent layout with a Scroll SemanticsAction»). Панель всегда на экране,
        // поэтому нажимаем напрямую.
        click(TAG_SAVE_BUTTON)
        waitForTag(TAG_MEDICATION_CARD_SCREEN)
    }

    /** Плашка покупки на дне и её уход после пополнения упаковки до 60. */
    private fun seeTheWarningAndRefillThePackage() {
        pressBack()
        openToday()
        waitUntil(SCREEN_TIMEOUT_MS, "плашки покупки на «Сегодня»") { exists(TAG_STOCK_BANNER) }
        // Доза — одна таблетка в сутки, поэтому дней в прогнозе столько же, сколько единиц.
        assertTrue(
            "the warning must name the units left and the days they last",
            textExists(warningText(UNITS_AFTER_TAKE, UNITS_AFTER_TAKE)),
        )
        capture("today-purchase-warning")
        assertTrue("raising the warning must reach the log", opLogHas(OpEvent.STOCK_WARNING_RAISED))

        openMedications()
        openMedicationCard(name)
        scrollTo(TAG_MEDICATION_CARD_SCREEN, TAG_STOCK_VALUE)
        scrollToAndClick(TAG_REFILL)
        waitForTag(TAG_REFILL_ROW)
        typeInto(TAG_REFILL_ROW, PACKAGE_SIZE)
        click(TAG_REFILL_CONFIRM)
        waitUntil(SCREEN_TIMEOUT_MS, "остатка «$PACKAGE_SIZE из $PACKAGE_SIZE»") {
            exists(TAG_STOCK_VALUE) && nodeText(TAG_STOCK_VALUE) == stockText(PACKAGE_UNITS)
        }
        capture("card-refilled")
        assertTrue("the refill must reach the log", opLogHas(OpEvent.STOCK_REFILLED))

        pressBack()
        openToday()
        // Лента дня обязана быть нарисована: отсутствие плашки на пустом экране ничего не доказывает.
        waitUntil(SCREEN_TIMEOUT_MS, "ленты дня без плашки покупки") {
            exists(TAG_TODAY_SCREEN) && textExists(name) && !exists(TAG_STOCK_BANNER)
        }
        capture("today-warning-cleared")

        assertEquals("the refill must not touch the mark of the day", 1, takenToday())
    }

    /** Отмеченные сегодня приёмы: списание остатка — следствие отметки, а не картинка. */
    private fun takenToday(): Int = dayIntakes().count { it.status == IntakeStatus.TAKEN }

    /** Чип порога: у каждого чипа нет тега, его подписью служит само число дней набора. */
    private fun chooseThreshold(days: Int) {
        compose.onNode(
            hasText(days.toString()) and hasAnyAncestor(hasTestTag(TAG_STOCK_THRESHOLD_OPTIONS)),
        ).performScrollTo().performClick()
    }

    /** «осталось 8 таблеток из 60» — строку блока остатка собирает продукт из ресурсов. */
    private fun stockText(unitsLeft: Int): String {
        val resources = targetContext.resources
        val left = resources.getQuantityString(MedsR.plurals.stock_left_tablets, unitsLeft, unitsLeft)
        return resources.getString(MedsR.string.card_stock_of_package, left, PACKAGE_SIZE)
    }

    /** «Магний B6 — осталось 8 таблеток, хватит на 8 дней» — плашка покупки на «Сегодня». */
    private fun warningText(unitsLeft: Int, daysLeft: Int): String {
        val resources = targetContext.resources
        val left = resources.getQuantityString(
            TodayR.plurals.today_stock_left_tablets,
            unitsLeft,
            unitsLeft.toString(),
        )
        val days = resources.getQuantityString(TodayR.plurals.today_days_left, daysLeft, daysLeft)
        return resources.getString(TodayR.string.today_stock_warning, name, left, days)
    }

    /** «Когда останется на 10 дней» — подпись ряда порогов формы. */
    private fun thresholdCaption(days: Int): String =
        targetContext.resources.getQuantityString(MedsR.plurals.stock_threshold_caption, days, days)

    private companion object {
        const val MEDICATION_ID = "journey-stock"
        const val STOCK_LEFT = "9"
        const val PACKAGE_SIZE = "60"
        const val PACKAGE_UNITS = 60
        const val UNITS_AFTER_TAKE = 8
        const val THRESHOLD_DAYS = 10
    }
}
