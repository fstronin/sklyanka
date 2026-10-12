package ru.stohastika.sklyanka.screen.journey

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.feature.meds.card.TAG_EDIT
import ru.stohastika.sklyanka.feature.meds.card.TAG_MEDICATION_CARD_SCREEN
import ru.stohastika.sklyanka.feature.meds.card.TAG_NOTICE
import ru.stohastika.sklyanka.feature.meds.card.TAG_REFILL
import ru.stohastika.sklyanka.feature.meds.card.TAG_REFILL_CONFIRM
import ru.stohastika.sklyanka.feature.meds.card.TAG_REFILL_ROW
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_VALUE
import ru.stohastika.sklyanka.feature.meds.form.TAG_DOSE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_REMINDER
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_THRESHOLD_OPTIONS
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.test.R
import java.math.BigDecimal
import java.time.LocalTime
import ru.stohastika.sklyanka.feature.meds.R as MedsR

/**
 * Враждебный проход «Карточка лекарства» (SKL-140, реестр походов № 16, поверхность
 * `MedsDestination.Card`).
 *
 * Человек пробует сломать блок остатка: вводит отрицательное значение, дважды подряд вводит
 * ноль, правит дозу выше остатка и уходит из карточки системной «Назад» ровно посреди ввода.
 *
 * Проверяемая норма — §2.4 `docs/norm/04-behavior.md`: пополнение **заменяет** значение остатка,
 * отклоняется только отрицательное, нулевой остаток законен («упаковка кончилась»), потолка нет.
 * Отсюда приёмы с разными следствиями:
 *
 * - **отрицательное** — отказано, остаток не тронут, следа в журнале нет, строка ввода остаётся
 *   открытой, человеку назван отказ (`error_stock_negative` под полем, не баннер);
 * - **ноль** — принят, показанный остаток становится нулём, и на каждое пополнение в журнал
 *   ложится запись `STOCK_REFILLED`;
 * - **доза выше остатка** — предупреждение покупки названо, а введённая доза сохранена как есть;
 * - **уход системной «Назад» посреди ввода** — набранное число не доходит до записи, остаток прежний.
 *
 * Свидетельство каждого приёма — не подпись на экране, а состояние: показанный остаток
 * `TAG_STOCK_VALUE` и число следов `STOCK_REFILLED` в журнале операций.
 *
 * **Вне проверки (вопрос, не находка).** Подпись остатка собирает `stockLeftLabel(left.toInt())`,
 * поэтому значение выше `Int.MAX_VALUE` показалось бы обёрнутым числом. Утверждать это здесь
 * нечем: нормы на потолок нет, а вопрос о переполнении подписи — к владельцу, не к походу.
 */
@HiltAndroidTest
class HostileMedicationCardTest : JourneyTest() {

    @Test
    fun thePersonCannotBreakTheMedicationCard() {
        seedJourney()
        launchApp()
        awaitRoot()
        openMedications()
        openMedicationCard(name)
        waitForTag(TAG_STOCK_BLOCK)
        waitUntil(SCREEN_TIMEOUT_MS, "остатка «5 таблеток» в карточке") {
            nodeText(TAG_STOCK_VALUE) == stockLabel(STOCK_START)
        }
        capture("card-stock")

        negativeRefillIsRefused()
        zeroRefillTwiceIsAccepted()
        doseAboveStockIsWarnedAndSaved()
        leavingMidRefillKeepsTheStock()
    }

    /** Сид похода: «Магний B6» с приёмом в 08:00 и остатком 5 таблеток, приёмы спланированы. */
    private fun seedJourney() {
        seedMedication(
            id = MEDICATION_ID,
            name = name,
            times = listOf(INTAKE_TIME),
            stockLeft = BigDecimal(STOCK_START),
        )
        planIntakes()
    }

    /**
     * Отрицательное значение отбито: остаток не тронут, отказ назван, следа в журнале нет.
     *
     * Отказ продукт называет под полем строки (`error_stock_negative`), а не баннером: строка
     * остаётся открытой вместе с введённым числом, чтобы человек исправил его, а не набирал заново.
     *
     * «Сохранить» доводится до вида прокруткой: `typeInto` прокручивает к полю лишь настолько,
     * чтобы поле вошло в окно (внизу полосы прокрутки), а кнопки строки остаются под окном —
     * `performClick` по невидимой кнопке до её `onClick` не доходит, и отказа не появляется вовсе.
     */
    private fun negativeRefillIsRefused() {
        val written = opLogCount(OpEvent.STOCK_REFILLED)
        val before = nodeText(TAG_STOCK_VALUE)

        scrollToAndClick(TAG_REFILL)
        waitForTag(TAG_REFILL_ROW)
        typeInto(TAG_REFILL_ROW, NEGATIVE)
        scrollToAndClick(TAG_REFILL_CONFIRM)

        waitUntil(SCREEN_TIMEOUT_MS, "названного отказа на отрицательное значение") {
            textExists(refusalText())
        }
        assertTrue("the input must stay open so the value can be fixed", exists(TAG_REFILL_ROW))
        assertEquals("a refused value must not touch the stock", before, nodeText(TAG_STOCK_VALUE))
        assertEquals("a refused value must not reach the journal", written, opLogCount(OpEvent.STOCK_REFILLED))
        capture("card-negative-refused")
    }

    /**
     * Ноль, введённый дважды подряд, — законный остаток «упаковка кончилась» (§2.4).
     *
     * Ноль не то же самое, что пустой остаток: он записывается числом, показывается нулём
     * и оставляет по себе запись в журнале. Отказа человеку не названо — и это норма, а не дефект.
     */
    private fun zeroRefillTwiceIsAccepted() {
        val written = opLogCount(OpEvent.STOCK_REFILLED)

        refillWith(ZERO)
        waitUntil(SCREEN_TIMEOUT_MS, "нулевого остатка в карточке") {
            nodeText(TAG_STOCK_VALUE) == stockLabel(ZERO_UNITS)
        }
        waitUntil(SCREEN_TIMEOUT_MS, "первой записи журнала о нулевом пополнении") {
            opLogCount(OpEvent.STOCK_REFILLED) == written + 1
        }
        // Строка закрывается только у принятого значения: отказ оставил бы её открытой.
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия строки пополнения после нуля") { !exists(TAG_REFILL_ROW) }
        assertTrue("a legal zero must not be blamed", !exists(TAG_NOTICE))
        capture("card-zero-refill")

        refillWith(ZERO)
        waitUntil(SCREEN_TIMEOUT_MS, "второй записи журнала о нулевом пополнении") {
            opLogCount(OpEvent.STOCK_REFILLED) == written + TWO_REFILLS
        }
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия строки пополнения во второй раз") { !exists(TAG_REFILL_ROW) }
        assertTrue("a second legal zero must not be blamed either", !exists(TAG_NOTICE))
    }

    /**
     * Доза выше остатка: человек правит дозу в форме и включает напоминание о покупке.
     *
     * Предупреждение карточки — не баннер `TAG_NOTICE` и не строка формы `TAG_STOCK_REMINDER`,
     * а надпись справа от заголовка блока остатка: `StockPanel.warning` с подписью
     * «хватит на N дней». Её и утверждаем вместе с тем, что введённая доза сохранена как есть,
     * а не подрезана по остатку.
     */
    private fun doseAboveStockIsWarnedAndSaved() {
        click(TAG_EDIT)
        waitForTag(TAG_MEDICATION_FORM)
        replaceText(TAG_DOSE_FIELD, DOSE_ABOVE_STOCK)
        scrollToAndClick(TAG_STOCK_REMINDER)
        waitForTag(TAG_STOCK_THRESHOLD_OPTIONS)
        capture("form-dose-above-stock")
        // Кнопка сохранения — закреплённая нижняя панель формы, вне прокручиваемой колонки:
        // прокрутка к ней недостижима, нажатие — доступно.
        click(TAG_SAVE_BUTTON)
        waitForTag(TAG_MEDICATION_CARD_SCREEN)

        // Доза 9 при остатке 0 — остатка не хватает ни на один полный день: «хватит на 0 дней».
        waitUntil(SCREEN_TIMEOUT_MS, "предупреждения «хватит на 0 дней»") {
            textExists(stockDaysLabel(NO_DAYS))
        }
        assertTrue(
            "the dose must be saved as entered, not clamped to the stock",
            textExists(scheduleSummary(INTAKES_PER_DAY, DOSE_ABOVE_STOCK)),
        )
        capture("card-dose-warning")
    }

    /**
     * Уход системной «Назад» посреди ввода остаток не меняет.
     *
     * Карточка перехвата возврата не заводит, и «Назад» покидает её в список; набранное число
     * живёт в состоянии экрана и умирает вместе с ним. Проверяется входом в карточку заново.
     */
    private fun leavingMidRefillKeepsTheStock() {
        val before = nodeText(TAG_STOCK_VALUE)

        scrollToAndClick(TAG_REFILL)
        waitForTag(TAG_REFILL_ROW)
        typeInto(TAG_REFILL_ROW, UNSAVED)
        capture("refill-mid-input")

        pressBack()
        waitForTag(TAG_MEDICATION_LIST)
        openMedicationCard(name)
        waitUntil(SCREEN_TIMEOUT_MS, "прежнего остатка после ухода из карточки") {
            nodeText(TAG_STOCK_VALUE) == before
        }
    }

    /**
     * Открыть пополнение, ввести значение и подтвердить: путь человека, без промежуточных утверждений.
     *
     * Подтверждение — той же прокруткой, что в [negativeRefillIsRefused]: после `typeInto` кнопка
     * строки стоит под окном прокрутки, и `click` по ней мимо `onClick`.
     */
    private fun refillWith(value: String) {
        scrollToAndClick(TAG_REFILL)
        waitForTag(TAG_REFILL_ROW)
        typeInto(TAG_REFILL_ROW, value)
        scrollToAndClick(TAG_REFILL_CONFIRM)
    }

    /** «Остаток не может быть отрицательным» — названный отказ под полем строки пополнения. */
    private fun refusalText(): String = targetContext.getString(MedsR.string.error_stock_negative)

    /** «осталось 5 таблеток» — строку блока остатка собирает продукт из ресурсов. */
    private fun stockLabel(units: Int): String =
        targetContext.resources.getQuantityString(MedsR.plurals.stock_left_tablets, units, units)

    /** «хватит на 0 дней» — подпись предупреждения о покупке у блока остатка. */
    private fun stockDaysLabel(days: Int): String =
        targetContext.resources.getQuantityString(MedsR.plurals.card_stock_days_left, days, days)

    /** «1 приём в сутки · по 9 таблеток» — фраза расписания карточки с сохранённой дозой. */
    private fun scheduleSummary(intakesPerDay: Int, dose: String): String {
        val res = targetContext.resources
        val frequency = res.getQuantityString(
            MedsR.plurals.card_intakes_per_day,
            intakesPerDay,
            intakesPerDay,
        )
        val doseText = res.getQuantityString(MedsR.plurals.card_dose_tablets, dose.toInt(), dose)
        return frequency + res.getString(MedsR.string.meds_separator) + doseText
    }

    private val name: String get() = testContext.getString(R.string.store_medication_magnesium)

    private companion object {
        const val MEDICATION_ID = "hostile-card"
        const val STOCK_START = 5
        const val NEGATIVE = "-5"
        const val ZERO = "0"
        const val ZERO_UNITS = 0
        const val DOSE_ABOVE_STOCK = "9"
        const val UNSAVED = "7"
        const val INTAKES_PER_DAY = 1
        const val NO_DAYS = 0
        const val TWO_REFILLS = 2
        val INTAKE_TIME: LocalTime = LocalTime.of(8, 0)
    }
}
