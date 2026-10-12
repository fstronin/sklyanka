package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.DeclaredExactAlarm
import ru.stohastika.sklyanka.acceptance.world.VendorFacts
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.VendorClassifier
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import ru.stohastika.sklyanka.domain.onboarding.VendorSteps
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckOutcome
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckRule
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import java.io.File

/**
 * Доменные шаги приёмки SKL-112: профиль прошивки, схема разрешений и предъявление баннером.
 *
 * Здесь живёт то, что не принадлежит ни одному экрану: классификатор — чистая функция домена,
 * состав цепочки решает `VendorSteps`, состояние права — ответ системы, а не объявление
 * манифеста, а предъявление баннером — решение журнала и учёта. Экранные утверждения
 * «что на карточке» проверяет стенд `feature-onboarding` своими шагами.
 *
 * Реестры приёмки (SKL-112, пункт 8 постановки) читают документы в репозитории: расхождение
 * прежних приёмок с новой нормой обязано валить прогон, а не жить зелёным.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class VendorProfileSteps(private val world: AcceptanceWorld) {

    // --- 1. Профиль прошивки: определение по фактам устройства ---------------------------------

    @Допустим(
        "^платформа сообщает производителя «([^»]+)», бренд «([^»]+)» " +
            "и наличие системного менеджера Huawei «([^»]+)»$",
    )
    fun thePlatformReports(manufacturer: String, brand: String, manager: String) {
        world.onboarding.vendorFacts = VendorFacts(
            manufacturer = manufacturer,
            brand = brand,
            huaweiManagerPresent = manager == "есть",
        )
    }

    @Когда("^приложение определяет профиль прошивки$")
    fun theAppClassifiesTheFirmware() {
        val facts = requireNotNull(world.onboarding.vendorFacts) { "the scenario must name the platform facts" }
        world.onboarding.detectedVendor = VendorClassifier.classify(
            manufacturer = facts.manufacturer,
            brand = facts.brand,
            systemManagerPresent = facts.huaweiManagerPresent,
        )
    }

    @Тогда("^профиль — (.+)$")
    fun theProfileIs(words: String) {
        assertEquals(vendorOf(words), world.onboarding.detectedVendor)
    }

    @И("^ни один вендорский шаг чужой прошивки ему не приписан$")
    fun noForeignVendorStepIsAttributed() {
        val profile = requireNotNull(world.onboarding.detectedVendor) { "the profile must be read first" }
        assertEquals(emptyList<PermissionStep>(), VendorSteps.of(profile))
    }

    // --- 2. Профиль и объявленное право как данность Предыстории --------------------------------

    @Допустим("^профиль прошивки телефона — (.+)$")
    fun thePhoneFirmwareIs(words: String) {
        world.onboarding.vendor = vendorOf(words)
    }

    @Допустим("^сборка приложения объявляет точные будильники как (.+)$")
    fun theBuildDeclaresExactAlarms(words: String) {
        world.onboarding.declaredExactAlarm = when (words) {
            "основное" -> DeclaredExactAlarm.PRIMARY
            "запасное" -> DeclaredExactAlarm.FALLBACK
            else -> throw IllegalArgumentException("unknown declared exact alarm right: «$words»")
        }
    }

    // --- 3. Предъявление баннером вместо карточки ----------------------------------------------

    @Когда("^(.+?) в (\\d{2}:\\d{2}) срабатывает напоминание о приёме и система предъявляет его баннером$")
    fun theReminderFiresAsABanner(date: String, time: String) {
        // Баннер — то же уведомление в шторке: система показывает его, не поднимая карточку
        // поверх блокировки, и для учёта это предъявление (НАП-2, ПЛ-17). Порт уведомлений
        // на стенде показывает уведомление при любом состоянии полноэкранного права —
        // ровно так же, как ведёт себя система на Android 14+ без права.
        val intake = world.intakes.currentOrScheduled()
        world.rememberState()
        world.reminders.reschedule(RescheduleReason.MANUAL)
        world.clock.moveTo(world.localMoment(date, time))
        world.reminders.deliverDue()
        assertTrue(
            world.reminders.isNotificationShown(intake.id),
            "the banner must reach the shade — otherwise there is nothing to account for",
        )
    }

    @Тогда("^напоминание считается предъявленным$")
    fun theReminderCountsAsPresented() {
        val shown = requireNotNull(runBlocking { world.reminders.journal.last(ReminderEvent.SHOWN) }) {
            "a presented reminder must be written down as shown"
        }
        assertEquals(true, shown.delivered, "the record must say the person saw it")
    }

    @И("^в журнале появляется запись предъявления с фактическим отклонением$")
    fun theJournalCarriesTheDeviation() {
        val shown = requireNotNull(runBlocking { world.reminders.journal.last(ReminderEvent.SHOWN) })
        assertNotNull(shown.deviationSeconds, "the show must carry its actual deviation (НАП-2)")
        assertNotNull(shown.firedAt, "the show must carry the moment it fired")
    }

    @И("^последнее предъявление в диагностике показано как состоявшееся$")
    fun theDiagnosticsNamesThePresentation() {
        val last = requireNotNull(runBlocking { diagnostics()() }.lastShown) {
            "diagnostics must name the last presentation"
        }
        assertEquals(true, last.delivered, "the last presentation must be marked as having happened")
        assertEquals(ReminderEvent.SHOWN, last.event, "a banner is a presentation, not a miss")
    }

    // --- 4. Самопроверка доставки, дошедшая баннером -------------------------------------------

    @Допустим("^самопроверка доставки запущена$")
    fun theDeliveryCheckIsRunning() {
        world.reminders.appForeground = false
        assertTrue(world.reminders.startDeliveryCheck() is DeliveryCheckState.Running, "the check must be running")
    }

    @Когда("^тестовое напоминание доходит баннером$")
    fun theTestReminderReachesAsABanner() {
        val dueAt = requireNotNull(world.reminders.deliveryCheckAlarm()) { "the check must be scheduled" }
        world.clock.moveTo(dueAt.atZone(world.clock.zone).toLocalDateTime())
        world.reminders.firedDeliveryCheck()
        assertTrue(
            world.reminders.isDeliveryCheckShown(),
            "the banner must reach the shade: without a presentation there is nothing to judge",
        )
    }

    @Тогда("^проверка названа пройденной$")
    fun theCheckIsNamedPassed() {
        val state = requireNotNull(deliveryCheckState()) { "the check must be finished" }
        assertEquals(
            DeliveryCheckOutcome.ON_TIME,
            state.outcome,
            "a banner presentation is an on-time result, not a failure (НАП-43, AC-24)",
        )
    }

    @И("^в блоке «Проверка доставки» сказано, что тестовое напоминание дошло$")
    fun theCheckBlockSaysItArrived() {
        // Блок рисует исход проверки: «дошло» — это исход «вовремя» или «с опозданием»,
        // и оба они читаются из одного правила (`DeliveryCheckRule`), а не из слов шага.
        val state = requireNotNull(deliveryCheckState()) { "the check must be finished" }
        assertTrue(
            state.outcome == DeliveryCheckOutcome.ON_TIME || state.outcome == DeliveryCheckOutcome.LATE,
            "the block must say the test reminder arrived, not that it failed",
        )
    }

    @Но("^провалом проверки это не называется$")
    fun theOutcomeIsNotAFailure() {
        val state = requireNotNull(deliveryCheckState()) { "the check must be finished" }
        assertFalse(
            state.outcome == DeliveryCheckOutcome.NOT_DELIVERED || state.outcome == DeliveryCheckOutcome.NOT_COMPLETED,
            "an honest presentation must not be named a failure",
        )
    }

    // --- 5. Реестры приёмки --------------------------------------------------------------------

    @Когда("^участник открывает приёмочные сценарии SKL-14, SKL-84 и этой задачи$")
    fun theParticipantOpensTheThreeFeatures() {
        documents = listOf("SKL-14", "SKL-84", "SKL-112").associateWith(::featureTextOf)
    }

    @Тогда("^состав и порядок карточек названы во всех трёх одинаково$")
    fun theCardCompositionMatchesEverywhere() {
        val huaweiChains = documents.mapValues { (task, text) -> cardChainsOf(text).firstOrNull() }
        val chains = huaweiChains.mapValues { (_, chain) -> requireNotNull(chain) { "no card order is named" } }
        val distinct = chains.values.distinct()
        assertEquals(1, distinct.size, "the three documents must name one and the same Huawei chain: $chains")

        // И каждая цепочка документов — часть состава, названного задачей: чужих карточек
        // в прежних приёмках быть не может.
        val namedInTask = cardChainsOf(documents.getValue("SKL-112")).flatten().toSet()
        documents.forEach { (task, text) ->
            cardChainsOf(text).flatten().forEach { card ->
                assertTrue(card in namedInTask, "$task names the card «$card», which SKL-112 does not know")
            }
        }
    }

    @И("^в SKL-84 нет утверждения, что у не-Huawei ровно два общих шага$")
    fun sk84DoesNotClaimTwoCommonSteps() {
        val text = documents.getValue("SKL-84")
        assertFalse(
            text.contains(NOT_HUAWEI_TWO_STEPS),
            "SKL-84 still says every non-Huawei phone gets exactly two common steps",
        )
        assertTrue(
            text.contains("профиль прошивки телефона"),
            "SKL-84 must name the firmware profile instead of «Huawei or not»",
        )
    }

    @И("^в SKL-14 нет утверждения, что право на точные будильники выдаётся системным запросом$")
    fun sk14DoesNotClaimADialog() {
        val text = documents.getValue("SKL-14")
        assertFalse(
            text.contains(EXACT_ALARM_DIALOG),
            "SKL-14 still says the exact alarm right is granted in a system dialog",
        )
        assertTrue(
            text.contains("системном экране особого доступа"),
            "SKL-14 must lead to the special access screen instead",
        )
    }

    @Когда("^участник смотрит на сценарии этой задачи и на экранные прогоны модулей$")
    fun theParticipantLooksAtTheScreenRuns() {
        screenRun = screenRunFile()
    }

    @Тогда("^сценарии о карточках и разрешениях помечены как исполняемые экранным прогоном$")
    fun theCardScenariosCarryTheScreenTag() {
        val screenScenarios = scenariosOf(featureTextOf("SKL-112")).filter { SCREEN_TAG in it.tags }
        assertTrue(
            screenScenarios.size >= SCREEN_SCENARIOS,
            "the card and permission scenarios must be marked as screen-executed: only ${screenScenarios.size} are",
        )
    }

    @И("^экранный прогон этой задачи назван в модуле, где живёт экран надёжности$")
    fun theRunIsNamedInTheOwnModule() {
        val run = screenRun
        assertTrue(run.contains("SKL-112"), "the reliability screen run must name the task")
        assertTrue(run.contains("ScreenAcceptanceRun"), "the run must execute the feature through the shared stand")
    }

    @И("^ни один из этих сценариев не исчезает из отчёта своей группы$")
    fun noneOfTheScenariosIsHidden() {
        val scenarios = scenariosOf(featureTextOf("SKL-112")).filter { SCREEN_TAG in it.tags }
        val postponed = scenarios.filter { "@postponed" in it.tags }.map { it.title }
        assertEquals(emptyList<String>(), postponed, "a screen scenario must not be postponed")

        // Прогон отбирает по тегам: ни один тег этих сценариев не имеет права быть
        // исключённым — иначе «выполняется» означало бы «не исполняется ни одним прогоном».
        val negations = Regex("""not\s+(@[\w-]+)""").findAll(screenRun).map { it.groupValues[1] }.toSet()
        val excluded = scenarios.flatMap { it.tags }.filter { it in negations }.distinct()
        assertEquals(emptyList<String>(), excluded, "the screen run excludes the tags of its own scenarios")
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private var documents: Map<String, String> = emptyMap()
    private var screenRun: String = ""

    private fun vendorOf(words: String): VendorProfile = when (words) {
        "Huawei" -> VendorProfile.EMUI
        "Xiaomi" -> VendorProfile.XIAOMI
        "Samsung" -> VendorProfile.SAMSUNG
        "общий Android" -> VendorProfile.GENERIC
        else -> throw IllegalArgumentException("unknown firmware profile: «$words»")
    }

    private fun deliveryCheckState(): DeliveryCheckState.Finished? =
        DeliveryCheckRule.stateOf(world.reminders.deliveryCheckRecords().lastOrNull()) as? DeliveryCheckState.Finished

    private fun diagnostics(): BuildReminderDiagnosticsUseCase = BuildReminderDiagnosticsUseCase(
        journal = world.reminders.journal,
        alarms = world.reminders.alarms,
        permissions = world.system,
        plan = world.reminders.planSource(),
        reliability = ReadReminderReliabilityUseCase(world.system),
        medications = world.repositories.medicationStore,
        measurement = world.reminders.measurement,
        acknowledgements = world.reminders.acknowledgements,
        time = world.clock.time,
    )

    private fun featureTextOf(task: String): String = File(RepositoryLayout.root, "$FEATURES/$task.feature").readText()

    private fun screenRunFile(): String = File(RepositoryLayout.root, SCREEN_RUN_DIRECTORY)
        .walkTopDown()
        .filter { it.isFile && it.name.endsWith(".kt") }
        .firstOrNull { it.readText().contains("SKL-112") && it.readText().contains("ScreenAcceptanceRun") }
        ?.readText()
        ?: error("no screen run of SKL-112 lives in $SCREEN_RUN_DIRECTORY")

    /** Документ как сценарии: теги, название и строки шагов. */
    private fun scenariosOf(text: String): List<Scenario> {
        val scenarios = mutableListOf<Scenario>()
        val tags = mutableListOf<String>()
        text.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("@") -> tags += line.split(WHITESPACE)
                line.startsWith("Сценарий:") || line.startsWith("Структура сценария:") ->
                    scenarios += Scenario(tags.toList(), line.substringAfter(":").trim(), mutableListOf())

                line.isEmpty() -> tags.clear()
                scenarios.isNotEmpty() -> scenarios.last().steps += line
            }
        }
        return scenarios
    }

    /** Все цепочки карточек документа: строки, где карточки названы по порядку. */
    private fun cardChainsOf(text: String): List<List<String>> =
        (stepChainsOf(text) + tableChainsOf(text)).filter { it.size > 1 }

    /**
     * Цепочки из строк шагов: «пять карточек в порядке: …» и «есть только карточки …».
     *
     * Названия берутся после названного места, а не из всей строки: иначе заголовок экрана
     * в той же строке («на экране «Настроить надёжность» есть только карточки…») сошёл бы
     * за карточку.
     */
    private fun stepChainsOf(text: String): List<List<String>> = text.lines()
        .flatMap { line ->
            CARD_MARKERS.mapNotNull { marker ->
                val tail = line.substringAfter(marker, missingDelimiterValue = "")
                if (tail.isEmpty()) null else cardNamesOf(tail)
            }
        }

    /** Цепочки из таблиц примеров: у структуры «карточки идут в порядке» они и есть данные. */
    private fun tableChainsOf(text: String): List<List<String>> = text.lines()
        .filter { it.trimStart().startsWith("|") }
        // Первая колонка — значение параметра (например, профиль), карточки — дальше.
        .map { row -> row.split("|").drop(2).joinToString("|") }
        .map { cardNamesOf(it) }
        .filter { it.size > 1 }

    /** Названия карточек из строки — в кавычках-ёлочках и в порядке появления. */
    private fun cardNamesOf(text: String): List<String> = CARD_NAME.findAll(text).map { it.groupValues[1] }.toList()

    private data class Scenario(val tags: List<String>, val title: String, val steps: MutableList<String>)

    private companion object {
        const val FEATURES = "acceptance/src/main/resources/features"
        const val SCREEN_RUN_DIRECTORY = "feature-onboarding/src/testDebug"
        const val SCREEN_TAG = "@screen"
        const val SCREEN_SCENARIOS = 7

        /** Прежняя формулировка SKL-84, ставшая ложной: «не-Huawei — ровно два общих шага». */
        const val NOT_HUAWEI_TWO_STEPS = "не Huawei, и системного менеджера Huawei на нём нет"

        /** Прежняя формулировка SKL-14, ставшая ложной: право выдаётся системным запросом. */
        const val EXACT_ALARM_DIALOG = "разрешение в системном запросе"

        val WHITESPACE = Regex("\\s+")
        val CARD_NAME = Regex("«([^»]+)»")

        /** Места в строках шагов, после которых названы карточки. */
        val CARD_MARKERS = listOf("в порядке:", "есть только карточки")
    }
}
