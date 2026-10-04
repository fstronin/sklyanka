package ru.stohastika.sklyanka.feature.history

import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.BuildDoctorReportUseCase
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.domain.history.ObserveAdherenceUseCase
import ru.stohastika.sklyanka.domain.history.TransferDoctorReportUseCase
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDate

/**
 * Стенд нажатия «Передать» через настоящий маршрут истории (SKL-101, дефект 4).
 *
 * Собирает настоящий `HistoryViewModel` на заготовках истории, настоящий `HistoryRoute`
 * и записывающий владелец реестра результатов: системного выбора получателя на JVM нет,
 * поэтому запуск представлен записью в реестре, а возврат — ответом реестра. Успех
 * обязан ждать этого ответа: пока диалог открыт, исход неизвестен.
 */
@OptIn(ExperimentalTestApi::class)
internal class ReportShareStand {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val activeProfile = FakeActiveProfile()
    private val profiles = FakeProfiles(HistoryTestData.profile())
    private val history = FakeHistory()
    private val log = RecordingLog()
    private val transfer = FakeTransfer()
    private val renderer = FakeRenderer()
    private val clock = HistoryTestData.clock()

    private val registry = RecordingRegistry()

    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
    }

    val viewModel: HistoryViewModel = HistoryViewModel(
        observeAdherence = ObserveAdherenceUseCase(activeProfile, history, AdherenceCalculator(), clock, log),
        buildReport = BuildDoctorReportUseCase(activeProfile, profiles, history, AdherenceCalculator(), clock, log),
        transferReport = TransferDoctorReportUseCase(transfer, activeProfile, log),
        renderer = renderer,
        missedPattern = MissedPatternRule(),
        time = clock,
    )

    init {
        history.put(
            profileId = HistoryTestData.PROFILE_ID,
            intakes = listOf(HistoryTestData.intake(LocalDate.of(2026, 9, 20).atTime(HistoryTestData.MORNING))),
            medications = listOf(HistoryTestData.medication()),
        )
    }

    fun openReport() {
        viewModel.onIntent(HistoryIntent.ReportRequested)
    }

    /** Нажать «Передать», снять наблюдаемое и ответить системным выбором. */
    fun pressShare(andAnswer: Int): ReportSharePress {
        var facts: ReportSharePress? = null
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                    SklyankaTheme { HistoryRoute(viewModel = viewModel, onBack = {}) }
                }
            }
            waitForIdle()
            onNodeWithTag(TAG_REPORT_SHARE).performClick()
            waitForIdle()
            val whileOpen = viewModel.state.value.reportOutcome
            val chooser = registry.launches.lastOrNull()
            registry.answer(andAnswer)
            waitForIdle()
            facts = ReportSharePress(
                outcomeWhileOpen = whileOpen,
                chooser = chooser,
                outcomeAfter = viewModel.state.value.reportOutcome,
            )
        }
        return requireNotNull(facts)
    }

    /** Наблюдаемое одного нажатия: исход до и после ответа и запущенное намерение. */
    data class ReportSharePress(
        val outcomeWhileOpen: ReportOutcome?,
        val chooser: Intent?,
        val outcomeAfter: ReportOutcome?,
    )

    /**
     * Реестр результатов: помнит запуск и умеет ответить на него.
     *
     * Системный `ChooserActivity` на JVM не существует; ответ реестра — та же точка,
     * из которой `rememberLauncherForActivityResult` получает `RESULT_OK`/`RESULT_CANCELED`.
     */
    private class RecordingRegistry : ActivityResultRegistry() {

        val launches: MutableList<Intent> = mutableListOf()

        private var lastRequest = -1

        override fun <I : Any?, O : Any?> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            (input as? Intent)?.let { launches += it }
            lastRequest = requestCode
        }

        /** Ответить на последний запуск тем же способом, каким это делает система. */
        fun answer(resultCode: Int) {
            check(lastRequest >= 0) { "nothing has been launched" }
            dispatchResult(lastRequest, ActivityResult(resultCode, null))
        }
    }
}
