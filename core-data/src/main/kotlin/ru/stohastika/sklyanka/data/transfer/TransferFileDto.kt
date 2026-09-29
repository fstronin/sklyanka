package ru.stohastika.sklyanka.data.transfer

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Проволочная форма файла переноса (`docs/spec/07-data-transfer.md` §2, ОВ-65).
 *
 * Отдельные типы рядом с доменными моделями — не церемония. Здесь всё, что может прийти
 * из файла, описано **терпимо**: перечисления строками, числа произвольным JSON-значением,
 * необязательные поля со значениями по умолчанию. Это и есть механизм МГ-6 и ПЕР-30:
 * незнакомое значение перечисления доходит до слоя, который умеет подставить значение
 * по умолчанию, а не роняет разбор; поле, которого в старом формате не было, получает
 * значение по умолчанию само.
 *
 * Дробные величины объявлены как [JsonElement], а не числом: доза и остаток в модели —
 * десятичные, а `Double` уводит остаток в «-0,0000001» после сотни списаний по половине
 * таблетки. Разбор идёт через текст значения, запись — числом.
 */
@Serializable
internal data class TransferFileDto(
    val format: String,
    @SerialName("format_version") val formatVersion: Int,
    @SerialName("exported_at") val exportedAt: String,
    val app: AppDto,
    val counts: CountsDto,
    val profiles: List<ProfileDto>,
    val medications: List<MedicationDto>,
    val intakes: List<IntakeDto>,
    val settings: SettingsDto,
)

@Serializable
internal data class AppDto(
    @SerialName("version_name") val versionName: String = "",
    @SerialName("version_code") val versionCode: Int = 0,
    val platform: String = "",
)

/**
 * Сводка файла.
 *
 * Раздел обязателен (ПЕР-5), но его числа при загрузке пересчитываются по содержимому:
 * сводка, разошедшаяся с содержимым, показала бы человеку не то, что применится,
 * а критерий 3 приёмки требует ровно совпадения.
 */
@Serializable
internal data class CountsDto(
    val profiles: Int = 0,
    val medications: Int = 0,
    val archived: Int = 0,
    val intakes: Int = 0,
)

@Serializable
internal data class ProfileDto(
    val id: String,
    val name: String,
    /** `#AARRGGBB` либо `#RRGGBB`, либо целое: цвет пришёл из палитры, а не из ввода человека. */
    val color: JsonElement? = null,
    @SerialName("is_self") val isSelf: Boolean = false,
    @SerialName("mirror_reminders") val mirrorReminders: Boolean = false,
)

@Suppress("LongParameterList")
@Serializable
internal data class MedicationDto(
    val id: String,
    @SerialName("profile_id") val profileId: String,
    val name: String,
    val form: String,
    val shape: String? = null,
    @SerialName("dose_amount") val doseAmount: JsonElement? = null,
    @SerialName("dose_unit") val doseUnit: String = "",
    val strength: JsonElement? = null,
    @SerialName("strength_unit") val strengthUnit: String? = null,
    @SerialName("food_relation") val foodRelation: String = "",
    @SerialName("schedule_mode") val scheduleMode: String = "",
    @SerialName("max_per_day") val maxPerDay: Int? = null,
    /** Расписание внутри лекарства (ПЕР-11): `HH:mm`, пустой список у курса по потребности. */
    val times: List<String> = emptyList(),
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("stock_left") val stockLeft: JsonElement? = null,
    /**
     * Размер упаковки (ОВ-45); `null` — размер не задан.
     *
     * Единственное поле файла, которое при пустом значении **опускается**, а не пишется
     * как `null` (остальные пишутся: `encodeDefaults = true`). Так требует решение
     * по ОВ-45: выгрузка пишет размер, если он задан, и опускает, если нет, а файл без
     * поля читается как «размер не задан» — ровно состояние лекарства, заведённого
     * до появления размера. `@EncodeDefault(NEVER)` делает это точечно, не меняя
     * поведение остальных полей.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("package_size") val packageSize: JsonElement? = null,
    @SerialName("stock_threshold_days") val stockThresholdDays: Int? = null,
    /** Архив — состояние записи, а не отдельный раздел (ПЕР-10). */
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("archive_reason") val archiveReason: String? = null,
)

@Serializable
internal data class IntakeDto(
    val id: String,
    @SerialName("medication_id") val medicationId: String,
    /** Локальные дата и время со смещением источника; смещение при чтении отбрасывается (ПЕР-8). */
    @SerialName("scheduled_at") val scheduledAt: String,
    val status: String,
    @SerialName("acted_at") val actedAt: String? = null,
    val source: String? = null,
    @SerialName("actor_profile_id") val actorProfileId: String? = null,
)

@Serializable
internal data class SettingsDto(val reminders: RemindersDto = RemindersDto(), val watch: WatchDto = WatchDto())

@Serializable
internal data class RemindersDto(
    /** Идентификатор встроенного сигнала, а не системная ссылка (ПЕР-12). */
    val sound: String? = null,
    @SerialName("persistent_enabled") val persistentEnabled: Boolean = false,
    @SerialName("snooze_minutes") val snoozeMinutes: Int? = null,
    @SerialName("quiet_hours") val quietHours: QuietHoursDto = QuietHoursDto(),
)

@Serializable
internal data class QuietHoursDto(val enabled: Boolean = false, val from: String? = null, val to: String? = null)

@Serializable
internal data class WatchDto(
    /** Желание человека пользоваться часами; выданные права Wear Engine не переносятся (ПЕР-26). */
    val enabled: Boolean = false,
    @SerialName("snooze_button") val snoozeButton: Boolean = false,
)
