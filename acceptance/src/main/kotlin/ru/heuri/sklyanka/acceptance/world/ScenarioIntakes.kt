package ru.heuri.sklyanka.acceptance.world

import ru.heuri.sklyanka.domain.intake.EndOfDayIntakeWindow
import ru.heuri.sklyanka.domain.intake.IntakeWindow
import ru.heuri.sklyanka.domain.intake.MarkOutcome
import ru.heuri.sklyanka.domain.intake.MarkResult
import ru.heuri.sklyanka.domain.intake.PrnOutcome
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.today.GroupMarkOutcome
import java.time.Instant
import java.time.LocalDateTime

/**
 * Приёмы сценария: сами приёмы, «текущий приём», снимок состояния до действия
 * и всё, что действие оставило после себя.
 *
 * Хранилище — в памяти, а не Room: сценарии говорят о составе списка приёмов и о правилах
 * перехода, то есть о домене. Неразрывность транзакции проверяется инструментальными
 * тестами `core-data`, эмулятора для них в сборочном контейнере нет.
 */
@Suppress("TooManyFunctions")
class ScenarioIntakes(private val clock: ScenarioClock, private val medications: ScenarioMedications) {

    private val byId = linkedMapOf<String, Intake>()
    private var sequence = 0

    /** Граница окна приёма: значение приходит из Предыстории сценария, а не из кода (ОВ-4). */
    var window: IntakeWindow = EndOfDayIntakeWindow()

    /** Приём, о котором шла речь последним: шаги «приём считается…» говорят о нём. */
    var currentId: String? = null
        private set

    /** Всё, что домен вернул наружу как повод что-то показать человеку. */
    val messagesToHuman: MutableList<String> = mutableListOf()

    val outcomes: MutableList<MarkOutcome> = mutableListOf()

    /**
     * Приём в том виде, в каком его записала отметка.
     *
     * Нужен проверке «момент отметки остаётся тем же моментом»: сверяться нужно с тем,
     * что записала сама отметка, а не с состоянием до сценария.
     */
    val appliedMarks: MutableMap<String, Intake> = linkedMapOf()

    var lastPrnOutcome: PrnOutcome? = null

    /** Чем кончилась последняя групповая отметка: по этому сценарий судит об отказе (ОВ-32). */
    var lastGroupOutcome: GroupMarkOutcome? = null

    /** Приёмы последней групповой операции: «все три приёма считаются принятыми». */
    var lastGroupIds: List<String> = emptyList()

    /**
     * Идентификатор лекарства, на записи которого хранилище отказывает.
     *
     * Сценарий групповой отметки (ОВ-32) проверяет, что отказ на одном приёме не
     * оставляет группу полуотмеченной. Хранилище в памяти, как и настоящее, умеет
     * отказывать — иначе эту ветку нечем завести.
     */
    var failingMedicationId: String? = null

    /** Нажатия на часах, ещё не дошедшие до телефона. */
    val presses: MutableList<WatchPress> = mutableListOf()

    // --- Хранилище -----------------------------------------------------------------------

    fun all(): List<Intake> = byId.values.toList()

    fun get(intakeId: String): Intake? = byId[intakeId]

    fun store(intake: Intake) {
        byId[intake.id] = intake
    }

    /** Снимок хранилища для транзакции: групповая отметка — «всё или ничего» (ОВ-32). */
    fun snapshot(): Map<String, Intake> = byId.toMap()

    fun restore(snapshot: Map<String, Intake>) {
        byId.clear()
        byId.putAll(snapshot)
    }

    fun removeIfPending(intakeId: String): Boolean = byId[intakeId]?.isPending == true && byId.remove(intakeId) != null

    /**
     * Убрать всю историю приёмов лекарства.
     *
     * Это данность Предыстории, заменяющая историю общего мира: сценарий, называющий
     * свой набор записей, не должен считать вместе с ним курс, заведённый Предысторией.
     */
    fun removeOf(medicationId: String) {
        byId.entries.removeIf { (_, intake) -> intake.medicationId == medicationId }
    }

    fun nextId(): String = "intake-${sequence++}"

    // --- Выборки -------------------------------------------------------------------------

    fun of(name: String): List<Intake> {
        val medicationId = medications.get(name).id
        return byId.values.filter { it.medicationId == medicationId }.sortedBy { it.scheduledAt }
    }

    fun pendingOf(name: String): List<Intake> = of(name).filter { it.isPending }

    fun ofProfile(profiles: ScenarioProfiles, profileName: String): List<Intake> {
        val profileId = profiles.get(profileName).id
        val owned = medications.all().filter { it.profileId == profileId }.map { it.id }.toSet()
        return byId.values.filter { it.medicationId in owned }.sortedBy { it.scheduledAt }
    }

    fun pendingOfProfile(profiles: ScenarioProfiles, profileName: String): List<Intake> =
        ofProfile(profiles, profileName).filter { it.isPending }

    fun expiredPending(before: LocalDateTime): List<Intake> =
        byId.values.filter { it.isPending && it.scheduledAt < before }

    fun current(): Intake {
        val id = requireNotNull(currentId) { "the scenario never mentioned an intake" }
        return requireNotNull(byId[id]) { "intake $id disappeared" }
    }

    fun at(name: String, moment: LocalDateTime): Intake =
        requireNotNull(of(name).firstOrNull { it.scheduledAt == moment }) { "no intake of $name at $moment" }

    fun focusOn(intake: Intake) {
        currentId = intake.id
    }

    // --- Данность Предыстории ------------------------------------------------------------

    /** Ожидающийся приём: существующий или созданный на месте — так его рождает SKL-06. */
    fun pending(name: String, moment: LocalDateTime): Intake {
        val existing = of(name).firstOrNull { it.scheduledAt == moment }
        val intake = existing ?: Intake(
            id = nextId(),
            medicationId = medications.get(name).id,
            scheduledAt = moment,
            status = IntakeStatus.PENDING,
            actedAt = null,
            source = null,
            actorProfileId = null,
        ).also { store(it) }
        medications.focusOn(name)
        currentId = intake.id
        return intake
    }

    /**
     * Уже отмеченный приём как данность Предыстории.
     *
     * Это не действие отметки: сценарий, которому важен сам переход, отмечает приём
     * доменным сценарием. Остаток такая строка не трогает — она описывает состояние,
     * в котором сценарий начинается.
     */
    fun markTaken(name: String, moment: LocalDateTime) {
        val medication = medications.get(name)
        val existing = byId.values.firstOrNull { it.medicationId == medication.id && it.scheduledAt == moment }
        val id = existing?.id ?: nextId()
        store(
            Intake(
                id = id,
                medicationId = medication.id,
                scheduledAt = moment,
                status = IntakeStatus.TAKEN,
                actedAt = clock.momentOf(moment),
                source = IntakeSource.PHONE,
                actorProfileId = null,
            ),
        )
    }

    /**
     * Приём, о котором говорит шаг, не назвавший ни лекарства, ни времени.
     *
     * Если сценарий его ещё не создавал, он создаётся по ближайшему времени расписания
     * текущего лекарства: сценарий говорит о приёме, который у человека действительно есть.
     */
    fun currentOrScheduled(): Intake {
        currentId?.let { return current() }
        val name = medications.last.ifEmpty { medications.names().first() }
        val at = medications.get(name).times.minOrNull() ?: clock.nowLocal().toLocalTime()
        return pending(name, clock.today().atTime(at))
    }

    // --- Снимок состояния ----------------------------------------------------------------

    /** Состояние приёмов до первого действия сценария: с ним сверяются «остаётся», «не изменяется». */
    var before: Map<String, Intake> = emptyMap()
        private set

    private var remembered = false

    fun remember() {
        if (remembered) return
        before = byId.toMap()
        remembered = true
    }
}

/** Нажатие на часах: момент действия и момент доставки — разные вещи (ПР-3, ЧАС-23). */
data class WatchPress(val intakeId: String, val result: MarkResult, val actedAt: Instant, var deliverAt: Instant)
