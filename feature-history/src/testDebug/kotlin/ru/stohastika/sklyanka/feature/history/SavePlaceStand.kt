package ru.stohastika.sklyanka.feature.history

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.BuildDoctorReportUseCase
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.domain.history.ObserveAdherenceUseCase
import ru.stohastika.sklyanka.domain.history.TransferDoctorReportUseCase
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDate

/**
 * Стенд нажатия «Сохранить файлом» (SKL-46).
 *
 * Собирает настоящий `HistoryViewModel` на заготовках истории, настоящий `HistoryRoute`
 * и записывающий владелец реестра результатов: системного `DocumentsUI` на JVM нет,
 * поэтому «запуск выбора места» представлен записью в реестре. Обработчик включается
 * и выключается регистрацией `ResolveInfo` — тем же способом, каким система отвечает
 * на `resolveActivity`.
 *
 * Мир сценария здесь не нужен: сценарии говорят о нажатии в устройстве без обработчика
 * и с ним, а история — лишь источник открытого отчёта.
 */
@OptIn(ExperimentalTestApi::class)
internal class SavePlaceStand {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val activeProfile = FakeActiveProfile()
    private val profiles = FakeProfiles(HistoryTestData.profile())
    private val history = FakeHistory()
    private val log = RecordingLog()
    private val transfer = FakeTransfer()
    private val renderer = FakeRenderer()
    private val clock = HistoryTestData.clock()

    private val launched = mutableListOf<String>()

    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
            override fun <I : Any?, O : Any?> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                launched += input.toString()
            }
        }
    }

    val viewModel: HistoryViewModel = HistoryViewModel(
        observeAdherence = ObserveAdherenceUseCase(activeProfile, history, AdherenceCalculator(), clock, log),
        buildReport = BuildDoctorReportUseCase(activeProfile, profiles, history, AdherenceCalculator(), clock, log),
        transferReport = TransferDoctorReportUseCase(transfer, activeProfile, log),
        renderer = renderer,
        missedPattern = MissedPatternRule(),
        time = clock,
    )

    /** Что осталось после нажатия: запускался ли выбор места и был ли отказ. */
    var lastPress: SavePress? = null
        private set

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

    /** Устройство без системного выбора места: обработчика нет. */
    fun withoutPlaceChooser() {
        shadowOf(context.packageManager).removeResolveInfosForIntent(probe(), CHOOSER_PACKAGE)
    }

    /** Устройство с системным выбором места: обработчик зарегистрирован. */
    fun withPlaceChooser() {
        shadowOf(context.packageManager).addResolveInfoForIntent(probe(), chooser())
    }

    /** Нажать «Сохранить файлом» в настоящем маршруте и снять наблюдаемое. */
    fun pressSave() {
        var facts: SavePress? = null
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                    SklyankaTheme { HistoryRoute(viewModel = viewModel, onBack = {}) }
                }
            }
            waitForIdle()
            onNodeWithTag(TAG_REPORT_SAVE).performClick()
            waitForIdle()
            facts = SavePress(
                launched = launched.toList(),
                refused = onAllNodesWithText(context.getString(R.string.history_report_save_failed))
                    .fetchSemanticsNodes().isNotEmpty(),
            )
        }
        lastPress = requireNotNull(facts)
    }

    val outcome: ReportOutcome? get() = viewModel.state.value.reportOutcome

    val isReportOpen: Boolean get() = viewModel.state.value.report != null

    /** Намерение-проба: тот же контракт и имя файла, что у запуска. */
    private fun probe(): Intent = ActivityResultContracts.CreateDocument(REPORT_MEDIA_TYPE)
        .createIntent(context, REPORT_FILE_NAME)

    private fun chooser() = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = CHOOSER_PACKAGE
            name = "$CHOOSER_PACKAGE.picker.PickActivity"
        }
    }

    /** Наблюдаемое одного нажатия. */
    data class SavePress(val launched: List<String>, val refused: Boolean)

    private companion object {
        const val REPORT_MEDIA_TYPE = "application/pdf"
        const val REPORT_FILE_NAME = "report.pdf"
        const val CHOOSER_PACKAGE = "com.android.documentsui"
    }
}
