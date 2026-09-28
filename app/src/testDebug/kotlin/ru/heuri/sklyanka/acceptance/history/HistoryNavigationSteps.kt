package ru.heuri.sklyanka.acceptance.history

import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.history.AdherenceCalculator
import ru.heuri.sklyanka.domain.history.BuildDoctorReportUseCase
import ru.heuri.sklyanka.domain.history.MissedPatternRule
import ru.heuri.sklyanka.domain.history.ObserveAdherenceUseCase
import ru.heuri.sklyanka.domain.history.TransferDoctorReportUseCase
import ru.heuri.sklyanka.feature.history.HistoryViewModel
import ru.heuri.sklyanka.feature.history.TextDoctorReportRenderer
import ru.heuri.sklyanka.feature.settings.R
import ru.heuri.sklyanka.feature.settings.SettingsIntent
import ru.heuri.sklyanka.feature.settings.SettingsScreen
import ru.heuri.sklyanka.feature.settings.SettingsUiState
import ru.heuri.sklyanka.navigation.HistoryOrigin
import ru.heuri.sklyanka.navigation.HistorySection
import ru.heuri.sklyanka.platform.report.SystemDoctorReportTransfer
import ru.heuri.sklyanka.ui.locale.withInterfaceLocale
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.R as UiR

/**
 * Экранные шаги SKL-18: вход в историю из настроек и возврат (группа `@composition`).
 *
 * Модуль выбран по правилу `TDD.md §4.3`: сценарий говорит и о настройках, и об истории,
 * а оба экрана существуют одновременно только в `app`. Здесь собирается настоящая связка
 * продукта: строка «История приёмов» экрана настроек отдаёт `OpenHistory`, а `HistorySection`
 * с местом входа `SETTINGS` ведёт возврат туда же — тем же значением, каким его собирает
 * `SettingsSection`.
 *
 * Сам `HistoryViewModel` — настоящий: история обязана быть собрана из мира сценария
 * (`world.history`), а не подставлена заглушкой. Сквозной путь через вкладки нижней панели
 * остаётся работой `MainActivity`: стенд собирает те же звенья по отдельности.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class HistoryNavigationSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resources = context.withInterfaceLocale().resources

    private var leftWith: HistoryOrigin? = null

    @Когда("^он нажимает «Назад»$")
    fun hePressesBack() {
        val viewModel = historyViewModel()
        runComposeUiTest {
            setContent {
                Pinned {
                    HistorySection(
                        viewModel = viewModel,
                        origin = HistoryOrigin.SETTINGS,
                        onLeave = { origin -> leftWith = origin },
                    )
                }
            }
            onNodeWithContentDescription(resources.getString(UiR.string.cd_back)).performClick()
            waitForIdle()
        }
    }

    @Тогда("^он возвращается в настройки$")
    fun heReturnsToSettings() {
        assertEquals(
            "the return must lead to the place the history was opened from (ПРФ-11)",
            HistoryOrigin.SETTINGS,
            leftWith,
        )
        // Вход действительно существует: строка «История приёмов» на экране настроек
        // отдаёт то же намерение, которое `SettingsSection` превращает в этот маршрут.
        val intents = mutableListOf<SettingsIntent>()
        runComposeUiTest {
            setContent {
                Pinned {
                    SettingsScreen(state = SettingsUiState(), onIntent = intents::add)
                }
            }
            onNodeWithText(resources.getString(R.string.settings_history)).performClick()
            waitForIdle()
        }
        assertTrue(
            "the settings screen must offer the entry into the history: $intents",
            intents.any { it is SettingsIntent.OpenHistory },
        )
    }

    /** Тот же маршрут, что у продукта: настоящий ViewModel на мире сценария. */
    private fun historyViewModel(): HistoryViewModel = HistoryViewModel(
        observeAdherence = ObserveAdherenceUseCase(
            activeProfile = world.profileSession.activeStore,
            history = world.history,
            calculator = AdherenceCalculator(),
            time = world.clock.time,
            log = world.log,
        ),
        buildReport = BuildDoctorReportUseCase(
            activeProfile = world.profileSession.activeStore,
            profiles = world.repositories.profileStore,
            history = world.history,
            calculator = AdherenceCalculator(),
            time = world.clock.time,
            log = world.log,
        ),
        transferReport = TransferDoctorReportUseCase(
            transfer = SystemDoctorReportTransfer(context),
            activeProfile = world.profileSession.activeStore,
            log = world.log,
        ),
        renderer = TextDoctorReportRenderer(context),
        missedPattern = MissedPatternRule(),
        time = world.clock.time,
    )

    /** Язык интерфейса закреплён: шаги зовут строки по-русски при любом телефоне (НФТ-43). */
    @androidx.compose.runtime.Composable
    private fun Pinned(content: @androidx.compose.runtime.Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale()
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
            // Системный выбор места живёт в `HistoryRoute` и требует владельца реестра
            // результатов: в стенде его даёт заглушка платформенного порта, потому что
            // сценарий говорит о возврате, а не о передаче отчёта.
            LocalActivityResultRegistryOwner provides NoResultRegistryOwner,
        ) {
            SklyankaTheme { content() }
        }
    }

    /**
     * Владелец реестра результатов без результата.
     *
     * Порт платформы, а не правило продукта: `rememberLauncherForActivityResult`
     * регистрируется у него при сборке маршрута, а сам выбор места сценарий не открывает.
     */
    private object NoResultRegistryOwner : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
            override fun <I : Any?, O : Any?> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) = Unit
        }
    }
}
