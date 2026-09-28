package ru.heuri.sklyanka.acceptance.world

import ru.heuri.sklyanka.domain.model.ArchiveReason
import ru.heuri.sklyanka.domain.model.FoodRelation
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.model.Profile
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.model.TabletShape
import ru.heuri.sklyanka.domain.transfer.TransferSettings
import ru.heuri.sklyanka.domain.transfer.TransferSnapshot
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Сборка содержимого файла переноса (SKL-19): записи стороны файла с собственными
 * идентификаторами.
 *
 * Снимок собирается здесь, а провод — нет: настоящий кодек приезжает в прогон
 * (`ScenarioTransfer.codec`), и файл, который читает экран, получается из этого снимка
 * настоящей сериализацией. Записи файла намеренно отделены от записей устройства:
 * сценарии про идентификаторы (ПЕР-9) и совпадения (ПЕР-20) живут ровно на различии
 * двух наборов.
 *
 * Ключ сценария («Магний B6», «Мама») — язык Gherkin; в модель уходят безымянные записи
 * (НФТ-19), а соответствие хранит [ScenarioTransfer.fileMedicationNames].
 */
class TransferFileBuilder(private val clock: ScenarioClock) {

    private val fileProfiles = mutableListOf<Profile>()
    private val fileMedications = mutableListOf<Medication>()
    private val fileIntakes = mutableListOf<Intake>()

    private val profilesByKey = linkedMapOf<String, Profile>()
    private val medicationsByKey = linkedMapOf<String, Medication>()

    private var sequence = 0

    /** Профиль файла: свой — по признаку, чужой — по имени, с которым потом сопоставится (ПЕР-19). */
    fun profile(key: String, isSelf: Boolean = false, mirrorReminders: Boolean = false): TransferFileBuilder {
        val profile = Profile(
            id = "file-profile-${++sequence}",
            name = key,
            color = 0,
            isSelf = isSelf,
            mirrorReminders = mirrorReminders,
        )
        profilesByKey[key] = profile
        fileProfiles += profile
        return this
    }

    fun profilesByKey(): Map<String, Profile> = profilesByKey.toMap()

    fun medicationsByKey(): Map<String, Medication> = medicationsByKey.toMap()

    @Suppress("LongParameterList")
    fun medication(
        key: String,
        profileKey: String,
        name: String,
        form: MedicationForm = MedicationForm.TABLET,
        shape: TabletShape? = null,
        doseAmount: BigDecimal = BigDecimal.ONE,
        doseUnit: String = UNIT_TABLET,
        strength: BigDecimal? = null,
        strengthUnit: String? = null,
        foodRelation: FoodRelation = FoodRelation.ANY,
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay: Int? = null,
        times: List<LocalTime> = emptyList(),
        startDate: LocalDate = clock.today(),
        endDate: LocalDate? = null,
        stockLeft: BigDecimal? = null,
        stockThresholdDays: Int? = null,
        archived: Boolean = false,
    ): TransferFileBuilder {
        val profile = requireNotNull(profilesByKey[profileKey]) { "unknown file profile: $profileKey" }
        val medication = Medication(
            id = "file-med-${++sequence}",
            profileId = profile.id,
            name = name,
            form = form,
            shape = shape,
            doseAmount = doseAmount,
            doseUnit = doseUnit,
            strength = strength,
            strengthUnit = strengthUnit,
            foodRelation = foodRelation,
            scheduleMode = mode,
            maxPerDay = maxPerDay,
            times = times,
            startDate = startDate,
            endDate = endDate,
            stockLeft = stockLeft,
            stockThresholdDays = stockThresholdDays,
            archivedAt = if (archived) clock.now() else null,
            archiveReason = if (archived) ArchiveReason.COURSE_FINISHED else null,
        )
        medicationsByKey[key] = medication
        fileMedications += medication
        return this
    }

    /** Копия записи устройства: тем же названием и дозировкой, но с идентификатором файла. */
    @Suppress("LongParameterList")
    fun medicationLike(
        key: String,
        profileKey: String,
        device: Medication,
        times: List<LocalTime> = device.times,
        stockLeft: BigDecimal? = device.stockLeft,
        id: String? = null,
        name: String? = null,
    ): TransferFileBuilder {
        val profile = requireNotNull(profilesByKey[profileKey]) { "unknown file profile: $profileKey" }
        val medication = device.copy(
            id = id ?: "file-med-${++sequence}",
            profileId = profile.id,
            name = name ?: device.name,
            times = times,
            stockLeft = stockLeft,
        )
        medicationsByKey[key] = medication
        fileMedications += medication
        return this
    }

    fun intake(
        medicationKey: String,
        at: LocalDateTime,
        status: IntakeStatus,
        actedAt: Instant? = null,
        source: IntakeSource? = null,
        actorProfileId: String? = null,
    ): TransferFileBuilder {
        val medication = requireNotNull(medicationsByKey[medicationKey]) {
            "unknown file medication: $medicationKey"
        }
        fileIntakes += Intake(
            id = "file-intake-${++sequence}",
            medicationId = medication.id,
            scheduledAt = at,
            status = status,
            actedAt = actedAt,
            source = source,
            actorProfileId = actorProfileId,
        )
        return this
    }

    fun build(exportedAt: Instant = clock.now()): TransferSnapshot = TransferSnapshot.of(
        exportedAt = exportedAt,
        app = ScenarioTransfer.APP_STAMP,
        profiles = fileProfiles,
        medications = fileMedications,
        intakes = fileIntakes,
        settings = TransferSettings.DEFAULT,
    )

    private companion object {
        const val UNIT_TABLET = "tablet"
    }
}
