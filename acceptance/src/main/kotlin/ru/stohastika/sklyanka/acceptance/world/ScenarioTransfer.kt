package ru.stohastika.sklyanka.acceptance.world

import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Invariants
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.transfer.AppStamp
import ru.stohastika.sklyanka.domain.transfer.ApplyTransferUseCase
import ru.stohastika.sklyanka.domain.transfer.BuildTransferPlan
import ru.stohastika.sklyanka.domain.transfer.DeviceState
import ru.stohastika.sklyanka.domain.transfer.ExportDataUseCase
import ru.stohastika.sklyanka.domain.transfer.ExportOutcome
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import ru.stohastika.sklyanka.domain.transfer.FindMedicationMatches
import ru.stohastika.sklyanka.domain.transfer.ImportSource
import ru.stohastika.sklyanka.domain.transfer.MatchDecision
import ru.stohastika.sklyanka.domain.transfer.MatchProfiles
import ru.stohastika.sklyanka.domain.transfer.MedicationMatch
import ru.stohastika.sklyanka.domain.transfer.ReadTransferFileUseCase
import ru.stohastika.sklyanka.domain.transfer.SafetyCopy
import ru.stohastika.sklyanka.domain.transfer.TransferApplied
import ru.stohastika.sklyanka.domain.transfer.TransferCodec
import ru.stohastika.sklyanka.domain.transfer.TransferCounts
import ru.stohastika.sklyanka.domain.transfer.TransferDecoded
import ru.stohastika.sklyanka.domain.transfer.TransferFiles
import ru.stohastika.sklyanka.domain.transfer.TransferFormat
import ru.stohastika.sklyanka.domain.transfer.TransferIdSource
import ru.stohastika.sklyanka.domain.transfer.TransferMode
import ru.stohastika.sklyanka.domain.transfer.TransferNotice
import ru.stohastika.sklyanka.domain.transfer.TransferOutcome
import ru.stohastika.sklyanka.domain.transfer.TransferPlan
import ru.stohastika.sklyanka.domain.transfer.TransferReject
import ru.stohastika.sklyanka.domain.transfer.TransferReplanner
import ru.stohastika.sklyanka.domain.transfer.TransferReport
import ru.stohastika.sklyanka.domain.transfer.TransferSettings
import ru.stohastika.sklyanka.domain.transfer.TransferSettingsStore
import ru.stohastika.sklyanka.domain.transfer.TransferSnapshot
import ru.stohastika.sklyanka.domain.transfer.TransferStore
import ru.stohastika.sklyanka.domain.transfer.ValidateSnapshot
import ru.stohastika.sklyanka.domain.transfer.logDetail
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Перенос данных в мире сценария (SKL-19).
 *
 * Здесь живут только **порты** платформы — файловая система, хранилище, настройки
 * и перепланирование. Правила не копируются: снимок собирает `TransferSnapshot.of`,
 * слияние и замену исполняют настоящие `BuildTransferPlan`, `ApplyTransferUseCase`
 * и `ReadTransferFileUseCase`, проверку файла — `ValidateSnapshot`. Харнесс на JVM
 * повторяет ровно то, что продукт делает поверх Room, SAF и `DataStore`.
 *
 * **Кодек подставляется прогоном.** Настоящий `JsonTransferCodec` живёт в `core-data`
 * (Android-модуль) и в чистый JVM-артефакт приёмки не приезжает (`ADR-0007`). Доменные
 * сценарии говорят о правилах применения, а не о проводе, и им достаточно
 * [DomainScenarioCodec]; экранные прогоны `feature-settings` и `app` подставляют
 * настоящий кодек — там, где файл и читается, и пишется по-настоящему. Второго
 * разбора JSON в приёмке нет: провод либо настоящий, либо не участвует вовсе.
 */
@Suppress("LongParameterList")
class ScenarioTransfer(
    private val profiles: ScenarioProfiles,
    private val medications: ScenarioMedications,
    private val intakes: ScenarioIntakes,
    private val activeProfile: ActiveProfileRepository,
    private val log: ScenarioLog,
    private val clock: ScenarioClock,
    fullReplan: suspend () -> Unit,
    medicationReplan: suspend (String) -> Unit,
) {

    /** Режим загрузки (ПЕР-14). До выбора человека — «Добавить к текущим». */
    var mode: TransferMode = TransferMode.MERGE
        private set

    /** Кодек провода: настоящий в модулях с `core-data`, доменная заглушка в сценарном прогоне. */
    var codec: TransferCodec = DomainScenarioCodec

    val files = ScenarioTransferFiles(clock)

    val settings = ScenarioTransferSettings()

    val store = ScenarioTransferStore(profiles, medications, intakes)

    /**
     * Перепланирование после загрузки (ПЕР-18, НАП-11) — тем же полным обходом, что
     * в `app` (`PlanningTransferReplanner`): сначала все профили, затем архивированные
     * курсы, следом пересборка очереди будильников настоящим `RescheduleRemindersUseCase`.
     */
    val replanner: TransferReplanner = WorldTransferReplanner(fullReplan, medicationReplan)

    /** Разобранный файл: то, что человек выбрал и подтвердил. */
    var prepared: TransferSnapshot? = null
        private set

    var preparedNotices: List<TransferNotice> = emptyList()
        private set

    /** Отказ, если файл не принят (ПЕР-28, ПЕР-29). */
    var receivedReject: TransferReject? = null
        private set

    var lastDecode: TransferDecoded? = null
        private set

    var lastOutcome: TransferOutcome? = null
        private set

    var lastReport: TransferReport? = null
        private set

    var lastExport: ExportOutcome? = null
        private set

    var exportedFileName: String? = null
        private set

    /** Совпадения последнего разбора: по ним проверяется «человека ни о чём не спрашивали». */
    var lastMatches: List<MedicationMatch> = emptyList()
        private set

    /** Состояние устройства перед последним применением: «ни одна запись не переписана» (ПЕР-21). */
    var medicationsBeforeApply: List<Medication> = emptyList()
        private set

    var profilesBeforeApply: List<Profile> = emptyList()
        private set

    var intakesBeforeApply: List<Intake> = emptyList()
        private set

    val decisions: MutableMap<String, MatchDecision> = linkedMapOf()

    /** Имена лекарств файла по ключу сценария: модель хранит только безымянные записи (НФТ-19). */
    val fileMedicationNames: MutableMap<String, String> = linkedMapOf()

    // --- Данность и подготовка файла -----------------------------------------------------------

    fun useMode(mode: TransferMode) {
        this.mode = mode
    }

    fun decide(fileMedicationId: String, decision: MatchDecision) {
        decisions[fileMedicationId] = decision
    }

    fun decisionFor(fileMedicationId: String): MatchDecision = decisions[fileMedicationId] ?: MatchDecision.DEFAULT

    fun prepare(snapshot: TransferSnapshot, notices: List<TransferNotice> = emptyList()) {
        prepared = snapshot
        preparedNotices = notices
        receivedReject = null
    }

    /**
     * Дописать приём в подготовленный файл.
     *
     * Сценарий называет приём отдельной строкой после лекарства, поэтому снимок файла
     * пересобирается тем же `TransferSnapshot.of`, что и первый раз: сводка считается
     * по содержимому, а не правится руками.
     */
    fun addFileIntake(
        medicationId: String,
        scheduledAt: LocalDateTime,
        status: IntakeStatus,
        actedAt: Instant? = null,
        source: IntakeSource? = null,
        actorProfileId: String? = null,
    ) {
        val base = requireNotNull(prepared) { "prepare the file before adding intakes to it" }
        val intake = Intake(
            id = "file-intake-${base.intakes.size + 1}",
            medicationId = medicationId,
            scheduledAt = scheduledAt,
            status = status,
            actedAt = actedAt,
            source = source,
            actorProfileId = actorProfileId,
        )
        prepare(
            TransferSnapshot.of(
                exportedAt = base.exportedAt,
                app = base.app,
                profiles = base.profiles,
                medications = base.medications,
                intakes = base.intakes + intake,
                settings = base.settings,
                formatVersion = base.formatVersion,
            ),
        )
    }

    fun accept(decoded: TransferDecoded) {
        lastDecode = decoded
        when (decoded) {
            is TransferDecoded.Loaded -> prepare(decoded.snapshot, decoded.notices)
            is TransferDecoded.Rejected -> {
                prepared = null
                receivedReject = decoded.reject
            }
        }
    }

    // --- Состояние устройства ------------------------------------------------------------------

    fun state(): DeviceState = store.state()

    fun counts(): TransferCounts = store.state().counts

    fun snapshot(): TransferSnapshot = TransferSnapshot.of(
        exportedAt = clock.now(),
        app = APP_STAMP,
        profiles = profiles.all(),
        medications = medications.all(),
        intakes = intakes.all(),
        settings = settings.values,
    )

    // --- Действия продукта ---------------------------------------------------------------------

    /** Выгрузка настоящим `ExportDataUseCase` (ПЕР-2, ПЕР-32). */
    fun export(handle: String = EXPORT_HANDLE): ExportOutcome {
        val useCase = ExportDataUseCase(store, settings, codec, files, clock.time, log, APP_STAMP)
        val outcome = runBlocking { useCase(ExportTarget(handle)) }
        lastExport = outcome
        if (outcome is ExportOutcome.Saved) exportedFileName = outcome.fileName
        return outcome
    }

    /** Чтение и проверка файла настоящим `ReadTransferFileUseCase` (ПЕР-15 п. 2). */
    fun read(handle: String = IMPORT_HANDLE): TransferDecoded {
        val useCase = ReadTransferFileUseCase(files, codec, ValidateSnapshot(), log)
        val decoded = runBlocking { useCase(ImportSource(handle), mode) }
        accept(decoded)
        return decoded
    }

    /** Поиск совпадений тем же доменом, что у `DataTransferViewModel.continueFromSummary`. */
    fun findMatches(): List<MedicationMatch> {
        val snapshot = requireNotNull(prepared) { "no file was prepared before looking for matches" }
        val matching = MatchProfiles(ScenarioTransferIds())(profiles.all(), snapshot.profiles)
        // Мягко удалённые не участвуют в поиске совпадений — тем же правилом, что в продукте
        // (`BuildTransferPlan`): решения не имеют права молча удалять или воскрешать.
        return FindMedicationMatches()(
            medications.all().filterNot { it.isDeleted },
            snapshot.medications.filterNot { it.isDeleted },
            matching,
        ).matches.also {
            lastMatches = it
        }
    }

    /**
     * Подтверждение загрузки: единственная точка, после которой что-то пишется (ПЕР-15 п. 6,
     * ПЕР-16 п. 4). Идёт настоящим `ApplyTransferUseCase` — план, страховочная копия,
     * транзакция и последействия целиком.
     */
    fun apply(): TransferOutcome {
        val snapshot = requireNotNull(prepared) { "no file was prepared before the import" }
        medicationsBeforeApply = medications.all()
        profilesBeforeApply = profiles.all()
        intakesBeforeApply = intakes.all()
        val useCase = ApplyTransferUseCase(
            store = store,
            settings = settings,
            codec = codec,
            files = files,
            activeProfile = activeProfile,
            replanner = replanner,
            log = log,
            buildPlan = BuildTransferPlan(ScenarioTransferIds()),
        )
        val outcome = runBlocking {
            if (mode == TransferMode.REPLACE_ALL) {
                useCase.replaceAll(snapshot, preparedNotices)
            } else {
                useCase.merge(snapshot, preparedNotices, decisions)
            }
        }
        lastOutcome = outcome
        if (outcome is TransferOutcome.Applied) lastReport = outcome.report
        return outcome
    }

    companion object {
        const val EXPORT_HANDLE = "chosen://export"
        const val IMPORT_HANDLE = "chosen://import"

        val APP_STAMP = AppStamp(versionName = "1.0", versionCode = 1)
    }
}

/**
 * Кодек сценарного прогона: провод не участвует.
 *
 * Загрузка в доменных сценариях идёт подготовленным снимком, а страховочной копии нужна
 * лишь непустая строка — содержимое копии проверяет экранный прогон настоящим кодеком
 * (`feature-settings`, сценарий «Перед стиранием делается страховочная копия»).
 * Чтение файла доменному прогону недоступно намеренно: подставить сюда второй разбор JSON
 * значило бы завести копию правил формата, которые живут в одном месте — `core-data`.
 */
object DomainScenarioCodec : TransferCodec {

    override fun encode(snapshot: TransferSnapshot): String = buildString {
        append(TransferFormat.MARKER)
        append(':')
        append(snapshot.formatVersion)
        append(':')
        append(snapshot.counts.logDetail())
    }

    override fun decode(text: String): TransferDecoded =
        error("the domain run never reads a file; the real codec is wired by the screen runs")
}

/** Перепланирование после загрузки: полный обход, затем курсы из архива (ПЕР-18, НАП-11). */
private class WorldTransferReplanner(
    private val fullReplan: suspend () -> Unit,
    private val medicationReplan: suspend (String) -> Unit,
) : TransferReplanner {

    override suspend fun replanAfterTransfer(archivedMedicationIds: List<String>) {
        fullReplan()
        archivedMedicationIds.forEach { medicationId -> medicationReplan(medicationId) }
    }
}

/** Идентификаторы перевыдачи при слиянии (ПЕР-9): детерминированные, чтобы связи читались. */
class ScenarioTransferIds(private val prefix: String = "transfer-") : TransferIdSource {
    private var next = 0
    override fun newId(): String = prefix + (++next)
}

/**
 * Файловые операции переноса в памяти (НФТ-17, ПЕР-32, ПЕР-36).
 *
 * «Документы» — места, выбранные человеком; их приложение не запоминает (ПЕР-36),
 * поэтому карта живёт только внутри сценария. Страховочная копия — отдельный слот
 * внутреннего хранилища под постоянным именем: перезаписывается, не копится.
 */
class ScenarioTransferFiles(private val clock: ScenarioClock) : TransferFiles {

    val documents: LinkedHashMap<String, String> = linkedMapOf()

    val discarded: MutableSet<String> = linkedSetOf()

    private var safetyText: String? = null
    private var safetyAt: Instant? = null

    /** Свободное место: сценарии нехватки выставляют его меньше нужного (ПЕР-31). */
    var freeSpace: Long = Long.MAX_VALUE

    /** Обрыв записи: «наполовину записанный файл не выглядит готовой копией» (ПЕР-32). */
    var failOnWrite: Boolean = false

    override fun freeSpaceBytes(): Long = freeSpace

    override suspend fun write(target: ExportTarget, content: String) {
        if (failOnWrite) error("the export write was interrupted")
        documents[target.handle] = content
    }

    override suspend fun discard(target: ExportTarget) {
        documents.remove(target.handle)
        discarded += target.handle
    }

    override suspend fun read(source: ImportSource): String =
        requireNotNull(documents[source.handle]) { "no document was chosen at ${source.handle}" }

    override suspend fun writeSafetyCopy(content: String): SafetyCopy {
        safetyText = content
        safetyAt = clock.now()
        return requireNotNull(safetyCopy())
    }

    override fun safetyCopy(): SafetyCopy? {
        val text = safetyText ?: return null
        return SafetyCopy(
            name = TransferFormat.SAFETY_COPY_NAME,
            sizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong(),
            createdAt = safetyAt ?: clock.now(),
        )
    }

    val safetyCopyContent: String? get() = safetyText

    /** Сколько копий лежит во внутреннем хранилище: решение ПЕР-36 — ровно одна. */
    val internalCopyCount: Int get() = if (safetyText == null) 0 else 1

    fun put(handle: String, content: String) {
        documents[handle] = content
    }
}

/** Настройки приложения и дата последней выгрузки в памяти (ПЕР-4, ПЕР-31). */
class ScenarioTransferSettings : TransferSettingsStore {

    var values: TransferSettings = TransferSettings.DEFAULT
        private set

    var lastExport: LocalDate? = null
        private set

    var importRunning: Boolean = false
        private set

    override suspend fun read(): TransferSettings = values

    override suspend fun write(settings: TransferSettings) {
        values = settings
    }

    override suspend fun lastExportDate(): LocalDate? = lastExport

    override suspend fun setLastExportDate(date: LocalDate) {
        lastExport = date
    }

    override suspend fun importInProgress(): Boolean = importRunning

    override suspend fun setImportInProgress(value: Boolean) {
        importRunning = value
    }

    /**
     * Процесс убит посреди загрузки: снять признак было некому (ПЕР-31).
     *
     * Так моделируется исход, который JVM-прогон иначе не воспроизводит: `ApplyTransferUseCase`
     * снимает признак, когда исключение поймано, а убийство процесса исключения не оставляет.
     */
    fun killedDuringImport() {
        importRunning = true
    }
}

/**
 * Хранилище переноса в памяти: одна транзакция на всю загрузку (ПЕР-27).
 *
 * Повторяется наблюдаемое следствие транзакции Room: снимок до, полное восстановление
 * при исключении. Ручного «откатим, что успели» здесь нет — откат тот же, что у хранилища.
 */
class ScenarioTransferStore(
    private val profiles: ScenarioProfiles,
    private val medications: ScenarioMedications,
    private val intakes: ScenarioIntakes,
) : TransferStore {

    val applied: MutableList<TransferPlan> = mutableListOf()

    /** Отказ хранилища на середине применения: им проверяется атомарность (ПЕР-27). */
    var failAfterPartial: Throwable? = null

    fun state(): DeviceState = DeviceState(profiles.all(), medications.all(), intakes.all())

    override suspend fun readAll(): DeviceState = state()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun apply(plan: TransferPlan): TransferApplied {
        val profilesBefore = profiles.snapshot()
        val medicationsBefore = medications.snapshot()
        val intakesBefore = intakes.snapshot()
        val before = counts()
        try {
            if (plan.erase) {
                // Порядок как у внешних ключей Room: приёмы → лекарства → профили.
                intakes.restore(emptyMap())
                medications.restore(emptyMap())
                profiles.clear()
            }
            plan.profilesToInsert.forEach { profile ->
                Invariants.requireValid(Invariants.violations(profile))
                profiles.insert(profile)
            }
            // Половина применённой загрузки: запись сделана, исключение следом — откат обязан
            // вернуть состояние к прежнему (ПЕР-27).
            failAfterPartial?.let { failure -> throw failure }
            (plan.medicationsToInsert + plan.medicationsToUpdate).forEach { medication ->
                Invariants.requireValid(Invariants.violations(medication))
                medications.put(medication)
            }
            plan.intakesToInsert.forEach(intakes::store)
            plan.intakesToUpdate.forEach(intakes::store)
            requireNotNull(profiles.self()) { "INV-1: exactly one self profile must exist after a transfer" }
        } catch (failure: Throwable) {
            profiles.restore(profilesBefore)
            medications.restore(medicationsBefore)
            intakes.restore(intakesBefore)
            throw failure
        }
        applied += plan
        val after = counts()
        return TransferApplied(
            profilesBefore = before.first,
            medicationsBefore = before.second,
            intakesBefore = before.third,
            profilesAfter = after.first,
            medicationsAfter = after.second,
            intakesAfter = after.third,
        )
    }

    private fun counts(): Triple<Int, Int, Int> =
        Triple(profiles.all().size, medications.all().size, intakes.all().size)
}
