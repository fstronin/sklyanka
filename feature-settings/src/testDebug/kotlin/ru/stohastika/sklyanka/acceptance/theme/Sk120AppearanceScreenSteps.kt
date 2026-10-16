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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.settings.SettingsScreenProbe
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import ru.stohastika.sklyanka.domain.pro.ProPoint
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
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки SKL-120 — блок «Оформление», его диалоги и тёмная среда экрана (§5.17).
 *
 * Стенд собирает **настоящий** экран настроек над миром сценария
 * ([SettingsScreenProbe]): состояние считает тот же `SettingsViewModel`, что у продукта,
 * и выбор проходит через домен. Правил в шагах нет.
 *
 * **Дверь и гейт разделены нарочно** (Р-67 п. 5, §5.17): строка открывает диалог и без
 * доступа — так выбор платной функции виден до покупки, — а платная строка диалога без
 * доступа выбор не меняет и открывает витрину. Стенд проверяет обе половины: открытие
 * диалога, состав и метки вариантов, применение базового выбора и отказ платного
 * с сигналом витрины.
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

    /** Точки-поводы последних открытых витрин: их просит сигнал блока «Оформление». */
    private val showcasePoints = mutableListOf<ProPoint>()

    private val showcaseScope = CoroutineScope(Dispatchers.Unconfined)

    private var showcaseJob: Job? = null

    // --- Блок «Оформление» ---------------------------------------------------------

    @Когда("^на стенде настроек открыт блок «Оформление»$")
    fun theAppearanceBlockIsOpen() {
        watchShowcase()
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

    @Тогда("^на стенде у обеих строк стоит метка «Про»$")
    fun bothRowsCarryTheProMark() {
        val mark = string(UiR.string.pro_mark)
        SettingsScreenProbe.ask(world) {
            assertEquals("the theme row must carry the Pro mark once", 1, marks(TAG_THEME_ROW, mark))
            assertEquals("the icon row must carry the Pro mark once", 1, marks(TAG_ICON_ROW, mark))
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

    @Когда("^на стенде настроек открыт выбор темы заново$")
    fun theThemePickerIsOpenAgain() {
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

    @Тогда("^на стенде метку «Про» несут три платные темы$")
    fun threeThemesCarryTheProMark() {
        val mark = string(UiR.string.pro_mark)
        SettingsScreenProbe.ask(world) {
            assertEquals(
                "three paid themes carry the mark, the base one does not (Р-71)",
                3,
                marks(TAG_THEME_DIALOG, mark),
            )
        }
        assertEquals(3, AccentTheme.entries.count { !it.isBase })
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
            "the base theme must be applied without access (§5.17)",
            themeNamed(name),
            SettingsScreenProbe.state(world).accentTheme,
        )
    }

    @Тогда("^на стенде выбор остался «([^»]+)»$")
    fun theChoiceStayedAs(name: String) {
        assertEquals(
            "the paid choice must not change the stored one without access (§5.17)",
            themeNamed(name),
            SettingsScreenProbe.state(world).accentTheme,
        )
    }

    @Тогда("^на стенде просят витрину ради акцента и иконок$")
    fun theShowcaseIsAskedForTheAccentPoint() {
        assertTrue(
            "the paid choice must open the showcase (Р-67 пп. 2–3), asked: $showcasePoints",
            showcasePoints.isNotEmpty(),
        )
        assertEquals(
            "the appearance block asks the showcase from its own entry (Р-67 п. 2)",
            ProPoint.ACCENT,
            showcasePoints.last(),
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

    @Тогда("^на стенде метку «Про» несёт только «Тёплая»$")
    fun onlyTheWarmVariantCarriesTheProMark() {
        val mark = string(UiR.string.pro_mark)
        SettingsScreenProbe.ask(world) {
            assertEquals("only the warm variant is paid (Р-56, Р-71)", 1, marks(TAG_ICON_DIALOG, mark))
        }
    }

    @Когда("^на стенде выбран тёплый вариант иконки$")
    fun theWarmVariantIsChosen() {
        selectInDialog(TAG_ICON_DIALOG, string(SettingsR.string.icon_variant_warm))
    }

    @Тогда("^на стенде вариант остался «([^»]+)»$")
    fun theVariantStayedIce(name: String) {
        val expected = if (name.startsWith(ICE)) IconVariant.ICE else IconVariant.WARM
        assertEquals(
            "the paid variant must not change the stored one without access (§5.17)",
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
        watchShowcase()
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

    /** Слушать сигнал витрины с того же ViewModel, что у экрана: поток без повтора, слушать надо заранее. */
    private fun watchShowcase() {
        showcasePoints.clear()
        val model = SettingsScreenProbe.model(world)
        showcaseJob?.cancel()
        showcaseJob = showcaseScope.launch { model.showcase.collect { showcasePoints += it } }
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

    private fun ComposeUiTest.marks(ancestorTag: String, mark: String): Int = onAllNodes(
        hasText(mark) and hasAnyAncestor(hasTestTag(ancestorTag)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size

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
