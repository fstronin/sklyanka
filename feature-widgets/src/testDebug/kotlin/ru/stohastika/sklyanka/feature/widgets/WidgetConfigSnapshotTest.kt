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
 * Снимков два — по одному на состояние платной границы: без подписки (слова причины над
 * кнопкой, кнопка «Добавить виджет» и не приглушена) и с подпиской и выбранным лекарством
 * (завершение доступно). Витрина и её состояния снимаются своим эталоном (SKL-122), а плиты
 * виджетов здесь не рисуются: Glance собирает RemoteViews, и кадры типов приходят
 * с устройства (фаза 6.2).
 *
 * Ширина окна — телефонная, как у настоящего экрана: он открывается системой поверх
 * домашнего экрана прошивки (НФТ-43).
 */
@RunWith(AndroidJUnit4::class)
class WidgetConfigSnapshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun withoutSubscription() {
        capture(fileName = "widget-config-no-pro.png", state = state(proIncluded = false))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun withSubscriptionAndMedication() {
        capture(
            fileName = "widget-config-pro.png",
            state = state(proIncluded = true, type = WidgetType.MEDICATION, selectedMedicationId = "mag"),
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
                        onOpenPro = {},
                    )
                }
            }
        }
    }

    private fun state(
        proIncluded: Boolean,
        type: WidgetType = WidgetType.TODAY,
        selectedMedicationId: String? = null,
    ): WidgetConfigState = WidgetConfigState(
        type = type,
        medications = listOf(magnesium()),
        selectedMedicationId = selectedMedicationId,
        proIncluded = proIncluded,
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
