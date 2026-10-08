package ru.stohastika.sklyanka.data.transfer

import kotlinx.serialization.json.JsonPrimitive
import ru.stohastika.sklyanka.domain.medication.doseUnit
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
import ru.stohastika.sklyanka.domain.transfer.QuietHours
import ru.stohastika.sklyanka.domain.transfer.ReminderSound
import ru.stohastika.sklyanka.domain.transfer.TransferNotice
import ru.stohastika.sklyanka.domain.transfer.TransferSettings
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/** Сбор замечаний по ходу разбора (МГ-6, ПЕР-12): загрузку они не срывают. */
internal class NoticeSink {
    private val collected = mutableListOf<TransferNotice>()

    fun add(notice: TransferNotice) {
        collected += notice
    }

    fun notices(): List<TransferNotice> = collected.toList()
}

/**
 * Перевод проволочной формы файла в доменную и обратно.
 *
 * Незнакомое значение перечисления здесь никогда не роняет загрузку (МГ-6): оно заменяется
 * значением по умолчанию, а факт замены уходит в отчёт человеку. Правило выбора замены
 * одно на все поля: замена не имеет права утверждать больше, чем сказано в файле, —
 * поэтому непонятая отметка становится «пропущено без отметки», а не «принято».
 */
internal class TransferMapping(private val zone: ZoneId) {

    // --- чтение ---

    fun profile(dto: ProfileDto): Profile = Profile(
        id = dto.id,
        name = dto.name,
        color = TransferValues.color(dto.color),
        isSelf = dto.isSelf,
        mirrorReminders = dto.mirrorReminders,
    )

    fun medication(dto: MedicationDto, notices: NoticeSink): Medication {
        val form = knownOr(dto.form, MedicationForm.TABLET, FIELD_FORM, dto.id, notices)
        return Medication(
            id = dto.id,
            profileId = dto.profileId,
            name = dto.name,
            form = form,
            shape = shape(dto, form, notices),
            doseAmount = TransferValues.requireDecimal(dto.doseAmount, "medications.dose_amount"),
            doseUnit = dto.doseUnit.ifBlank { form.doseUnit().code },
            strength = TransferValues.decimal(dto.strength, "medications.strength"),
            strengthUnit = dto.strengthUnit?.trim()?.ifBlank { null },
            foodRelation = knownOr(dto.foodRelation, FoodRelation.ANY, FIELD_FOOD, dto.id, notices),
            scheduleMode = knownOr(dto.scheduleMode, ScheduleMode.SCHEDULED, FIELD_MODE, dto.id, notices),
            maxPerDay = dto.maxPerDay,
            times = dto.times.map { TransferValues.time(it, "medications.times") },
            startDate = TransferValues.date(dto.startDate, "medications.start_date"),
            endDate = dto.endDate?.let { TransferValues.date(it, "medications.end_date") },
            stockLeft = TransferValues.decimal(dto.stockLeft, "medications.stock_left"),
            packageSize = TransferValues.decimal(dto.packageSize, "medications.package_size"),
            stockThresholdDays = dto.stockThresholdDays,
            archivedAt = dto.archivedAt?.let { TransferValues.moment(it, "medications.archived_at") },
            archiveReason = archiveReason(dto, notices),
            deletedAt = dto.deletedAt?.let { TransferValues.moment(it, "medications.deleted_at") },
        )
    }

    fun intake(dto: IntakeDto, notices: NoticeSink): Intake {
        val actedAt = dto.actedAt?.let { TransferValues.moment(it, "intakes.acted_at") }
        val status = IntakeStatus.entries.firstOrNull { it.name == dto.status }
            ?: unknownStatus(actedAt, dto.id, notices)
        return Intake(
            id = dto.id,
            medicationId = dto.medicationId,
            scheduledAt = TransferValues.localDateTime(dto.scheduledAt, "intakes.scheduled_at"),
            status = status,
            actedAt = actedAt,
            source = source(dto.source, status, dto.id, notices),
            actorProfileId = dto.actorProfileId,
        )
    }

    fun settings(dto: SettingsDto, notices: NoticeSink): TransferSettings {
        val sound = ReminderSound.ofCode(dto.reminders.sound)
        // Ссылка на звук чужого устройства не разрешилась — загрузка продолжается,
        // а человек узнаёт об этом из отчёта (ПЕР-12).
        if (dto.reminders.sound != null && sound == null) notices.add(TransferNotice.SoundNotFound)
        val quiet = dto.reminders.quietHours
        return TransferSettings(
            reminderSound = sound ?: ReminderSound.DEFAULT,
            persistentReminder = dto.reminders.persistentEnabled,
            snoozeMinutes = dto.reminders.snoozeMinutes ?: TransferSettings.DEFAULT_SNOOZE_MINUTES,
            quietHours = if (quiet.enabled && quiet.from != null && quiet.to != null) {
                QuietHours(
                    from = TransferValues.time(quiet.from, "settings.quiet_hours.from"),
                    to = TransferValues.time(quiet.to, "settings.quiet_hours.to"),
                )
            } else {
                null
            },
        )
    }

    // --- запись ---

    fun profileDto(profile: Profile) = ProfileDto(
        id = profile.id,
        name = profile.name,
        color = JsonPrimitive(TransferValues.colorText(profile.color)),
        isSelf = profile.isSelf,
        mirrorReminders = profile.mirrorReminders,
    )

    fun medicationDto(medication: Medication) = MedicationDto(
        id = medication.id,
        profileId = medication.profileId,
        name = medication.name,
        form = medication.form.name,
        shape = medication.shape?.name,
        doseAmount = TransferValues.number(medication.doseAmount),
        doseUnit = medication.doseUnit,
        strength = TransferValues.number(medication.strength),
        strengthUnit = medication.strengthUnit,
        foodRelation = medication.foodRelation.name,
        scheduleMode = medication.scheduleMode.name,
        maxPerDay = medication.maxPerDay,
        times = medication.times.map { it.format(TransferValues.TIME) },
        startDate = medication.startDate.format(TransferValues.DATE),
        endDate = medication.endDate?.format(TransferValues.DATE),
        stockLeft = TransferValues.number(medication.stockLeft),
        packageSize = TransferValues.number(medication.packageSize),
        stockThresholdDays = medication.stockThresholdDays,
        archivedAt = medication.archivedAt?.let(::momentText),
        archiveReason = medication.archiveReason?.name,
        deletedAt = medication.deletedAt?.let(::momentText),
    )

    fun intakeDto(intake: Intake) = IntakeDto(
        id = intake.id,
        medicationId = intake.medicationId,
        // Локальное время плюс смещение источника: читатель возьмёт локальную часть,
        // а смещение остаётся, чтобы файл читался человеком и другими инструментами (ПЕР-8).
        scheduledAt = intake.scheduledAt.atZone(zone).toOffsetDateTime().format(TransferValues.OFFSET),
        status = intake.status.name,
        actedAt = intake.actedAt?.let(::momentText),
        source = intake.source?.name,
        actorProfileId = intake.actorProfileId,
    )

    fun settingsDto(settings: TransferSettings) = SettingsDto(
        reminders = RemindersDto(
            sound = settings.reminderSound.code,
            persistentEnabled = settings.persistentReminder,
            snoozeMinutes = settings.snoozeMinutes,
            quietHours = QuietHoursDto(
                enabled = settings.quietHours != null,
                from = settings.quietHours?.from?.format(TransferValues.TIME),
                to = settings.quietHours?.to?.format(TransferValues.TIME),
            ),
        ),
    )

    fun momentText(value: Instant): String = OffsetDateTime.ofInstant(value, zone).format(TransferValues.OFFSET)

    // --- подстановки по умолчанию (МГ-6) ---

    private inline fun <reified T : Enum<T>> knownOr(
        raw: String,
        fallback: T,
        fieldName: String,
        medicationId: String,
        notices: NoticeSink,
    ): T = enumValues<T>().firstOrNull { it.name == raw }
        ?: fallback.also { notices.add(TransferNotice.UnknownEnum(fieldName, medicationId)) }

    /** Вид таблетки неприменим к спрею и сиропу (ИНВ-16): лишнее значение снимается, а не роняет файл. */
    private fun shape(dto: MedicationDto, form: MedicationForm, notices: NoticeSink): TabletShape? {
        val raw = dto.shape ?: return null
        val known = TabletShape.entries.firstOrNull { it.name == raw }
        if (known == null) notices.add(TransferNotice.UnknownEnum(FIELD_SHAPE, dto.id))
        return known?.takeIf { form.allowsShape }
    }

    /** Момент и причина архивации заполняются вместе (ИНВ-9). */
    private fun archiveReason(dto: MedicationDto, notices: NoticeSink): ArchiveReason? {
        if (dto.archivedAt == null) return null
        val raw = dto.archiveReason
        val known = raw?.let { value -> ArchiveReason.entries.firstOrNull { it.name == value } }
        if (known == null) notices.add(TransferNotice.UnknownEnum(FIELD_ARCHIVE_REASON, dto.id))
        return known ?: ArchiveReason.MANUAL
    }

    /**
     * Незнакомый статус приёма.
     *
     * Отмеченная запись с непонятым исходом становится «пропущено без отметки», а не
     * «принято»: приложение не имеет права утверждать, что лекарство было выпито.
     */
    private fun unknownStatus(actedAt: Instant?, intakeId: String, notices: NoticeSink): IntakeStatus {
        notices.add(TransferNotice.UnknownEnum(FIELD_STATUS, intakeId))
        return if (actedAt == null) IntakeStatus.PENDING else IntakeStatus.MISSED
    }

    /** Источник отметки согласован со статусом (ИНВ-10): у отмеченной записи он обязателен. */
    private fun source(raw: String?, status: IntakeStatus, intakeId: String, notices: NoticeSink): IntakeSource? {
        val known = raw?.let { value -> IntakeSource.entries.firstOrNull { it.name == value } }
        if (raw != null && known == null) notices.add(TransferNotice.UnknownEnum(FIELD_SOURCE, intakeId))
        return when {
            status == IntakeStatus.PENDING -> null
            known != null -> known
            else -> IntakeSource.PHONE
        }
    }

    private companion object {
        const val FIELD_FORM = "form"
        const val FIELD_SHAPE = "shape"
        const val FIELD_FOOD = "food_relation"
        const val FIELD_MODE = "schedule_mode"
        const val FIELD_ARCHIVE_REASON = "archive_reason"
        const val FIELD_STATUS = "status"
        const val FIELD_SOURCE = "source"
    }
}
