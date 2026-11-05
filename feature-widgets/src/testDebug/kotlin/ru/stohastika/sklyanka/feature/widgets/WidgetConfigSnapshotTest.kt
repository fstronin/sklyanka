package ru.stohastika.sklyanka.feature.widgets

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.feature.widgets.config.WidgetConfigScreen
import ru.stohastika.sklyanka.feature.widgets.config.WidgetConfigState
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetType
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.io.File

/**
 * Эталонные снимки экрана настройки виджета (SKL-118, §5.12).
 *
 * Снимков два — по одному на состояние экрана: тип «Сегодня» (выбор
 * по умолчанию) и «По лекарству» с выбранным лекарством (завершение доступно).
 * Плиты виджетов здесь не рисуются: Glance собирает RemoteViews, и кадры
 * типов приходят с устройства (фаза 6.2).
 *
 * Ширина окна — телефонная, как у настоящего экрана: он открывается системой поверх
 * домашнего экрана прошивки (НФТ-43).
 */
@RunWith(AndroidJUnit4::class)
class WidgetConfigSnapshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun todayWithoutMedication() {
        capture(fileName = "widget-config-today.png", state = state())
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun medicationChosen() {
        capture(
            fileName = "widget-config-medication.png",
            state = state(type = WidgetType.MEDICATION, selectedMedicationId = "mag"),
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(fileName: String, state: WidgetConfigState) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "screen/$fileName"),
            roborazziComposeOptions = RoborazziComposeOptions { size(widthDp = WIDTH, heightDp = HEIGHT) },
            roborazziOptions = RoborazziOptions(),
        ) {
            ProfileChrome(state = state.profile) {
                SklyankaTheme {
                    WidgetConfigScreen(
                        state = state,
                        onSelectType = {},
                        onSelectMedication = {},
                        onComplete = {},
                    )
                }
            }
        }
    }

    private fun state(type: WidgetType = WidgetType.TODAY, selectedMedicationId: String? = null): WidgetConfigState =
        WidgetConfigState(
            type = type,
            medications = listOf(magnesium()),
            selectedMedicationId = selectedMedicationId,
            profile = ProfileChromeState.Owner,
        )

    private fun magnesium() = ru.stohastika.sklyanka.domain.model.Medication(
        id = "mag",
        profileId = "self",
        name = "Магний B6",
        form = ru.stohastika.sklyanka.domain.model.MedicationForm.TABLET,
        shape = ru.stohastika.sklyanka.domain.model.TabletShape.ROUND,
        doseAmount = java.math.BigDecimal.ONE,
        doseUnit = "tablet",
        strength = null,
        strengthUnit = null,
        foodRelation = ru.stohastika.sklyanka.domain.model.FoodRelation.ANY,
        scheduleMode = ru.stohastika.sklyanka.domain.model.ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = listOf(java.time.LocalTime.of(8, 0)),
        startDate = java.time.LocalDate.of(2026, 10, 1),
        endDate = null,
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = null,
        archiveReason = null,
    )

    private companion object {
        const val WIDTH = 390
        const val HEIGHT = 844
    }
}
