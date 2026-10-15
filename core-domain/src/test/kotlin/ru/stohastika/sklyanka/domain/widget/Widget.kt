package ru.stohastika.sklyanka.domain.widget

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.profile.ProfileContext
import ru.stohastika.sklyanka.domain.profile.ProfileContextProvider
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.schedule.FakeIntakeRepository
import ru.stohastika.sklyanka.domain.schedule.FakeMedicationRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Сутки сценариев виджета — вторник, 13 октября 2026 (`SKL-118.feature`).
 *
 * Дата взята из сценариев приёмки, а не придумана: сверить поведение с документом проще,
 * когда числа в нём и в тесте одни и те же.
 */
internal object Widget {

    val TODAY: LocalDate = LocalDate.of(2026, 10, 13)
    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")

    const val SELF_PROFILE = "profile-self"
    const val FOREIGN_PROFILE = "profile-mother"
    const val SELF_COLOUR = 0x2F6F4E
    const val FOREIGN_COLOUR = 0x8F3E23

    /** Профиль владельца: индикации у него нет (ПРФ-16). */
    val SELF = ProfileContext(
        id = SELF_PROFILE,
        name = "self",
        color = SELF_COLOUR,
        isSelf = true,
        mirrorReminders = false,
    )

    /** Чужой профиль: виджет называет его именем и цветом, без слов «чужой» (ПР-24). */
    val FOREIGN = ProfileContext(
        id = FOREIGN_PROFILE,
        name = "mother",
        color = FOREIGN_COLOUR,
        isSelf = false,
        mirrorReminders = true,
    )

    fun at(hour: Int, minute: Int = 0): LocalDateTime = TODAY.atTime(hour, minute)

    fun moment(local: LocalDateTime): Instant = local.atZone(ZONE).toInstant()

    @Suppress("LongParameterList")
    fun medication(
        id: String,
        name: String = id,
        profileId: String = SELF_PROFILE,
        times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        dose: String = "1",
        strength: String? = null,
        archived: Boolean = false,
    ) = Medication(
        id = id,
        profileId = profileId,
        name = name,
        form = MedicationForm.TABLET,
        shape = TabletShape.ROUND,
        doseAmount = BigDecimal(dose),
        doseUnit = "tablet",
        strength = strength?.let { BigDecimal(it) },
        strengthUnit = strength?.let { "mg" },
        foodRelation = FoodRelation.ANY,
        scheduleMode = mode,
        maxPerDay = if (mode == ScheduleMode.PRN) 3 else null,
        times = times,
        startDate = TODAY.minusMonths(1),
        endDate = null,
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = if (archived) moment(TODAY.minusDays(1).atStartOfDay()) else null,
        archiveReason = null,
    )

    @Suppress("LongParameterList")
    fun intake(
        id: String,
        medicationId: String,
        at: LocalDateTime,
        status: IntakeStatus = IntakeStatus.PENDING,
        dose: String? = null,
        actedAt: Instant? = null,
    ) = Intake(
        id = id,
        medicationId = medicationId,
        scheduledAt = at,
        doseAmount = dose?.let { BigDecimal(it) },
        status = status,
        actedAt = actedAt ?: if (status == IntakeStatus.PENDING) null else moment(at),
        source = if (status == IntakeStatus.PENDING) null else IntakeSource.PHONE,
        actorProfileId = null,
    )
}

/**
 * Приёмы профиля, читаемые как хранилище.
 *
 * Штатная подделка отдаёт на `observeDay` пустой поток — она писалась для генерации плана,
 * которой наблюдение не нужно. Виджет читает день именно потоком, поэтому наблюдаемая часть
 * добавляется здесь, а не переписывается в чужом наборе. Профиль берётся у лекарства:
 * собственного поля профиля у приёма нет (ПР-10).
 */
internal class WidgetIntakeRepository(
    private val delegate: FakeIntakeRepository,
    private val medications: FakeMedicationRepository,
) : IntakeRepository by delegate {

    fun put(intake: Intake) = delegate.put(intake)

    override fun observeDay(profileId: String, date: LocalDate): Flow<List<Intake>> = flowOf(
        ofProfile(profileId).filter { it.scheduledAt.toLocalDate() == date }.sortedBy { it.scheduledAt },
    )

    override fun observePeriod(profileId: String, from: LocalDate, to: LocalDate): Flow<List<Intake>> = flowOf(
        ofProfile(profileId).filter {
            !it.scheduledAt.toLocalDate().isBefore(from) && !it.scheduledAt.toLocalDate().isAfter(to)
        },
    )

    override fun observeByMedication(
        profileId: String,
        medicationId: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Intake>> = flowOf(ofProfile(profileId).filter { it.medicationId == medicationId })

    private fun ofProfile(profileId: String): List<Intake> = delegate.stored.values
        .filter { medications.stored[it.medicationId]?.profileId == profileId }
}

/** Активный профиль в памяти: разовое чтение отвечает тем, кто активен сейчас. */
internal class FakeProfileContextProvider(private var context: ProfileContext?) : ProfileContextProvider {

    override val current: Flow<ProfileContext> get() = context?.let { flowOf(it) } ?: emptyFlow()

    override suspend fun snapshot(): ProfileContext? = context

    fun switchTo(next: ProfileContext?) {
        context = next
    }
}
