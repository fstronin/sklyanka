package ru.stohastika.sklyanka.feature.today

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.reminder.ReminderWarning
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.today.IntakeItem
import ru.stohastika.sklyanka.domain.today.IntakePresentation
import ru.stohastika.sklyanka.domain.today.PartOfDay
import ru.stohastika.sklyanka.domain.today.TodayLayout
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Состояние экрана «Сегодня» и разбор намерений.
 *
 * Проверяется то, что увидит человек, а не то, что `ViewModel` кому-то позвонила:
 * под ней работают настоящие сценарии отметки поверх хранилища в памяти, и результат
 * приходит новым значением потока, как и в приложении (`TDD.md §3.1`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class TodayViewModelTest {

    private val dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a profile without medications sees the empty state`() = runTest {
        val world = TodayTestWorld(testScheduler)

        world.viewModel().state.test {
            assertEquals(TodayUiState.Empty(TodayTest.DATE), awaitItem().afterOpening(this))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the day is laid out by time with the current group marked`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)

        world.viewModel().state.test {
            val content = awaitContent()

            assertEquals(
                listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(21, 0)),
                content.layout.groups.map { it.time },
            )
            assertEquals(LocalTime.of(14, 0), content.layout.groups.single { it.isNow }.time)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `marking an intake as taken changes the state and writes off the stock`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()

            viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))

            val content = awaitContentWhere { it.itemAt(LocalTime.of(14, 0)).presentation == IntakePresentation.TAKEN }
            val item = content.itemAt(LocalTime.of(14, 0))
            assertEquals(LocalTime.of(14, 5), item.markedAt)
            assertEquals(BigDecimal("7"), world.medications.stored.getValue("m-magnesium").stockLeft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `skipping an intake leaves the stock alone`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 20)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()

            viewModel.onIntent(TodayIntent.MarkSkipped("i-magnesium"))

            val content = awaitContentWhere { it.itemAt(LocalTime.of(14, 0)).presentation == IntakePresentation.MISSED }
            assertEquals(BigDecimal("9"), world.medications.stored.getValue("m-magnesium").stockLeft)
            assertEquals(IntakePresentation.MISSED, content.itemAt(LocalTime.of(14, 0)).presentation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a repeated mark changes neither the record nor the state`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))
            val marked = awaitContentWhere { content ->
                content.itemAt(LocalTime.of(14, 0)).presentation == IntakePresentation.TAKEN &&
                    content.undo?.intakeIds == listOf("i-magnesium")
            }

            viewModel.onIntent(TodayIntent.MarkSkipped("i-magnesium"))

            expectNoEvents()
            assertEquals(IntakeStatus.TAKEN, world.intakes.stored.getValue("i-magnesium").status)
            assertEquals(marked, viewModel.state.value)
            assertEquals(BigDecimal("7"), world.medications.stored.getValue("m-magnesium").stockLeft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the group mark takes every intake of the time at once`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()

            viewModel.onIntent(TodayIntent.MarkGroupTaken(listOf("i-a", "i-b", "i-c")))

            val content = awaitContentWhere { candidate ->
                val group = candidate.layout.groups.single { it.time == LocalTime.of(14, 0) }
                candidate.undo?.intakeIds?.size == 3 && group.items.all { it.presentation == IntakePresentation.TAKEN }
            }
            val group = content.layout.groups.single { it.time == LocalTime.of(14, 0) }
            assertTrue(group.items.all { it.presentation == IntakePresentation.TAKEN })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `taking an on-demand medication creates an intake but not a plan`() = runTest {
        val world = worldWithDay(startAt = 16, minute = 40)
        world.medications.put(TodayTest.medication("m-prn", mode = ScheduleMode.PRN, maxPerDay = 3))
        val viewModel = world.viewModel()

        viewModel.state.test {
            val before = awaitContent()
            assertEquals(0, before.prn.single().takenToday)

            viewModel.onIntent(TodayIntent.TakePrn("m-prn"))

            // Кадров после нажатия два: окно отмены публикуется раньше, чем повестка дня
            // соберётся заново (Р-46, SKL-127), — ждём именно пересобранную повестку.
            val content = awaitContentWhere { it.prn.single().takenToday == 1 }
            assertEquals(1, content.prn.single().takenToday)
            // Приём «по потребности» не входит ни в числитель, ни в знаменатель (ЭКР-1).
            assertEquals(0, content.progress.taken)
            assertEquals(3, content.progress.planned)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an exhausted daily limit closes the take button`() = runTest {
        val world = worldWithDay(startAt = 21, minute = 0)
        world.medications.put(TodayTest.medication("m-prn", mode = ScheduleMode.PRN, maxPerDay = 1))
        world.intakes.put(TodayTest.intake("i-prn", "m-prn", TodayTest.at(8, 10), IntakeStatus.TAKEN))
        val viewModel = world.viewModel()

        viewModel.state.test {
            assertFalse(awaitContent().prn.single().canTake)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a collapsed section opens on a tap and stays open after a mark`() = runTest {
        val world = denseWorld()
        val viewModel = world.viewModel(denseThreshold = 2)

        viewModel.state.test {
            val before = awaitContent()
            val evening = (before.layout as TodayLayout.Dense).sections.single { it.partOfDay == PartOfDay.EVENING }
            assertFalse(before.isExpanded(evening))

            viewModel.onIntent(TodayIntent.ToggleSection(PartOfDay.EVENING))
            val expanded = awaitItem() as TodayUiState.Content
            assertTrue(expanded.isExpanded(evening))

            viewModel.onIntent(TodayIntent.MarkTaken("i-day"))
            val afterMark = awaitItem() as TodayUiState.Content
            // Развёрнутая секция переживает отметку: она в состоянии экрана, а не
            // в локальном состоянии Compose (TDD §5.2).
            assertTrue(afterMark.isExpanded(evening))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigation intents do not touch the state`() = runTest {
        val world = worldWithDay(startAt = 9, minute = 0)
        val viewModel = world.viewModel()

        viewModel.state.test {
            val before = awaitContent()

            viewModel.onIntent(TodayIntent.OpenMedication("m-magnesium"))
            viewModel.onIntent(TodayIntent.OpenStock("m-magnesium"))
            viewModel.onIntent(TodayIntent.AddMedication)
            viewModel.onIntent(TodayIntent.OpenMedicationList)
            viewModel.onIntent(TodayIntent.OpenSettings)
            viewModel.onIntent(TodayIntent.OpenPermissions)

            expectNoEvents()
            assertEquals(before, viewModel.state.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Надёжность напоминаний (SKL-29) -----------------------------------------

    @Test
    fun `fully granted permissions leave no reliability warning`() = runTest {
        val world = worldWithDay(startAt = 9, minute = 0)

        world.viewModel().state.test {
            assertNull(awaitContent().reliability)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `revoked notifications carry their own reason`() = runTest {
        val world = worldWithDay(startAt = 9, minute = 0)
        world.denyPermissions(ReminderPermission.NOTIFICATIONS)

        world.viewModel().state.test {
            assertEquals(ReminderWarning.NOTIFICATIONS_OFF, awaitContent().reliability)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `denied exact alarms carry their own reason`() = runTest {
        val world = worldWithDay(startAt = 9, minute = 0)
        world.denyPermissions(ReminderPermission.EXACT_ALARMS)

        world.viewModel().state.test {
            assertEquals(ReminderWarning.EXACT_ALARMS_OFF, awaitContent().reliability)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `both revoked permissions are named by a single reason`() = runTest {
        val world = worldWithDay(startAt = 9, minute = 0)
        world.denyPermissions(ReminderPermission.NOTIFICATIONS, ReminderPermission.EXACT_ALARMS)

        world.viewModel().state.test {
            assertEquals(ReminderWarning.BOTH_OFF, awaitContent().reliability)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Ровно то, ради чего признак читается на каждой сборке: отозванное разрешение
     * появляется без перезапуска экрана (НФТ-52, критерий приёмки 5 SKL-29).
     */
    @Test
    fun `a revoked permission appears without restarting the screen`() = runTest {
        val world = worldWithDay(startAt = 9, minute = 0)
        val viewModel = world.viewModel()

        viewModel.state.test {
            assertNull(awaitContent().reliability)

            world.denyPermissions(ReminderPermission.NOTIFICATIONS)
            // Новая сборка состояния — следствие отметки, а не перезапуска.
            viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))

            val content = awaitItem() as TodayUiState.Content
            assertEquals(ReminderWarning.NOTIFICATIONS_OFF, content.reliability)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Перечитывание признака на живом экране (SKL-48) --------------------------

    /**
     * Выдача разрешения мимо приложения при живом экране: `refresh()` — тот же вызов,
     * который делает `MainActivity.onResume` (`TDD.md §5.2` SKL-48).
     */
    @Test
    fun `a granted permission clears the warning on the next refresh`() = runTest {
        val world = worldWithDay(startAt = 9, minute = 0)
        world.denyPermissions(ReminderPermission.NOTIFICATIONS)
        val viewModel = world.viewModel()

        viewModel.state.test {
            assertEquals(ReminderWarning.NOTIFICATIONS_OFF, awaitContent().reliability)

            world.grantPermissions(ReminderPermission.NOTIFICATIONS)
            viewModel.refresh()

            assertNull(awaitContentWhere { it.reliability == null }.reliability)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Обратная сторона: отзыв разрешения на живом экране поднимает предупреждение. */
    @Test
    fun `a revoked permission reappears on the next refresh`() = runTest {
        val world = worldWithDay(startAt = 9, minute = 0)
        val viewModel = world.viewModel()

        viewModel.state.test {
            assertNull(awaitContent().reliability)

            world.denyPermissions(ReminderPermission.NOTIFICATIONS)
            viewModel.refresh()

            assertEquals(
                ReminderWarning.NOTIFICATIONS_OFF,
                awaitContentWhere { it.reliability != null }.reliability,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Минутный такт перечитывает признак, даже когда ни одно действие не случилось
     * и повестка не изменилась: `pm grant` из ADB не рождает событий жизненного цикла
     * (наблюдение smoke-прогона 30.09.2026).
     */
    @Test
    fun `the minute tick rereads the permission while the screen is open`() = runTest {
        val world = worldWithDay(startAt = 9, minute = 0)
        world.denyPermissions(ReminderPermission.NOTIFICATIONS)
        val viewModel = world.viewModel()

        viewModel.state.test {
            assertEquals(ReminderWarning.NOTIFICATIONS_OFF, awaitContent().reliability)

            world.grantPermissions(ReminderPermission.NOTIFICATIONS)
            advanceTimeBy(60_001)
            runCurrent()

            assertNull((viewModel.state.value as TodayUiState.Content).reliability)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Такт без изменения причины не перерисовывает экран: состояние не эмитится. */
    @Test
    fun `an unchanged permission does not rebuild the screen on the tick`() = runTest {
        val world = worldWithDay(startAt = 9, minute = 0)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()

            advanceTimeBy(60_001)
            runCurrent()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the stock plate shows one warning and disappears without a threshold`() = runTest {
        val world = TodayTestWorld(testScheduler)
        world.medications.put(
            TodayTest.medication(
                id = "m-magnesium",
                times = listOf(LocalTime.of(14, 0)),
                dose = "2",
                stockLeft = "9",
                thresholdDays = 5,
            ),
        )
        world.medications.put(TodayTest.medication("m-plain", stockLeft = "100", thresholdDays = 5))

        world.viewModel().state.test {
            val content = awaitContent()

            assertEquals("m-magnesium", content.stockWarning?.medicationId)
            assertEquals(4, content.stockWarning?.daysLeft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `medications without intakes today are not the first run empty state`() = runTest {
        val world = TodayTestWorld(testScheduler)
        world.medications.put(TodayTest.medication("m-tomorrow"))

        world.viewModel().state.test {
            val content = awaitContent()

            assertTrue(content.nothingToday)
            assertFalse(content.progress.hasPlan)
            assertNull(content.stockWarning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Окно отмены (SKL-30) --------------------------------------------------------

    @Test
    fun `a taken mark publishes the undo notice with the medication name`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))

            val undo = awaitUndo { it.result == MarkResult.TAKEN }
            assertEquals(listOf("i-magnesium"), undo.intakeIds)
            assertEquals(listOf("m-magnesium"), undo.medicationNames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a skipped mark publishes the skipped notice`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.MarkSkipped("i-magnesium"))

            val undo = awaitUndo { it.result == MarkResult.SKIPPED }
            assertEquals(listOf("i-magnesium"), undo.intakeIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a dropped second mark does not publish anything`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))
            val published = awaitContentWhere { content ->
                content.itemAt(LocalTime.of(14, 0)).presentation == IntakePresentation.TAKEN &&
                    content.undo?.result == MarkResult.TAKEN
            }.undo

            // Повторная отметка того же приёма отброшена: это не новое действие,
            // и срок снэкбара идёт от единственной состоявшейся отметки.
            viewModel.onIntent(TodayIntent.MarkSkipped("i-magnesium"))

            expectNoEvents()
            assertEquals(published, (viewModel.state.value as TodayUiState.Content).undo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the snackbar disappears exactly on the fifth second`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))
            awaitUndo { it.result == MarkResult.TAKEN }

            advanceTimeBy(4_999)
            runCurrent()
            assertNotNull((viewModel.state.value as TodayUiState.Content).undo)

            advanceTimeBy(1)
            runCurrent()
            assertNull((viewModel.state.value as TodayUiState.Content).undo)
            assertNull(world.undoActions.live())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a second mark replaces the notice and restarts the window from its own moment`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))
            awaitUndo { it.intakeIds == listOf("i-magnesium") }

            advanceTimeBy(3_000)
            runCurrent()
            viewModel.onIntent(TodayIntent.MarkSkipped("i-eutirox"))

            val replaced = awaitUndo { it.intakeIds == listOf("i-eutirox") }
            assertEquals(MarkResult.SKIPPED, replaced.result)

            // Срок идёт от второго действия: к 07,999 от первой отметки окно ещё живо.
            advanceTimeBy(4_999)
            runCurrent()
            assertNotNull((viewModel.state.value as TodayUiState.Content).undo)

            advanceTimeBy(1)
            runCurrent()
            assertNull((viewModel.state.value as TodayUiState.Content).undo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the undo action returns the intake, restores the dose and closes the window`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))
            val undo = awaitUndo { it.result == MarkResult.TAKEN }

            viewModel.onIntent(TodayIntent.UndoMark(undo.intakeIds))

            assertEquals(IntakeStatus.PENDING, world.intakes.stored.getValue("i-magnesium").status)
            assertEquals(BigDecimal("9"), world.medications.stored.getValue("m-magnesium").stockLeft)
            assertNull((viewModel.state.value as TodayUiState.Content).undo)
            assertNull(world.undoActions.live())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the group mark publishes exactly the intakes of the action`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.MarkGroupTaken(listOf("i-a", "i-b", "i-c")))

            val undo = awaitUndo { it.intakeIds.size == 3 }
            assertEquals(MarkResult.TAKEN, undo.result)
            assertEquals(listOf("m-a", "m-b", "m-c"), undo.medicationNames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `taking an on-demand medication opens the same undo window as a planned mark`() = runTest {
        // Было наоборот: окна не открывалось вовсе. Решение Р-46 (SKL-127) даёт записи
        // «по потребности» то же окно — снятие записи в пять секунд.
        val world = worldWithDay(startAt = 16, minute = 40)
        world.medications.put(TodayTest.medication("m-prn", mode = ScheduleMode.PRN, maxPerDay = 3))
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.TakePrn("m-prn"))

            val undo = awaitUndo { it.result == MarkResult.TAKEN }
            assertEquals(1, undo.intakeIds.size)
            // Имя лекарства берётся из очереди суток — то же, что у плановой отметки.
            assertEquals(listOf("m-prn"), undo.medicationNames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an exhausted daily limit opens no undo window`() = runTest {
        // Отказ лимита — не новое действие: снимать в нём нечего, и окно не открывается.
        val world = worldWithDay(startAt = 21, minute = 0)
        world.medications.put(TodayTest.medication("m-prn", mode = ScheduleMode.PRN, maxPerDay = 1))
        world.intakes.put(TodayTest.intake("i-prn", "m-prn", TodayTest.at(8, 10), IntakeStatus.TAKEN))
        val viewModel = world.viewModel()

        viewModel.state.test {
            assertFalse(awaitContent().prn.single().canTake)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onIntent(TodayIntent.TakePrn("m-prn"))
        runCurrent()

        assertNull(world.undoActions.live())
    }

    @Test
    fun `leaving the screen closes the undo window`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.MarkTaken("i-magnesium"))
            awaitUndo { it.result == MarkResult.TAKEN }

            viewModel.onScreenLeft()

            assertNull((viewModel.state.value as TodayUiState.Content).undo)
            assertNull(world.undoActions.live())
            assertEquals(IntakeStatus.TAKEN, world.intakes.stored.getValue("i-magnesium").status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Подтверждение групповой отметки (SKL-31) ------------------------------------

    @Test
    fun `arming the group mark marks nothing and expires exactly on the tenth second`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.ArmGroupMark(LocalTime.of(14, 0)))

            val armed = awaitContent()
            assertEquals(LocalTime.of(14, 0), armed.confirmingGroup)
            assertTrue(world.intakes.stored.values.all { it.status == IntakeStatus.PENDING })
            assertTrue(world.log.events().isEmpty())
            assertNull(world.undoActions.live())

            advanceTimeBy(9_999)
            runCurrent()
            assertEquals(LocalTime.of(14, 0), (viewModel.state.value as TodayUiState.Content).confirmingGroup)

            advanceTimeBy(1)
            runCurrent()
            assertNull((viewModel.state.value as TodayUiState.Content).confirmingGroup)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a repeated arm restarts the ten second window`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.ArmGroupMark(LocalTime.of(14, 0)))
            awaitContentWhere { it.confirmingGroup == LocalTime.of(14, 0) }

            advanceTimeBy(6_000)
            runCurrent()
            viewModel.onIntent(TodayIntent.ArmGroupMark(LocalTime.of(14, 0)))

            // Срок идёт от второго арма: к 9,999 от первого подтверждение ещё живо.
            advanceTimeBy(9_999)
            runCurrent()
            assertEquals(LocalTime.of(14, 0), (viewModel.state.value as TodayUiState.Content).confirmingGroup)

            advanceTimeBy(1)
            runCurrent()
            assertNull((viewModel.state.value as TodayUiState.Content).confirmingGroup)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `marking a row dismisses the confirmation`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.ArmGroupMark(LocalTime.of(14, 0)))
            awaitContentWhere { it.confirmingGroup == LocalTime.of(14, 0) }

            viewModel.onIntent(TodayIntent.MarkTaken("i-a"))

            awaitContentWhere { candidate ->
                candidate.confirmingGroup == null &&
                    candidate.layout.groups.single { it.time == LocalTime.of(14, 0) }
                        .items.single { it.id == "i-a" }
                        .presentation == IntakePresentation.TAKEN
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the second press marks the group and dismisses the confirmation`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.ArmGroupMark(LocalTime.of(14, 0)))
            awaitContentWhere { it.confirmingGroup == LocalTime.of(14, 0) }

            viewModel.onIntent(TodayIntent.MarkGroupTaken(listOf("i-a", "i-b", "i-c")))

            awaitContentWhere { candidate ->
                val group = candidate.layout.groups.single { it.time == LocalTime.of(14, 0) }
                candidate.confirmingGroup == null &&
                    group.items.all { it.presentation == IntakePresentation.TAKEN }
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the undo action dismisses the confirmation`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.MarkTaken("i-a"))
            val undo = awaitUndo { it.intakeIds == listOf("i-a") }

            viewModel.onIntent(TodayIntent.ArmGroupMark(LocalTime.of(14, 0)))
            awaitContentWhere { it.confirmingGroup == LocalTime.of(14, 0) }

            viewModel.onIntent(TodayIntent.UndoMark(undo.intakeIds))

            assertEquals(IntakeStatus.PENDING, world.intakes.stored.getValue("i-a").status)
            assertNull((viewModel.state.value as TodayUiState.Content).confirmingGroup)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissing the confirmation leaves the intakes untouched`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.ArmGroupMark(LocalTime.of(14, 0)))
            awaitContentWhere { it.confirmingGroup == LocalTime.of(14, 0) }

            viewModel.onIntent(TodayIntent.DismissGroupConfirm)

            awaitContentWhere { it.confirmingGroup == null }
            assertTrue(world.intakes.stored.values.all { it.status == IntakeStatus.PENDING })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `leaving the screen dismisses the confirmation`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.ArmGroupMark(LocalTime.of(14, 0)))
            awaitContentWhere { it.confirmingGroup == LocalTime.of(14, 0) }

            viewModel.onScreenLeft()

            awaitContentWhere { it.confirmingGroup == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening a section dismisses the confirmation`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.ArmGroupMark(LocalTime.of(14, 0)))
            awaitContentWhere { it.confirmingGroup == LocalTime.of(14, 0) }

            viewModel.onIntent(TodayIntent.ToggleSection(PartOfDay.EVENING))

            awaitContentWhere { it.confirmingGroup == null && PartOfDay.EVENING in it.toggledSections }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigation intents leave the confirmation alone`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.ArmGroupMark(LocalTime.of(14, 0)))
            awaitContentWhere { it.confirmingGroup == LocalTime.of(14, 0) }

            viewModel.onIntent(TodayIntent.OpenMedication("m-a"))
            viewModel.onIntent(TodayIntent.OpenStock("m-a"))
            viewModel.onIntent(TodayIntent.AddMedication)
            viewModel.onIntent(TodayIntent.OpenMedicationList)
            viewModel.onIntent(TodayIntent.OpenSettings)
            viewModel.onIntent(TodayIntent.OpenPermissions)
            viewModel.onIntent(TodayIntent.OpenHistory)

            expectNoEvents()
            assertEquals(LocalTime.of(14, 0), (viewModel.state.value as TodayUiState.Content).confirmingGroup)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a group that lost its second pending intake does not hold the confirmation`() = runTest {
        val world = worldWithSimultaneous(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            viewModel.onIntent(TodayIntent.ArmGroupMark(LocalTime.of(14, 0)))
            awaitContentWhere { it.confirmingGroup == LocalTime.of(14, 0) }

            // Чужая повторная отметка: ожидающий в группе остаётся один, батч теряет
            // предмет, и подтверждение гаснет реконсиляцией `screen()` (TDD §16.4).
            listOf("i-a", "i-b").forEach { id ->
                world.intakes.put(
                    world.intakes.stored.getValue(id).copy(
                        status = IntakeStatus.TAKEN,
                        actedAt = TodayTest.moment(TodayTest.at(14, 6)),
                    ),
                )
            }
            runCurrent()

            awaitContentWhere { it.confirmingGroup == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Обвязка ---------------------------------------------------------------------

    /**
     * Ждать состояние, удовлетворяющее условию.
     *
     * Окно отмены и повестка дня — два независимых источника состояния, и между их
     * обновлениями возможен промежуточный кадр. Тест ждёт кадр, в котором видно
     * следствие действия, а не первый попавшийся.
     */
    private suspend fun app.cash.turbine.TurbineTestContext<TodayUiState>.awaitContentWhere(
        predicate: (TodayUiState.Content) -> Boolean,
    ): TodayUiState.Content {
        while (true) {
            val content = awaitItem() as TodayUiState.Content
            if (predicate(content)) return content
        }
    }

    /** Ждать состояние с опубликованным окном, пропуская промежуточные пересборки. */
    private suspend fun app.cash.turbine.TurbineTestContext<TodayUiState>.awaitUndo(
        predicate: (UndoNotice) -> Boolean,
    ): UndoNotice {
        while (true) {
            val content = awaitItem() as TodayUiState.Content
            val undo = content.undo
            if (undo != null && predicate(undo)) return undo
        }
    }

    /** Приём указанного времени на собранном экране. */
    private fun TodayUiState.Content.itemAt(time: LocalTime): IntakeItem =
        layout.groups.single { it.time == time }.items.single()

    private suspend fun app.cash.turbine.TurbineTestContext<TodayUiState>.awaitContent(): TodayUiState.Content {
        var state = awaitItem()
        // Первый кадр — Opening: поток ещё не выдал ни одного значения.
        while (state is TodayUiState.Opening) state = awaitItem()
        return state as TodayUiState.Content
    }

    private suspend fun TodayUiState.afterOpening(
        turbine: app.cash.turbine.TurbineTestContext<TodayUiState>,
    ): TodayUiState {
        var state = this
        while (state is TodayUiState.Opening) state = turbine.awaitItem()
        return state
    }

    @Test
    fun `a deletion window is shown on today and undo closes it`() = runTest {
        val world = worldWithDay(startAt = 14, minute = 5)
        val viewModel = world.viewModel()

        viewModel.state.test {
            awaitContent()
            world.undoActions.publishDeletion(medicationId = "m-magnesium", medicationName = "med-magnesium")

            var shown = awaitItem()
            while ((shown as? TodayUiState.Content)?.deletion == null) shown = awaitItem()
            assertEquals("med-magnesium", (shown as TodayUiState.Content).deletion?.medicationName)

            viewModel.onIntent(TodayIntent.UndoDeletion("m-magnesium"))
            var cleared = awaitItem()
            while ((cleared as? TodayUiState.Content)?.deletion != null) cleared = awaitItem()
            assertNull(world.undoActions.live())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a deletion window is shown over the empty state`() = runTest {
        // Удалили последнее лекарство — «Сегодня» пусто, но окно «Отменить» обязано быть
        // видимым (ОВ-124): иначе способ вернуть пропадает вместе с записью.
        val world = TodayTestWorld(testScheduler, TodayTest.at(14, 5))
        val viewModel = world.viewModel()

        viewModel.state.test {
            var state = awaitItem()
            while (state !is TodayUiState.Empty) state = awaitItem()

            world.undoActions.publishDeletion(medicationId = "m-gone", medicationName = "med-gone")

            var shown = awaitItem()
            while ((shown as? TodayUiState.Empty)?.deletion == null) shown = awaitItem()
            assertEquals("med-gone", (shown as TodayUiState.Empty).deletion?.medicationName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Обычный день сценария приёмки: 08:00, 14:00 и 21:00. */
    private fun TestScope.worldWithDay(startAt: Int, minute: Int): TodayTestWorld {
        val world = TodayTestWorld(testScheduler, TodayTest.at(startAt, minute))
        val eutirox = TodayTest.medication("m-eutirox", times = listOf(LocalTime.of(8, 0)))
        val magnesium = TodayTest.medication(
            id = "m-magnesium",
            times = listOf(LocalTime.of(14, 0)),
            dose = "2",
            stockLeft = "9",
        )
        val losartan = TodayTest.medication("m-losartan", times = listOf(LocalTime.of(21, 0)))
        listOf(eutirox, magnesium, losartan).forEach(world.medications::put)
        world.intakes.put(TodayTest.intake("i-eutirox", eutirox.id, TodayTest.at(8, 0)))
        world.intakes.put(TodayTest.intake("i-magnesium", magnesium.id, TodayTest.at(14, 0)))
        world.intakes.put(TodayTest.intake("i-losartan", losartan.id, TodayTest.at(21, 0)))
        return world
    }

    /** Три приёма на одно время — расклад группы одновременных приёмов. */
    private fun TestScope.worldWithSimultaneous(startAt: Int, minute: Int): TodayTestWorld {
        val world = TodayTestWorld(testScheduler, TodayTest.at(startAt, minute))
        listOf("a", "b", "c").forEach { suffix ->
            val medication = TodayTest.medication("m-$suffix", times = listOf(LocalTime.of(14, 0)), stockLeft = "20")
            world.medications.put(medication)
            world.intakes.put(TodayTest.intake("i-$suffix", medication.id, TodayTest.at(14, 0)))
        }
        return world
    }

    /** Три приёма в разное время суток: утро позади, день сейчас, вечер впереди. */
    private fun TestScope.denseWorld(): TodayTestWorld {
        val world = TodayTestWorld(testScheduler, TodayTest.at(14, 5))
        val medication = TodayTest.medication("m-dense", times = listOf(LocalTime.of(8, 0)))
        world.medications.put(medication)
        world.intakes.put(TodayTest.intake("i-morning", medication.id, TodayTest.at(8, 0), IntakeStatus.TAKEN))
        world.intakes.put(TodayTest.intake("i-day", medication.id, TodayTest.at(14, 0)))
        world.intakes.put(TodayTest.intake("i-evening", medication.id, TodayTest.at(21, 0)))
        return world
    }
}
