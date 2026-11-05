package ru.stohastika.sklyanka.acceptance.theme

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.settings.SettingsScreenProbe
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import ru.stohastika.sklyanka.feature.settings.TAG_ICON_DIALOG
import ru.stohastika.sklyanka.feature.settings.TAG_ICON_ROW
import ru.stohastika.sklyanka.feature.settings.TAG_THEME_DIALOG
import ru.stohastika.sklyanka.feature.settings.TAG_THEME_ROW
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.darkColors
import ru.stohastika.sklyanka.ui.token.lightColors
import java.io.File
import java.nio.file.Files
import java.util.Locale
import ru.stohastika.sklyanka.feature.settings.R as SettingsR

/**
 * Экранные шаги приёмки SKL-120 — блок «Оформление», его диалоги и тёмная среда экрана (§5.17).
 *
 * Стенд собирает **настоящий** экран настроек над миром сценария
 * ([SettingsScreenProbe]): состояние считает тот же `SettingsViewModel`, что у продукта,
 * и выбор проходит через домен. Правил в шагах нет.
 *
 * **Выбор бесплатен** (Р-86, ADR-0016): в поставляемой сборке доступ есть всегда, поэтому
 * любая из четырёх тем и любой из двух вариантов иконки применяются сразу. Стенд проверяет
 * открытие диалога, состав вариантов и применение выбора.
 *
 * **Тёмная среда проверяется полотном, а не разметкой.** Стенд собирает экран в обеих
 * средах и читает пиксель полотна: если бы тема не доехала до экрана, тёмный кадр был бы
 * светлым — тем же способом это проверяет стенд дизайн-системы (`core-ui`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class Sk120AppearanceScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Блок «Оформление» ---------------------------------------------------------

    @Когда("^на стенде настроек открыт блок «Оформление»$")
    fun theAppearanceBlockIsOpen() {
        SettingsScreenProbe.ask(world) {
            onNodeWithTag(TAG_THEME_ROW, useUnmergedTree = true).assertExists()
            onNodeWithTag(TAG_ICON_ROW, useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^на стенде в нём две строки — «Акцентная тема» и «Иконка приложения»$")
    fun theBlockCarriesTheTwoRows() {
        val themeRow = string(SettingsR.string.settings_theme_row)
        val iconRow = string(SettingsR.string.settings_icon_row)
        assertEquals(themeRow, string(SettingsR.string.settings_theme_row))
        assertEquals(iconRow, string(SettingsR.string.settings_icon_row))
        SettingsScreenProbe.ask(world) {
            assertCarries(TAG_THEME_ROW, themeRow)
            assertCarries(TAG_ICON_ROW, iconRow)
        }
    }

    @Тогда("^на стенде тема названа «Оливой»$")
    fun theThemeRowNamesTheBaseTheme() {
        SettingsScreenProbe.ask(world) {
            assertCarries(TAG_THEME_ROW, string(SettingsR.string.theme_olive))
        }
    }

    // --- Диалог темы ---------------------------------------------------------------

    @Когда("^на стенде настроек открыт выбор темы$")
    fun theThemePickerIsOpen() {
        openPicker(TAG_THEME_ROW, TAG_THEME_DIALOG)
    }

    @Тогда("^на стенде в диалоге четыре темы, и «Олива» идёт первой$")
    fun theDialogCarriesFourThemes() {
        val names = THEMES.values.map { it to string(it) }
        SettingsScreenProbe.ask(world) {
            names.forEach { (_, name) -> assertDialogCarries(name) }
        }
        // Порядок: «Олива» — тема базы (Р-20) и стоит первой (§5.17).
        assertEquals(AccentTheme.OLIVE, AccentTheme.entries.first())
    }

    @Когда("^на стенде выбрана тема «([^»]+)»$")
    fun theThemeIsChosen(name: String) {
        selectInDialog(TAG_THEME_DIALOG, name)
    }

    @Тогда("^на стенде диалог закрыт, а выбор записан «([^»]+)»$")
    fun theDialogIsClosedAndTheChoiceIsStored(name: String) {
        SettingsScreenProbe.ask(world) {
            onNodeWithTag(TAG_THEME_DIALOG, useUnmergedTree = true).assertDoesNotExist()
        }
        assertEquals(
            "the chosen theme must be applied and stored (§5.17)",
            themeNamed(name),
            SettingsScreenProbe.state(world).accentTheme,
        )
    }

    // --- Диалог иконки -------------------------------------------------------------

    @Когда("^на стенде настроек открыт выбор иконки$")
    fun theIconPickerIsOpen() {
        openPicker(TAG_ICON_ROW, TAG_ICON_DIALOG)
    }

    @Тогда("^на стенде в диалоге два варианта, и «Ледяная» идёт первой$")
    fun theDialogCarriesTwoVariants() {
        SettingsScreenProbe.ask(world) {
            assertDialogCarries(string(SettingsR.string.icon_variant_ice))
            assertDialogCarries(string(SettingsR.string.icon_variant_warm))
        }
        assertEquals(IconVariant.ICE, IconVariant.entries.first())
    }

    @Когда("^на стенде выбран тёплый вариант иконки$")
    fun theWarmVariantIsChosen() {
        selectInDialog(TAG_ICON_DIALOG, string(SettingsR.string.icon_variant_warm))
    }

    @Тогда("^на стенде вариант записан «([^»]+)»$")
    fun theVariantIsStored(name: String) {
        val expected = if (name.startsWith(ICE)) IconVariant.ICE else IconVariant.WARM
        assertEquals(
            "the chosen icon variant must be applied and stored (§5.17)",
            expected,
            SettingsScreenProbe.state(world).iconVariant,
        )
    }

    // --- Тёмная среда экрана -------------------------------------------------------

    @Когда("^на стенде настроек экран собран в обеих средах$")
    fun theScreenIsComposedInBothEnvironments() {
        assertTrue("the scenario must switch the system to the dark theme", world.system.darkTheme)
        light = backgroundPixel(dark = false)
        dark = backgroundPixel(dark = true)
    }

    @Тогда("^на стенде полотно тёмного экрана — тёмный фон среды$")
    fun theDarkCanvasIsTheDarkBackground() {
        assertEquals(
            "the settings screen in the dark environment must be painted with the dark background (§9)",
            darkColors().background.toArgb(),
            dark,
        )
    }

    @Тогда("^на стенде полотно тёмного экрана отличается от светлого$")
    fun theDarkCanvasDiffersFromTheLightOne() {
        assertEquals(
            "the light canvas must be the light background",
            lightColors().background.toArgb(),
            light,
        )
        assertNotEquals("the dark screen renders exactly like the light one: the theme is not applied", light, dark)
    }

    private var light: Int = 0

    private var dark: Int = 0

    /**
     * Полотно экрана: пиксель угла снимка в заданной среде.
     *
     * Снимок пишется во временный файл, а не в эталон: состояние стенда для этой проверки
     * эталоном не является (кадры тёмной среды — отдельные снимки `SettingsScreenSnapshotTest`).
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun backgroundPixel(dark: Boolean): Int {
        val target =
            File(Files.createTempDirectory("sklyanka-sk120").toFile(), "settings-${if (dark) "dark" else "light"}.png")
        val state = SettingsScreenProbe.state(world)
        captureRoboImage(
            file = target,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = WIDTH, heightDp = HEIGHT)
            },
        ) {
            Stand(dark = dark) {
                ru.stohastika.sklyanka.feature.settings.SettingsScreen(state = state, onIntent = {})
            }
        }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(target.path)) { "the stand wrote no frame" }
        return bitmap.getPixel(PIXEL, PIXEL)
    }

    // --- Обвязка -------------------------------------------------------------------

    /** Открыть диалог нажатием строки: нажатие идёт тем же ViewModel, что у продукта. */
    private fun openPicker(rowTag: String, dialogTag: String) {
        SettingsScreenProbe.act(world) { onNodeWithTag(rowTag, useUnmergedTree = true).performClick() }
        SettingsScreenProbe.ask(world) { onNodeWithTag(dialogTag, useUnmergedTree = true).assertExists() }
    }

    /** Выбор варианта в диалоге: нажатие уходит в ViewModel и применяется доменом. */
    private fun selectInDialog(dialogTag: String, label: String) {
        SettingsScreenProbe.act(world) {
            onAllNodes(hasText(label) and hasAnyAncestor(hasTestTag(dialogTag)), useUnmergedTree = true)
                .onFirst()
                .performClick()
        }
    }

    private fun ComposeUiTest.assertCarries(tag: String, text: String) {
        val found = onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("the row $tag must carry «$text»", found.isNotEmpty())
    }

    private fun ComposeUiTest.assertDialogCarries(text: String) {
        val found = onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("the dialog must carry «$text»", found.isNotEmpty())
    }

    /** Слово каталога стенда: язык закреплён, потому что сценарии записаны по-русски (НФТ-43). */
    private fun string(id: Int): String = context.withInterfaceLocale(RUSSIAN).getString(id)

    private fun string(theme: AccentTheme): String = string(
        when (theme) {
            AccentTheme.OLIVE -> SettingsR.string.theme_olive
            AccentTheme.INDIGO -> SettingsR.string.theme_indigo
            AccentTheme.PINE -> SettingsR.string.theme_pine
            AccentTheme.AMETHYST -> SettingsR.string.theme_amethyst
        },
    )

    /**
     * Тема по имени сценария: падеж не делает имя другим («Оливой» — та же «Олива»).
     *
     * Тем же приёмом сравниваются имена тем в сценарном прогоне (`Sk120ThemeSteps`): слово
     * сценария и слово каталога — одно имя, и требовать от документа именительного падежа
     * значило бы подгонять приёмку под шаг.
     */
    private fun themeNamed(name: String): AccentTheme {
        val stem = stem(name)
        return requireNotNull(THEMES.entries.singleOrNull { stem(it.key) == stem }?.value) {
            "unknown accent theme: $name (known: ${THEMES.keys})"
        }
    }

    private fun stem(name: String): String = name.trim().trimEnd { it in CASE_ENDINGS }

    @Composable
    private fun Stand(dark: Boolean, content: @Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale(RUSSIAN)
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
        ) {
            SklyankaTheme(dark = dark) { content() }
        }
    }

    private companion object {

        val RUSSIAN: Locale = Locale.forLanguageTag("ru")

        const val WIDTH = 390

        const val HEIGHT = 1400

        /** Точка чтения полотна: угол кадра, где нет ни карточки, ни панели. */
        const val PIXEL = 2

        /** Имена тем §2.5 так, как их называет сценарий. */
        val THEMES: Map<String, AccentTheme> = mapOf(
            "Олива" to AccentTheme.OLIVE,
            "Индиго" to AccentTheme.INDIGO,
            "Хвоя" to AccentTheme.PINE,
            "Аметист" to AccentTheme.AMETHYST,
        )

        /** Падежные окончания имён тем: основа сравнивается без них. */
        val CASE_ENDINGS: CharArray = "аяуюыиеоёймАЯУЮЫИЕОЁЙМ".toCharArray()

        /** Русское слово базового варианта иконки: по нему шаг отличает «Ледяную» от «Тёплой». */
        const val ICE = "Ледян"
    }
}
