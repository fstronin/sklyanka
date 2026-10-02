package ru.stohastika.sklyanka.acceptance.locale.matrix

import android.os.Build
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import java.util.Locale

/**
 * Шаги экранной группы приёмки SKL-91 (`@screen`).
 *
 * Матрица прогоняется шагами, а не отдельной командой: сценарий приёмки — то место,
 * где видно, что именно проверено, а `LocalizationMatrix` остаётся механизмом.
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class LocalizationMatrixSteps {

    private var screens: List<MatrixScreen> = LocalizationScreenCatalog.screens
    private var locales: List<Locale> = InterfaceLocale.SUPPORTED
    private var scan: MatrixScan? = null

    // --- Предыстория -----------------------------------------------------------

    @Допустим("стенд экранных проверок поднят без устройства")
    fun theScreenStandIsUpWithoutADevice() {
        assertTrue(
            "the matrix must run on the JVM stand, not on a device",
            Build.FINGERPRINT.contains(ROBOLECTRIC, ignoreCase = true),
        )
    }

    @Допустим("объявлено шесть языков интерфейса")
    fun sixLanguagesAreDeclared() {
        assertEquals(
            "the declared interface languages are the source of truth for the matrix",
            DECLARED_LANGUAGES,
            InterfaceLocale.SUPPORTED.size,
        )
    }

    // --- Матрица ---------------------------------------------------------------

    @Допустим("в матрице выбран список лекарств с карточкой по потребности")
    fun thePrnListIsSelected() {
        screens = LocalizationScreenCatalog.screens.filter { screen -> screen.id == PRN_LIST_SCREEN }
        assertEquals("the PRN list must exist in the catalog", 1, screens.size)
    }

    @Когда("^разработчик прогоняет матрицу «экраны × языки»$")
    fun theMatrixRunsOverTheScreens() {
        scan = LocalizationMatrix.scanDetailed(screens = screens, locales = locales)
    }

    @Тогда("ни одна подпись не обрезана по высоте")
    fun nothingIsCutOff() {
        assertNoFindings(MatrixDefect.CUT)
    }

    @Тогда("ни одна подпись не обрезана многоточием")
    fun nothingIsTruncatedWithAnEllipsis() {
        assertNoFindings(MatrixDefect.ELLIPSIS)
    }

    @Тогда("ни одно слово не разорвано переносом")
    fun noWordIsBroken() {
        assertNoFindings(MatrixDefect.WORD_BREAK)
    }

    @Тогда("название лекарства по потребности видно на каждом из шести языков")
    fun thePrnNameIsVisibleInEveryLanguage() {
        val result = requireNotNull(scan)
        val expected = locales.map { locale -> matrixLocaleTag(locale) }.toSet()
        assertEquals(
            "every declared language must be read",
            expected,
            result.groups.map { group -> group.locale }.toSet(),
        )
        result.groups.forEach { group ->
            val title = group.facts.firstOrNull { fact -> fact.text == PRN_TITLE }
            assertNotNull("the on-demand card title was not read on ${group.locale}", title)
            assertTrue(
                "the chip hid the name on ${group.locale}: ${requireNotNull(title).lines}",
                requireNotNull(title).visibleCharacters >= LocalizationMatrix.MIN_VISIBLE_ELLIPSIS_CHARACTERS,
            )
        }
    }

    @Тогда("в матрицу вошло не меньше восемнадцати экранов и состояний")
    fun theMatrixCoversEnoughScreens() {
        assertTrue(
            "the matrix covered only ${screens.size} screens: ${screens.map { it.id }}",
            screens.size >= MINIMUM_SCREENS,
        )
    }

    @Тогда("каждый объявленный язык представлен в каждой комбинации")
    fun everyLocaleIsPresentInEveryCombination() {
        val expected = locales.map { locale -> matrixLocaleTag(locale) }.toSet()
        val incomplete = requireNotNull(scan).groups
            .groupBy { group -> group.screen }
            .filter { (_, groups) ->
                groups.map { group -> group.locale }.toSet() != expected ||
                    groups.any { group -> group.facts.isEmpty() }
            }
            .keys
        assertTrue("these screens were not read on every declared language: $incomplete", incomplete.isEmpty())
    }

    // --- Разрешённое усечение --------------------------------------------------

    @Допустим("карточка лекарства с длинным названием на узком экране")
    fun theCardWithALongNameIsSelected() {
        screens = LocalizationScreenCatalog.screens.filter { screen -> screen.id == LONG_NAME_SCREEN }
        assertEquals("the long-name card must exist in the catalog", 1, screens.size)
    }

    @Когда("матрица разбирает разметку карточки")
    fun theMatrixReadsTheCard() {
        scan = LocalizationMatrix.scanDetailed(screens = screens, locales = locales)
    }

    @Тогда("усечение названия помечено разрешённым")
    fun theEllipsisIsMarkedAllowed() {
        val allowed = LocalizationMatrix.allowedEllipsis(requireNotNull(scan))
        assertTrue(
            "no allowed ellipsis was found on the long-name card: ${requireNotNull(scan).groups.flatMap { it.facts }}",
            allowed.isNotEmpty(),
        )
        val facts = allowed.flatMap { group -> group.facts }
        assertTrue(
            "the allowed ellipsis must be the medication title",
            facts.any { fact ->
                fact.text.startsWith("Magnesium")
            },
        )
        assertTrue(
            "the allowed ellipsis must leave the name readable: $facts",
            facts.all { fact -> fact.visibleCharacters >= LocalizationMatrix.MIN_VISIBLE_ELLIPSIS_CHARACTERS },
        )
    }

    @Но("рядом нет ни одной другой обрезанной подписи")
    fun noOtherTextIsClippedOnTheCard() {
        val findings = requireNotNull(scan).findings
        assertTrue(
            "the long-name card clipped more than the medication title: $findings",
            findings.isEmpty(),
        )
    }

    // --- Языки без пробелов ----------------------------------------------------

    @Допустим("^матрица собрана только на языке «([^»]+)»$")
    fun theMatrixIsLimitedToOneLanguage(name: String) {
        val locale = LANGUAGES[name] ?: error("the scenario names an unknown language: $name")
        locales = listOf(locale)
    }

    @Когда("разработчик прогоняет матрицу на этом языке")
    fun theMatrixRunsOnThatLanguage() {
        scan = LocalizationMatrix.scanDetailed(screens = screens, locales = locales)
    }

    @Тогда("иероглифический перенос не помечен разрывом слова")
    fun theSyllableBreakIsNotAWordBreak() {
        val result = requireNotNull(scan)
        assertTrue(
            "a language without spaces must not be blamed for breaking words: " +
                result.findings.filter { it.defect == MatrixDefect.WORD_BREAK },
            result.findings.none { it.defect == MatrixDefect.WORD_BREAK },
        )
        assertTrue("the run read no syllable text at all", result.syllableFacts > 0)
    }

    @И("проверка обрезки на этом языке действительно шла")
    fun theClipCheckDidRunOnThatLanguage() {
        val result = requireNotNull(scan)
        assertTrue("no text was read on this language", result.groups.all { group -> group.facts.isNotEmpty() })
        assertTrue(
            "the clip check must read every text of the screen",
            result.groups.sumOf { group -> group.facts.size } >= MINIMUM_FACTS,
        )
    }

    // --- Обвязка ---------------------------------------------------------------

    private fun assertNoFindings(defect: MatrixDefect) {
        val found = requireNotNull(scan) { "the matrix has not run yet" }
            .findings
            .filter { finding -> finding.defect == defect }
        assertTrue(found.joinToString(separator = "\n"), found.isEmpty())
    }

    private companion object {
        const val ROBOLECTRIC = "robolectric"
        const val DECLARED_LANGUAGES = 6
        const val MINIMUM_SCREENS = 18

        /** Экран настроек даёт больше тридцати надписей; порог доказывает, что обход состоялся. */
        const val MINIMUM_FACTS = 30
        const val LONG_NAME_SCREEN = "meds-long-name"
        const val PRN_LIST_SCREEN = "meds-list"
        const val PRN_TITLE = "med-c"

        /** Имена языков из сценариев: сценарий говорит словами, матрица — тегом. */
        val LANGUAGES = mapOf(
            "русский" to Locale.forLanguageTag("ru"),
            "английский (США)" to Locale.forLanguageTag("en-US"),
            "испанский" to Locale.forLanguageTag("es"),
            "немецкий" to Locale.forLanguageTag("de"),
            "китайский (упрощённый)" to Locale.forLanguageTag("zh-CN"),
            "казахский" to Locale.forLanguageTag("kk"),
        )
    }
}
