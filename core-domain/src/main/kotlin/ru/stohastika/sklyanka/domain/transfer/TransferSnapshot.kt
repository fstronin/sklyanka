package ru.stohastika.sklyanka.domain.transfer

import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.Profile
import java.time.Instant
import java.time.LocalDate

/** Метаданные выгрузки: какой сборкой и на какой платформе снят снимок (ПЕР-5). */
data class AppStamp(val versionName: String, val versionCode: Int, val platform: String = TransferFormat.PLATFORM)

/**
 * Сводка файла (ПЕР-5): то, что человек видит до применения (ПЕР-15 п. 3).
 *
 * Считается из самого содержимого, а не хранится отдельно набранным: иначе экран
 * покажет одно, а применится другое, и критерий 3 приёмки перестанет быть проверяемым.
 */
data class TransferCounts(
    val profiles: Int,
    val medications: Int,
    val archived: Int,
    val intakes: Int,
    /** Начало истории: дата самого раннего приёма. Пустая история даёт null. */
    val historyFrom: LocalDate? = null,
) {
    companion object {
        fun of(profiles: List<Profile>, medications: List<Medication>, intakes: List<Intake>): TransferCounts =
            TransferCounts(
                profiles = profiles.size,
                // Активные и архивные считаются раздельно: в чипах экрана это два разных числа,
                // а в файле — одна коллекция с признаком архива внутри записи (ПЕР-10).
                medications = medications.count { !it.isArchived },
                archived = medications.count { it.isArchived },
                intakes = intakes.size,
                historyFrom = intakes.minOfOrNull { it.scheduledAt }?.toLocalDate(),
            )
    }
}

/**
 * Снимок всего состояния приложения (`TDD.md §5`).
 *
 * Отдельная модель рядом с сущностями хранилища, а не они сами: сущность привязана
 * к версии схемы базы, снимок — к версии формата файла, и склеивание двух версий
 * превратило бы любую миграцию Room в несовместимое изменение файла (МГ-4).
 *
 * Доменные типы внутри взяты как есть: они и есть общий язык приложения, а перевод
 * в них и обратно делает кодек. Архив отдельным разделом не лежит — это состояние
 * лекарства (ПЕР-10), расписание лежит внутри лекарства (ПЕР-11).
 */
data class TransferSnapshot(
    val formatVersion: Int,
    val exportedAt: Instant,
    val app: AppStamp,
    val counts: TransferCounts,
    val profiles: List<Profile>,
    val medications: List<Medication>,
    val intakes: List<Intake>,
    val settings: TransferSettings,
) {
    /** Свой профиль файла. После проверки снимка он существует и ровно один (ИНВ-1). */
    val selfProfile: Profile? get() = profiles.firstOrNull { it.isSelf }

    /** Архивированные курсы файла: по ним после загрузки нужен отдельный обход планировщика. */
    val archivedMedicationIds: List<String> get() = medications.filter { it.isArchived }.map { it.id }

    companion object {
        /**
         * Снимок из собранного состояния: сводка считается здесь, а не приходит снаружи.
         *
         * Это единственный способ сдержать критерий 3 приёмки — «чипы состава совпадают
         * с содержимым файла»: экран и файл берут числа из одного места.
         */
        @Suppress("LongParameterList")
        fun of(
            exportedAt: Instant,
            app: AppStamp,
            profiles: List<Profile>,
            medications: List<Medication>,
            intakes: List<Intake>,
            settings: TransferSettings,
            formatVersion: Int = TransferFormat.CURRENT_VERSION,
        ): TransferSnapshot = TransferSnapshot(
            formatVersion = formatVersion,
            exportedAt = exportedAt,
            app = app,
            counts = TransferCounts.of(profiles, medications, intakes),
            profiles = profiles,
            medications = medications,
            intakes = intakes,
            settings = settings,
        )
    }
}
