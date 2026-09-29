package ru.stohastika.sklyanka.feature.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.profile.ProfileContext
import ru.stohastika.sklyanka.domain.profile.ProfileContextProvider
import ru.stohastika.sklyanka.domain.profile.SwitchReason
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.PermissionStateReader
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.watch.WatchLinkState
import ru.stohastika.sklyanka.domain.watch.WatchLinkStateReader
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Подставные границы экрана настроек.
 *
 * Простые намеренно: проверяется сборка состояния экрана, а не хранилища. Единственное,
 * что здесь устроено «как в жизни», — изменяемость: состояние разрешений и связи с часами
 * меняется мимо приложения, и проверка НФТ-52 без этого невозможна.
 */
internal class FakeProfiles(profiles: List<Profile> = emptyList()) : ProfileRepository {

    val state = MutableStateFlow(profiles)

    override fun observeAll(): Flow<List<Profile>> = state

    override suspend fun get(profileId: String): Profile? = state.value.firstOrNull { it.id == profileId }

    override suspend fun getSelf(): Profile? = state.value.firstOrNull { it.isSelf }

    override suspend fun save(profile: Profile) {
        state.value = state.value.filterNot { it.id == profile.id } + profile
    }
}

internal class FakeActiveProfile(initial: String) : ActiveProfileRepository {

    val reasons = mutableListOf<SwitchReason>()
    private val state = MutableStateFlow(initial)

    override val activeProfileId: Flow<String> = state

    override suspend fun current(): String = state.value

    override suspend fun switchTo(profileId: String) {
        state.value = profileId
    }

    override suspend fun switchTo(profileId: String, reason: SwitchReason) {
        reasons += reason
        switchTo(profileId)
    }
}

/**
 * Контекст активного профиля поверх тех же подставных хранилищ.
 *
 * Собран из них, а не задан отдельно: два независимых источника контекста дали бы экран
 * с отметкой у одного профиля и данными другого — ровно то, чего `ProfileContext`
 * не допускает.
 */
internal class FakeProfileContext(private val profiles: FakeProfiles, private val active: FakeActiveProfile) :
    ProfileContextProvider {

    override val current: Flow<ProfileContext> = active.activeProfileId
        .map { id -> profiles.state.value.firstOrNull { it.id == id }?.let(ProfileContext::of) }
        .filterNotNull()

    override suspend fun snapshot(): ProfileContext? = profiles.get(active.current())?.let(ProfileContext::of)
}

internal class FakeMedications(private val byProfile: Map<String, Int>) : MedicationRepository {

    private val state = MutableStateFlow(byProfile)

    override fun observeActive(profileId: String): Flow<List<Medication>> =
        state.map { counts -> List(counts[profileId] ?: 0) { index -> medication("$profileId-$index", profileId) } }

    override fun observeArchived(profileId: String): Flow<List<Medication>> = MutableStateFlow(emptyList())

    override suspend fun get(medicationId: String): Medication? = null

    override suspend fun save(medication: Medication) = Unit

    override suspend fun archive(medicationId: String, reason: ArchiveReason): Boolean = false

    override suspend fun setStock(medicationId: String, stockLeft: BigDecimal?) = Unit

    /**
     * Лекарство, от которого экрану нужно только одно — что оно существует.
     *
     * Названий из жизни здесь нет намеренно: строка настроек показывает количество,
     * а не названия, и подставлять их значило бы проверять не то (НФТ-19).
     */
    private fun medication(id: String, profileId: String) = Medication(
        id = id,
        profileId = profileId,
        name = id,
        form = MedicationForm.TABLET,
        shape = null,
        doseAmount = BigDecimal.ONE,
        doseUnit = PIECE,
        strength = null,
        strengthUnit = null,
        foodRelation = FoodRelation.ANY,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = listOf(LocalTime.of(MORNING_HOUR, 0)),
        startDate = LocalDate.of(YEAR, MONTH, DAY),
        endDate = null,
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = null,
        archiveReason = null,
    )

    private companion object {
        const val PIECE = "piece"
        const val MORNING_HOUR = 9
        const val YEAR = 2026
        const val MONTH = 10
        const val DAY = 12
    }
}

internal class FakeReminderSettings(var values: ReminderSettingsValues = ReminderSettingsValues()) : ReminderSettings {

    override suspend fun read(): ReminderSettingsValues = values

    override suspend fun setPersistentEnabled(enabled: Boolean) {
        values = values.copy(persistentEnabled = enabled)
    }

    override suspend fun setSnoozeMinutes(minutes: Int) {
        values = values.copy(snoozeMinutes = minutes)
    }
}

/**
 * Состояние разрешений, которое меняется мимо приложения.
 *
 * Именно так оно и меняется в жизни: человек уходит в системные настройки и возвращается,
 * а приложение об этом никто не извещает (НФТ-52).
 */
internal class FakePermissions(
    var states: Map<ReminderPermission, PermissionState> = ReminderPermission.entries.associateWith {
        PermissionState.GRANTED
    },
) : PermissionStateReader {

    override fun state(permission: ReminderPermission): PermissionState = states[permission] ?: PermissionState.UNKNOWN
}

internal class FakeWatchLink(var link: WatchLinkState = WatchLinkState.NotConfigured) : WatchLinkStateReader {

    override suspend fun read(): WatchLinkState = link
}

internal object SettingsFixtures {

    const val SELF_ID = "self"
    const val FOREIGN_ID = "mother"

    /**
     * Имена здесь латиницей намеренно: строка настроек показывает **число** лекарств
     * и подпись «Мои лекарства» из ресурсов, а не имя владельца, и русское имя в коде
     * проверяло бы не то (НФТ-42).
     */
    fun self(name: String = "Owner") = Profile(
        id = SELF_ID,
        name = name,
        color = OWNER_COLOR,
        isSelf = true,
        // У владельца поле игнорируется (INV-2) — и в строке настроек не показывается.
        mirrorReminders = true,
    )

    fun foreign(name: String = "Mother", mirror: Boolean = true) = Profile(
        id = FOREIGN_ID,
        name = name,
        color = FOREIGN_COLOR,
        isSelf = false,
        mirrorReminders = mirror,
    )

    /** Цвета из закрытого набора дизайн-системы; числа здесь — только опора проверок. */
    private const val OWNER_COLOR = 0xFFB4512F.toInt()
    private const val FOREIGN_COLOR = 0xFF1F5F6B.toInt()
}
