package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.steps.resultOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.stock.RefillStockUseCase
import ru.stohastika.sklyanka.domain.stock.StockForecast
import ru.stohastika.sklyanka.domain.stock.StockPanel
import ru.stohastika.sklyanka.domain.stock.StockWarning
import ru.stohastika.sklyanka.domain.stock.StockWarningForProfileUseCase
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Шаги приёмки SKL-12 — остаток упаковки и предупреждение о покупке.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса: шаг обязан
 * совпадать с документом дословно, иначе прогон перестаёт быть контрактом задачи. Правило
 * RussianTextInCode разрешает пакет ru.stohastika.sklyanka.acceptance вместе с подпакетами
 * именно поэтому (config/detekt/detekt.yml).
 *
 * **Общие шаги переиспользуются, а не объявляются заново.** «Остаток упаковки «Х» —
 * N таблеток», «приём … ожидается», «человек отмечает приём принятым» живут в общем
 * словаре `steps/shared` и работают поверх того же состояния сценария ([AcceptanceWorld]),
 * что и шаги этой задачи. Своего второго состояния у SKL-12 больше нет: остаток, который
 * списывает отметка, и остаток, который читает предупреждение, — одно и то же поле
 * одного лекарства.
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
class StockSteps(private val world: AcceptanceWorld) {

    /** Карточка лекарства, открытая последней: шаги «в нём …» говорят о ней. */
    private var openedCard: String = ""

    // --- Предыстория: порог, остаток, лекарства -------------------------------------------

    @Given("^порог предупреждения «([^»]+)» — (\\d+) дн\\S+$")
    fun theWarningThresholdOfThatMedication(medication: String, days: Int) {
        threshold(medication, days)
    }

    @Given("^предупреждение о покупке «([^»]+)» выключено$")
    fun theWarningOfThatMedicationIsOff(medication: String) {
        // Порог пуст — «напоминать о покупке не просили». Остаток при этом ведётся,
        // и блок остатка в карточке остаётся на месте.
        threshold(medication, null)
    }

    @Given("^остаток упаковки «([^»]+)» — (\\S+) таблет\\S+ при пороге (\\d+) дн\\S+$")
    fun theStockAndThresholdOfThatMedication(medication: String, units: String, days: Int) {
        stock(medication, RussianDates.amount(units))
        threshold(medication, days)
    }

    @Given(
        "^у «([^»]+)» остаток (\\d+) (?:таблет\\S+|капсул\\S*), и его хватит на " +
            "(\\d+) дн\\S+ при пороге (\\d+) дн\\S+$",
    )
    fun theStockThatLastsThoseDays(medication: String, units: Int, days: Int, thresholdDays: Int) {
        // Имя приходит в падеже сценария: «у «Магния B6» остаток…» — и приводится
        // к ключу Предыстории, иначе шаг завёл бы второе лекарство.
        val name = world.medicationNamed(medication)
        stock(name, BigDecimal(units))
        threshold(name, thresholdDays)
        // «Хватит на 4 дня» — следствие остатка, дозы и расписания, а не отдельное поле:
        // сценарий называет ожидание, шаг сверяет его с прогнозом домена.
        assertEquals(StockForecast.Days(days), forecast(name), "forecast of $name")
    }

    /**
     * «Остатка „Магния B6“ хватает на 4 дня при пороге 5 дней» — без числа остатка.
     *
     * Шаг **обеспечивает** факт, а не проверяет его: если текущего остатка на названное
     * число дней не хватает, он достраивает остаток до ровно этого прогноза (суточный
     * расход × дни). Предыстория карточки задаёт остаток «9 таблеток из 60» — при двух
     * приёмах в сутки это ровно 4 дня, и первый сценарий SKL-10 остаётся при своих числах.
     */
    @Given("^остатка «([^»]+)» хватает на (\\d+) дн\\S+ при пороге (\\d+) дн\\S+$")
    fun theStockLastsExactlyThoseDays(medication: String, days: Int, thresholdDays: Int) {
        val name = world.medicationNamed(medication)
        threshold(name, thresholdDays)
        if (forecast(name) != StockForecast.Days(days)) {
            val daily = requireNotNull(StockForecast.dailyUse(world.medications.get(name))) {
                "«$name» must have a known daily use for this step"
            }
            stock(name, daily * BigDecimal(days))
        }
        assertEquals(StockForecast.Days(days), forecast(name), "forecast of $name")
    }

    @Given("^размер упаковки «([^»]+)» — (\\d+) таблет\\S+$")
    fun thePackageSizeOfThatMedication(medication: String, units: Int) {
        val name = world.medicationNamed(medication)
        if (world.acted) {
            assertEquals(BigDecimal(units), world.medications.packageSizeOf(name), "package size of $name")
        } else {
            world.medications.update(name) { it.copy(packageSize = BigDecimal(units)) }
        }
    }

    @Given("^ни у одного активного лекарства порог остатка не сработал$")
    fun noThresholdOfTheActiveProfileHasFired() {
        assertNull(warningOnToday(), "the scenario needs no stock warning at all")
    }

    @Given("^у профиля «([^»]+)» есть лекарство «([^»]+)» с (\\d+) приём\\S* в сутки по (\\S+) таблетк\\S+$")
    fun theProfileHasAMedicationWithThatManyIntakes(profile: String, medication: String, perDay: Int, dose: String) {
        addScheduled(profile, medication, perDay, RussianDates.amount(dose))
    }

    @Given("^у профиля «([^»]+)» есть лекарство «([^»]+)» с числом приёмов в сутки (\\d+)$")
    fun theProfileHasAMedicationWithThatNumberOfIntakes(profile: String, medication: String, perDay: Int) {
        addScheduled(profile, medication, perDay, BigDecimal.ONE)
    }

    @Given("^у профиля «([^»]+)» нет других лекарств с учётом остатка$")
    fun theProfileHasNoOtherTrackedMedications(profile: String) {
        assertEquals(1, trackedOf(profile).size, "tracked medications of $profile")
    }

    @Given("^приёмы «([^»]+)» на (.+?) в (\\d{2}:\\d{2}) и (\\d{2}:\\d{2}) ожидаются$")
    fun bothIntakesOfThatDayArePending(medication: String, date: String, first: String, second: String) {
        listOf(first, second).forEach { time -> world.intakes.pending(medication, moment(date, time)) }
    }

    @Given("^человек пополнил остаток «([^»]+)» до (\\d+) таблет\\S+ (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonAlreadyRefilledThatMedication(medication: String, units: String, date: String, time: String) {
        refill(medication, BigDecimal(units), moment(date, time), asAction = false)
    }

    // --- Действия ----------------------------------------------------------------------------

    @When("^наступает (.+?), (\\d{2}:\\d{2}) и приём становится пропущенным без отметки$")
    fun thatIntakeBecomesMissedWithoutAMark(date: String, time: String) {
        world.rememberState()
        world.clock.moveTo(moment(date, time))
        world.sweep(SweepReason.TIMER)
        assertEquals(IntakeStatus.MISSED, world.intakes.current().status, "the intake had to expire by itself")
    }

    @When("^человек отмечает оба приёма принятыми (.+)$")
    fun thePersonMarksBothIntakesTaken(date: String) {
        world.rememberState()
        val day = date(date)
        world.intakes.of(world.medications.last)
            .filter { it.scheduledAt.toLocalDate() == day && it.isPending }
            .forEach { intake ->
                val at = intake.scheduledAt.plusMinutes(MINUTES_AFTER)
                world.clock.moveTo(at)
                world.mark(intake.id, MarkResult.TAKEN, IntakeSource.PHONE, world.clock.momentOf(at))
            }
    }

    @When("^в (\\d{2}:\\d{2}) тот же приём отмечают (принятым|пропущенным) ещё раз$")
    fun theSameIntakeIsMarkedOnceMoreAt(time: String, result: String) {
        markFromPhone(time, result)
    }

    @When("^человек открывает «Сегодня» (\\d.+)$")
    fun thePersonOpensToday(date: String) {
        openDay(date(date))
    }

    @When("^человек открывает «Сегодня» в профиле «([^»]+)» (.+)$")
    fun thePersonOpensTodayInThatProfile(profile: String, date: String) {
        world.profiles.use(profile)
        openDay(date(date))
    }

    @When("^человек открывает карточку лекарства «([^»]+)» (.+)$")
    fun thePersonOpensTheMedicationCard(medication: String, date: String) {
        openDay(date(date))
        openedCard = medication
    }

    @When("^человек переключается на профиль «([^»]+)»$")
    fun thePersonSwitchesToThatProfile(profile: String) {
        world.profiles.use(profile)
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек пополняет остаток «([^»]+)» до (\\d+) таблет\\S+$")
    fun thePersonRefillsThatMedication(date: String, time: String, medication: String, units: String) {
        refill(medication, BigDecimal(units), moment(date, time))
    }

    // --- Проверки: прогноз запаса ----------------------------------------------------------------

    @Then("^число дней, на которые хватит запаса «([^»]+)», — (\\d+)$")
    fun theNumberOfDaysTheStockLastsIs(medication: String, expected: Int) {
        assertEquals(StockForecast.Days(expected), forecast(medication), "forecast of $medication")
    }

    @Then("^предупреждение о покупке «([^»]+)» (показано|не показано)$")
    fun theWarningOfThatMedicationIsShownOrNot(medication: String, shown: String) {
        val warning = warningOf(medication)
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
        val warning = requireNotNull(warningOnToday()) { "no warning on Today at all" }
        assertEquals(world.medications.get(medication).id, warning.medicationId, "the plate names another medication")
        assertEquals(BigDecimal(units), warning.unitsLeft, "units left")
        assertEquals(days.toInt(), warning.daysLeft, "days left")
        // Числа приходят из домена, склонения — из ресурсов (НФТ-44). Здесь проверяется
        // согласие строки макета с числом: «4 дня», но «5 дней».
        assertPlural(units.toInt(), unitWord, TABLET_FORMS)
        assertPlural(days.toInt(), dayWord, DAY_FORMS)
    }

    @Then("^человек видит предупреждение о покупке «([^»]+)»$")
    fun thePersonSeesTheWarningOfThatMedication(medication: String) {
        val warning = warningOnToday()
        assertEquals(world.medications.get(medication).id, warning?.medicationId, "the plate shows $warning")
    }

    @Then("^рядом с предупреждением есть действие «Купить»$")
    fun theWarningOffersToBuy() {
        // Подпись кнопки живёт в ресурсах, переход — в SKL-08. На этом уровне проверяемо
        // одно: плашка есть и знает, о каком лекарстве открывать карточку.
        val warning = requireNotNull(warningOnToday()) { "no warning to act upon" }
        assertTrue(warning.medicationId.isNotEmpty(), "the action needs a medication to open")
    }

    @Then("^предупреждение сопровождается текстом, а не только цветом$")
    fun theWarningCarriesTextAndNotOnlyColour() {
        // НФТ-33: статус не передаётся одним цветом. Доменная сторона этого требования —
        // предупреждение несёт числа, из которых собирается фраза, а не один флаг.
        val warning = requireNotNull(warningOnToday()) { "no warning at all" }
        assertTrue(warning.unitsLeft >= BigDecimal.ZERO, "units left")
        assertTrue(warning.daysLeft >= 0, "days left")
        assertTrue(warning.medicationName.isNotEmpty(), "the plate names the medication")
    }

    @Then("^человек видит ровно одно предупреждение о покупке$")
    fun thePersonSeesExactlyOneWarning() {
        assertTrue(firedOf(world.profiles.active).size > 1, "the scenario has to fire more than one threshold")
        assertEquals(1, plateOnToday().size, "the plate shows one warning, the most urgent one")
    }

    @Then("^это предупреждение о «([^»]+)»$")
    fun thatWarningIsAboutThatMedication(medication: String) {
        assertEquals(world.medications.get(medication).id, plateOnToday().single().medicationId)
    }

    @Then("^предупреждения о покупке человек не видит$")
    fun thePersonSeesNoWarningAtAll() {
        assertNull(warningOnToday(), "no threshold was supposed to fire")
    }

    @Then("^предупреждения о покупке «([^»]+)» человек (?:больше )?не видит$")
    fun thePersonSeesNoWarningOfThatMedication(medication: String) {
        assertNull(warningOf(medication), "unexpected warning for $medication")
        assertTrue(
            plateOnToday().none { it.medicationId == world.medications.get(medication).id },
            "the plate still shows $medication",
        )
    }

    @Then("^предупреждения о покупке «([^»]+)» на «Сегодня» человек не видит$")
    fun thePersonSeesNoWarningOfThatMedicationOnToday(medication: String) {
        assertTrue(
            plateOnToday().none { it.medicationId == world.medications.get(medication).id },
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
        assertTrue(panel(medication) is StockPanel.Visible, "the stock block of $medication is hidden")
    }

    // --- Проверки: доля от размера упаковки (SKL-34, ОВ-45) --------------------------------------

    @Then("^доля остатка «([^»]+)» от упаковки — (\\d+) %$")
    fun thePackageShareOfThatMedication(medication: String, percent: Int) {
        val visible = panel(medication) as StockPanel.Visible
        val share = requireNotNull(visible.packageFraction) { "no package share for $medication" }
        assertEquals(percent, (share * PERCENT).roundToInt(), "share of $medication")
    }

    @Then("^доли остатка «([^»]+)» нет$")
    fun thePackageShareOfThatMedicationIsAbsent(medication: String) {
        val visible = panel(medication) as StockPanel.Visible
        // Доли нет, когда размер не задан или остаток больше упаковки: полоса не рисуется,
        // а карточка показывает прежнее «осталось N».
        assertNull(visible.packageFraction, "unexpected share of $medication")
    }

    // --- Предыстория и действия задачи поверх общего мира ----------------------------------------

    /**
     * Порог предупреждения о покупке.
     *
     * Лекарство заводится по первому упоминанию, если сценарий не завёл его общим шагом:
     * шаги Предыстории разных задач стоят вперемежку, и порядок между ними не гарантирован.
     */
    private fun threshold(name: String, days: Int?) {
        ensureKnown(name)
        world.medications.update(name) { it.copy(stockThresholdDays = days) }
    }

    private fun stock(name: String, units: BigDecimal) {
        ensureKnown(name)
        world.medications.update(name) { it.copy(stockLeft = units) }
    }

    /**
     * Лекарство по расписанию, заданное числом приёмов в сутки.
     *
     * Сценарий говорит «два приёма в сутки», а не «в 08:00 и 20:00»: важно их число,
     * потому что суточный расход считается именно по нему. Времена раскладываются
     * с утра с равным шагом, лишь бы их было ровно столько.
     */
    private fun addScheduled(profileName: String, name: String, perDay: Int, dose: BigDecimal) {
        world.medications.add(
            profile = world.profiles,
            profileName = profileName,
            name = name,
            times = (0 until perDay).map { MORNING.plusHours(HOURS_BETWEEN * it) },
        )
        world.medications.update(name) { it.copy(doseAmount = dose) }
    }

    private fun openDay(date: LocalDate) {
        world.rememberState()
        world.clock.moveToDate(date)
    }

    /**
     * Пополнение остатка человеком.
     *
     * Снимок приёмов берётся до действия: «пополнение не меняет историю приёмов» —
     * это утверждение о том, чего действие не сделало, и проверить его можно только
     * сравнением с состоянием до.
     */
    private fun refill(name: String, units: BigDecimal, at: LocalDateTime, asAction: Boolean = true) {
        if (asAction) world.rememberState()
        world.clock.moveTo(at)
        val useCase = RefillStockUseCase(world.repositories.medicationStore, world.log)
        runBlocking { useCase(world.medications.get(name).id, units) }
    }

    private fun forecast(name: String): StockForecast = StockForecast.of(world.medications.get(name))

    private fun panel(name: String): StockPanel = StockPanel.of(world.medications.get(name))

    /** Предупреждение конкретного лекарства — без учёта того, чьё оно самое срочное. */
    private fun warningOf(name: String): StockWarning? = StockWarning.of(world.medications.get(name))

    /** Все сработавшие пороги профиля — плашка показывает из них ровно одно. */
    private fun firedOf(profileName: String): List<StockWarning> =
        trackedOf(profileName).mapNotNull { StockWarning.of(it) }

    /** То единственное предупреждение, которое видно на «Сегодня» у активного профиля. */
    private fun warningOnToday(): StockWarning? {
        val useCase = StockWarningForProfileUseCase(
            world.repositories.medicationStore,
            world.clock.time,
            world.log,
        )
        return runBlocking { useCase(world.profiles.get(world.profiles.active).id).first() }
    }

    /** Плашка показывает одно предупреждение или ни одного — списка на экране не бывает. */
    private fun plateOnToday(): List<StockWarning> = listOfNotNull(warningOnToday())

    /**
     * Лекарства профиля, у которых остаток действительно ведётся.
     *
     * Список берётся из того же хранилища, что видит домен, а не из имён, упомянутых
     * в шагах: «других лекарств с учётом остатка нет» — утверждение обо всём профиле,
     * и проверять его по списку упоминаний значит проверять сам сценарий.
     */
    private fun trackedOf(profileName: String): List<Medication> {
        val profileId = world.profiles.get(profileName).id
        return runBlocking { world.repositories.medicationStore.observeActive(profileId).first() }
            .filter { it.stockLeft != null }
    }

    private fun ensureKnown(name: String) {
        if (!world.medications.has(name)) {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = name,
                times = listOf(MORNING),
            )
        }
    }

    // --- Вспомогательное ------------------------------------------------------------------------

    private fun markFromPhone(time: String, result: String) {
        world.markFromPhone(world.clock.today().atTime(LocalTime.parse(time)), resultOf(result))
    }

    private fun visiblePanel(): StockPanel.Visible {
        val name = openedCard.ifEmpty { world.medications.last }
        val panel = panel(name)
        assertTrue(panel is StockPanel.Visible, "the stock block of $name is hidden")
        return panel as StockPanel.Visible
    }

    private fun date(text: String): LocalDate = RussianDates.date(text, world.clock.today())

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
        const val MINUTES_AFTER = 5L

        /** Доля полосы: 9/60 = 15 %. */
        const val PERCENT = 100f

        val MORNING: LocalTime = LocalTime.of(8, 0)

        /** Шаг между приёмами суток: сценарию важно их число, а не расстановка. */
        const val HOURS_BETWEEN = 4L
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
