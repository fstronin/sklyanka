package ru.stohastika.sklyanka.acceptance.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.feature.settings.PermissionsSummary
import ru.stohastika.sklyanka.feature.settings.ProfileRowState
import ru.stohastika.sklyanka.feature.settings.SettingsScreen
import ru.stohastika.sklyanka.feature.settings.SettingsUiState
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfileColor
import java.util.Locale

/**
 * Экранные шаги приёмки локализации (SKL-62, группа `@screen`).
 *
 * Язык приходит из сценария, а не из квалификатора прогона: сценарии перечисляют шесть
 * языков, и заводить по прогону на язык значило бы держать шесть почти одинаковых
 * проверок. Контекст собирается тем же путём, что в продукте: сначала язык устройства,
 * затем выбор приложения (`InterfaceLocale.resolve`) — поэтому шаг падает, если запасной
 * русский перестанет работать.
 */
@OptIn(ExperimentalTestApi::class)
class LocalizationSteps(private val world: AcceptanceWorld) {

    private var languageTag: String = "ru"
    private var shown: List<String> = emptyList()

    @Допустим("^язык интерфейса — «([^»]+)»$")
    fun theInterfaceLanguageIs(language: String) {
        languageTag = LANGUAGES[language] ?: error("the scenario names an unknown language: $language")
    }

    @Когда("^человек открывает настройки на этом языке$")
    fun thePersonOpensSettingsInThatLanguage() {
        render { SettingsScreen(state = settingsState(), onIntent = {}) }
    }

    @Когда("^человек смотрит на шапку «Сегодня» на этом языке$")
    fun thePersonLooksAtTodayInThatLanguage() {
        render { TodayScreen(state = TodayFromWorld.state(world), onIntent = {}) }
    }

    @Тогда("^на экране есть «([^»]+)»$")
    fun theScreenShows(expected: String) {
        assertTrue(shown.any { it == expected }, "no «$expected» on the screen: ${shown.take(SAMPLE)}")
    }

    @Тогда("^заголовок называет дату «([^»]+)»$")
    fun theHeaderNamesTheDate(expected: String) {
        assertTrue(
            shown.any { it.equals(expected, ignoreCase = true) },
            "no «$expected» in the header: ${shown.take(SAMPLE)}",
        )
    }

    private fun render(content: @Composable () -> Unit) {
        val deviceLanguage = Locale.forLanguageTag(languageTag)
        runComposeUiTest {
            setContent {
                val device = LocalContext.current.withDeviceLanguage(deviceLanguage)
                val pinned = device.withInterfaceLocale()
                CompositionLocalProvider(
                    LocalContext provides pinned,
                    LocalConfiguration provides pinned.resources.configuration,
                ) {
                    SklyankaTheme { content() }
                }
            }
            shown = texts(onRoot(useUnmergedTree = true).fetchSemanticsNode())
        }
    }

    /** Язык телефона до выбора приложения: продукт сам решает, какой язык объявлен. */
    private fun Context.withDeviceLanguage(locale: Locale): Context {
        val configuration = Configuration(resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return createConfigurationContext(configuration)
    }

    private fun texts(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { texts(it) }

    private fun settingsState() = SettingsUiState(
        profiles = listOf(
            ProfileRowState(
                id = "self",
                name = world.profiles.active,
                colorArgb = ProfileColor.Teal.argb,
                isSelf = true,
                isActive = true,
                medicationCount = 0,
                mirrorReminders = false,
            ),
            ProfileRowState(
                id = "foreign",
                name = FOREIGN,
                colorArgb = ProfileColor.Terracotta.argb,
                isSelf = false,
                isActive = false,
                medicationCount = FOREIGN_MEDICATIONS,
                mirrorReminders = false,
            ),
        ),
        reminders = ReminderSettingsValues(
            persistentEnabled = true,
            snoozeMinutes = world.reminders.settings().snoozeMinutes,
        ),
        permissions = PermissionsSummary(denied = 0, manual = 1),
        version = "0.1.0",
    )

    private companion object {
        const val FOREIGN = "Мама"
        const val FOREIGN_MEDICATIONS = 5
        const val SAMPLE = 20

        /** Имена языков из сценариев: сценарий говорит словами, контекст — тегом. */
        val LANGUAGES = mapOf(
            "русский" to "ru",
            "английский (США)" to "en-US",
            "испанский" to "es",
            "немецкий" to "de",
            "китайский (упрощённый)" to "zh-CN",
            "казахский" to "kk",
            "нидерландский" to "nl",
            "украинский" to "uk",
        )
    }
}
