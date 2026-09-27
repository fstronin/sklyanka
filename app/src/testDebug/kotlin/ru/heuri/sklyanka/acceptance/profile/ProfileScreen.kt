package ru.heuri.sklyanka.acceptance.profile

import androidx.compose.runtime.Composable
import ru.heuri.sklyanka.domain.history.AdherencePeriod
import ru.heuri.sklyanka.domain.medication.MedicationDraft
import ru.heuri.sklyanka.domain.medication.MedicationSubtitle
import ru.heuri.sklyanka.domain.medication.WeekAdherenceStrip
import ru.heuri.sklyanka.domain.medication.DoseUnit
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.stock.StockPanel
import ru.heuri.sklyanka.feature.history.HistoryScreen
import ru.heuri.sklyanka.feature.history.HistoryUiState
import ru.heuri.sklyanka.feature.meds.archive.ArchiveScreen
import ru.heuri.sklyanka.feature.meds.archive.ArchiveUiState
import ru.heuri.sklyanka.feature.meds.card.MedCardUiState
import ru.heuri.sklyanka.feature.meds.card.MedicationCardScreen
import ru.heuri.sklyanka.feature.meds.card.ScheduleBlockUi
import ru.heuri.sklyanka.feature.meds.form.FormInputs
import ru.heuri.sklyanka.feature.meds.form.FormMode
import ru.heuri.sklyanka.feature.meds.form.MedFormUiState
import ru.heuri.sklyanka.feature.meds.form.MedicationFormScreen
import ru.heuri.sklyanka.feature.meds.list.MedicationListScreen
import ru.heuri.sklyanka.feature.meds.list.MedsUiState
import ru.heuri.sklyanka.feature.today.TodayScreen
import ru.heuri.sklyanka.feature.today.TodayUiState
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Шесть экранов, которые обходит `Структура сценария` SKL-15.
 *
 * Экраны настоящие, а не их подобия: сценарий говорит «экран, на котором индикация
 * исчезает, — дыра в защите» (`ПРФ-12`), и подобие экрана такую дыру не покажет.
 *
 * Состояния минимальные намеренно: индикация чужого профиля от наполнения экрана не
 * зависит — её даёт `ProfileChrome` в корне и общая шапка `ScreenHeader`. Экран с одним
 * лекарством и экран с сорока обязаны показывать её одинаково, и проверять это на
 * наполнении означало бы проверять наполнение.
 *
 * Названия и числа — заглушки латиницей: в артефактах сборки не бывает правдоподобных
 * медицинских данных (`НФТ-19`).
 */
internal sealed interface ProfileScreen {

    /** Как экран назван в сценарии. */
    val name: String

    @Composable
    fun Content()

    data object Today : ProfileScreen {
        override val name: String get() = "экран «Сегодня»"

        @Composable
        override fun Content() = TodayScreen(state = TodayUiState.Empty(DATE), onIntent = {})
    }

    data object MedicationList : ProfileScreen {
        override val name: String get() = "список лекарств"

        @Composable
        override fun Content() = MedicationListScreen(state = MedsUiState(loading = false), onIntent = {})
    }

    data object MedicationCard : ProfileScreen {
        override val name: String get() = "карточку лекарства"

        @Composable
        override fun Content() = MedicationCardScreen(state = card(), onIntent = {})
    }

    data object Archive : ProfileScreen {
        override val name: String get() = "архив"

        @Composable
        override fun Content() = ArchiveScreen(state = ArchiveUiState(loading = false), onIntent = {})
    }

    data object History : ProfileScreen {
        override val name: String get() = "историю приёмов"

        @Composable
        override fun Content() = HistoryScreen(state = history(), onIntent = {})
    }

    data object MedicationFormScreen : ProfileScreen {
        override val name: String get() = "форму нового лекарства"

        @Composable
        override fun Content() = MedicationFormScreen(
            state = form(),
            onIntent = {},
            onCancel = {},
            onPickDate = {},
            onPickTime = { _, _ -> },
        )
    }

    companion object {

        val DATE: LocalDate = LocalDate.of(2026, 9, 25)

        private const val PROFILE_ID = "profile-1"

        fun of(name: String): ProfileScreen = requireNotNull(
            listOf(Today, MedicationList, MedicationCard, Archive, History, MedicationFormScreen)
                .firstOrNull { it.name == name },
        ) { "the scenario names a screen the stand does not know: $name" }

        private fun card(): MedCardUiState = MedCardUiState(
            medicationId = "medication-1",
            title = "med-1",
            form = MedicationForm.TABLET,
            subtitle = MedicationSubtitle(
                form = MedicationForm.TABLET,
                shape = null,
                strength = null,
                strengthUnit = null,
                foodRelation = null,
            ),
            schedule = ScheduleBlockUi.Scheduled(
                timesPerDay = 1,
                doseAmount = BigDecimal.ONE,
                doseUnit = DoseUnit.TABLET,
                times = listOf(LocalTime.of(9, 0)),
            ),
            course = null,
            stock = StockPanel.Hidden,
            stockUnit = DoseUnit.TABLET,
            week = WeekAdherenceStrip(days = emptyList(), taken = 0, planned = 0),
            watchMirror = null,
            isArchived = false,
            courseFinishedOffer = false,
            refill = null,
            notice = null,
        )

        private fun history(): HistoryUiState {
            val month = YearMonth.from(DATE)
            return HistoryUiState(
                month = month,
                period = AdherencePeriod.empty(PROFILE_ID, month.atDay(1), month.atEndOfMonth()),
            )
        }

        private fun form(): MedFormUiState = MedFormUiState.of(
            draft = MedicationDraft(id = null, profileId = PROFILE_ID, startDate = DATE),
            mode = FormMode.Create,
            inputs = FormInputs(),
        )
    }
}
