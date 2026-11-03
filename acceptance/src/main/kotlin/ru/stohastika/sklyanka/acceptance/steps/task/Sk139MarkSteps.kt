package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import java.io.File

/**
 * Шаги приёмки SKL-139 без экрана: силуэты, норма и макеты.
 *
 * Сценарии знака, помеченные `@stand`, идут сценарным прогоном на JVM, где Compose
 * недоступен, — и это правильно: силуэты знака, норма палитры и макеты проверяются
 * **файлами**, а не отрисовкой. Экранная половина задачи (краски, контрасты, заставка,
 * название при 200 %) живёт в шагах модуля `app` (`acceptance/mark/Sk139MarkSteps.kt`).
 *
 * Предыстория файла — «значения палитры знака закреплены нормой (Р-57)» — тоже здесь:
 * она нужна обеим половинам, а шаг, объявленный дважды, гасит прогон
 * (`:acceptance:verifyAcceptanceSteps`). Пакет `ru.stohastika.sklyanka.acceptance`
 * виден обоим прогонам, поэтому объявление одно.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class Sk139MarkSteps {

    private var monochrome = ""
    private var notification = ""
    private var palette = ""
    private var normative = ""
    private var manifest = ""
    private var mocks: Map<String, String> = emptyMap()
    private var sheet = ""

    // --- Предыстория --------------------------------------------------------------------------

    @Допустим("^значения палитры знака закреплены нормой \\(Р-57\\)$")
    fun theMarkPaletteIsFixedByTheDocument() {
        normative = read(NORMATIVE_DOCUMENT)
        val missing = REQUIRED_COLD_VALUES.filterNot { normative.contains(it, ignoreCase = true) }
        assertTrue(missing.isEmpty(), "the design system document does not name ${missing.joinToString()}")
    }

    // --- Силуэты форму не меняют ---------------------------------------------------------------

    @Когда("сверяют монохромный слой знака и значок уведомления")
    fun theSilhouettesAreCompared() {
        monochrome = read(MONOCHROME_LAYER)
        notification = read(NOTIFICATION_MARK)
        palette = read(MARK_COLOURS)
    }

    @Тогда("монохромный слой остаётся белой маской предельной ступени редукции")
    fun theMonochromeLayerStaysTheLimitStep() {
        // Предельная ступень — одно кольцо r=80 толщиной 26 и точка r=30 на поле 200
        // (SKL-21, `FLAT_MARK_SHAPES`): у силуэта геометрия не пересматривается.
        assertTrue(monochrome.contains(LIMIT_RING), "the layer must keep the ring")
        assertTrue(monochrome.contains(STROKE_WIDTH), "the layer must keep the stroke")
        assertTrue(monochrome.contains(LIMIT_DOT), "the layer must keep the limit dot")
        assertTrue(monochrome.contains(SILHOUETTE_TOKEN), "the layer must be painted with the silhouette token")
        assertFalse(LITERAL_COLOUR.containsMatchIn(monochrome), "the layer must carry no literal colour")
    }

    @И("его цвет системе не навязан")
    fun itsColourIsNotImposedOnTheSystem() {
        assertTrue(
            palette.contains("<color name=\"mark_silhouette\">#FFFFFF</color>"),
            "the silhouette token must be white: the system tints the mask",
        )
    }

    @И("значок уведомления остаётся силуэтом")
    fun theNotificationIconStaysASilhouette() {
        assertTrue(notification.contains(LIMIT_RING), "the notification icon must keep the ring")
        assertTrue(notification.contains(LIMIT_DOT), "the notification icon must keep the limit dot")
        assertTrue(notification.contains(SILHOUETTE_TOKEN), "the notification icon must be a mask too")
        assertFalse(
            LITERAL_COLOUR.containsMatchIn(notification),
            "the notification icon must carry no literal colour",
        )
    }

    // --- Вторая палитра и макеты ---------------------------------------------------------------

    @Когда("сверяют норму палитры знака")
    fun thePaletteNormIsCompared() {
        normative = read(NORMATIVE_DOCUMENT)
        manifest = read(MANIFEST)
    }

    @Тогда("норма фиксирует обе палитры")
    fun theNormFixesBothPalettes() {
        val section = markSection()
        assertTrue(section.isNotEmpty(), "the mark palette section is missing from $NORMATIVE_DOCUMENT")
        val missing = (REQUIRED_COLD_VALUES + REQUIRED_WARM_VALUES)
            .filterNot { section.contains(it, ignoreCase = true) }
        assertTrue(missing.isEmpty(), "the section does not name ${missing.joinToString()}")
    }

    @И("прежняя тёплая оливовая иконка остаётся вторым вариантом")
    fun theWarmIconStaysTheSecondVariant() {
        // The second palette is named in words, not only in numbers: otherwise the warm look
        // would read as forgotten values rather than as a second variant, available like the cold
        // one.
        val section = markSection()
        assertTrue(
            section.contains("warm", ignoreCase = true) && normative.contains("free", ignoreCase = true),
            "the warm palette must be named as the second variant, free like the cold one",
        )
    }

    @И("оба варианта иконки объявлены манифестом как переключаемые")
    fun bothIconVariantsAreDeclaredAsSwitchable() {
        // Механизм смены иконки заведён фазой C (SKL-120, Р-31): фильтр `MAIN`/`LAUNCHER`
        // несут два `activity-alias` — по одному на вариант, — и включён ровно один.
        // До SKL-120 здесь стоял обратный запрет, и он был границей **задачи**, а не
        // решением продукта: SKL-139 заканчивалась до механизма, и норму это удерживало.
        // Задача границу сняла, и проверка перевёрнута вместе с нормой, а не удалена:
        // «тёплый вид — второй вариант, доступный как холодный» остаётся утверждением
        // о продукте (Р-86), и у него обязана быть опора в сборке.
        val aliases = ALIAS_BLOCK.findAll(manifest).map { it.value }.toList()
        assertEquals(2, aliases.size, "two icon variants must be declared")
        aliases.forEach { alias ->
            assertTrue(MAIN_FILTER in alias, "each variant must be launchable: $alias")
            // Имя в лончере от варианта не зависит: название принадлежит продукту (В-120-2).
            assertTrue(APP_NAME_LABEL in alias, "the launcher name must not change with the variant: $alias")
        }
        assertEquals(
            2,
            aliases.count { TARGET_ACTIVITY in it },
            "both variants must launch the same activity: the icon switch is not a second app",
        )
        assertTrue(
            manifest.contains("android:enabled=\"false\""),
            "the paid variant must arrive disabled: the base icon shows right after install (Р-56)",
        )
    }

    @Когда("сверяют макеты знака")
    fun theMockupsAreCompared() {
        mocks = MOCKUPS.associateWith { read(it) }
        sheet = read(PREVIEW_SHEET)
    }

    @Тогда("терракотовой краски `#C9633D` в них нет")
    fun theTerracottaIsGone() {
        val guilty = mocks.filterValues { it.contains(TERRACOTTA, ignoreCase = true) }.keys
        assertTrue(guilty.isEmpty(), "the terracotta is still in ${guilty.joinToString()}")
    }

    @И("макеты несут холодную палитру знака")
    fun theMockupsCarryTheColdPalette() {
        mocks.forEach { (path, text) ->
            assertTrue(text.contains(MARK_INK_DARK, ignoreCase = true), "$path does not carry the cold mark ink")
            assertTrue(text.contains(MARK_AMBER, ignoreCase = true), "$path does not carry the amber arc")
        }
    }

    @И("сводный макет пересобран")
    fun thePreviewSheetIsRebuilt() {
        assertTrue(sheet.contains(MARK_INK_DARK, ignoreCase = true), "the sheet must carry the cold mark ink")
        val missing = MOCKUPS.filterNot { sheet.contains(File(it).name) }
        assertTrue(missing.isEmpty(), "the sheet does not show ${missing.joinToString()}")
        assertFalse(sheet.contains(TERRACOTTA, ignoreCase = true), "the sheet must not carry the terracotta")
    }

    /** Раздел нормы о палитре знака: от его заголовка до следующего раздела первого уровня. */
    private fun markSection(): String = normative.substringAfter(MARK_SECTION).substringBefore(NEXT_SECTION)

    private fun read(path: String): String {
        val file = File(RepositoryLayout.root, path)
        assertTrue(file.isFile, "$path is missing")
        return file.readText()
    }

    private companion object {
        const val NORMATIVE_DOCUMENT = "docs/spec/design-system.md"
        const val MANIFEST = "app/src/main/AndroidManifest.xml"
        const val MARK_COLOURS = "app/src/main/res/values/colors.xml"
        const val MONOCHROME_LAYER = "app/src/main/res/drawable/ic_launcher_monochrome.xml"
        const val NOTIFICATION_MARK = "app/src/main/res/drawable/ic_notification_mark.xml"
        const val PREVIEW_SHEET = "design/preview.html"

        /** Четыре макета знака: их приведение к холодной палитре разрешено решением Р-56. */
        val MOCKUPS = listOf(
            "design/screens/SklyankaMark.dc.html",
            "design/screens/SklyankaIcon.dc.html",
            "design/screens/SklyankaLockup.dc.html",
            "design/screens/Splash.dc.html",
        )

        /** Значения Р-57, которые обязана назвать норма (§2.4). */
        val REQUIRED_COLD_VALUES = listOf("#C4F0F7", "#0B2831", "#111A1E", "#E7F0F4", "#A86A22")

        /** Вторая палитра — тёплый оливковый вид «Про»: норма фиксирует обе (Р-56). */
        val REQUIRED_WARM_VALUES = listOf("#F8F6EF", "#241F1B", "#181712", "#EFE7DB", "#B5C47A")

        const val MARK_SECTION = "### 2.4 Mark palette"
        const val NEXT_SECTION = "## 3."

        /** Объявление варианта иконки целиком: имя, значок, фильтр и метаданные ярлыка живут в нём. */
        val ALIAS_BLOCK = Regex("<activity-alias.*?</activity-alias>", RegexOption.DOT_MATCHES_ALL)

        /** Фильтр запуска: его несёт каждый вариант иконки — по одному на алиас. */
        const val MAIN_FILTER = "android.intent.category.LAUNCHER"

        /** Цель обоих алиасов одна: переключение значка не поднимает второе приложение. */
        const val TARGET_ACTIVITY = "android:targetActivity=\".MainActivity\""

        /** Имя в лончере: у обоих вариантов одно — название принадлежит продукту (В-120-2). */
        const val APP_NAME_LABEL = "android:label=\"@string/app_name\""
        const val SILHOUETTE_TOKEN = "@color/mark_silhouette"
        const val LIMIT_RING = "a80,80"
        const val LIMIT_DOT = "a30,30"
        const val STROKE_WIDTH = "strokeWidth=\"26\""
        const val TERRACOTTA = "#C9633D"
        const val MARK_INK_DARK = "#C4F0F7"
        const val MARK_AMBER = "#A86A22"

        /** Литерал цвета в разметке: у силуэта его быть не должно — цвет назначает система. */
        val LITERAL_COLOUR = Regex("#[0-9A-Fa-f]{6}")
    }
}
