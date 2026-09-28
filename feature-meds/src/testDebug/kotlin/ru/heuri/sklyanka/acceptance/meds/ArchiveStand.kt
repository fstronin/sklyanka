package ru.heuri.sklyanka.acceptance.meds

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.acceptance.world.ScenarioTransactor
import ru.heuri.sklyanka.domain.medication.ArchiveGrouping
import ru.heuri.sklyanka.domain.medication.MedicationListRules
import ru.heuri.sklyanka.domain.medication.RestoreMedicationUseCase
import ru.heuri.sklyanka.domain.medication.RestoreOutcome
import ru.heuri.sklyanka.feature.meds.archive.ArchiveIntent
import ru.heuri.sklyanka.feature.meds.archive.ArchiveNotice
import ru.heuri.sklyanka.feature.meds.archive.ArchiveScreen
import ru.heuri.sklyanka.feature.meds.archive.ArchiveUiState
import ru.heuri.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд вкладки «Архив»: состояние из мира сценария и собранная композиция.
 *
 * Состояние собирается тем же путём, каким `ArchiveViewModel` строит `ArchiveUiState`:
 * срез архивных лекарств профиля, история одним запросом от самой ранней даты начала,
 * группировка `ArchiveGrouping` (ЭКР-8), счётчик активных — `MedicationListRules` (ЭКР-3).
 * Выдуманное состояние проверяло бы разметку, а приёмка SKL-11 говорит о продукте.
 *
 * Нажатие «Вернуть» уходит намерением и применяется настоящим `RestoreMedicationUseCase`
 * с портом планировщика мира: приёмы и будильники возвращаются тем же путём, что
 * в продукте. Объяснение отказа — часть состояния, как и в ViewModel.
 */
@OptIn(ExperimentalTestApi::class)
internal object ArchiveStand {

    private var lastWorld: AcceptanceWorld? = null
    private var noticeHere: ArchiveNotice? = null

    /** Состояние вкладки: архив, счётчики и объяснение отказа в возврате. */
    fun state(world: AcceptanceWorld): ArchiveUiState {
        resetIfNewWorld(world)
        val profileId = world.profiles.get(world.profiles.active).id
        val active = runBlocking { world.repositories.medicationStore.observeActive(profileId).first() }
        val archived = runBlocking { world.repositories.medicationStore.observeArchived(profileId).first() }
        val history = if (archived.isEmpty()) {
            emptyList()
        } else {
            runBlocking {
                world.repositories.intakeStore
                    .observePeriod(profileId, archived.minOf { it.startDate }, world.clock.today())
                    .first()
            }
        }
        val content = ArchiveGrouping.of(archived, history, world.clock.zone)
        return ArchiveUiState(
            activeCount = MedicationListRules.activeCount(active),
            archiveCount = content.total,
            content = content.takeUnless { it.isEmpty },
            notice = noticeHere,
            loading = false,
        )
    }

    /** Объяснение отказа последнего возврата: «период приёма нужно продлить». */
    val notice: ArchiveNotice? get() = noticeHere

    /** Собрать экран и задать вопрос его композиции. */
    fun <T> ask(world: AcceptanceWorld, question: (ComposeUiTest) -> T): T {
        var answer: T? = null
        runComposeUiTest {
            setContent { SklyankaTheme { ArchiveScreen(state = state(world), onIntent = {}) } }
            answer = question(this)
        }
        @Suppress("UNCHECKED_CAST")
        return answer as T
    }

    /** Действие человека: архив отдаёт намерения, стенд применяет их к миру. */
    fun act(world: AcceptanceWorld, action: (ComposeUiTest) -> Unit): List<ArchiveIntent> {
        val intents = mutableListOf<ArchiveIntent>()
        runComposeUiTest {
            setContent { SklyankaTheme { ArchiveScreen(state = state(world), onIntent = intents::add) } }
            action(this)
            waitForIdle()
        }
        intents.forEach { apply(world, it) }
        return intents
    }

    private fun apply(world: AcceptanceWorld, intent: ArchiveIntent) {
        when (intent) {
            is ArchiveIntent.Restore -> restore(world, intent.medicationId)
            // Переключение вкладки и открытие карточки — состояние маршрута: архив их
            // только сообщает наружу.
            else -> Unit
        }
    }

    /** Возврат тем же use-case, что в продукте; отказ объясняется, а не проглатывается. */
    private fun restore(world: AcceptanceWorld, medicationId: String) {
        val outcome = runBlocking {
            RestoreMedicationUseCase(
                medications = world.repositories.medicationStore,
                planner = world.planner(),
                transactor = ScenarioTransactor(world.intakes, world.medications),
                time = world.clock.time,
                log = world.log,
            )(medicationId)
        }
        noticeHere = if (outcome is RestoreOutcome.NeedsPeriodUpdate) ArchiveNotice.PeriodNeedsUpdate else null
    }

    private fun resetIfNewWorld(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            noticeHere = null
        }
    }
}
