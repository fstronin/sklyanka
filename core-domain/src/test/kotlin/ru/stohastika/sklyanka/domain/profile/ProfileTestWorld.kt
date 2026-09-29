package ru.stohastika.sklyanka.domain.profile

import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.schedule.FakeMedicationRepository
import ru.stohastika.sklyanka.domain.schedule.FakeProfileRepository
import ru.stohastika.sklyanka.domain.schedule.RecordingOperationLog
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Обвязка тестов профилей: хранилище в памяти, управляемые часы и счётчик перепланирований.
 *
 * Счётчик перепланирований здесь — главный измерительный прибор. Две ошибки, которые
 * путаются местами и обе дорогие: перепланирование при смене активного профиля (шум
 * в журнале и лишняя работа на каждом переключении, НАП-12) и его отсутствие при смене
 * дублирования напоминаний (человек остаётся без напоминаний до следующего якорного
 * будильника, ПРФ-6). Оба утверждения проверяются числом, а не чтением кода.
 */
internal class ProfileTestWorld {

    val zone: ZoneId = ZoneId.of("Europe/Moscow")
    val clock = FixedTimeSource(momentOf(LocalDateTime.of(2026, 9, 25, 11, 0)), zone)

    val profiles = FakeProfileRepository()
    val medications = FakeMedicationRepository(clock)
    val active = RecordingActiveProfile(Fixtures.PROFILE_ID)
    val log = RecordingOperationLog()
    val replanner = CountingReplanner()
    val ids = SequentialProfileIds()

    val switch = SwitchProfileUseCase(profiles = profiles, active = active)

    val setMirror = SetMirrorRemindersUseCase(profiles = profiles, replan = replanner, log = log)

    val create = CreateProfileUseCase(
        profiles = profiles,
        palette = PALETTE,
        ids = ids,
        replan = replanner,
        log = log,
    )

    val openReminder = OpenReminderProfileUseCase(
        medications = medications,
        profiles = profiles,
        switch = switch,
    )

    init {
        profiles.put(Fixtures.profile())
    }

    fun momentOf(local: LocalDateTime): Instant = local.atZone(zone).toInstant()

    /** Чужой профиль «Мама» из сценариев приёмки: четыре лекарства и дублирование напоминаний. */
    fun foreign(mirrorReminders: Boolean = true): Profile {
        val foreign = Profile(
            id = Fixtures.OTHER_PROFILE_ID,
            name = "mother",
            color = FOREIGN_COLOUR,
            isSelf = false,
            mirrorReminders = mirrorReminders,
        )
        profiles.put(foreign)
        return foreign
    }

    /** Лекарство чужого профиля: профиль приёма определяется через лекарство (ПР-10). */
    fun foreignMedication(profileId: String = Fixtures.OTHER_PROFILE_ID): String {
        val medication = Fixtures.scheduledMedication().copy(id = FOREIGN_MEDICATION_ID, profileId = profileId)
        medications.put(medication)
        return medication.id
    }

    fun eventsOf(event: OpEvent): List<ru.stohastika.sklyanka.domain.log.OpLogRecord> =
        log.records.filter { it.event == event }

    companion object {

        /** Набор цветов профиля — те же значения, что в палитре дизайн-системы (ПРФ-15). */
        const val FOREIGN_COLOUR = 0xFF8F3E23.toInt()
        const val SECOND_COLOUR = 0xFF1F5F6B.toInt()
        const val OUTSIDE_PALETTE_COLOUR = 0xFFB4512F.toInt()
        const val FOREIGN_MEDICATION_ID = "55555555-5555-4555-8555-555555555555"

        val PALETTE: Set<Int> = setOf(FOREIGN_COLOUR, SECOND_COLOUR)
    }
}

/**
 * Активный профиль в памяти, помнящий причины переключений.
 *
 * Причина нужна не для красоты: «почему я оказался в мамином профиле» — реальный вопрос
 * человека, и тест обязан утверждать, что ответ на него записан (`TDD.md §11.1`).
 */
internal class RecordingActiveProfile(initial: String) :
    ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository {

    var value: String = initial
        private set

    val reasons: MutableList<SwitchReason> = mutableListOf()

    override val activeProfileId = kotlinx.coroutines.flow.flowOf(value)

    override suspend fun current(): String = value

    override suspend fun switchTo(profileId: String) {
        switchTo(profileId, SwitchReason.USER_SETTINGS)
    }

    override suspend fun switchTo(profileId: String, reason: SwitchReason) {
        value = profileId
        reasons += reason
    }
}

/** Счётчик перепланирований: утверждения «вызвано» и «не вызвано» здесь одинаково важны. */
internal class CountingReplanner : ProfileSetReplanner {

    var calls: Int = 0
        private set

    override suspend fun onProfileSetChanged() {
        calls++
    }
}

/** Предсказуемые идентификаторы: иначе тест «профиль получил свой id» ничего не доказывает. */
internal class SequentialProfileIds : ProfileIdSource {

    private var next = 0

    override fun newId(): String = "profile-${next++}"
}
