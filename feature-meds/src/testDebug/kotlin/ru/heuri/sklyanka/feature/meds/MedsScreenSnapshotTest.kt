package ru.heuri.sklyanka.feature.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.domain.medication.ArchiveCaption
import ru.heuri.sklyanka.domain.medication.ArchiveCard
import ru.heuri.sklyanka.domain.medication.ArchiveContent
import ru.heuri.sklyanka.domain.medication.CourseProgress
import ru.heuri.sklyanka.domain.medication.DayCell
import ru.heuri.sklyanka.domain.medication.DoseUnit
import ru.heuri.sklyanka.domain.medication.MedicationDraft
import ru.heuri.sklyanka.domain.medication.MedicationSubtitle
import ru.heuri.sklyanka.domain.medication.TrailingLine
import ru.heuri.sklyanka.domain.medication.WeekAdherenceStrip
import ru.heuri.sklyanka.domain.model.FoodRelation
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.model.TabletShape
import ru.heuri.sklyanka.domain.stock.StockPanel
import ru.heuri.sklyanka.feature.meds.archive.ArchiveScreen
import ru.heuri.sklyanka.feature.meds.archive.ArchiveUiState
import ru.heuri.sklyanka.feature.meds.card.MedCardUiState
import ru.heuri.sklyanka.feature.meds.card.MedicationCardScreen
import ru.heuri.sklyanka.feature.meds.card.ScheduleBlockUi
import ru.heuri.sklyanka.feature.meds.card.WatchMirrorUi
import ru.heuri.sklyanka.feature.meds.form.FormInputs
import ru.heuri.sklyanka.feature.meds.form.FormMode
import ru.heuri.sklyanka.feature.meds.form.MedFormUiState
import ru.heuri.sklyanka.feature.meds.form.MedicationFormScreen
import ru.heuri.sklyanka.feature.meds.list.MedicationCardUi
import ru.heuri.sklyanka.feature.meds.list.MedicationListScreen
import ru.heuri.sklyanka.feature.meds.list.MedsUiState
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Эталонные снимки экранов лекарств (SKL-24, критерий 5).
 *
 * Список, форма, карточка и архив — по снимку на основное состояние. Числа те же,
 * что в проверках состава этих экранов, чтобы снимок и проверка говорили об одном
 * экране, а не о двух похожих.
 *
 * Названия латиницей: заглушки не изображают правдоподобных лекарств (НФТ-19).
 */
@RunWith(AndroidJUnit4::class)
class MedsScreenSnapshotTest {

    @Test
    fun theMedicationList() = capture("meds-list") {
        MedicationListScreen(
            state = MedsUiState(
                activeCount = ACTIVE_COUNT,
                archiveCount = ARCHIVE_COUNT,
                items = listOf(scheduledCard(), asNeededCard()),
                loading = false,
            ),
            onIntent = {},
        )
    }

    @Test
    fun theEmptyMedicationList() = capture("meds-list-empty") {
        MedicationListScreen(state = MedsUiState(loading = false), onIntent = {})
    }

    @Test
    fun theMedicationForm() = capture("meds-form", heightDp = TALL) {
        MedicationFormScreen(
            state = MedFormUiState.of(draft(), FormMode.Create, FormInputs()),
            onIntent = {},
            onCancel = {},
            onPickDate = {},
            onPickTime = { _, _ -> },
        )
    }

    @Test
    fun theMedicationCard() = capture("meds-card", heightDp = TALL) {
        MedicationCardScreen(state = cardState(), onIntent = {})
    }

    @Test
    fun theArchive() = capture("meds-archive") {
        ArchiveScreen(state = archiveState(), onIntent = {})
    }

    private fun scheduledCard() = MedicationCardUi(
        medicationId = "scheduled",
        title = "med-a",
        form = MedicationForm.TABLET,
        strength = "50",
        strengthUnit = "mg",
        timesPerDay = 1,
        times = listOf(LocalTime.of(8, 0)),
        trailingLine = TrailingLine.Stock(BigDecimal("9"), DoseUnit.TABLET),
        asNeeded = null,
    )

    private fun asNeededCard() = MedicationCardUi(
        medicationId = "as-needed",
        title = "med-b",
        form = MedicationForm.CAPSULE,
        strength = "200",
        strengthUnit = "mg",
        timesPerDay = 0,
        times = emptyList(),
        trailingLine = TrailingLine.Period(START_DATE, null),
        asNeeded = ru.heuri.sklyanka.feature.meds.list.AsNeededCardUi(
            maxPerDay = MAX_PER_DAY,
            lastIntakeDate = LocalDate.of(2026, 9, 19),
            takeEnabled = true,
        ),
    )

    private fun draft() = MedicationDraft(
        id = null,
        profileId = PROFILE_ID,
        name = "med-a",
        form = MedicationForm.TABLET,
        scheduleMode = ScheduleMode.SCHEDULED,
        times = listOf(LocalTime.of(8, 0)),
        startDate = START_DATE,
    )

    private fun cardState() = MedCardUiState(
        medicationId = "medication-1",
        title = "med-a",
        form = MedicationForm.TABLET,
        subtitle = MedicationSubtitle(
            form = MedicationForm.TABLET,
            shape = TabletShape.ROUND_SCORED,
            strength = BigDecimal("48"),
            strengthUnit = "mg",
            foodRelation = FoodRelation.BEFORE_MEAL,
        ),
        schedule = ScheduleBlockUi.Scheduled(
            timesPerDay = 2,
            doseAmount = BigDecimal("2"),
            doseUnit = DoseUnit.TABLET,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        ),
        course = CourseProgress.Bounded(
            start = START_DATE,
            end = END_DATE,
            daysLeft = DAYS_LEFT,
            fraction = COURSE_FRACTION,
        ),
        stock = StockPanel.Visible(unitsLeft = BigDecimal("9"), daysLeft = STOCK_DAYS, warning = true),
        stockUnit = DoseUnit.TABLET,
        week = week(),
        watchMirror = WatchMirrorUi(checked = true, enabled = true),
        isArchived = false,
        courseFinishedOffer = false,
        refill = null,
        notice = null,
    )

    private fun week(): WeekAdherenceStrip {
        val days = (1..WEEK_PAST).map { back ->
            DayCell.Completed(date = TODAY.minusDays(back.toLong()), taken = 2, planned = 2)
        }.reversed() + DayCell.Today(TODAY)
        return WeekAdherenceStrip(days = days, taken = WEEK_TAKEN, planned = WEEK_PLANNED)
    }

    private fun archiveState() = ArchiveUiState(
        activeCount = ACTIVE_COUNT,
        archiveCount = ARCHIVE_COUNT,
        content = ArchiveContent(
            finished = listOf(
                ArchiveCard(
                    medicationId = "finished-1",
                    title = "med-a",
                    form = MedicationForm.TABLET,
                    caption = ArchiveCaption.Outcome(days = COURSE_DAYS, taken = WEEK_TAKEN, planned = WEEK_PLANNED),
                ),
            ),
            manual = listOf(
                ArchiveCard(
                    medicationId = "manual-1",
                    title = "med-b",
                    form = MedicationForm.SPRAY,
                    caption = ArchiveCaption.ArchivedOn(LocalDate.of(2026, 8, 3)),
                ),
            ),
            total = ARCHIVE_COUNT,
        ),
        loading = false,
    )

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, heightDp: Int = HEIGHT, content: @Composable () -> Unit) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "$GROUP/$name.png"),
            roborazziComposeOptions = RoborazziComposeOptions { size(widthDp = WIDTH, heightDp = heightDp) },
        ) {
            SklyankaTheme {
                Box(modifier = Modifier.fillMaxWidth().background(SklyankaTheme.colors.background)) {
                    content()
                }
            }
        }
    }

    private companion object {
        const val GROUP = "screen"
        const val WIDTH = 390
        const val HEIGHT = 844

        /** Форма и карточка длиннее экрана телефона: снимок берёт их целиком. */
        const val TALL = 1800

        const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
        const val ACTIVE_COUNT = 5
        const val ARCHIVE_COUNT = 12
        const val MAX_PER_DAY = 3
        const val DAYS_LEFT = 17
        const val STOCK_DAYS = 4
        const val COURSE_DAYS = 7
        const val COURSE_FRACTION = 0.43f
        const val WEEK_PAST = 6
        const val WEEK_TAKEN = 12
        const val WEEK_PLANNED = 14

        val TODAY: LocalDate = LocalDate.of(2026, 9, 25)
        val START_DATE: LocalDate = LocalDate.of(2026, 9, 12)
        val END_DATE: LocalDate = LocalDate.of(2026, 10, 12)
    }
}
