package ru.stohastika.sklyanka.domain.transfer

import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.Medication

/**
 * Построение плана применения — вся логика загрузки, кроме записи (`TDD.md §5`).
 *
 * Здесь нет ни хранилища, ни файла, ни часов: на входе снимок устройства, снимок файла
 * и решения человека, на выходе готовый план. Это и есть причина, по которой атомарность
 * (ПЕР-27) не стоит ничего: транзакции остаётся исполнить список операций.
 *
 * Два режима различаются ровно двумя вещами — стиранием и судьбой идентификаторов
 * (ПЕР-9, ПЕР-16), поэтому живут в одном классе: разнесённые по двум, они разойдутся
 * в подсчёте отчёта.
 */
class BuildTransferPlan(
    private val ids: TransferIdSource,
    private val matchProfiles: MatchProfiles = MatchProfiles(ids),
    private val findMatches: FindMedicationMatches = FindMedicationMatches(),
    private val mergeHistory: MergeIntakeHistory = MergeIntakeHistory(),
) {

    /**
     * Режим «Добавить к текущим» (ПЕР-15).
     *
     * Идентификаторы файла перевыдаются: на приёмнике они не уникальны (ПЕР-9), и запись
     * устройства, случайно совпавшая идентификатором с записью файла, не имеет права
     * оказаться переписанной.
     */
    fun merge(
        device: DeviceState,
        snapshot: TransferSnapshot,
        decisions: Map<String, MatchDecision>,
        notices: List<TransferNotice> = emptyList(),
    ): TransferPlan {
        val profiles = matchProfiles(device.profiles, snapshot.profiles)
        // Мягко удалённые (ПР-14) в поиске совпадений не участвуют: решение «взять из файла»
        // молча удаляло бы запись устройства, а «оставить моё» — молча воскрешало удалённую.
        // Записи файла возвращаются в unmatched и добавляются в своём состоянии — удалёнными
        // и скрытыми; удалённые записи устройства не переписываются ни одним решением.
        val matching = findMatches(
            device.medications.filterNot { it.isDeleted },
            snapshot.medications.filterNot { it.isDeleted },
            profiles,
        ).let { found ->
            found.copy(unmatched = found.unmatched + snapshot.medications.filter { it.isDeleted })
        }
        val routing = routeMedications(matching, decisions, profiles)
        val incoming = remapIntakes(snapshot.intakes, routing.medicationIdByFileId, profiles)
        val history = mergeHistory(device.intakes, incoming.intakes)

        val decisionCounts = matching.matches
            .groupingBy { decisions[it.file.id] ?: MatchDecision.DEFAULT }
            .eachCount()
        val reportNotices = notices + listOfNotNull(
            history.skipped.takeIf { it > 0 }?.let(TransferNotice::DuplicateIntakes),
        )

        return TransferPlan(
            mode = TransferMode.MERGE,
            erase = false,
            profilesToInsert = profiles.added,
            medicationsToInsert = routing.toInsert,
            medicationsToUpdate = routing.toUpdate,
            intakesToInsert = history.inserted,
            intakesToUpdate = history.updated,
            // Слияние не переключает профиль: человек остаётся там, где был (`TDD.md §6`).
            activeProfileId = null,
            archivedMedicationIds = (routing.toInsert + routing.toUpdate).filter { it.isArchived }.map { it.id },
            report = TransferReport(
                mode = TransferMode.MERGE,
                profilesAdded = profiles.added.size,
                medicationsAdded = routing.toInsert.size,
                medicationsUpdated = routing.toUpdate.size,
                intakesAdded = history.inserted.size,
                intakesUpdated = history.updated.size,
                intakesSkipped = history.skipped + incoming.dropped,
                decisions = decisionCounts,
                notices = reportNotices,
            ),
        )
    }

    /**
     * Режим «Заменить всё» (ПЕР-16).
     *
     * Идентификаторы переносятся как есть: сливать не с чем, а сохранение идентификаторов
     * делает цикл «выгрузка → чистая установка → загрузка → выгрузка» точным, чего требует
     * НФТ-12 и критерий 11 приёмки.
     */
    fun replaceAll(snapshot: TransferSnapshot, notices: List<TransferNotice> = emptyList()): TransferPlan =
        TransferPlan(
            mode = TransferMode.REPLACE_ALL,
            erase = true,
            profilesToInsert = snapshot.profiles,
            medicationsToInsert = snapshot.medications,
            medicationsToUpdate = emptyList(),
            intakesToInsert = snapshot.intakes,
            intakesToUpdate = emptyList(),
            // Активным становится свой профиль из файла (ПЕР-16 п. 5, ПРФ-25).
            activeProfileId = snapshot.selfProfile?.id,
            archivedMedicationIds = snapshot.archivedMedicationIds,
            report = TransferReport(
                mode = TransferMode.REPLACE_ALL,
                profilesAdded = snapshot.profiles.size,
                medicationsAdded = snapshot.medications.size,
                medicationsUpdated = 0,
                intakesAdded = snapshot.intakes.size,
                intakesUpdated = 0,
                intakesSkipped = 0,
                decisions = emptyMap(),
                notices = notices,
            ),
        )

    /**
     * Куда уезжает каждая запись файла.
     *
     * @property medicationIdByFileId лекарство файла → лекарство устройства; отсутствие ключа
     *   означает решение «не загружать», то есть историю этой записи нужно отбросить
     */
    private data class MedicationRouting(
        val medicationIdByFileId: Map<String, String>,
        val toInsert: List<Medication>,
        val toUpdate: List<Medication>,
    )

    private fun routeMedications(
        matching: MedicationMatching,
        decisions: Map<String, MatchDecision>,
        profiles: ProfileMatching,
    ): MedicationRouting {
        val route = mutableMapOf<String, String>()
        val toInsert = mutableListOf<Medication>()
        val toUpdate = mutableListOf<Medication>()

        matching.unmatched.forEach { file ->
            val profileId = profiles.deviceProfileId(file.profileId) ?: return@forEach
            val fresh = file.copy(id = ids.newId(), profileId = profileId)
            route[file.id] = fresh.id
            toInsert += fresh
        }

        matching.matches.forEach { match ->
            when (decisions[match.file.id] ?: MatchDecision.DEFAULT) {
                MatchDecision.KEEP_MINE -> route[match.file.id] = match.device.id

                MatchDecision.TAKE_FROM_FILE -> {
                    // Идентификатор и профиль остаются устройства: профиль лекарства
                    // неизменяем после создания (ИНВ-3), а смена идентификатора оторвала бы
                    // от записи её собственную историю.
                    toUpdate += match.file.copy(id = match.device.id, profileId = match.device.profileId)
                    route[match.file.id] = match.device.id
                }

                MatchDecision.ADD_SEPARATELY -> {
                    val fresh = match.file.copy(id = ids.newId(), profileId = match.device.profileId)
                    route[match.file.id] = fresh.id
                    toInsert += fresh
                }

                // Ключа в карте не появляется — и вся история этой записи отбрасывается.
                MatchDecision.DO_NOT_LOAD -> Unit
            }
        }
        return MedicationRouting(route, toInsert, toUpdate)
    }

    /** @property dropped сколько приёмов отброшено решением «не загружать» (ПЕР-21). */
    private data class IncomingIntakes(val intakes: List<Intake>, val dropped: Int)

    /**
     * Перенаправление истории файла на записи устройства.
     *
     * Отдельного вопроса «что делать с приёмами» человеку не задаётся: профиль приёма
     * определяется через лекарство, поэтому решение о лекарстве автоматически решает
     * судьбу всей его истории (`task.md`, «Механика „как есть“»).
     */
    private fun remapIntakes(
        intakes: List<Intake>,
        medicationIdByFileId: Map<String, String>,
        profiles: ProfileMatching,
    ): IncomingIntakes {
        var dropped = 0
        val remapped = intakes.mapNotNull { intake ->
            val medicationId = medicationIdByFileId[intake.medicationId]
            if (medicationId == null) {
                dropped++
                null
            } else {
                intake.copy(
                    id = ids.newId(),
                    medicationId = medicationId,
                    // Отметивший профиль тоже перевыдаётся: иначе подпись «отметили вы»
                    // сослалась бы на профиль, которого на этом устройстве нет (ПР-12).
                    actorProfileId = intake.actorProfileId?.let(profiles::deviceProfileId),
                )
            }
        }
        return IncomingIntakes(remapped, dropped)
    }
}
