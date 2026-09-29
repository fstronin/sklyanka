package ru.stohastika.sklyanka.acceptance.world

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.domain.profile.DeleteProfileOutcome
import ru.stohastika.sklyanka.domain.profile.DeleteProfileUseCase
import ru.stohastika.sklyanka.domain.profile.ProfileEraser
import ru.stohastika.sklyanka.domain.profile.ProfileLosses
import ru.stohastika.sklyanka.domain.profile.ProfileLossesSource
import ru.stohastika.sklyanka.domain.profile.ProfileSetReplanner
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository

/**
 * Удаление профиля в мире сценария (SKL-36, ОВ-7).
 *
 * Правила не копируются: сценарий исполняет настоящий `DeleteProfileUseCase`, числа
 * потерь считаются тем же соединением приёмов с лекарствами, что история, а снятие
 * будильников делает настоящий `RescheduleRemindersUseCase` через перепланировщик мира.
 * В памяти живёт только стирание — оно повторяет транзакцию `RoomProfileEraser`:
 * сначала приёмы (вместе с ними каскадом уходят отложенные срабатывания — их снимает
 * пересборка), потом лекарства, потом профиль.
 *
 * Мир не умеет «половину стирания»: транзакционность и внешние ключи проверяются
 * инструментальным тестом `core-data`, эмулятора для них здесь нет.
 */
class ScenarioProfileDeletion(
    private val profiles: ScenarioProfiles,
    private val medications: ScenarioMedications,
    private val intakes: ScenarioIntakes,
    store: ProfileRepository,
    active: ActiveProfileRepository,
    replan: ProfileSetReplanner,
    log: ScenarioLog,
) {

    /** Снимок чисел потерь на момент подписки: экран спрашивает их при открытии. */
    val losses: ProfileLossesSource = ProfileLossesSource { profileId -> flowOf(readLosses(profileId)) }

    private val eraser = ProfileEraser { profileId ->
        medications.all()
            .filter { it.profileId == profileId }
            .forEach { medication ->
                intakes.removeOf(medication.id)
                medications.removeById(medication.id)
            }
        profiles.removeById(profileId)
    }

    val useCase: DeleteProfileUseCase = DeleteProfileUseCase(
        profiles = store,
        eraser = eraser,
        active = active,
        replan = replan,
        log = log,
    )

    /** Подтверждение удаления профиля по имени сценария; исход — предмет шага. */
    fun confirm(profileName: String): DeleteProfileOutcome = runBlocking { useCase(profiles.get(profileName).id) }

    /** Числа потерь профиля по имени сценария — то же, что увидит экран. */
    fun lossesOf(profileName: String): ProfileLosses = readLosses(profiles.get(profileName).id)

    private fun readLosses(profileId: String): ProfileLosses {
        val owned = medications.all().filter { it.profileId == profileId }
        return ProfileLosses(
            medications = owned.size,
            archived = owned.count { it.isArchived },
            intakes = intakes.all().count { intake -> medications.byId(intake.medicationId)?.profileId == profileId },
        )
    }
}
