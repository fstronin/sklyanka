package ru.stohastika.sklyanka.acceptance.locale.matrix

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.stohastika.sklyanka.acceptance.onboarding.OnboardingAppProbe
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.AdherencePeriod
import ru.stohastika.sklyanka.domain.history.DayOutcome
import ru.stohastika.sklyanka.domain.history.DayState
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.MedicationAdherence
import ru.stohastika.sklyanka.domain.history.OnTimeShare
import ru.stohastika.sklyanka.domain.history.ReportDocument
import ru.stohastika.sklyanka.domain.history.ReportMedicationRow
import ru.stohastika.sklyanka.domain.medication.ArchiveCaption
import ru.stohastika.sklyanka.domain.medication.ArchiveCard
import ru.stohastika.sklyanka.domain.medication.ArchiveContent
import ru.stohastika.sklyanka.domain.medication.CourseProgress
import ru.stohastika.sklyanka.domain.medication.DayCell
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.MedicationSubtitle
import ru.stohastika.sklyanka.domain.medication.TrailingLine
import ru.stohastika.sklyanka.domain.medication.WeekAdherenceStrip
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.onboarding.ManualStepState
import ru.stohastika.sklyanka.domain.onboarding.NotificationAccessState
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionChecklist
import ru.stohastika.sklyanka.domain.onboarding.PermissionGate
import ru.stohastika.sklyanka.domain.onboarding.PermissionGrant
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import ru.stohastika.sklyanka.domain.profile.ProfileLosses
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.domain.stock.StockPanel
import ru.stohastika.sklyanka.domain.transfer.MatchDecision
import ru.stohastika.sklyanka.domain.transfer.TransferCounts
import ru.stohastika.sklyanka.domain.transfer.TransferMode
import ru.stohastika.sklyanka.domain.transfer.TransferNotice
import ru.stohastika.sklyanka.domain.transfer.TransferReport
import ru.stohastika.sklyanka.feature.history.PdfDoctorReportRenderer
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveNotice
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveUiState
import ru.stohastika.sklyanka.feature.meds.card.MedCardUiState
import ru.stohastika.sklyanka.feature.meds.card.ScheduleBlockUi
import ru.stohastika.sklyanka.feature.meds.form.FormInputs
import ru.stohastika.sklyanka.feature.meds.form.FormMode
import ru.stohastika.sklyanka.feature.meds.form.MedFormUiState
import ru.stohastika.sklyanka.feature.meds.list.AsNeededCardUi
import ru.stohastika.sklyanka.feature.meds.list.MedicationCardUi
import ru.stohastika.sklyanka.feature.meds.list.MedsUiState
import ru.stohastika.sklyanka.feature.reminder.ReminderRow
import ru.stohastika.sklyanka.feature.reminder.ReminderUiState
import ru.stohastika.sklyanka.feature.settings.PermissionsSummary
import ru.stohastika.sklyanka.feature.settings.ProfileRowState
import ru.stohastika.sklyanka.feature.settings.SettingsUiState
import ru.stohastika.sklyanka.feature.settings.profile.DeleteProfileUiState
import ru.stohastika.sklyanka.ui.token.ProfileColor
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Состояния экранов для локализационной матрицы (SKL-91).
 *
 * Числа и идентификаторы те же, что у снимков и приёмки соседних задач; названия —
 * латиницей, заглушки не изображают правдоподобных лекарств (НФТ-19). Состояния —
 * настоящие `UiState` экранов: матрица проверяет разметку перевода, а не собственную
 * выдумку. «Сегодня» и диагностика собираются доменом через мир приёмки
 * ([TodayFromWorld], [OnboardingAppProbe]) — их состояние считать должно правило,
 * а не тест.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal object LocalizationScreenFixtures {

    val TODAY: LocalDate = LocalDate.of(2026, 9, 25)
    val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 25, 8, 0)
    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")

    private const val OWNER = "Self"
    private const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"

    // --- «Сегодня» ------------------------------------------------------------------------------

    fun todayDay(world: AcceptanceWorld): ru.stohastika.sklyanka.feature.today.TodayUiState =
        TodayFromWorld.state(world)

    fun todayEmptyWorld(): AcceptanceWorld = world()

    fun todayDayWorld(): AcceptanceWorld = world(
        MedicationSpec(
            name = "med-a",
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            stock = "9",
            threshold = 7,
        ),
        MedicationSpec(name = "med-b", times = listOf(LocalTime.of(8, 0))),
        MedicationSpec(name = "med-c", times = listOf(LocalTime.of(20, 0))),
    )

    fun todayDenseWorld(): AcceptanceWorld = world(
        MedicationSpec(name = "med-a", times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
        MedicationSpec(name = "med-b", times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
        MedicationSpec(name = "med-c", times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
        MedicationSpec(name = "med-d", times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
        MedicationSpec(name = "med-e", times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
        MedicationSpec(name = "med-f", times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
    )

    // --- Лекарства ------------------------------------------------------------------------------

    fun medsList(longTitle: Boolean = false): MedsUiState = MedsUiState(
        activeCount = 3,
        archiveCount = 12,
        loading = false,
        items = listOf(
            scheduledCard(
                id = "scheduled-1",
                title = if (longTitle) LONG_MEDICATION_TITLE else "med-a",
                times = listOf(LocalTime.of(8, 0), LocalTime.of(13, 30), LocalTime.of(20, 0)),
                trailing = TrailingLine.Stock(BigDecimal("9"), DoseUnit.TABLET),
            ),
            scheduledCard(id = "scheduled-2", title = "med-b"),
            asNeededCard(),
        ),
    )

    fun medsEmpty(): MedsUiState = MedsUiState(loading = false)

    fun archive(): ArchiveUiState = ArchiveUiState(
        activeCount = 3,
        archiveCount = 2,
        loading = false,
        content = ArchiveContent(
            finished = listOf(
                ArchiveCard(
                    medicationId = "med-a",
                    title = "med-a",
                    form = MedicationForm.TABLET,
                    caption = ArchiveCaption.Outcome(days = 7, taken = 13, planned = 14),
                ),
            ),
            manual = listOf(
                ArchiveCard(
                    medicationId = "med-b",
                    title = "med-b",
                    form = MedicationForm.CAPSULE,
                    caption = ArchiveCaption.ArchivedOn(TODAY.minusDays(23)),
                ),
            ),
            total = 2,
        ),
        notice = ArchiveNotice.PeriodNeedsUpdate,
    )

    fun medCard(): MedCardUiState = MedCardUiState(
        medicationId = "medication-1",
        title = "med-a",
        form = MedicationForm.TABLET,
        subtitle = MedicationSubtitle(
            form = MedicationForm.TABLET,
            shape = TabletShape.ROUND_SCORED,
            strength = BigDecimal("48"),
            strengthUnit = "mg",
            foodRelation = FoodRelation.BEFORE_MEAL,
        ),
        schedule = ScheduleBlockUi.Scheduled(
            timesPerDay = 2,
            doseAmount = BigDecimal("2"),
            doseUnit = DoseUnit.TABLET,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        ),
        course = CourseProgress.Bounded(
            start = TODAY.minusDays(13),
            end = TODAY.plusDays(17),
            daysLeft = 17,
            fraction = 0.43f,
        ),
        stock = StockPanel.Visible(unitsLeft = BigDecimal("9"), daysLeft = 4, warning = true),
        stockUnit = DoseUnit.TABLET,
        week = week(),
        isArchived = false,
        courseFinishedOffer = false,
        refill = null,
        notice = null,
    )

    fun medForm(): MedFormUiState = MedFormUiState.of(
        draft = MedicationDraft(
            id = null,
            profileId = PROFILE_ID,
            name = "med-a",
            form = MedicationForm.TABLET,
            shape = TabletShape.ROUND_SCORED,
            doseAmount = BigDecimal("2"),
            strength = BigDecimal("48"),
            strengthUnit = "mg",
            foodRelation = FoodRelation.BEFORE_MEAL,
            scheduleMode = ScheduleMode.SCHEDULED,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            startDate = TODAY,
            stockLeft = BigDecimal("60"),
            packageSize = BigDecimal("60"),
            stockThresholdDays = 7,
        ),
        mode = FormMode.Create,
        inputs = FormInputs(doseAmount = "2", strength = "48", stockLeft = "60", packageSize = "60"),
    )

    // --- История --------------------------------------------------------------------------------

    fun history(): ru.stohastika.sklyanka.feature.history.HistoryUiState =
        ru.stohastika.sklyanka.feature.history.HistoryUiState(
            month = SEPTEMBER,
            period = septemberPeriod(),
            insightTime = EVENING,
        )

    fun historyEmpty(): ru.stohastika.sklyanka.feature.history.HistoryUiState =
        ru.stohastika.sklyanka.feature.history.HistoryUiState(
            month = YearMonth.of(2026, 7),
            period = AdherencePeriod.empty(
                profileId = PROFILE_ID,
                from = LocalDate.of(2026, 7, 1),
                to = LocalDate.of(2026, 7, 31),
            ),
        )

    /**
     * Открытый отчёт врачу с составом на языке варианта.
     *
     * Состав шторки собирает настоящий рендер отчёта, а не строка-заглушка: дизайнер
     * проверяет по листу, что и PDF, и шторка говорят на языке интерфейса (SKL-45/SKL-62).
     * Рендер закрепляет язык на контексте приложения, поэтому закреплённый матрицей
     * контекст отдаётся ему и в `applicationContext`.
     */
    @Composable
    fun historyReport(): ru.stohastika.sklyanka.feature.history.HistoryUiState {
        val context = LocalContext.current
        val report = historyDoctorReport()
        val renderer = remember(context) { PdfDoctorReportRenderer(PinnedLocaleContext(context)) }
        return history().copy(
            report = ru.stohastika.sklyanka.feature.history.ReportUi(
                document = ReportDocument(
                    fileName = "sklyanka-report-2026-09.pdf",
                    mediaType = "application/pdf",
                    bytes = ByteArray(0),
                ),
                composition = renderer.composition(report),
                preset = ru.stohastika.sklyanka.domain.history.ReportPreset.THIS_MONTH,
                customFrom = report.period.from,
                customTo = report.period.to,
                today = report.period.to,
            ),
        )
    }

    private fun historyDoctorReport(): DoctorReport = DoctorReport(
        period = septemberPeriod(),
        profileName = "Self",
        generatedAt = NOW.atZone(ZONE).toInstant(),
        medications = listOf(
            reportMedication(id = "med-a", taken = 29, planned = 30),
            reportMedication(id = "med-b", taken = 43, planned = 60),
        ),
    )

    private fun reportMedication(id: String, taken: Int, planned: Int): ReportMedicationRow = ReportMedicationRow(
        medicationId = id,
        name = id,
        form = MedicationForm.TABLET,
        doseAmount = BigDecimal.ONE,
        doseUnit = "tablet",
        strength = null,
        strengthUnit = null,
        foodRelation = FoodRelation.BEFORE_MEAL,
        times = listOf(LocalTime.of(8, 0)),
        courseStart = TODAY.minusDays(13),
        courseEnd = null,
        taken = taken,
        planned = planned,
        skippedByPerson = 0,
        missedWithoutMark = planned - taken,
        archived = false,
        missedDates = emptyList(),
    )

    // --- Напоминание ----------------------------------------------------------------------------

    fun reminderCard(): ReminderUiState.Card = ReminderUiState.Card(
        scheduledAt = NOW,
        items = listOf(
            ReminderRow(
                intakeId = "intake-1",
                name = "med-a",
                form = MedicationForm.TABLET,
                doseAmount = BigDecimal("2"),
                strength = BigDecimal("48"),
                strengthUnit = "mg",
            ),
            ReminderRow(
                intakeId = "intake-2",
                name = "med-b",
                form = MedicationForm.CAPSULE,
                doseAmount = BigDecimal.ONE,
                strength = null,
                strengthUnit = null,
            ),
        ),
        snoozeMinutes = 15,
        foreignProfileName = null,
    )

    // --- Разрешения, настройки, удаление профиля ------------------------------------------------

    fun permissions(): ru.stohastika.sklyanka.feature.onboarding.OnboardingUiState =
        ru.stohastika.sklyanka.feature.onboarding.OnboardingUiState(
            source = OnboardingSource.FIRST_LAUNCH,
            checklist = PermissionChecklist(
                vendor = VendorProfile.EMUI,
                notifications = NotificationAccessState(
                    permission = PermissionGrant.Granted(Instant.EPOCH),
                    channel = PermissionGate.GRANTED,
                    fullScreenIntent = PermissionGate.GRANTED,
                ),
                exactAlarms = PermissionGrant.Granted(Instant.EPOCH),
                appStartup = ManualStepState(confirmedByUser = false),
                batterySaving = ManualStepState(confirmedByUser = false),
                recentsPin = ManualStepState(confirmedByUser = false),
            ),
        )

    fun settings(): SettingsUiState {
        val own = ProfileRowState(
            id = "self",
            name = OWNER,
            colorArgb = ProfileColor.Teal.argb,
            isSelf = true,
            isActive = true,
            medicationCount = 2,
            mirrorReminders = false,
        )
        val mother = ProfileRowState(
            id = "mother",
            name = "Mum",
            colorArgb = ProfileColor.Terracotta.argb,
            isSelf = false,
            isActive = false,
            medicationCount = 4,
            mirrorReminders = true,
        )
        return SettingsUiState(
            profiles = listOf(own, mother),
            reminders = ReminderSettingsValues(persistentEnabled = true),
            permissions = PermissionsSummary(denied = 1, manual = 1),
            version = "0.1.0",
        )
    }

    fun deleteProfile(): DeleteProfileUiState = DeleteProfileUiState(
        profileId = "mother",
        profileName = "Mum",
        losses = ProfileLosses(medications = 4, archived = 1, intakes = 318),
        word = "",
        exportFileName = "sklyanka-before-delete.json",
        zone = ZONE,
    )

    // --- Перенос данных -------------------------------------------------------------------------

    val DEVICE_COUNTS: TransferCounts = TransferCounts(
        profiles = 2,
        medications = 5,
        archived = 12,
        intakes = 3184,
        historyFrom = LocalDate.of(2026, 2, 12),
    )

    val FILE_COUNTS: TransferCounts = TransferCounts(
        profiles = 3,
        medications = 7,
        archived = 4,
        intakes = 2100,
        historyFrom = LocalDate.of(2026, 1, 1),
    )

    fun transferOverview(): ru.stohastika.sklyanka.feature.settings.transfer.TransferUiState =
        ru.stohastika.sklyanka.feature.settings.transfer.TransferUiState(
            deviceCounts = DEVICE_COUNTS,
            deviceHistoryMonths = 8,
            exportFileName = "sklyanka-2026-10-12.json",
            zone = ZONE,
        )

    fun transferSummary(): ru.stohastika.sklyanka.feature.settings.transfer.TransferUiState =
        ru.stohastika.sklyanka.feature.settings.transfer.TransferUiState(
            step = ru.stohastika.sklyanka.feature.settings.transfer.TransferStep.SUMMARY,
            deviceCounts = DEVICE_COUNTS,
            deviceHistoryMonths = 8,
            exportFileName = "sklyanka-2026-10-12.json",
            mode = TransferMode.MERGE,
            fileCounts = FILE_COUNTS,
            fileExportedAt = Instant.parse("2026-10-05T18:14:03Z"),
            zone = ZONE,
        )

    fun transferReport(): TransferReport = TransferReport(
        mode = TransferMode.MERGE,
        profilesAdded = 1,
        medicationsAdded = 2,
        medicationsUpdated = 1,
        intakesAdded = 40,
        intakesUpdated = 2,
        intakesSkipped = 3,
        decisions = mapOf(MatchDecision.KEEP_MINE to 1, MatchDecision.TAKE_FROM_FILE to 1),
        notices = listOf(TransferNotice.SoundNotFound),
    )

    fun transferMedication(): Medication = Medication(
        id = "file-med",
        profileId = "p1",
        name = "Magnesium B6",
        form = MedicationForm.TABLET,
        shape = TabletShape.ROUND_SCORED,
        doseAmount = BigDecimal("2"),
        doseUnit = "tablet",
        strength = BigDecimal("48"),
        strengthUnit = "mg",
        foodRelation = FoodRelation.BEFORE_MEAL,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        startDate = LocalDate.of(2026, 2, 12),
        endDate = null,
        stockLeft = BigDecimal("9"),
        stockThresholdDays = null,
        archivedAt = null,
        archiveReason = null,
    )

    // --- Диагностика ----------------------------------------------------------------------------

    fun diagnosticsWorld(): AcceptanceWorld = todayDayWorld()

    /**
     * Снимок диагностики собирается настоящей моделью на мире сценария, а модель живёт
     * на подменённом главном диспетчере (приём SKL-20) — он же и снимается после сборки.
     */
    fun diagnosticsState(world: AcceptanceWorld): ru.stohastika.sklyanka.feature.reminder.DiagnosticsUiState {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val state = OnboardingAppProbe.diagnosticsState(world)
        Dispatchers.resetMain()
        return state
    }

    // --- Мир приёмки ----------------------------------------------------------------------------

    private fun world(vararg specs: MedicationSpec): AcceptanceWorld {
        val world = AcceptanceWorld()
        world.profiles.use(OWNER)
        specs.forEach { spec ->
            world.medications.add(
                profile = world.profiles,
                profileName = OWNER,
                name = spec.name,
                times = spec.times,
                mode = spec.mode,
                maxPerDay = spec.maxPerDay,
            )
            if (spec.stock != null) {
                world.medications.update(spec.name) { medication ->
                    medication.copy(
                        stockLeft = BigDecimal(spec.stock),
                        stockThresholdDays = spec.threshold,
                    )
                }
            }
        }
        return world
    }

    private data class MedicationSpec(
        val name: String,
        val times: List<LocalTime> = emptyList(),
        val mode: ScheduleMode = ScheduleMode.SCHEDULED,
        val maxPerDay: Int? = null,
        val stock: String? = null,
        val threshold: Int? = null,
    )

    // --- Обвязка --------------------------------------------------------------------------------

    private fun scheduledCard(
        id: String,
        title: String,
        times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
        trailing: TrailingLine = TrailingLine.Period(TODAY.minusDays(13), null),
    ) = MedicationCardUi(
        medicationId = id,
        title = title,
        form = MedicationForm.TABLET,
        shape = TabletShape.ROUND_SCORED,
        strength = "50",
        strengthUnit = "mg",
        timesPerDay = times.size,
        times = times,
        trailingLine = trailing,
        asNeeded = null,
    )

    private fun asNeededCard() = MedicationCardUi(
        medicationId = "as-needed",
        title = "med-c",
        form = MedicationForm.CAPSULE,
        shape = null,
        strength = "200",
        strengthUnit = "mg",
        timesPerDay = 0,
        times = emptyList(),
        trailingLine = TrailingLine.Period(TODAY.minusDays(13), null),
        asNeeded = AsNeededCardUi(
            maxPerDay = 3,
            lastIntakeDate = TODAY.minusDays(6),
            takeEnabled = true,
        ),
    )

    private fun week(): WeekAdherenceStrip {
        val days = (1..6).map { back ->
            DayCell.Completed(date = TODAY.minusDays(back.toLong()), taken = 2, planned = 2)
        }.reversed() + DayCell.Today(TODAY)
        return WeekAdherenceStrip(days = days, taken = 12, planned = 14)
    }

    private fun septemberPeriod(): AdherencePeriod = AdherencePeriod(
        profileId = PROFILE_ID,
        from = SEPTEMBER.atDay(1),
        to = SEPTEMBER.atEndOfMonth(),
        days = SEPTEMBER.dates(),
        byMedication = listOf(
            MedicationAdherence(
                medicationId = "med-a",
                name = "med-a",
                taken = 29,
                planned = 30,
                misses = 1,
                archived = false,
                low = false,
            ),
            MedicationAdherence(
                medicationId = "med-b",
                name = "med-b",
                taken = 43,
                planned = 60,
                misses = 17,
                archived = false,
                low = true,
            ),
        ),
        daysWithoutMisses = 9,
        missesInPeriod = 12,
        onTimeShare = OnTimeShare.Undefined,
        missesByTime = mapOf(EVENING to 9, LocalTime.of(8, 0) to 3),
        scheduledTimes = setOf(LocalTime.of(8, 0), EVENING),
    )

    private fun YearMonth.dates(): List<DayOutcome> = (1..lengthOfMonth()).map { day ->
        val date = atDay(day)
        val state = when (day) {
            TODAY.dayOfMonth -> DayState.AHEAD
            15 -> DayState.PARTIAL
            16 -> DayState.MISSED
            17 -> DayState.NO_INTAKES
            else -> DayState.ALL_TAKEN
        }
        DayOutcome(date = date, state = state, isToday = date == TODAY)
    }

    private val SEPTEMBER: YearMonth = YearMonth.of(2026, 9)
    private val EVENING: LocalTime = LocalTime.of(20, 0)

    private const val LONG_MEDICATION_TITLE =
        "Magnesium B6 with a very long name that cannot fit into a single line"
}

/**
 * Контекст, оставляющий закреплённый язык и для `applicationContext` (SKL-91).
 *
 * Композер отчёта закрепляет язык на контексте приложения; без этой обёртки матрица
 * показала бы состав шторки на одном языке продукта во всех шести вариантах листа.
 */
private class PinnedLocaleContext(base: Context) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
}
