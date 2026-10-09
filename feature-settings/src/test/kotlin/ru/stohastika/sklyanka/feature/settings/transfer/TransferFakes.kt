package ru.stohastika.sklyanka.feature.settings.transfer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OpLogRecord
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.transfer.AppStamp
import ru.stohastika.sklyanka.domain.transfer.DeviceState
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import ru.stohastika.sklyanka.domain.transfer.ImportSource
import ru.stohastika.sklyanka.domain.transfer.SafetyCopy
import ru.stohastika.sklyanka.domain.transfer.TransferApplied
import ru.stohastika.sklyanka.domain.transfer.TransferCodec
import ru.stohastika.sklyanka.domain.transfer.TransferDecoded
import ru.stohastika.sklyanka.domain.transfer.TransferFiles
import ru.stohastika.sklyanka.domain.transfer.TransferIdSource
import ru.stohastika.sklyanka.domain.transfer.TransferPlan
import ru.stohastika.sklyanka.domain.transfer.TransferReplanner
import ru.stohastika.sklyanka.domain.transfer.TransferSettings
import ru.stohastika.sklyanka.domain.transfer.TransferSettingsStore
import ru.stohastika.sklyanka.domain.transfer.TransferSnapshot
import ru.stohastika.sklyanka.domain.transfer.TransferStore
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Подставные границы переноса.
 *
 * Поведение экрана проверяется на JVM: устройства в среде задачи нет, а порядку шагов
 * загрузки оно и не нужно. Границы простые намеренно — проверяется экран, а не они.
 */
internal class FakeTransferStore(var state: DeviceState = DeviceState.EMPTY) : TransferStore {

    val applied = mutableListOf<TransferPlan>()
    var failWith: Throwable? = null

    override suspend fun readAll(): DeviceState = state

    override suspend fun apply(plan: TransferPlan): TransferApplied {
        failWith?.let { throw it }
        applied += plan
        return TransferApplied(
            profilesBefore = state.profiles.size,
            medicationsBefore = state.medications.size,
            intakesBefore = state.intakes.size,
            profilesAfter = state.profiles.size + plan.profilesToInsert.size,
            medicationsAfter = state.medications.size + plan.medicationsToInsert.size,
            intakesAfter = state.intakes.size + plan.intakesToInsert.size,
        )
    }
}

internal class FakeTransferFiles(private var content: String = "") : TransferFiles {

    var written: String? = null
    var discarded = false
    var safetyCopyContent: String? = null
    var freeSpace: Long = Long.MAX_VALUE
    var failOnWrite = false
    var readFails = false

    override fun freeSpaceBytes(): Long = freeSpace

    override suspend fun write(target: ExportTarget, content: String) {
        if (failOnWrite) error("the write was interrupted")
        written = content
    }

    override suspend fun discard(target: ExportTarget) {
        discarded = true
    }

    override suspend fun read(source: ImportSource): String {
        if (readFails) error("the file cannot be read")
        return content
    }

    override suspend fun writeSafetyCopy(content: String): SafetyCopy {
        safetyCopyContent = content
        return SafetyCopy(name = SAFETY_NAME, sizeBytes = content.length.toLong(), createdAt = Instant.EPOCH)
    }

    override fun safetyCopy(): SafetyCopy? = safetyCopyContent?.let {
        SafetyCopy(name = SAFETY_NAME, sizeBytes = it.length.toLong(), createdAt = Instant.EPOCH)
    }

    fun holds(text: String) {
        content = text
    }

    private companion object {
        const val SAFETY_NAME = "sklyanka-before-replace.json"
    }
}

internal class FakeSettingsStore : TransferSettingsStore {

    var settings: TransferSettings = TransferSettings.DEFAULT
    var lastExport: LocalDate? = null
    var importRunning = false

    override suspend fun read(): TransferSettings = settings

    override suspend fun write(settings: TransferSettings) {
        this.settings = settings
    }

    override suspend fun lastExportDate(): LocalDate? = lastExport

    override suspend fun setLastExportDate(date: LocalDate) {
        lastExport = date
    }

    override suspend fun importInProgress(): Boolean = importRunning

    override suspend fun setImportInProgress(value: Boolean) {
        importRunning = value
    }
}

/**
 * Кодек, отдающий заранее заданный исход.
 *
 * Настоящий кодек проверяется своими тестами в `core-data`; экрану нужен предсказуемый
 * ответ, а не второй прогон разбора JSON.
 */
internal class FakeCodec(var outcome: TransferDecoded) : TransferCodec {

    var encoded: TransferSnapshot? = null

    override fun encode(snapshot: TransferSnapshot): String {
        encoded = snapshot
        return ENCODED
    }

    override fun decode(text: String): TransferDecoded = outcome

    companion object {
        const val ENCODED = "{}"
    }
}

internal class FakeReplanner : TransferReplanner {
    var archivedSeen: List<String>? = null

    override suspend fun replanAfterTransfer(archivedMedicationIds: List<String>) {
        archivedSeen = archivedMedicationIds
    }
}

internal class FakeActiveProfile(initial: String = TransferData.SELF_ID) : ActiveProfileRepository {
    private val flow = MutableStateFlow(initial)

    override val activeProfileId: Flow<String> get() = flow

    override suspend fun current(): String = flow.value

    override suspend fun switchTo(profileId: String) {
        flow.value = profileId
    }
}

internal class RecordingLog : OperationLog {

    val records = mutableListOf<OpLogRecord>()

    @Suppress("LongParameterList")
    override suspend fun record(
        event: OpEvent,
        profileId: String?,
        medicationId: String?,
        intakeId: String?,
        before: String?,
        after: String?,
        source: String?,
        detail: String?,
    ) {
        records += OpLogRecord(
            id = records.size.toLong(),
            at = Instant.EPOCH,
            event = event,
            profileId = profileId,
            medicationId = medicationId,
            intakeId = intakeId,
            before = before,
            after = after,
            source = source,
            detail = detail,
        )
    }

    override suspend fun recent(limit: Int): List<OpLogRecord> = records.takeLast(limit)

    /** Подделка пишет всем записям один момент: «когда это было в прошлый раз» ею не проверяется. */
    override suspend fun lastOccurredAt(event: OpEvent): Instant? = records.lastOrNull { it.event == event }?.at

    override suspend fun prune(keepDays: Long, keepRecords: Int) = Unit

    /** Всё, что попало в журнал, одной строкой — для проверки на отсутствие названий (НФТ-19). */
    fun asText(): String = records.joinToString("\n") { record ->
        listOfNotNull(
            record.event.name,
            record.profileId,
            record.medicationId,
            record.intakeId,
            record.before,
            record.after,
            record.source,
            record.detail,
        ).joinToString(" ")
    }
}

internal class FixedIds(private val prefix: String = "new-") : TransferIdSource {
    private var next = 0

    override fun newId(): String = prefix + (++next)
}

/** Данные, узнаваемые в журнале: один из тестов доказывает, что их там нет. */
internal object TransferData {

    const val SELF_ID = "p1"
    const val MEDICATION_ID = "m1"
    const val MEDICATION_NAME = "Magnesium B6"
    const val STRENGTH_UNIT = "mg"

    val EXPORTED_AT: Instant = Instant.parse("2026-10-05T18:14:03Z")

    fun self() = Profile(id = SELF_ID, name = "Owner", color = 1, isSelf = true, mirrorReminders = false)

    fun medication(id: String = MEDICATION_ID, archived: Boolean = false) = Medication(
        id = id,
        profileId = SELF_ID,
        name = MEDICATION_NAME,
        form = MedicationForm.TABLET,
        shape = TabletShape.ROUND,
        doseAmount = BigDecimal("1"),
        doseUnit = "tablet",
        strength = BigDecimal("48"),
        strengthUnit = STRENGTH_UNIT,
        foodRelation = FoodRelation.ANY,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = listOf(LocalTime.of(8, 0)),
        startDate = LocalDate.of(2026, 2, 12),
        endDate = null,
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = if (archived) EXPORTED_AT else null,
        archiveReason = if (archived) ArchiveReason.COURSE_FINISHED else null,
    )

    fun intake(id: String = "i1", medicationId: String = MEDICATION_ID) = Intake(
        id = id,
        medicationId = medicationId,
        scheduledAt = LocalDateTime.of(2026, 10, 5, 8, 0),
        status = IntakeStatus.TAKEN,
        actedAt = EXPORTED_AT,
        source = IntakeSource.PHONE,
        actorProfileId = null,
    )

    fun snapshot(
        profiles: List<Profile> = listOf(self()),
        medications: List<Medication> = listOf(medication()),
        intakes: List<Intake> = listOf(intake()),
    ) = TransferSnapshot.of(
        exportedAt = EXPORTED_AT,
        app = AppStamp(versionName = "1.0", versionCode = 10),
        profiles = profiles,
        medications = medications,
        intakes = intakes,
        settings = TransferSettings.DEFAULT,
    )
}
