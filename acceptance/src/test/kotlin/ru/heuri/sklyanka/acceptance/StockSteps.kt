package ru.heuri.sklyanka.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.domain.intake.MarkResult
import ru.heuri.sklyanka.domain.intake.SweepReason
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.stock.StockForecast
import ru.heuri.sklyanka.domain.stock.StockPanel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Шаги приёмки SKL-12 — остаток упаковки и предупреждение о покупке.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса: шаг обязан
 * совпадать с документом дословно, иначе прогон перестаёт быть контрактом задачи. Правило
 * RussianTextInCode для пакета ru.heuri.sklyanka.acceptance отключено именно поэтому
 * (config/detekt/detekt.yml).
 *
 * **Шаги SKL-07 переиспользуются, а не объявляются заново.** «Остаток упаковки «X» —
 * N таблеток», «приём … ожидается», «человек отмечает приём принятым» принадлежат
 * приёмке SKL-07 и работают поверх того же состояния (см. [AcceptanceWorlds]): второе
 * определение той же строки Cucumber считает неоднозначным и ломает прогон целиком.
 *
 * **Чего эти шаги не проверяют.** Строк интерфейса в чистом JVM-модуле нет: тексты
 * плашки и блока живут в ресурсах `core-ui` и проверяются вёрсткой SKL-08 и SKL-10.
 * Здесь проверяются числа, которые экран подставит, и признаки, по которым он решает,
 * показывать блок и предупреждение или нет. Согласованность самой строки макета
 * с числом (склонение «4 дня» против «5 дней») проверяется отдельно — см. [assertPlural].
 *
 * Выражения шагов намеренно узкие, а имена в кавычках читаются как `[^»]+`: прогон общий
 * на все задачи процесса, и шаг, поймавший чужую строку, превращает чужой
 * нереализованный сценарий в упавший.
 */
@Suppress("TooManyFunctions", "LargeClass")
class StockSteps {

    private val world = AcceptanceWorlds.stock()
    private val intakes = AcceptanceWorlds.lifecycle()

    // --- Предыстория: порог, остаток, лекарства -------------------------------------------

    @Given("^порог предупреждения «([^»]+)» — (\\d+) дн\\S+$")
    fun theWarningThresholdOfThatMedication(medication: String, days: Int) {
        world.threshold(medication, days)
    }

    @Given("^предупреждение о покупке «([^»]+)» выключено$")
    fun theWarningOfThatMedicationIsOff(medication: String) {
        // Порог пуст — «напоминать о покупке не просили». Остаток при этом ведётся,
        // и блок остатка в карточке остаётся на месте.
        world.threshold(medication, null)
    }

    @Given("^остаток упаковки «([^»]+)» — (\\S+) таблет\\S+ при пороге (\\d+) дн\\S+$")
    fun theStockAndThresholdOfThatMedication(medication: String, units: String, days: Int) {
        world.stock(medication, RussianDates.amount(units))
        world.threshold(medication, days)
    }

    @Given("^у профиля «([^»]+)» есть лекарство «([^»]+)» с (\\d+) приём\\S* в сутки по (\\S+) таблетк\\S+$")
    fun theProfileHasAMedicationWithThatManyIntakes(profile: String, medication: String, perDay: Int, dose: String) {
        world.addScheduled(profile, medication, perDay, RussianDates.amount(dose))
    }

    @Given("^у профиля «([^»]+)» есть лекарство «([^»]+)» с числом приёмов в сутки (\\d+)$")
    fun theProfileHasAMedicationWithThatNumberOfIntakes(profile: String, medication: String, perDay: Int) {
        world.addScheduled(profile, medication, perDay, BigDecimal.ONE)
    }

    @Given(
        "^у профиля «([^»]+)» есть лекарство «([^»]+)» по потребности не чаще (\\d+) раз в сутки " +
            "по (\\S+) таблетк\\S+$",
    )
    fun theProfileHasAnOnDemandMedicationWithADose(profile: String, medication: String, limit: Int, dose: String) {
        world.addOnDemand(profile, medication, limit, RussianDates.amount(dose))
    }

    @Given("^у профиля «([^»]+)» есть лекарство «([^»]+)» с остатком (\\S+) таблет\\S+ при пороге (\\d+) дн\\S+$")
    fun theProfileHasAMedicationWithThatStock(profile: String, medication: String, units: String, days: Int) {
        // Расписание сценарий не называет: речь о принадлежности профилю, а не о курсе.
        // Один приём в сутки — минимальное расписание, при котором прогноз вообще существует.
        world.addScheduled(profile, medication, 1, BigDecimal.ONE)
        world.stock(medication, RussianDates.amount(units))
        world.threshold(medication, days)
    }

    @Given("^у профиля «([^»]+)» нет других лекарств с учётом остатка$")
    fun theProfileHasNoOtherTrackedMedications(profile: String) {
        assertEquals(1, world.trackedOf(profile).size, "tracked medications of $profile")
    }

    @Given("^приёмы «([^»]+)» на (.+?) в (\\d{2}:\\d{2}) и (\\d{2}:\\d{2}) ожидаются$")
    fun bothIntakesOfThatDayArePending(medication: String, date: String, first: String, second: String) {
        listOf(first, second).forEach { time -> intakes.pending(medication, moment(date, time)) }
    }

    @Given("^человек пополнил остаток «([^»]+)» до (\\d+) таблет\\S+ (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonAlreadyRefilledThatMedication(medication: String, units: String, date: String, time: String) {
        world.refill(medication, BigDecimal(units), moment(date, time), asAction = false)
    }

    // --- Действия ----------------------------------------------------------------------------

    @When("^наступает (.+?), (\\d{2}:\\d{2}) и приём становится пропущенным без отметки$")
    fun thatIntakeBecomesMissedWithoutAMark(date: String, time: String) {
        intakes.rememberState()
        intakes.moveTo(moment(date, time))
        intakes.sweep(SweepReason.TIMER)
        assertEquals(IntakeStatus.MISSED, intakes.current().status, "the intake had to expire by itself")
    }

    @When("^человек отмечает оба приёма принятыми (.+)$")
    fun thePersonMarksBothIntakesTaken(date: String) {
        intakes.rememberState()
        val day = date(date)
        intakes.intakesOf(intakes.lastMedication)
            .filter { it.scheduledAt.toLocalDate() == day && it.isPending }
            .forEach { intake ->
                val at = intake.scheduledAt.plusMinutes(MINUTES_AFTER)
                intakes.moveTo(at)
                intakes.mark(intake.id, MarkResult.TAKEN, IntakeSource.PHONE, intakes.momentOf(at))
            }
    }

    @When("^в (\\d{2}:\\d{2}) человек отмечает приём (принятым|пропущенным)$")
    fun thePersonMarksTheIntakeAt(time: String, result: String) {
        markFromPhone(time, result)
    }

    @When("^в (\\d{2}:\\d{2}) тот же приём отмечают (принятым|пропущенным) ещё раз$")
    fun theSameIntakeIsMarkedOnceMoreAt(time: String, result: String) {
        markFromPhone(time, result)
    }

    @When("^человек открывает «Сегодня» (\\d.+)$")
    fun thePersonOpensToday(date: String) {
        world.openDay(date(date))
    }

    @When("^человек открывает «Сегодня» в профиле «([^»]+)» (.+)$")
    fun thePersonOpensTodayInThatProfile(profile: String, date: String) {
        world.switchTo(profile)
        world.openDay(date(date))
    }

    @When("^человек открывает карточку лекарства «([^»]+)» (.+)$")
    fun thePersonOpensTheMedicationCard(medication: String, date: String) {
        world.openDay(date(date))
        world.openedCard = medication
    }

    @When("^человек переключается на профиль «([^»]+)»$")
    fun thePersonSwitchesToThatProfile(profile: String) {
        world.switchTo(profile)
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек пополняет остаток «([^»]+)» до (\\d+) таблет\\S+$")
    fun thePersonRefillsThatMedication(date: String, time: String, medication: String, units: String) {
        world.refill(medication, BigDecimal(units), moment(date, time))
    }

    // --- Проверки: прогноз запаса ----------------------------------------------------------------

    @Then("^число дней, на которые хватит запаса «([^»]+)», — (\\d+)$")
    fun theNumberOfDaysTheStockLastsIs(medication: String, expected: Int) {
        assertEquals(StockForecast.Days(expected), world.forecast(medication), "forecast of $medication")
    }

    @Then("^предупреждение о покупке «([^»]+)» (показано|не показано)$")
    fun theWarningOfThatMedicationIsShownOrNot(medication: String, shown: String) {
        val warning = world.warningOf(medication)
        if (shown == SHOWN) {
            assertNotNull(warning, "expected a warning for $medication")
        } else {
            assertNull(warning, "expected no warning for $medication")
        }
    }

    // --- Проверки: плашка на «Сегодня» --------------------------------------------------------

    @Then("^человек видит предупреждение «([^»]+) — осталось (\\d+) (\\S+), хватит на (\\d+) (\\S+)»$")
    fun thePersonSeesThatWarningText(
        medication: String,
        units: String,
        unitWord: String,
        days: String,
        dayWord: String,
    ) {
        val warning = requireNotNull(world.warningOnToday()) { "no warning on Today at all" }
        assertEquals(world.medication(medication).id, warning.medicationId, "the plate names another medication")
        assertEquals(BigDecimal(units), warning.unitsLeft, "units left")
        assertEquals(days.toInt(), warning.daysLeft, "days left")
        // Числа приходят из домена, склонения — из ресурсов (НФТ-44). Здесь проверяется
        // согласие строки макета с числом: «4 дня», но «5 дней».
        assertPlural(units.toInt(), unitWord, TABLET_FORMS)
        assertPlural(days.toInt(), dayWord, DAY_FORMS)
    }

    @Then("^человек видит предупреждение о покупке «([^»]+)»$")
    fun thePersonSeesTheWarningOfThatMedication(medication: String) {
        val warning = world.warningOnToday()
        assertEquals(world.medication(medication).id, warning?.medicationId, "the plate shows $warning")
    }

    @Then("^рядом с предупреждением есть действие «Купить»$")
    fun theWarningOffersToBuy() {
        // Подпись кнопки живёт в ресурсах, переход — в SKL-08. На этом уровне проверяемо
        // одно: плашка есть и знает, о каком лекарстве открывать карточку.
        val warning = requireNotNull(world.warningOnToday()) { "no warning to act upon" }
        assertTrue(warning.medicationId.isNotEmpty(), "the action needs a medication to open")
    }

    @Then("^предупреждение сопровождается текстом, а не только цветом$")
    fun theWarningCarriesTextAndNotOnlyColour() {
        // НФТ-33: статус не передаётся одним цветом. Доменная сторона этого требования —
        // предупреждение несёт числа, из которых собирается фраза, а не один флаг.
        val warning = requireNotNull(world.warningOnToday()) { "no warning at all" }
        assertTrue(warning.unitsLeft >= BigDecimal.ZERO, "units left")
        assertTrue(warning.daysLeft >= 0, "days left")
        assertTrue(warning.medicationName.isNotEmpty(), "the plate names the medication")
    }

    @Then("^человек видит ровно одно предупреждение о покупке$")
    fun thePersonSeesExactlyOneWarning() {
        assertTrue(world.firedOf(world.activeProfile).size > 1, "the scenario has to fire more than one threshold")
        assertEquals(1, world.plateOnToday().size, "the plate shows one warning, the most urgent one")
    }

    @Then("^это предупреждение о «([^»]+)»$")
    fun thatWarningIsAboutThatMedication(medication: String) {
        assertEquals(world.medication(medication).id, world.plateOnToday().single().medicationId)
    }

    @Then("^предупреждения о покупке человек не видит$")
    fun thePersonSeesNoWarningAtAll() {
        assertNull(world.warningOnToday(), "no threshold was supposed to fire")
    }

    @Then("^предупреждения о покупке «([^»]+)» человек (?:больше )?не видит$")
    fun thePersonSeesNoWarningOfThatMedication(medication: String) {
        assertNull(world.warningOf(medication), "unexpected warning for $medication")
        assertTrue(
            world.plateOnToday().none { it.medicationId == world.medication(medication).id },
            "the plate still shows $medication",
        )
    }

    @Then("^предупреждения о покупке «([^»]+)» на «Сегодня» человек не видит$")
    fun thePersonSeesNoWarningOfThatMedicationOnToday(medication: String) {
        assertTrue(
            world.plateOnToday().none { it.medicationId == world.medication(medication).id },
            "the plate still shows $medication",
        )
    }

    // --- Проверки: блок остатка в карточке лекарства -------------------------------------------

    @Then("^человек видит раздел «Остаток»$")
    fun thePersonSeesTheStockBlock() {
        // Блок есть тогда и только тогда, когда остаток ведётся; проверка живёт
        // в visiblePanel и роняет шаг, если блок скрыт.
        assertTrue(visiblePanel().unitsLeft >= BigDecimal.ZERO, "the stock block is hidden")
    }

    @Then("^человек видит раздел «Остаток» со значением «(\\d+) таблет\\S+»$")
    fun thePersonSeesTheStockBlockWithThatValue(units: String) {
        assertEquals(BigDecimal(units), visiblePanel().unitsLeft, "value of the stock block")
    }

    @Then("^человек видит остаток «(\\d+) таблет\\S+»$")
    fun thePersonSeesThatStock(units: String) {
        assertEquals(BigDecimal(units), visiblePanel().unitsLeft, "value of the stock block")
    }

    @Then("^в нём подпись «хватит на (\\d+) дн\\S+»$")
    fun theBlockCarriesThatCaption(days: String) {
        assertEquals(days.toInt(), visiblePanel().captionDays, "caption of the stock block")
    }

    @Then("^в нём значение «(\\d+) таблет\\S+»$")
    fun theBlockShowsThatValue(units: String) {
        // ОВ-45: макет показывает знаменатель «9 таблеток из 60», поля размера упаковки
        // в модели нет, и приёмка закрепляет числитель — «9 таблеток».
        assertEquals(BigDecimal(units), visiblePanel().unitsLeft, "value of the stock block")
    }

    @Then("^в нём действие «Пополнить»$")
    fun theBlockOffersToRefill() {
        // Кнопка живёт в вёрстке SKL-10, её подпись — в ресурсах. Проверяемо здесь одно:
        // блок остатка показан, а значит, действию есть где быть и над чем работать.
        assertTrue(visiblePanel().unitsLeft >= BigDecimal.ZERO, "a refill needs a stock to replace")
    }

    @Then("^подписи «хватит на N дней» в разделе нет$")
    fun theBlockCarriesNoCaption() {
        assertNull(visiblePanel().captionDays, "the caption belongs to a fired threshold only")
    }

    @Then("^подписи «хватит на N дней» человек не видит$")
    fun thePersonSeesNoCaptionAtAll() {
        // ОВ-39: суточный расход приёма по потребности неизвестен, число дней не обещается.
        assertNull(visiblePanel().daysLeft, "an on-demand medication must not promise days")
        assertNull(visiblePanel().captionDays)
    }

    @Then("^остаток упаковки «([^»]+)» виден в карточке лекарства$")
    fun theStockOfThatMedicationIsVisibleInTheCard(medication: String) {
        assertTrue(world.panel(medication) is StockPanel.Visible, "the stock block of $medication is hidden")
    }

    // --- Вспомогательное ------------------------------------------------------------------------

    private fun markFromPhone(time: String, result: String) {
        intakes.rememberState()
        val at = intakes.today().atTime(LocalTime.parse(time))
        val outcome = if (result.startsWith(TAKEN_WORD)) MarkResult.TAKEN else MarkResult.SKIPPED
        intakes.markCurrent(outcome, IntakeSource.PHONE, at)
    }

    private fun visiblePanel(): StockPanel.Visible {
        val name = world.openedCard.ifEmpty { intakes.lastMedication }
        val panel = world.panel(name)
        assertTrue(panel is StockPanel.Visible, "the stock block of $name is hidden")
        return panel as StockPanel.Visible
    }

    private fun date(text: String): LocalDate = RussianDates.date(text, intakes.today())

    private fun moment(date: String, time: String): LocalDateTime = date(date).atTime(LocalTime.parse(time))

    /**
     * Согласие слова с числом по правилам русского языка.
     *
     * Проверяется строка сценария, а не код: склонения задаются `plurals` в ресурсах
     * (НФТ-44) и проверяются вёрсткой. Здесь важно, чтобы сам документ приёмки не
     * закреплял «4 дней».
     */
    private fun assertPlural(value: Int, word: String, forms: Triple<String, String, String>) {
        val hundreds = value % HUNDRED
        val tens = value % TEN
        val expected = when {
            hundreds in TEEN_FROM..TEEN_TO -> forms.third
            tens == 1 -> forms.first
            tens in FEW_FROM..FEW_TO -> forms.second
            else -> forms.third
        }
        assertEquals(expected, word, "plural form for $value")
    }

    private companion object {
        const val SHOWN = "показано"
        const val TAKEN_WORD = "принят"
        const val MINUTES_AFTER = 5L
        const val HUNDRED = 100
        const val TEN = 10
        const val TEEN_FROM = 11
        const val TEEN_TO = 14
        const val FEW_FROM = 2
        const val FEW_TO = 4

        val TABLET_FORMS = Triple("таблетка", "таблетки", "таблеток")
        val DAY_FORMS = Triple("день", "дня", "дней")
    }
}
