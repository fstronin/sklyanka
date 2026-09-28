package ru.heuri.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.RussianDates
import ru.heuri.sklyanka.acceptance.steps.localMoment
import ru.heuri.sklyanka.acceptance.steps.medicationNamed
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.history.AdherenceCalculator
import ru.heuri.sklyanka.domain.history.AdherencePeriod
import ru.heuri.sklyanka.domain.history.HistorySlice
import ru.heuri.sklyanka.domain.history.HistoryWindow
import ru.heuri.sklyanka.domain.intake.MarkResult
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import java.time.LocalTime
import java.time.YearMonth

/**
 * Шаги SKL-15: разделение профилей — негативная группа `@isolation` (`ПРФ-2`, `ПРФ-4`,
 * `ПРФ-24`).
 *
 * Группа вопросов доменная, и живёт она в общем артефакте, а не в модуле экрана
 * (`SKL-26/TDD.md §8.1`): «приёмы двух профилей не показываются вместе» — это утверждение
 * о выборках, а не о разметке. Поэтому и «открыть экран» здесь значит то же, что
 * в доменных шагах SKL-12: прочитать ровно те данные, которые экран показал бы, —
 * запросами репозитория (`ПРФ-4`: разделение на уровне запроса, а не фильтра на экране).
 *
 * Копии правил в шагах нет. Приёмы суток порождает настоящий планировщик ([AcceptanceWorld.plan]),
 * список лекарств читается `MedicationRepository`, показатели считает настоящий
 * [AdherenceCalculator] — тот же, что на экране истории.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class ProfileIsolationSteps(private val world: AcceptanceWorld) {

    /** Приёмы суток, которые показал бы экран «Сегодня». */
    private var openedDay: List<Intake> = emptyList()

    /** Лекарства, которые показал бы список лекарств. */
    private var openedList: List<Medication> = emptyList()

    /** Месяц, за который сценарий смотрит показатели. */
    private var countedMonth: YearMonth? = null

    // --- Действия --------------------------------------------------------------

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек открывает экран «Сегодня»$")
    fun thePersonOpensToday(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        openToday()
    }

    @Когда("^открывает экран «Сегодня»$")
    fun thatPersonOpensToday() {
        openToday()
    }

    @Когда("^человек открывает список лекарств (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonOpensTheMedicationList(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        openedList = runBlocking {
            world.repositories.medicationStore.observeActive(activeProfileId).first()
        }
    }

    @Когда("^активен профиль «([^»]+)» и человек смотрит показатели за (.+)$")
    fun thePersonLooksAtTheAdherenceOfThatProfile(profile: String, month: String) {
        world.profiles.use(profile)
        countedMonth = RussianDates.month(month, world.clock.today())
    }

    // --- Предыстория: пропуски за месяц ----------------------------------------

    /**
     * Пропуски как данность Предыстории.
     *
     * Каждый пропуск проводится настоящей отметкой ([AcceptanceWorld.markExisting]):
     * сценарий, начинающийся с двух пропусков, обязан начинаться с того же состояния,
     * которое даёт отметка. Момент сценария сохраняется: пропуски были в прошлом,
     * а сценарий продолжается в своём «сейчас».
     */
    @Допустим("^у профиля «([^»]+)» за (.+?) (\\S+) пропуск\\S*$")
    fun theProfileHasThatManyMisses(profile: String, month: String, count: String) {
        val at = world.clock.nowLocal()
        val window = RussianDates.month(month, world.clock.today())
        val medications = world.medications.all()
            .filter { it.profileId == world.profiles.get(profile).id && it.times.isNotEmpty() }
        assertTrue(medications.isNotEmpty(), "«$profile» has no scheduled medication to miss")
        val slots = generateSequence(window.atDay(1)) { it.plusDays(1) }
            .flatMap { date -> medications.asSequence().map { medication -> date to medication } }
            .take(RussianDates.count(count))
            .toList()
        slots.forEach { (date, medication) ->
            val time = medication.times.first()
            world.markExisting(nameOf(medication.id), date.atTime(time), time, MarkResult.SKIPPED)
        }
        world.clock.moveTo(at)
    }

    // --- Проверки: состав суток и списка ---------------------------------------

    @Тогда("^он видит приём «([^»]+)» на (\\d{2}:\\d{2})$")
    fun heSeesThatIntake(medication: String, time: String) {
        assertTrue(
            intakeIsOnScreen(medication, time),
            "the day of «${world.profiles.active}» must show the intake of «$medication» at $time: " +
                openedDay.map { "${world.medications.byId(it.medicationId)?.name} at ${it.scheduledAt}" },
        )
    }

    @Тогда("^он видит приёмы «([^»]+)» на (\\d{2}:\\d{2}) и «([^»]+)» на (\\d{2}:\\d{2})$")
    fun heSeesBothIntakes(first: String, firstTime: String, second: String, secondTime: String) {
        assertTrue(intakeIsOnScreen(first, firstTime), "the day must show «$first» at $firstTime")
        assertTrue(intakeIsOnScreen(second, secondTime), "the day must show «$second» at $secondTime")
    }

    @Тогда("^(?:он )?не видит приёма «([^»]+)» на (\\d{2}:\\d{2})$")
    fun heDoesNotSeeThatIntake(medication: String, time: String) {
        assertFalse(
            intakeIsOnScreen(medication, time),
            "an intake of «$medication» at $time belongs to another profile and must never be shown (ПРФ-2)",
        )
    }

    /**
     * На экране нет способа показать приёмы нескольких профилей сразу.
     *
     * Это утверждение о контракте запросов, а не о разметке (`ПРФ-4`): наблюдать список
     * приёмов без профиля в репозитории нечем — значит, и экрану взять такой список
     * неоткуда. Проверка идёт по самому интерфейсу [IntakeRepository], и её молчание
     * было бы худшим исходом, поэтому отдельно утверждается, что она вообще что-то нашла.
     *
     * Suspend-выборки сюда не входят: они не наблюдают данные для экрана, а `expiredPending`
     * и вовсе обязан видеть все профили — автоматический перевод в «пропущено» идёт
     * независимо от того, чей экран открыт (`ПР-1`).
     */
    @Тогда("^на экране нет способа показать приёмы всех профилей сразу$")
    fun thereIsNoWayToShowTheIntakesOfAllProfilesAtOnce() {
        val screenReads = IntakeRepository::class.java.methods.filter { method ->
            method.returnType == Flow::class.java && method.genericReturnType.typeName.contains(INTAKE)
        }
        assertTrue(
            screenReads.isNotEmpty(),
            "the guard found no observable intake queries at all: the repository contract changed under it",
        )
        val unscoped = screenReads
            .filterNot { method -> method.parameterTypes.any { it == String::class.java } }
            .map { it.name }
        assertEquals(
            emptyList<String>(),
            unscoped,
            "an observable intake query appeared without a profile: one screen mistake would show " +
                "another person's medical data (ПРФ-4)",
        )
    }

    // --- Проверки: список лекарств ---------------------------------------------

    @Тогда("^в списке есть «([^»]+)» и «([^»]+)»$")
    fun theListContainsBoth(first: String, second: String) {
        listOf(first, second).forEach { name ->
            val medication = world.medications.get(world.medicationNamed(name))
            assertTrue(
                openedList.any { it.id == medication.id },
                "the list of «${world.profiles.active}» must contain «$name»",
            )
        }
    }

    @Тогда("^в списке нет «([^»]+)»$")
    fun theListDoesNotContainThatMedication(medication: String) {
        val id = world.medications.get(world.medicationNamed(medication)).id
        assertFalse(
            openedList.any { it.id == id },
            "a medication of another profile appeared in the list of «${world.profiles.active}» (ПРФ-4)",
        )
    }

    // --- Проверки: показатели приверженности -----------------------------------

    @Тогда("^показано (\\S+) пропуск\\S*$")
    fun thatManyMissesAreShown(count: String) {
        assertEquals(
            RussianDates.count(count),
            adherenceOf(world.profiles.active).missesInPeriod,
            "the misses of «${world.profiles.active}» counted for the month",
        )
    }

    @Тогда("^пропуски профиля «([^»]+)» в расчёт не попадают$")
    fun theMissesOfThatProfileAreNotCounted(profile: String) {
        // Чужой профиль обязан иметь собственные пропуски: иначе утверждение проверяло бы
        // пустоту. Дальше — что ни один пропуск из его лекарств в расчёт не вошёл.
        assertTrue(
            adherenceOf(profile).missesInPeriod > 0,
            "«$profile» must have misses of its own for the exclusion to mean anything",
        )
        val owner = world.profiles.get(world.profiles.active)
        val ownMedications = world.medications.all().filter { it.profileId == owner.id }.map { it.id }.toSet()
        assertTrue(
            adherenceOf(world.profiles.active).byMedication.all { it.medicationId in ownMedications },
            "a period shown in the context of «${world.profiles.active}» contains a medication of another profile",
        )
    }

    // --- Обвязка ---------------------------------------------------------------

    private val activeProfileId: String get() = world.profiles.get(world.profiles.active).id

    /**
     * Что показал бы экран «Сегодня»: приёмы суток активного профиля из того же запроса,
     * которым их читает экран.
     */
    private fun openToday() {
        // Приёмы порождает планировщик, а не шаг: Предыстория называет расписание,
        // а приёмы из него выводит SKL-06.
        world.plan()
        val date = world.clock.today()
        openedDay = runBlocking { world.repositories.intakeStore.observeDay(activeProfileId, date).first() }
    }

    private fun intakeIsOnScreen(medication: String, time: String): Boolean {
        val id = world.medications.get(world.medicationNamed(medication)).id
        val moment = world.clock.today().atTime(LocalTime.parse(time))
        return openedDay.any { it.medicationId == id && it.scheduledAt == moment }
    }

    private fun adherenceOf(profileName: String): AdherencePeriod {
        val month = requireNotNull(countedMonth) { "the scenario never named the month of the measures" }
        val profile = world.profiles.get(profileName)
        val window = HistoryWindow.of(month)
        val intakes = world.intakes.ofProfile(world.profiles, profileName)
            .filter { it.scheduledAt.toLocalDate() in window.from..window.to }
        val slice = HistorySlice(
            profileId = profile.id,
            intakes = intakes,
            medications = world.medications.all().filter { it.profileId == profile.id },
            historyStartsAt = intakes.minOfOrNull { it.scheduledAt.toLocalDate() },
        )
        return AdherenceCalculator().of(slice, window, world.clock.today(), world.clock.zone)
    }

    private fun nameOf(medicationId: String): String = requireNotNull(
        world.medications.names().firstOrNull { world.medications.get(it).id == medicationId },
    ) { "unknown medication id: $medicationId" }

    private companion object {
        const val INTAKE = "Intake"
    }
}
