package ru.stohastika.sklyanka.acceptance.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.today.GroupMarkOutcome
import ru.stohastika.sklyanka.feature.reminder.ReminderActions
import ru.stohastika.sklyanka.feature.reminder.ReminderScreen
import ru.stohastika.sklyanka.feature.reminder.ReminderUiState
import ru.stohastika.sklyanka.feature.reminder.ReminderViewModel
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.ProfileAccent
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд композиции «карточка напоминания → Сегодня» для приёмки SKL-30.
 *
 * Собирает настоящий `ReminderViewModel` над миром сценария (как стенд карточки SKL-13)
 * и «Сегодня» состоянием из [TodayFromWorld]; окно отмены публикуется и применяется
 * теми же правилами, что в `TodayViewModel` и `ReminderViewModel`. Уход с экрана шаг
 * воспроизводит вызовом точки выхода — жизненный цикл композиции между шагами стенд
 * не воспроизводит, и сам `DisposableEffect` закрыт Compose-тестом `TodayRoute`
 * (`TDD.md §12.2`, §15.5).
 *
 * Стенд живёт в `app`, потому что сценарию нужны оба экрана сразу: «Сегодня» и карточка
 * напоминания живут в разных модулях (`SKL-26/TDD.md §4.3`).
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
internal object UndoAppProbe {

    private var lastWorld: AcceptanceWorld? = null
    private var viewModel: ReminderViewModel? = null

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            viewModel = null
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
    }

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }

    fun reminder(world: AcceptanceWorld): ReminderViewModel {
        reset(world)
        return viewModel ?: build(world).also { viewModel = it }
    }

    fun reminderState(world: AcceptanceWorld): ReminderUiState = reminder(world).state.value

    /** Открыть карточку последнего показанного напоминания — как это делает активность. */
    fun openCard(world: AcceptanceWorld) {
        val card = world.reminders.shownCards.lastOrNull() ?: return
        reminder(world).open(card.slot.profileId, card.slot.scheduledAt)
    }

    /** Нажатие на карточке напоминания: экран отдаёт действие, стенд исполняет его у VM. */
    fun pressOnCard(world: AcceptanceWorld, tag: String) {
        reset(world)
        val performed = mutableListOf<() -> Unit>()
        runComposeUiTest {
            setContent {
                val state = reminderState(world)
                if (state is ReminderUiState.Card) {
                    SklyankaTheme(dark = true) {
                        ReminderScreen(
                            state = state,
                            actions = ReminderActions(
                                onTaken = { intakeId -> performed += { reminder(world).onTaken(intakeId) } },
                                onSkipped = { intakeId -> performed += { reminder(world).onSkipped(intakeId) } },
                                onTakenAll = { performed += { reminder(world).onTakenAll() } },
                                onSkippedAll = { performed += { reminder(world).onSkippedAll() } },
                                onSnooze = { performed += { reminder(world).onSnooze() } },
                            ),
                        )
                    }
                }
            }
            onNodeWithTag(tag).performClick()
            waitForIdle()
        }
        performed.forEach { it() }
    }

    /**
     * «Сегодня»: собрать экран, нажать и применить намерения к миру.
     *
     * Намерения применяются после композиции: экран за нажатие отвечает намерением,
     * а мир меняет тот же путь, что у продукта.
     */
    fun today(
        world: AcceptanceWorld,
        profile: ProfileAccent = ProfileAccent.Owner,
        action: (ComposeUiTest) -> Unit = {},
    ): List<TodayIntent> {
        val intents = mutableListOf<TodayIntent>()
        runComposeUiTest {
            setContent {
                Pinned(profile) {
                    TodayScreen(state = TodayFromWorld.state(world), onIntent = intents::add)
                }
            }
            waitForIdle()
            action(this)
            waitForIdle()
        }
        intents.forEach { applyTodayIntent(world, it) }
        return intents
    }

    /** Акцент, действующий на «Сегодня» в профиле [profile] — то, чем подписано действие. */
    fun todayAccent(world: AcceptanceWorld, profile: ProfileAccent, action: (ComposeUiTest) -> Unit = {}): Color {
        var captured: Color? = null
        runComposeUiTest {
            setContent {
                Pinned(profile) {
                    captured = SklyankaTheme.colors.accent
                    TodayScreen(state = TodayFromWorld.state(world), onIntent = {})
                }
            }
            action(this)
            waitForIdle()
        }
        return requireNotNull(captured)
    }

    fun applyTodayIntent(world: AcceptanceWorld, intent: TodayIntent) {
        when (intent) {
            is TodayIntent.MarkTaken -> mark(world, intent.intakeId, MarkResult.TAKEN)
            is TodayIntent.MarkSkipped -> mark(world, intent.intakeId, MarkResult.SKIPPED)
            is TodayIntent.MarkGroupTaken -> markTheGroup(world, intent.intakeIds)
            is TodayIntent.UndoMark -> {
                // «Отменить» закрывает окно синхронно, как в `TodayViewModel`.
                world.undoActions.clear()
                world.undo(intent.intakeIds, world.clock.nowLocal())
            }

            else -> Unit
        }
    }

    private fun mark(world: AcceptanceWorld, intakeId: String, result: MarkResult) {
        world.markFromScreen(intakeId, world.clock.nowLocal(), result)
        val outcome = world.intakes.outcomes.last()
        if (!outcome.isCompleted) return
        world.undoActions.publishMark(
            intakeIds = listOf(intakeId),
            result = outcome.intake?.status?.let(MarkResult::of) ?: result,
            medicationNames = listOfNotNull(medicationName(world, intakeId)),
        )
    }

    private fun markTheGroup(world: AcceptanceWorld, intakeIds: List<String>) {
        when (val outcome = world.markGroup(intakeIds, world.clock.nowLocal())) {
            is GroupMarkOutcome.Applied -> if (outcome.changedIntakeIds.isNotEmpty()) {
                world.undoActions.publishMark(
                    intakeIds = outcome.changedIntakeIds,
                    result = MarkResult.TAKEN,
                    medicationNames = outcome.changedIntakeIds.mapNotNull { medicationName(world, it) },
                )
            }

            is GroupMarkOutcome.Rejected -> Unit
        }
    }

    private fun medicationName(world: AcceptanceWorld, intakeId: String): String? {
        val medicationId = world.intakes.get(intakeId)?.medicationId ?: return null
        return world.medications.byId(medicationId)?.name
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
        undoActions = world.undoActions,
        analytics = world.analytics.recorder(CoroutineScope(Dispatchers.Unconfined)),
    )

    /** Язык интерфейса закреплён: склонения и даты читаются по-русски при любом телефоне. */
    @Composable
    private fun Pinned(profile: ProfileAccent, content: @Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale()
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
        ) {
            SklyankaTheme(profile = profile) { content() }
        }
    }
}
