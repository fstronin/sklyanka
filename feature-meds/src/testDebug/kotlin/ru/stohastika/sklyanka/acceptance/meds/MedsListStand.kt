package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.MedicationListRules
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.feature.meds.list.AsNeededCardUi
import ru.stohastika.sklyanka.feature.meds.list.MedicationCardUi
import ru.stohastika.sklyanka.feature.meds.list.MedicationListScreen
import ru.stohastika.sklyanka.feature.meds.list.MedsIntent
import ru.stohastika.sklyanka.feature.meds.list.MedsUiState
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDate

/**
 * Стенд экрана «Лекарства» для шагов приёмки: состояние из мира и собранная композиция.
 *
 * Состояние собирается тем же путём, каким `MedicationListViewModel` превращает срез
 * лекарств профиля в карточки: счётчики считает [MedicationListRules] из домена, строку
 * остатка или периода выбирает то же правило ЭКР-4, историю «по потребности» читает
 * тот же запрос слоя данных. Выдуманное состояние проверяло бы разметку, а приёмка
 * SKL-09 говорит о продукте.
 *
 * Нажатия уходят намерениями и применяются к миру настоящими доменными сценариями:
 * «Принять» в карточке создаёт приём тем же `CreatePrnIntakeUseCase`, что и в продукте.
 * Переходы наружу состояние списка не меняют — их разбирает маршрут `app`.
 */
@OptIn(ExperimentalTestApi::class)
internal object MedsListStand {

    /** Состояние, которое продукт показал бы сейчас. */
    fun state(world: AcceptanceWorld): MedsUiState {
        val profileId = world.profiles.get(world.profiles.active).id
        val active = runBlocking { world.repositories.medicationStore.observeActive(profileId).first() }
        val archived = runBlocking { world.repositories.medicationStore.observeArchived(profileId).first() }
        val asNeeded = active.filter { it.scheduleMode == ScheduleMode.PRN }
        val history = if (asNeeded.isEmpty()) {
            emptyList()
        } else {
            runBlocking {
                world.repositories.intakeStore
                    .observePeriod(profileId, asNeeded.minOf { it.startDate }, world.clock.today())
                    .first()
            }
        }
        return MedsUiState(
            activeCount = MedicationListRules.activeCount(active),
            archiveCount = archived.size,
            items = active.map { card(it, history, world.clock.today()) },
            loading = false,
        )
    }

    /** Собрать экран и задать вопрос его композиции. */
    fun <T> ask(world: AcceptanceWorld, question: (ComposeUiTest) -> T): T {
        var answer: T? = null
        runComposeUiTest {
            setContent { SklyankaTheme { MedicationListScreen(state = state(world), onIntent = {}) } }
            answer = question(this)
        }
        @Suppress("UNCHECKED_CAST")
        return answer as T
    }

    /** Действие человека: экран отдаёт намерения, стенд применяет их к миру. */
    fun act(world: AcceptanceWorld, action: (ComposeUiTest) -> Unit): List<MedsIntent> {
        val intents = mutableListOf<MedsIntent>()
        runComposeUiTest {
            setContent { SklyankaTheme { MedicationListScreen(state = state(world), onIntent = intents::add) } }
            action(this)
            waitForIdle()
        }
        intents.forEach { apply(world, it) }
        return intents
    }

    /** Имя лекарства в модели экрана: сценарий зовёт «Магнием B6», карточка — `med-3`. */
    fun displayName(world: AcceptanceWorld, medication: String): String =
        world.medications.get(world.medicationNamed(medication)).name

    /** Карточка названного лекарства: карточка кликабельна, но имя лежит у потомков. */
    fun card(world: AcceptanceWorld, medication: String): SemanticsMatcher =
        hasTestTag(TAG_MEDICATION_CARD) and hasAnyDescendant(hasText(displayName(world, medication)))

    private fun card(medication: Medication, history: List<Intake>, today: LocalDate): MedicationCardUi =
        MedicationCardUi(
            medicationId = medication.id,
            title = medication.name,
            form = medication.form,
            strength = medication.strength?.stripTrailingZeros()?.toPlainString(),
            strengthUnit = medication.strengthUnit,
            timesPerDay = medication.times.size,
            times = medication.times.sorted(),
            trailingLine = MedicationListRules.trailingLineOf(medication),
            asNeeded = asNeededOf(medication, history, today),
        )

    private fun asNeededOf(medication: Medication, history: List<Intake>, today: LocalDate): AsNeededCardUi? {
        val limit = medication.maxPerDay.takeIf { medication.scheduleMode == ScheduleMode.PRN } ?: return null
        val taken = history.filter { it.medicationId == medication.id && it.status == IntakeStatus.TAKEN }
        return AsNeededCardUi(
            maxPerDay = limit,
            lastIntakeDate = taken.maxOfOrNull { intake -> intake.scheduledAt }?.toLocalDate(),
            // Ограничение считается по назначенному времени: у приёма по потребности оно
            // равно фактическому (ИНВ-14), поэтому сутки определяются однозначно.
            takeEnabled = taken.count { intake -> intake.scheduledAt.toLocalDate() == today } < limit,
        )
    }

    private fun apply(world: AcceptanceWorld, intent: MedsIntent) {
        when (intent) {
            is MedsIntent.TakeNow -> world.pressTakeOnDemand(nameOf(world, intent.medicationId))
            // Переключение вкладки, открытие карточки и добавление — состояние маршрута:
            // список их только сообщает наружу.
            else -> Unit
        }
    }

    private fun nameOf(world: AcceptanceWorld, medicationId: String): String = requireNotNull(
        world.medications.names().firstOrNull { world.medications.get(it).id == medicationId },
    ) { "unknown medication id: $medicationId" }
}
