package ru.stohastika.sklyanka.data.transfer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.transfer.AppStamp
import ru.stohastika.sklyanka.domain.transfer.TransferCodec
import ru.stohastika.sklyanka.domain.transfer.TransferCounts
import ru.stohastika.sklyanka.domain.transfer.TransferDecoded
import ru.stohastika.sklyanka.domain.transfer.TransferFormat
import ru.stohastika.sklyanka.domain.transfer.TransferNotice
import ru.stohastika.sklyanka.domain.transfer.TransferReject
import ru.stohastika.sklyanka.domain.transfer.TransferRejectReason
import ru.stohastika.sklyanka.domain.transfer.TransferSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Файл переноса: запись и разбор (ПЕР-5 … ПЕР-12, ПЕР-28 … ПЕР-30).
 *
 * Разбор идёт тремя шагами, и порядок — это и есть требование ОВ-65 «будущее изменение
 * формата отличимо от битого файла»:
 *
 * 1. **Опознание.** Читаются только `format` и `format_version`. Чужой опознаватель — файл
 *    не наш (ПЕР-28); версия выше поддерживаемой — файл из будущего, и знакомые разделы
 *    даже не просматриваются (ПЕР-29), потому что незнакомые поля могут нести смысл,
 *    потеря которого не видна глазом.
 * 2. **Миграция снизу**, если версия ниже текущей (ПЕР-30).
 * 3. **Чтение разделов.** Обязательные разделы проверяются по именам до разбора, чтобы
 *    отказ назвал раздел, а не «ошибку разбора на 412-м символе».
 *
 * Терпимость к незнакомым ключам включена намеренно: при **совпадающей** версии формата
 * незнакомый ключ означает не файл из будущего, а расширение, которым эта сборка
 * не пользуется (`TDD.md §4`). Пример из спецификации несёт `watch_reminder` у лекарства —
 * ровно такой ключ.
 *
 * Одно расхождение с `TDD.md §4`: там разбор описан потоковым — «ровно до момента, когда
 * опознаватель и версия известны». Здесь файл сначала разбирается в дерево целиком, и только
 * потом опознаётся. Потоковый предпроход означал бы второй разборщик JSON рядом с первым,
 * а два разборщика расходятся. Смысл требования ПЕР-29 при этом сохранён: **ни один знакомый
 * раздел не истолкован** до проверки версии — из дерева читаются только `format`
 * и `format_version`, а записи не превращаются в модели вовсе.
 */
@Singleton
class JsonTransferCodec @Inject constructor(private val time: TimeSource) : TransferCodec {

    private val json = Json {
        // Строгость по обязательным полям и терпимость к незнакомым ключам — два разных
        // свойства, и здесь они настроены раздельно.
        ignoreUnknownKeys = true
        // Записываются все поля, включая равные значению по умолчанию и пустые, — как
        // в примере `docs/spec/07-data-transfer.md` §2. Экономия на пропущенных полях
        // здесь вредна дважды: файл читает человек (это его резервная копия), и «поля нет»
        // против «поле пусто» — ровно та разница, которая через версию формата
        // становится неразрешимой.
        encodeDefaults = true
        prettyPrint = true
    }

    override fun encode(snapshot: TransferSnapshot): String {
        val mapping = TransferMapping(time.zone())
        val dto = TransferFileDto(
            format = TransferFormat.MARKER,
            formatVersion = snapshot.formatVersion,
            exportedAt = mapping.momentText(snapshot.exportedAt),
            app = AppDto(
                versionName = snapshot.app.versionName,
                versionCode = snapshot.app.versionCode,
                platform = snapshot.app.platform,
            ),
            counts = CountsDto(
                profiles = snapshot.counts.profiles,
                medications = snapshot.counts.medications,
                archived = snapshot.counts.archived,
                intakes = snapshot.counts.intakes,
            ),
            profiles = snapshot.profiles.map(mapping::profileDto),
            medications = snapshot.medications.map(mapping::medicationDto),
            intakes = snapshot.intakes.map(mapping::intakeDto),
            settings = mapping.settingsDto(snapshot.settings),
        )
        return json.encodeToString(TransferFileDto.serializer(), dto)
    }

    @Suppress("ReturnCount")
    override fun decode(text: String): TransferDecoded {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return rejected(TransferRejectReason.NOT_JSON, "root")

        identify(root)?.let { return it }

        val version = requireNotNull(root[FORMAT_VERSION]?.jsonPrimitive?.intOrNull)
        val migrated = if (version == TransferFormat.CURRENT_VERSION) {
            root
        } else {
            TransferFormatMigrations.migrate(root, version, TransferFormat.CURRENT_VERSION)
                ?: return rejected(TransferRejectReason.INVALID_VERSION, "v$version")
        }

        REQUIRED_SECTIONS.firstOrNull { it !in migrated }
            ?.let { return rejected(TransferRejectReason.MISSING_SECTION, it) }

        return read(migrated, version)
    }

    /**
     * Опознание файла: только опознаватель и версия (ПЕР-6, ПЕР-7, ПЕР-29).
     *
     * @return отказ, если файл не наш или из будущего; null, если читать можно дальше.
     */
    @Suppress("ReturnCount")
    private fun identify(root: JsonObject): TransferDecoded.Rejected? {
        val marker = root[FORMAT]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            ?: return rejected(TransferRejectReason.MISSING_MARKER, FORMAT)
        if (marker != TransferFormat.MARKER) return rejected(TransferRejectReason.FOREIGN_MARKER, FORMAT)

        val version = root[FORMAT_VERSION]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
            ?: return rejected(TransferRejectReason.INVALID_VERSION, FORMAT_VERSION)
        return when {
            version < TransferFormat.MINIMUM_VERSION ->
                rejected(TransferRejectReason.INVALID_VERSION, "v$version")

            version > TransferFormat.CURRENT_VERSION ->
                rejected(TransferRejectReason.FUTURE_FORMAT, "v$version")

            else -> null
        }
    }

    private fun read(document: JsonObject, fileVersion: Int): TransferDecoded {
        val mapping = TransferMapping(time.zone())
        val notices = NoticeSink()
        if (fileVersion < TransferFormat.CURRENT_VERSION) {
            notices.add(TransferNotice.OldFormat(fileVersion))
        }

        return try {
            val dto = json.decodeFromJsonElement(TransferFileDto.serializer(), document)
            val profiles = dto.profiles.map(mapping::profile)
            val medications = dto.medications.map { mapping.medication(it, notices) }
            val intakes = dto.intakes.map { mapping.intake(it, notices) }
            TransferDecoded.Loaded(
                snapshot = TransferSnapshot(
                    formatVersion = TransferFormat.CURRENT_VERSION,
                    exportedAt = TransferValues.moment(dto.exportedAt, EXPORTED_AT),
                    app = AppStamp(
                        versionName = dto.app.versionName,
                        versionCode = dto.app.versionCode,
                        platform = dto.app.platform,
                    ),
                    // Сводка пересчитывается по содержимому: числа, показанные человеку,
                    // обязаны совпадать с тем, что применится (критерий 3).
                    counts = TransferCounts.of(profiles, medications, intakes),
                    profiles = profiles,
                    medications = medications,
                    intakes = intakes,
                    settings = mapping.settings(dto.settings, notices),
                ),
                notices = notices.notices(),
            )
        } catch (failure: DecodeFailure) {
            TransferDecoded.Rejected(failure.reject)
        } catch (failure: IllegalArgumentException) {
            // Обязательного поля нет или его тип не тот: kotlinx сообщает об этом
            // исключением. Наружу уходит код причины, а не текст исключения: в текст
            // попадает содержимое файла, то есть медицинские данные (НФТ-19).
            TransferDecoded.Rejected(TransferReject(TransferRejectReason.INVALID_VALUE, sectionOf(failure)))
        }
    }

    /**
     * Раздел, на котором споткнулся разбор.
     *
     * Из сообщения библиотеки берётся только имя поля, и только если оно принадлежит
     * известному списку разделов: сообщение целиком содержит фрагмент файла.
     */
    private fun sectionOf(failure: IllegalArgumentException): String {
        val message = failure.message.orEmpty()
        return REQUIRED_SECTIONS.firstOrNull { message.contains(it) } ?: UNKNOWN_SECTION
    }

    private fun rejected(reason: TransferRejectReason, detail: String) =
        TransferDecoded.Rejected(TransferReject(reason, detail))

    private companion object {
        const val FORMAT = "format"
        const val FORMAT_VERSION = "format_version"
        const val EXPORTED_AT = "exported_at"
        const val UNKNOWN_SECTION = "unknown"

        /** Обязательные разделы (ПЕР-5). Порядок — порядок проверки и порядок в отказе. */
        val REQUIRED_SECTIONS = listOf(
            EXPORTED_AT,
            "app",
            "counts",
            "profiles",
            "medications",
            "intakes",
            "settings",
        )
    }
}
