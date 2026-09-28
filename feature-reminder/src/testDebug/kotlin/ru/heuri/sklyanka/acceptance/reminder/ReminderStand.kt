package ru.heuri.sklyanka.acceptance.reminder

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.intake.MarkIntakeUseCase
import ru.heuri.sklyanka.feature.reminder.ReminderActions
import ru.heuri.sklyanka.feature.reminder.ReminderScreen
import ru.heuri.sklyanka.feature.reminder.ReminderUiState
import ru.heuri.sklyanka.feature.reminder.ReminderViewModel
import ru.heuri.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд полноэкранного напоминания: настоящий `ReminderViewModel` на мире сценария
 * (SKL-13, группа `@reminder-screen`).
 *
 * Карточка показывается тем же экраном, что в продукте, а отметка и откладывание идут
 * единственной точкой отметки домена — с теми же последствиями (снятие уведомления,
 * отзыв карточки часов), что в `ReminderModule`. Открывается карточка так же, как её
 * открывает активность: по профилю и назначенному времени последнего показанного
 * напоминания.
 *
 * Подменённый главный диспетчер обязан сниматься прогоном (`dropMainDispatcher`).
 */
@OptIn(ExperimentalTestApi::class)
internal object ReminderStand {

    private var lastWorld: AcceptanceWorld? = null
    private var viewModel: ReminderViewModel? = null

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            viewModel = null
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
    }

    fun model(world: AcceptanceWorld): ReminderViewModel {
        reset(world)
        return viewModel ?: build(world).also { viewModel = it }
    }

    fun state(world: AcceptanceWorld): ReminderUiState = model(world).state.value

    /** Открыть карточку последнего показанного напоминания — как это делает активность. */
    fun open(world: AcceptanceWorld) {
        val card = world.reminders.shownCards.lastOrNull() ?: return
        model(world).open(card.slot.profileId, card.slot.scheduledAt)
    }

    fun ask(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(world, onAction = {}) }
            question()
            waitForIdle()
        }
    }

    /** Действие человека: нажатия копятся и применяются после композиции. */
    fun act(world: AcceptanceWorld, action: ComposeUiTest.() -> Unit) {
        reset(world)
        val performed = mutableListOf<() -> Unit>()
        runComposeUiTest {
            setContent { Content(world, onAction = { performed += it }) }
            action(this)
            waitForIdle()
        }
        performed.forEach { it() }
    }

    @Composable
    private fun Content(world: AcceptanceWorld, onAction: (() -> Unit) -> Unit) {
        SklyankaTheme(dark = true) {
            val state = state(world)
            if (state is ReminderUiState.Card) {
                ReminderScreen(
                    state = state,
                    actions = ReminderActions(
                        onTaken = { intakeId -> onAction { model(world).onTaken(intakeId) } },
                        onSkipped = { intakeId -> onAction { model(world).onSkipped(intakeId) } },
                        onTakenAll = { onAction { model(world).onTakenAll() } },
                        onSkippedAll = { onAction { model(world).onSkippedAll() } },
                        onSnooze = { onAction { model(world).onSnooze() } },
                    ),
                )
            }
        }
    }

    private fun build(world: AcceptanceWorld): ReminderViewModel = ReminderViewModel(
        cards = world.reminders.cardSource(),
        mark = MarkIntakeUseCase(
            world.repositories.intakeStore,
            world.repositories.medicationStore,
            world.repositories.profileStore,
            world.clock.time,
            world.reminders.markSideEffects(),
        ),
        snooze = world.reminders.snoozeReminder(),
        settings = world.reminders.settingsStore,
    )

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }
}
