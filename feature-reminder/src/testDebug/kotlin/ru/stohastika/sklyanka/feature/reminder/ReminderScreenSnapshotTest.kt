package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Эталонные снимки полноэкранного напоминания (SKL-24, критерий 5).
 *
 * Единственный экран продукта в тёмной среде (`docs/spec/08-design-system.md` §5):
 * здесь снимок заодно проверяет, что тёмные токены применяются там, где заявлены.
 *
 * Чего снимок НЕ доказывает: что карточка действительно появится поверх экрана
 * блокировки. Это свойство прошивки, и проверяется оно на эталонном устройстве
 * (НАП-29, `docs/tasks/SKL-24/TDD.md §2.2`).
 */
@RunWith(AndroidJUnit4::class)
class ReminderScreenSnapshotTest {

    @Test
    fun aSingleIntake() = capture("reminder-single") { show(state()) }

    @Test
    fun twoIntakesAtOneTime() = capture("reminder-two-rows") {
        show(state(rows = listOf(row(), row(id = "intake-2", name = "med-b"))))
    }

    private fun row(id: String = "intake-1", name: String = "med-a") = ReminderRow(
        intakeId = id,
        name = name,
        form = MedicationForm.TABLET,
        doseAmount = BigDecimal("2"),
        strength = BigDecimal("48"),
        strengthUnit = "mg",
    )

    private fun state(rows: List<ReminderRow> = listOf(row())) = ReminderUiState.Card(
        scheduledAt = LocalDateTime.of(2026, 9, 25, 14, 0),
        items = rows,
        snoozeMinutes = SNOOZE_MINUTES,
        mirroredToWatch = false,
        foreignProfileName = null,
    )

    @Composable
    private fun show(state: ReminderUiState.Card) {
        SklyankaTheme(dark = true) {
            ReminderScreen(
                state = state,
                actions = ReminderActions(
                    onTaken = {},
                    onSkipped = {},
                    onTakenAll = {},
                    onSkippedAll = {},
                    onSnooze = {},
                ),
            )
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, content: @Composable () -> Unit) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "$GROUP/$name.png"),
            roborazziComposeOptions = RoborazziComposeOptions { size(widthDp = WIDTH, heightDp = HEIGHT) },
            content = content,
        )
    }

    private companion object {
        const val GROUP = "screen"
        const val WIDTH = 390
        const val HEIGHT = 844
        const val SNOOZE_MINUTES = 15
    }
}
