package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.SaveResult
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.pro.ProNotice
import ru.stohastika.sklyanka.domain.pro.ProPoint
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.pro.ProStoreFailure
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.stock.StockThresholds
import ru.stohastika.sklyanka.domain.stock.StockWarningForProfileUseCase
import java.io.File
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Шаги приёмки SKL-122 — витрина «Склянка Про», покупка и её отсутствие в лечебном сценарии.
 *
 * Мир подставляет **магазин**, а правила исполняет настоящий `ProSheetSession`
 * (`docs/tasks/SKL-122/TDD.md` §2): сценарий говорит «кнопка недоступна» и «причина названа
 * словами» про то же состояние, каким живёт экран. Текстов интерфейса в чистом JVM-модуле нет,
 * поэтому «названа словами» проверяется двумя вещами: модель называет **ключ** причины,
 * а ключ имеет непустую строку в каталоге `core-ui` — существующий страж полноты переводов
 * держит её во всех шести языках (`LocalizationResourcesTest`).
 *
 * **Чего эти шаги не проверяют.** Вёрстку и цены на экране — их держат экранный прогон
 * `@screen`, кадр похода и матрица шести языков; здесь проверяются состояния, которые
 * поверхность обязана показать.
 */
@Suppress("TooManyFunctions")
class Sk122ProSteps(private val world: AcceptanceWorld) {

    /** Приём с предъявленной карточкой — им говорят строки «в ней» и «в подтверждении». */
    private var shownIntakeId: String? = null

    /** Лекарство лечебного сценария: без него карточки, отметки и остатка не существует. */
    private var medicalMedication: String? = null

    // --- Предыстория: магазин -------------------------------------------------------------

    @Допустим("^в магазине продаются «([^»]+)» за (\\d+) ₽ и «([^»]+)» за (\\d+) ₽$")
    fun theStoreSellsBothPeriods(monthlyName: String, monthlyRubles: Long, yearlyName: String, yearlyRubles: Long) {
        world.pro.sells(
            monthlyRubles = monthlyRubles,
            yearlyRubles = yearlyRubles,
            monthlyName = monthlyName,
            yearlyName = yearlyName,
        )
    }

    @Допустим("^магазин продаёт год за (\\d+) ₽ при месяце за (\\d+) ₽$")
    fun theStoreSellsYearFor(yearlyRubles: Long, monthlyRubles: Long) {
        world.pro.sells(monthlyRubles = monthlyRubles, yearlyRubles = yearlyRubles)
    }

    @Допустим("^магазин отвечает отказом на покупку$")
    fun theStoreRefusesThePurchase() {
        world.pro.refusesPurchase(ProStoreFailure.NETWORK)
    }

    @Допустим("^подписка «Склянка Про» не оформлена$")
    fun theSubscriptionIsNotPurchased() {
        world.pro.none()
    }

    @Допустим("^подписка «Склянка Про» активна до (.+)$")
    fun theSubscriptionIsActiveUntil(until: String) {
        world.pro.owned(ProProductId.MONTHLY, world.dateOf(until))
    }

    @Допустим("^подписка «Склянка Про» истекла (.+)$")
    fun theSubscriptionExpiredOn(date: String) {
        // Истёкшая подписка — это «магазин назвал срок, и срок прошёл»: доступ считает
        // не хранилище, а правило истечения (Р-67 п. 3, ADR-0015).
        world.pro.owned(ProProductId.MONTHLY, world.dateOf(date))
    }

    /**
     * Человек закрыл окно магазина, не заплатив (Р-68 п. 6).
     *
     * Шаг делает весь путь сам: открывает витрину, выбирает период и нажимает «Оформить», —
     * потому что сценарий говорит только о том, что человек ушёл из окна, а проверяет
     * состояние шторки после этого.
     */
    @Допустим("^человек закрыл окно магазина, не заплатив$")
    fun thePersonClosedTheStoreWindow() {
        world.pro.sells(monthlyRubles = DEFAULT_MONTHLY_RUBLES, yearlyRubles = DEFAULT_YEARLY_RUBLES)
        world.pro.personClosedTheWindow()
        openShowcase()
        world.pro.session.select(ProProductId.MONTHLY)
        runBlocking { world.pro.session.buy() }
    }

    @Допустим("^покупка есть в магазине, а на устройстве состояние потеряно после переустановки$")
    fun thePurchaseIsInTheStoreAndTheDeviceForgotIt() {
        world.pro.forget()
        world.pro.restoreFinds(ProProductId.YEARLY, world.clock.today().plusMonths(1))
    }

    @Допустим("^магазин подтверждает покупку, а записи о ней не отдаёт$")
    fun theStoreConfirmsThePurchaseWithoutARecord() {
        world.pro.sells(monthlyRubles = DEFAULT_MONTHLY_RUBLES, yearlyRubles = DEFAULT_YEARLY_RUBLES)
        world.pro.confirmsPurchaseWithoutARecord()
    }

    @Допустим("^в магазине есть записи о подписке, ни одна не действует$")
    fun theStoreHasRecordsThatAreNotActive() {
        world.pro.restoreFindsRecordsThatAreNotActive()
    }

    @Допустим("^магазин не может открыть экран подписок$")
    fun theStoreCannotOpenItsSubscriptionScreen() {
        world.pro.sells(monthlyRubles = DEFAULT_MONTHLY_RUBLES, yearlyRubles = DEFAULT_YEARLY_RUBLES)
        world.pro.managementDoesNotOpen()
    }

    @Допустим("^у человека заведён ежедневный приём в (\\d{2}:\\d{2})$")
    fun thePersonHasADailyIntakeAt(time: String) {
        val name = MEDICAL_MEDICATION
        val dose = LocalTime.parse(time)
        // Курс заводится за час до приёма, и это не удобство сценария: продукт намеренно
        // не ставит будильник на приём, чьё время уже прошло (НФТ-35, `RescheduleRemindersUseCase`),
        // поэтому «напоминание предъявлено» при часах, стоящих после дозы, недостижимо вовсе.
        world.clock.moveTo(world.clock.today().atTime(dose.minusHours(HOUR_BEFORE_THE_DOSE)))
        val result = world.save(
            MedicationDraft(
                id = null,
                profileId = world.profiles.get(world.profiles.active).id,
                name = name,
                form = MedicationForm.TABLET,
                doseAmount = BigDecimal.ONE,
                scheduleMode = ScheduleMode.SCHEDULED,
                times = listOf(dose),
                startDate = world.clock.today(),
                stockLeft = STOCK_ENOUGH,
                stockThresholdDays = StockThresholds.DEFAULT_DAYS,
            ),
        )
        assertTrue(result is SaveResult.Saved, "the medication must be created: $result")
        medicalMedication = name
        // Будильники пересобираются тем же путём, что у человека после сохранения курса:
        // без пересборки приём есть, а срабатывания нет, и «напоминание предъявлено»
        // проверяло бы состояние, которого сценарий не создавал.
        world.reminders.reschedule(RescheduleReason.MANUAL)
    }

    // --- Действия: витрина, покупка, восстановление -----------------------------------------

    @Когда("^человек открывает настройки и строку «Склянка Про»$")
    fun thePersonOpensSettingsAndTheProRow() {
        // Строка настроек живёт в ресурсах и вёрстке модуля экрана, а её нажимаемое значение —
        // в модели состояния подписки. Проверяется то, что доступно без Android: строка ведёт
        // в витрину (намерение и тег) и несёт состояние подписки тем же чтением, что и экран.
        val settingsScreen = File(
            RepositoryLayout.root,
            "feature-settings/src/main/kotlin/ru/stohastika/sklyanka/feature/settings/SettingsScreen.kt",
        ).readText()
        assertTrue(
            settingsScreen.contains("TAG_PRO_ROW") && settingsScreen.contains("SettingsIntent.OpenPro"),
            "the settings screen must lead to the showcase with its own row",
        )
        openShowcase()
    }

    @Когда("^открывается шторка «Склянка Про»$")
    fun theShowcaseOpens() {
        openShowcase()
    }

    @Когда("^магазин не отвечает$")
    fun theStoreIsSilent() {
        world.pro.silent()
    }

    @Когда("^человек выбирает период «([^»]+)» и нажимает «Оформить»$")
    fun thePersonChoosesThePeriodAndBuys(period: String) {
        // Период выбирают на открытой витрине: выбор — нажатие на карточке периода (Р-68 п. 1).
        openShowcase()
        world.pro.session.select(periodId(period))
        runBlocking { world.pro.session.buy() }
    }

    @Когда("^человек нажимает «Управление подпиской»$")
    fun thePersonManagesTheSubscription() {
        // Экран подписок — экран магазина: приложение туда только ведёт (Р-73 п. 1).
        runBlocking { world.pro.session.manage() }
    }

    @Когда("^человек нажимает «Восстановить покупку»$")
    fun thePersonRestoresThePurchase() {
        // Шторка уже открыта сценарием: восстановление живёт на ней (Р-67 п. 4).
        openShowcase()
        runBlocking { world.pro.session.restore() }
    }

    @Когда("^человеку предъявлена карточка напоминания$")
    fun theReminderCardIsShown() {
        // Срабатывание идёт как у системы: часы доходят до назначенного часа, будильник
        // срабатывает, карточка встаёт в шторку (тот же приём, что у шагов напоминаний).
        val intake = focusedIntake()
        val moment = intake.scheduledAt
        world.clock.moveTo(moment)
        world.reminders.deliverThrough(moment)
        shownIntakeId = intake.id
        assertTrue(world.reminders.isNotificationShown(intake.id), "the reminder card must be in the shade")
    }

    @Когда("^человек отмечает приём «Принято»$")
    fun thePersonMarksTheIntakeTaken() {
        // Отметка идёт настоящим сценарием домена по сфокусированному приёму: сценарий
        // проверяет, что отметка работает и что слов про подписку в её подтверждении нет.
        focusedIntake()
        world.markFromPhone(world.clock.nowLocal(), MarkResult.TAKEN)
    }

    @Когда("^остаток упаковки становится ниже порога$")
    fun theStockGoesBelowTheThreshold() {
        val medication = requireNotNull(medicalMedication) { "the scenario must bring a medication" }
        world.medications.update(medication) { it.copy(stockLeft = BigDecimal.ONE) }
    }

    // --- Проверки: витрина и её состояния ---------------------------------------------------

    @Тогда("^в ней названы шесть возможностей «Про»$")
    fun theSixBenefitsAreNamed() {
        assertVisible()
        assertEquals(SIX, world.pro.state.benefits.size, "the showcase must name six benefits")
    }

    @Тогда("^шесть возможностей названы$")
    fun sixBenefitsAreNamed() {
        assertVisible()
        assertEquals(SIX, world.pro.state.benefits.size, "the showcase must name six benefits")
    }

    @Тогда("^строка настроек несёт значение «Не оформлена»$")
    fun theSettingsRowSaysNotSubscribed() {
        val status = world.pro.status()
        assertEquals(ProNotice.STATE_INACTIVE, status.notice, "the row must say the subscription is absent")
        assertWordsAreInTheCatalog(ProNotice.STATE_INACTIVE, "Не оформлена")
    }

    @Тогда("^у обоих периодов названы цены магазина$")
    fun bothPeriodsNameStorePrices() {
        assertVisible()
        assertEquals(TWO, world.pro.state.periods.size, "both periods must be offered")
        world.pro.state.periods.forEach { offer ->
            assertTrue(offer.price.text.isNotBlank(), "the store must name a price of ${offer.id}")
        }
    }

    @Тогда("^ни один период не выбран$")
    fun noPeriodIsChosen() {
        assertNull(world.pro.state.selected, "the showcase must not preselect a period")
    }

    @Тогда("^в карточке года стоит «Год дешевле»$")
    fun theYearCardSaysItIsCheaper() {
        assertTrue(world.pro.state.yearlyCheaper, "the yearly claim must be made")
    }

    @Тогда("^слов «Год дешевле» нет ни в карточке года, ни рядом с ней$")
    fun theYearCardDoesNotClaimToBeCheaper() {
        assertFalse(world.pro.state.yearlyCheaper, "the yearly claim must not be made")
        // Само слово в каталоге есть: отсутствие плашки — свойство текущих цен, а не пропавший текст.
        assertWordsAreInTheCatalog(ProNotice.YEAR_CHEAPER, "Год дешевле")
    }

    @Тогда("^магазин получает запрос на товар «([^»]+)»$")
    fun theStoreReceivesTheRequest(productId: String) {
        assertEquals(
            listOf(productId),
            world.pro.requestedProducts.map { it.storeId },
            "the store must receive the identifier of the chosen product",
        )
    }

    @Тогда("^до ответа магазина «Про» не открывается$")
    fun proDoesNotOpenBeforeTheStoreAnswers() {
        assertFalse(world.pro.state.management, "'Pro' must not open before the store confirms")
    }

    @Тогда("^шторка остаётся открытой$")
    fun theShowcaseStaysOpen() {
        assertTrue(world.pro.state.visible, "the showcase must stay open")
    }

    @Тогда("^причина названа словами$")
    fun theReasonIsNamedInWords() {
        val notice = world.pro.state.notice
        assertNotNull(notice, "the reason must be named")
        assertWordsAreInTheCatalog(requireNotNull(notice))
    }

    @Тогда("^слова не называют покупку несостоявшейся$")
    fun theWordsDoNotCallThePurchaseFailed() {
        // Магазин сказал «успех»: отказом это называть нельзя ни в одну сторону (П-7).
        assertEquals(ProNotice.PURCHASE_CONFIRMING, world.pro.state.notice)
        assertWordsAreInTheCatalog(ProNotice.PURCHASE_CONFIRMING)
    }

    @Тогда("^сказано, что магазин знает о подписке, но она не действует$")
    fun theWordsNameTheSubscriptionThatIsNotActive() {
        assertEquals(ProNotice.RESTORE_NOT_ACTIVE, world.pro.state.notice)
        assertWordsAreInTheCatalog(ProNotice.RESTORE_NOT_ACTIVE)
    }

    @Тогда("^сказано, что подписка не отменена, потому что экран магазина не открылся$")
    fun theWordsNameTheScreenThatDidNotOpen() {
        assertEquals(ProNotice.MANAGE_FAILED, world.pro.state.notice)
        assertWordsAreInTheCatalog(ProNotice.MANAGE_FAILED)
        assertTrue(
            world.pro.managementOpened == 0,
            "the journal must not claim the person visited the store",
        )
    }

    @Тогда("^«Про» не открывается$")
    fun proDoesNotOpen() {
        assertFalse(world.pro.state.management, "'Pro' must not open")
    }

    @Тогда("^сообщений о неудаче нет$")
    fun thereIsNoFailureMessage() {
        // Своя отмена окна покупки — не событие приложения: сообщение о собственном решении
        // человека было бы шумом (Р-68 п. 6).
        assertNull(world.pro.state.notice, "the person's own cancellation must not be commented")
    }

    @Тогда("^цен нет ни у одного периода$")
    fun noPeriodHasAPrice() {
        assertTrue(world.pro.state.storeUnavailable, "the store must be unavailable")
        assertTrue(world.pro.state.periods.isEmpty(), "no period may carry a price")
    }

    @Тогда("^главное действие «Оформить» недоступно$")
    fun theBuyButtonIsDisabled() {
        assertFalse(world.pro.state.buyEnabled, "the buy action must be unavailable")
    }

    @Тогда("^доступ «Про» сохранён$")
    fun theProAccessIsKept() {
        assertTrue(world.pro.state.management, "the active subscription must keep its access")
    }

    @Тогда("^тревожного вида нет$")
    fun thereIsNoAlarmingView() {
        assertNull(world.pro.state.notice, "a failed check must not look alarming")
    }

    // --- Проверки: управление и раскрытие ---------------------------------------------------

    @Тогда("^состояние названо «Активна до (.+)», и назван состав «Про»$")
    fun theStateAndTheCompositionAreNamed(until: String) {
        assertTrue(world.pro.state.management, "the active state must be shown")
        val status = world.pro.status()
        assertEquals(world.dateOf(until), status.until, "the state must name the date the store gave")
        assertEquals(SIX, world.pro.state.benefits.size, "the composition must stay visible")
        assertWordsAreInTheCatalog(ProNotice.STATE_ACTIVE_UNTIL)
    }

    @Тогда("^названа цена продления$")
    fun theRenewalPriceIsNamed() {
        val price = world.pro.state.renewalPrice
        assertNotNull(price, "the renewal price must be named")
        assertTrue(requireNotNull(price).text.isNotBlank(), "the renewal price must come from the store")
    }

    @Тогда("^«Отменить подписку» ведёт в раздел подписок магазина$")
    fun theCancelRowLeadsToTheStore() {
        runBlocking { world.pro.session.manage() }
        assertEquals(1, world.pro.managementOpened, "the cancel row must lead to the store subscriptions")
    }

    @Тогда("^своя отмена подписки приложением не делается$")
    fun theProductDoesNotCancelTheSubscriptionItself() {
        // Своего экрана отмены у продукта нет: отмена платформенная (Р-73 п. 1). Проверяется
        // тем, что после ухода в магазин доступ остался прежним — приложение подписку
        // не тронуло.
        assertTrue(world.pro.state.management, "the product must not change the subscription itself")
    }

    @Тогда("^магазин подтверждает владение$")
    fun theStoreConfirmsTheOwnership() {
        assertTrue(world.pro.state.management, "the store must confirm the ownership")
    }

    @Тогда("^«Про» открывается вновь$")
    fun proOpensAgain() {
        assertTrue(world.pro.state.management, "'Pro' must open again")
    }

    @Тогда("^до кнопки «Оформить» сказано, что подписка продлевается автоматически$")
    fun theRenewalIsNamedBeforeTheButton() {
        assertWordsAreInTheCatalog(ProNotice.RENEW_AUTO)
        assertTrue(
            world.pro.state.disclosureRows.contains(ProNotice.RENEW_AUTO) ||
                world.pro.state.disclosure.expanded.contains(ProNotice.RENEW_AUTO),
            "the words about the automatic renewal must stand before the button",
        )
    }

    @Тогда("^сказано, когда и как спишут$")
    fun theChargeIsNamed() {
        assertWordsAreInTheCatalog(ProNotice.RENEW_CHARGE)
        assertTrue(
            world.pro.state.disclosure.expanded.contains(ProNotice.RENEW_CHARGE),
            "the disclosure must name the charge day and the store notice",
        )
    }

    @Тогда("^сказано, что отменить можно в подписках магазина$")
    fun theCancellationIsNamed() {
        assertWordsAreInTheCatalog(ProNotice.RENEW_CANCEL)
        assertTrue(
            world.pro.state.disclosure.expanded.contains(ProNotice.RENEW_CANCEL),
            "the disclosure must name the way to cancel",
        )
    }

    @Тогда("^рядом есть строка «Условия и политика»$")
    fun theTermsRowIsNearby() {
        assertEquals(ProNotice.TERMS_ROW, world.pro.state.termsRow, "the terms row must stand nearby")
        assertWordsAreInTheCatalog(ProNotice.TERMS_ROW)
    }

    // --- Проверки: лечебный сценарий чист ---------------------------------------------------

    @Тогда("^в ней нет слов про подписку, цену и «Про»$")
    fun theReminderCardIsFreeOfPro() {
        val intakeId = requireNotNull(shownIntakeId) { "the scenario must show the reminder card" }
        assertTrue(world.reminders.isNotificationShown(intakeId), "the reminder card must still be shown")
        assertMedicalSurfacesAreFreeOfPro()
    }

    @Тогда("^в подтверждении отметки нет слов про подписку, цену и «Про»$")
    fun theMarkConfirmationIsFreeOfPro() {
        assertEquals(IntakeStatus.TAKEN, world.intakes.current().status, "the mark must have worked")
        assertMedicalSurfacesAreFreeOfPro()
    }

    @Тогда("^в предупреждении об остатке нет слов про подписку, цену и «Про»$")
    fun theStockWarningIsFreeOfPro() {
        val warning = runBlocking {
            StockWarningForProfileUseCase(world.repositories.medicationStore, world.clock.time, world.log)(
                world.profiles.get(world.profiles.active).id,
            ).first()
        }
        assertNotNull(warning, "the stock warning must have appeared")
        assertMedicalSurfacesAreFreeOfPro()
    }

    @Тогда("^напоминание предъявлено$")
    fun theReminderIsShown() {
        val intake = focusedIntake()
        assertTrue(world.reminders.isNotificationShown(intake.id), "the reminder must be shown")
    }

    @Тогда("^отметка «Принято» работает$")
    fun theTakenMarkWorks() {
        // Сценарий не называет отметку отдельным действием: он утверждает, что после
        // истечения подписки учёт не тронут. Отметку делает сам шаг — иначе утверждение
        // проверяло бы состояние, которого в сценарии не было.
        val intake = focusedIntake()
        if (intake.isPending) world.markFromPhone(world.clock.nowLocal(), MarkResult.TAKEN)
        assertEquals(IntakeStatus.TAKEN, world.intakes.current().status, "the taken mark must work")
    }

    @Тогда("^шторка «Склянка Про» снова показывает витрину без упрёков$")
    fun theShowcaseShowsTheOfferAgain() {
        openShowcase()
        assertFalse(world.pro.state.management, "an expired subscription shows the offer again")
        assertEquals(SIX, world.pro.state.benefits.size, "the offer must name the composition")
        assertNull(world.pro.state.notice, "the offer must come without reproaches")
    }

    // --- Внутреннее ---------------------------------------------------------------------------

    private fun openShowcase() {
        runBlocking { world.pro.session.open(ProPoint.SETTINGS) }
    }

    /**
     * Приём лечебного сценария — тот самый, о котором говорят строки «в ней» и «в подтверждении».
     *
     * Приём берётся у лекарства и **берётся в фокус мира**: отметка, карточка и подтверждение
     * говорят об одном приёме, и без фокуса «приём» у каждого шага был бы свой.
     */
    private fun focusedIntake(): Intake {
        val medication = requireNotNull(medicalMedication) { "the scenario must bring a scheduled intake" }
        val time = requireNotNull(world.medications.get(medication).times.firstOrNull()) {
            "the medication must have a time"
        }
        val intake = world.intakes.at(medication, world.clock.today().atTime(time))
        world.intakes.focusOn(intake)
        return intake
    }

    private fun assertVisible() {
        assertTrue(world.pro.state.visible, "the showcase must be open")
    }

    /** Ключ причины обязан иметь непустую строку в каталоге: ключ без слов — не слова (П-7). */
    private fun assertWordsAreInTheCatalog(notice: ProNotice, expected: String? = null) {
        val key = catalogKey(notice)
        val value = CATALOG.getValue(key)
        assertTrue(value.isNotBlank(), "the catalog must carry words for $key")
        if (expected != null) {
            assertEquals(expected, value, "the word of $key must be the one the scenario names")
        }
    }

    /**
     * Ключ каталога по ключу домена.
     *
     * Сопоставление повторяет `ProTexts` из `core-ui` намеренно и только по именам: проверить
     * его иначе JVM-прогон не может, а расхождение имён — единственная ошибка, которую здесь
     * стоит ловить. Полноту перевода держит `LocalizationResourcesTest` (шесть каталогов).
     */
    private fun catalogKey(notice: ProNotice): String = requireNotNull(CATALOG_KEYS[notice]) {
        "the scenario checks no words of $notice"
    }

    /**
     * Страж лечебного сценария: слов про подписку в нём нет (ИИ-4, §9 монетизации).
     *
     * Проверяются три вещи, и ни одна из них не обещание. Витрина закрыта — значит её
     * не предъявляли. В исходниках лечебных поверхностей нет ни одного ключа `pro_*`
     * и ни одной ссылки на компонент или модель витрины — значит слову про подписку неоткуда
     * там взяться. Плюс сама поверхность лечебного шага на месте: карточка напоминания,
     * отметка или предупреждение об остатке существуют, и проверка не пустая.
     */
    private fun assertMedicalSurfacesAreFreeOfPro() {
        assertFalse(world.pro.state.visible, "the showcase must not be shown in the medical scenario")
        val offenders = MEDICAL_MODULES
            .flatMap { module -> sourcesOf(module) }
            .filter { file -> PRO_MARKERS.any { marker -> file.readText().contains(marker) } }
        assertTrue(
            offenders.isEmpty(),
            "the medical surfaces must carry no words or components of 'Pro': $offenders",
        )
    }

    private fun sourcesOf(module: String): List<File> = File(RepositoryLayout.root, "$module/src/main")
        .walkTopDown()
        .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
        .toList()

    private companion object {

        const val SIX = 6

        const val TWO = 2

        const val DEFAULT_MONTHLY_RUBLES = 149L

        const val DEFAULT_YEARLY_RUBLES = 990L

        /** Лекарство лечебного сценария: имя латиницей, потому что это данные, а не текст. */
        const val MEDICAL_MEDICATION = "magnesium"

        /** За сколько до приёма заводится курс: раньше него, иначе будильник не ставится (НФТ-35). */
        const val HOUR_BEFORE_THE_DOSE = 1L

        val STOCK_ENOUGH = BigDecimal("30")

        /** Модули лечебного сценария: напоминание, отметка, остаток, карточка приёма. */
        val MEDICAL_MODULES = listOf("feature-today", "feature-reminder", "feature-meds")

        /**
         * Признаки платного контура в исходниках.
         *
         * Ключ ресурса, тег, компонент и модель — узкие признаки именно витрины: слово «Pro»
         * само по себе встречается и в чужом контексте (акцентная тема знака), и широкий
         * поиск ловил бы не то.
         */
        val PRO_MARKERS = listOf("pro_", "ProMark", "ProSheet", "ProNotice", "ProShowcase", "TAG_PRO_")

        /** Русский каталог `core-ui`: слова, которые сценарий называет вслух. */
        val CATALOG: Map<String, String> by lazy { parseCatalog() }

        /**
         * Ключи слов, о которых сценарий говорит: домен → каталог.
         *
         * Данные, а не логика: список читается глазами рядом с таблицей §10 нормы дизайна,
         * и его полнота проверяется тем, что ключа нет — шаг падает с внятным сообщением,
         * а не проверяет пустую строку.
         */
        val CATALOG_KEYS: Map<ProNotice, String> = mapOf(
            ProNotice.STATE_INACTIVE to "pro_state_inactive",
            ProNotice.STATE_ACTIVE_UNTIL to "pro_state_active_until",
            ProNotice.YEAR_CHEAPER to "pro_year_cheaper",
            ProNotice.RENEW_AUTO to "pro_renew_auto",
            ProNotice.RENEW_CHARGE to "pro_renew_charge",
            ProNotice.RENEW_CANCEL to "pro_renew_cancel",
            ProNotice.TERMS_ROW to "pro_terms_row",
            ProNotice.STORE_UNAVAILABLE to "pro_store_unavailable",
            ProNotice.PURCHASE_FAILED_NETWORK to "pro_purchase_failed_network",
            ProNotice.PURCHASE_FAILED_UNAVAILABLE to "pro_purchase_failed_unavailable",
            ProNotice.PURCHASE_FAILED_DECLINED to "pro_purchase_failed_declined",
            ProNotice.PURCHASE_FAILED_PRODUCT to "pro_purchase_failed_product",
            ProNotice.PURCHASE_FAILED_STORE to "pro_purchase_failed_store",
            ProNotice.RESTORE_FAILED to "pro_restore_failed",
            ProNotice.RESTORE_NOTHING_FOUND to "pro_restore_nothing_found",
            ProNotice.PURCHASE_CONFIRMING to "pro_purchase_confirming",
            ProNotice.RESTORE_NOT_ACTIVE to "pro_restore_not_active",
            ProNotice.MANAGE_FAILED to "pro_manage_failed",
        )

        fun parseCatalog(): Map<String, String> {
            val file = File(RepositoryLayout.root, "core-ui/src/main/res/values/strings.xml")
            val text = file.readText()
            return Regex("""<string name="([^"]+)">([^<]*)</string>""")
                .findAll(text)
                .associate { match -> match.groupValues[1] to match.groupValues[2] }
        }

        fun periodId(period: String): ProProductId = when (period.lowercase()) {
            "месяц" -> ProProductId.MONTHLY
            "год" -> ProProductId.YEARLY
            else -> error("unknown period: $period")
        }
    }
}
