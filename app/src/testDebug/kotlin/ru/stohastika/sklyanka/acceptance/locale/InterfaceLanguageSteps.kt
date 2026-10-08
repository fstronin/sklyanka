package ru.stohastika.sklyanka.acceptance.locale

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
import androidx.test.platform.app.InstrumentationRegistry
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.data.room.SklyankaConverters
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.feature.settings.PermissionsSummary
import ru.stohastika.sklyanka.feature.settings.ProfileRowState
import ru.stohastika.sklyanka.feature.settings.SettingsScreen
import ru.stohastika.sklyanka.feature.settings.SettingsUiState
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfileColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * Экранные шаги приёмки языка интерфейса (SKL-28, группа `@screen`).
 *
 * Модуль выбран не по удобству: склонения живут на экране настроек, дата — в шапке
 * «Сегодня», а шаг Предыстории «язык системы телефона — английский» объявляется один раз
 * на весь словарь. Единственное место, где оба экрана существуют одновременно, — `app`;
 * так же и по той же причине здесь лежит приёмка профилей (SKL-26).
 *
 * Экраны рисуются контекстом с закреплённым языком — тем же, каким их рисует продукт, —
 * а язык телефона при этом чужой: его задаёт квалификатор прогона.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class InterfaceLanguageSteps(private val world: AcceptanceWorld) {

    private var foreignMedications: Int = 0
    private var shown: List<String> = emptyList()
    private var russianExport: List<String> = emptyList()
    private var foreignExport: List<String> = emptyList()
    private var storedTime: String = ""
    private var readBack: List<LocalTime> = emptyList()

    // --- Условия ---------------------------------------------------------------

    @Допустим("^язык системы телефона — английский$")
    fun theSystemLanguageOfThePhoneIsEnglish() {
        val locale = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0]
        // Шаг не меняет язык телефона, а требует его: язык задаёт квалификатор прогона.
        // Прогон с русским телефоном ничего не доказал бы — дефект нашёлся на чужом.
        assertFalse(
            locale.language == InterfaceLocale.VALUE.language,
            "the stand must run with a device language other than the interface one, got $locale",
        )
    }

    @Допустим("^у профиля «([^»]+)» заведено лекарств: (\\d+)$")
    fun theForeignProfileHasThatManyMedications(profile: String, count: Int) {
        foreignMedications = count
    }

    // --- Действия --------------------------------------------------------------

    @Когда("^человек открывает настройки$")
    fun thePersonOpensSettings() {
        render { SettingsScreen(state = settingsState(), onIntent = {}) }
    }

    // Формулировка развода с общим шагом «человек открывает экран «Сегодня»» из приёмки
    // профилей: тот шаг открывает экран в контексте профиля и говорит о другом.
    @Когда("^человек смотрит на шапку экрана «Сегодня»$")
    fun thePersonLooksAtTheHeaderOfToday() {
        render { TodayScreen(state = TodayFromWorld.state(world), onIntent = {}) }
    }

    // --- Проверки --------------------------------------------------------------

    @Тогда("^интервал откладывания показан как «([^»]+)»$")
    fun theSnoozeIntervalIsShownAs(expected: String) {
        assertTrue(shown.any { it == expected }, "no «$expected» on the screen: ${shown.take(SAMPLE)}")
    }

    @Тогда("^строка профиля «([^»]+)» начинается с «([^»]+)»$")
    fun theProfileRowStartsWith(profile: String, expected: String) {
        assertTrue(
            shown.any { it.startsWith(expected) },
            "no row of «$profile» starting with «$expected»: ${shown.take(SAMPLE)}",
        )
    }

    @Тогда("^в заголовке показано «([^»]+)»$")
    fun theHeaderShowsThat(expected: String) {
        // Шапка набирает дату прописными — это типографика, а не другой текст.
        assertTrue(
            shown.any { it.equals(expected, ignoreCase = true) },
            "no «$expected» on the screen: ${shown.take(SAMPLE)}",
        )
    }

    // --- Хранимые значения -----------------------------------------------------

    @Допустим("^на телефоне с русским языком системы записаны время и дата приёма$")
    fun theTimeAndDateWereStoredOnARussianPhone() {
        russianExport = storedValues(InterfaceLocale.VALUE)
    }

    @Когда("^те же время и дата записаны на телефоне с английским языком системы$")
    fun theSameTimeAndDateAreStoredOnAnEnglishPhone() {
        foreignExport = storedValues(Locale.forLanguageTag("en-US"))
    }

    @Тогда("^записанные значения совпадают посимвольно$")
    fun theStoredValuesMatchCharacterByCharacter() {
        assertTrue(
            russianExport == foreignExport && russianExport.isNotEmpty(),
            "the stored values differ: $russianExport vs $foreignExport",
        )
    }

    @Допустим("^человек завёл лекарство с приёмом в (\\d{2}:\\d{2}) на телефоне с английским языком$")
    fun aMedicationWasCreatedOnAnEnglishPhone(time: String) {
        storedTime = withDefaultLocale(Locale.forLanguageTag("en-US")) {
            SklyankaConverters.timesToText(listOf(LocalTime.parse(time)))
        }
    }

    @Когда("^файл переноса с этим лекарством загружен на телефоне с русским языком$")
    fun theTransferFileIsLoadedOnARussianPhone() {
        readBack = withDefaultLocale(InterfaceLocale.VALUE) { SklyankaConverters.textToTimes(storedTime) }
    }

    @Тогда("^приём лекарства остаётся на (\\d{2}:\\d{2})$")
    fun theIntakeStaysAtThatTime(time: String) {
        assertTrue(
            readBack == listOf(LocalTime.parse(time)),
            "the stored time did not survive the move between phones: «$storedTime» read back as $readBack",
        )
    }

    // --- Обвязка ---------------------------------------------------------------

    /**
     * Хранимые значения под указанным языком системы.
     *
     * Локаль по умолчанию подменяется на время замера и возвращается обратно: она общая
     * на весь прогон, и оставленная чужой, сломала бы соседние проверки.
     */
    private fun storedValues(locale: Locale): List<String> = withDefaultLocale(locale) {
        listOf(
            SklyankaConverters.timesToText(listOf(LocalTime.of(8, 5))),
            SklyankaConverters.dateToText(LocalDate.of(2026, 9, 27)),
            SklyankaConverters.dateTimeToText(LocalDateTime.of(2026, 9, 27, 14, 0)),
        )
    }

    private fun <T> withDefaultLocale(locale: Locale, action: () -> T): T {
        val original = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            return action()
        } finally {
            Locale.setDefault(original)
        }
    }

    private fun render(content: @Composable () -> Unit) {
        runComposeUiTest {
            setContent {
                val pinned = LocalContext.current.withInterfaceLocale()
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

    private fun texts(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { texts(it) }

    /**
     * Состояние настроек: числа берутся из шагов, остальное — минимально достаточное.
     *
     * Домен здесь не нужен: проверяется склонение числительного, а число называет сам
     * сценарий. Собирать его планировщиком значило бы проверять планировщик.
     */
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
                medicationCount = foreignMedications,
                mirrorReminders = true,
            ),
        ),
        // Интервал называет общий шаг «настройка «Отложить на» — …»; здесь он читается
        // из того же мира, а не из второй копии значения (ОДИН шаг — одно объявление).
        reminders = ReminderSettingsValues(
            persistentEnabled = true,
            snoozeMinutes = world.reminders.settings().snoozeMinutes,
        ),
        permissions = PermissionsSummary(denied = 0, manual = 1),
        version = "0.1.0",
    )

    private companion object {
        const val FOREIGN = "Мама"

        /** Сколько строк экрана показать в сообщении об отказе: весь экран нечитаем. */
        const val SAMPLE = 20
    }
}
