package ru.stohastika.sklyanka.feature.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.DoctorReportTransfer
import ru.stohastika.sklyanka.domain.history.HistorySlice
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.history.ReportDocument
import ru.stohastika.sklyanka.domain.history.ReportShare
import ru.stohastika.sklyanka.domain.history.ReportShareLauncher
import ru.stohastika.sklyanka.domain.history.ReportShareResult
import ru.stohastika.sklyanka.domain.history.ReportTarget
import ru.stohastika.sklyanka.domain.history.ReportTransferOutcome
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
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.HistoryRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Заготовки истории и хранилища в памяти вместо Room.
 *
 * Состояние экрана — это арифметика по потокам, и проверять её на настоящей базе значит
 * проверять Room. Даты те же, что в `acceptance/src/main/resources/features/SKL-18.feature`: сентябрь 2026,
 * «сейчас» — 30 сентября, 10:00. Настоящих названий лекарств и имён людей здесь нет:
 * тестовые данные попадают в отчёты (НФТ-19).
 */
internal object HistoryTestData {

    const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
    const val OTHER_PROFILE_ID = "22222222-2222-4222-8222-222222222222"
    const val MEDICATION_ID = "33333333-3333-4333-8333-333333333333"
    const val SECOND_MEDICATION_ID = "44444444-4444-4444-8444-444444444444"

    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
    val SEPTEMBER: YearMonth = YearMonth.of(2026, 9)
    val TODAY: LocalDate = LocalDate.of(2026, 9, 30)
    val MORNING: LocalTime = LocalTime.of(8, 0)
    val EVENING: LocalTime = LocalTime.of(20, 0)

    fun clock(at: LocalDateTime = TODAY.atTime(10, 0)): FixedTimeSource =
        FixedTimeSource(at.atZone(ZONE).toInstant(), ZONE)

    fun instant(at: LocalDateTime): Instant = at.atZone(ZONE).toInstant()

    fun profile(id: String = PROFILE_ID, name: String = "profile-a", isSelf: Boolean = true) = Profile(
        id = id,
        name = name,
        color = 0,
        isSelf = isSelf,
        mirrorReminders = true,
    )

    @Suppress("LongParameterList")
    fun medication(
        id: String = MEDICATION_ID,
        profileId: String = PROFILE_ID,
        name: String = "med-a",
        times: List<LocalTime> = listOf(MORNING),
        startDate: LocalDate = LocalDate.of(2026, 9, 1),
        endDate: LocalDate? = null,
        archivedAt: Instant? = null,
    ) = Medication(
        id = id,
        profileId = profileId,
        name = name,
        form = MedicationForm.TABLET,
        shape = null,
        doseAmount = BigDecimal.ONE,
        doseUnit = "tablet",
        strength = BigDecimal("50"),
        strengthUnit = "mg",
        foodRelation = FoodRelation.ANY,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = times,
        startDate = startDate,
        endDate = endDate,
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = archivedAt,
        archiveReason = archivedAt?.let { ArchiveReason.COURSE_FINISHED },
    )

    fun intake(
        at: LocalDateTime,
        status: IntakeStatus = IntakeStatus.TAKEN,
        medicationId: String = MEDICATION_ID,
        actorProfileId: String? = null,
    ) = Intake(
        id = "intake-$medicationId-$at-$status",
        medicationId = medicationId,
        scheduledAt = at,
        status = status,
        actedAt = if (status == IntakeStatus.PENDING) null else instant(at),
        source = if (status == IntakeStatus.PENDING) null else IntakeSource.PHONE,
        actorProfileId = actorProfileId,
    )

    fun daily(
        from: LocalDate,
        to: LocalDate,
        time: LocalTime = MORNING,
        status: IntakeStatus = IntakeStatus.TAKEN,
        medicationId: String = MEDICATION_ID,
    ): List<Intake> = generateSequence(from) { it.plusDays(1) }
        .takeWhile { !it.isAfter(to) }
        .map { date -> intake(date.atTime(time), status, medicationId) }
        .toList()
}

/** Активный профиль: меняется на ходу — экран обязан пересобраться сам (ПР-10). */
internal class FakeActiveProfile(initial: String = HistoryTestData.PROFILE_ID) : ActiveProfileRepository {

    private val id = MutableStateFlow(initial)

    override val activeProfileId: Flow<String> get() = id

    override suspend fun current(): String = id.value

    override suspend fun switchTo(profileId: String) {
        id.value = profileId
    }
}

/** Профили в памяти: отчёт называет профиль по имени. */
internal class FakeProfiles(vararg profiles: Profile) : ProfileRepository {

    private val stored = MutableStateFlow(profiles.toList())

    override fun observeAll(): Flow<List<Profile>> = stored

    override suspend fun get(profileId: String): Profile? = stored.value.firstOrNull { it.id == profileId }

    override suspend fun getSelf(): Profile? = stored.value.firstOrNull { it.isSelf }

    override suspend fun save(profile: Profile) {
        stored.value = stored.value.filterNot { it.id == profile.id } + profile
    }
}

/**
 * История в памяти: срез на профиль, отфильтрованный по окну.
 *
 * Фильтр по окну здесь настоящий: без него проверка «переключение месяца пересчитывает
 * всё» проходила бы при сломанном переключении.
 */
internal class FakeHistory : HistoryRepository {

    private val slices = MutableStateFlow<Map<String, HistorySlice>>(emptyMap())

    fun put(profileId: String, intakes: List<Intake>, medications: List<Medication>) {
        val slice = HistorySlice(
            profileId = profileId,
            intakes = intakes,
            medications = medications,
            historyStartsAt = intakes.minOfOrNull { it.scheduledAt.toLocalDate() },
        )
        slices.value = slices.value + (profileId to slice)
    }

    override fun observe(profileId: String, window: HistoryWindow): Flow<HistorySlice> = slices.map { stored ->
        val slice = stored[profileId] ?: HistorySlice.empty(profileId)
        slice.copy(
            intakes = slice.intakes.filter { intake ->
                val date = intake.scheduledAt.toLocalDate()
                !date.isBefore(window.from) && !date.isAfter(window.to)
            },
        )
    }
}

/** Отрисовка отчёта без ресурсов: состав проверяется здесь, формулировки — на стенде. */
internal class FakeRenderer : DoctorReportRenderer {

    val rendered: MutableList<DoctorReport> = mutableListOf()

    /** Состав шторки: подставляется проверкой, а не собирается из ресурсов. */
    var composition: ReportComposition = ReportComposition(
        profile = "profile-a",
        period = "period",
        from = "from",
        to = "to",
        taken = "taken",
        format = "PDF",
    )

    override fun render(report: DoctorReport, zone: ZoneId): ReportDocument {
        rendered += report
        return ReportDocument(
            fileName = "report.pdf",
            mediaType = "application/pdf",
            bytes = "medications=${report.medications.size}".toByteArray(),
        )
    }

    override fun composition(report: DoctorReport): ReportComposition = composition
}

/** Передача отчёта, которой можно назначить исход: настоящая живёт в `app`. */
internal class FakeTransfer : DoctorReportTransfer {

    /** Принудительный исход вместо выбора получателя: проверки отказа (иначе `null`). */
    var next: ReportTransferOutcome? = null

    val shared: MutableList<ReportDocument> = mutableListOf()
    val saved: MutableList<Pair<ReportDocument, ReportTarget>> = mutableListOf()

    override suspend fun share(document: ReportDocument, launcher: ReportShareLauncher): ReportTransferOutcome {
        shared += document
        next?.let { return it }
        val share = ReportShare("content://share/report.pdf", document.fileName, document.mediaType)
        return when (launcher.launch(share)) {
            ReportShareResult.CHOSEN -> ReportTransferOutcome.Shared
            ReportShareResult.DISMISSED -> ReportTransferOutcome.Cancelled
            ReportShareResult.UNAVAILABLE -> ReportTransferOutcome.Failed("no_activity_for_share")
        }
    }

    override suspend fun save(document: ReportDocument, target: ReportTarget): ReportTransferOutcome {
        saved += document to target
        return next ?: ReportTransferOutcome.Saved
    }
}

/** Журнал в памяти: по нему проверяется и след, и отсутствие в нём медицинских данных. */
internal class RecordingLog : OperationLog {

    val records: MutableList<OpLogRecord> = mutableListOf()

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
}
