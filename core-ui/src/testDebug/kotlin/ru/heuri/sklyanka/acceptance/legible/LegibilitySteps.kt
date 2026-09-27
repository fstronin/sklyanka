package ru.heuri.sklyanka.acceptance.legible

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import ru.heuri.sklyanka.ui.R
import ru.heuri.sklyanka.ui.component.ScreenHeader
import ru.heuri.sklyanka.ui.component.ScreenHeaderLabels
import ru.heuri.sklyanka.ui.component.TAG_BANNER
import ru.heuri.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.heuri.sklyanka.ui.component.TAG_PRIMARY_BUTTON
import ru.heuri.sklyanka.ui.component.TAG_SCREEN_HEADER
import ru.heuri.sklyanka.ui.component.TextFacts
import ru.heuri.sklyanka.ui.component.textFacts
import ru.heuri.sklyanka.ui.component.touchTargets
import ru.heuri.sklyanka.ui.snapshot.EnvironmentSheet
import ru.heuri.sklyanka.ui.snapshot.SnapshotSurface
import ru.heuri.sklyanka.ui.snapshot.Snapshots
import ru.heuri.sklyanka.ui.theme.ProfileAccent
import ru.heuri.sklyanka.ui.token.ProfileColor
import ru.heuri.sklyanka.ui.token.SklyankaColors
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.darkColors
import ru.heuri.sklyanka.ui.token.lightColors
import java.io.File
import java.nio.file.Files
import kotlin.math.abs

/**
 * Шаги экранной группы приёмки SKL-25 (`@screen`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса: шаг обязан
 * совпадать с документом дословно. Пакет `ru.heuri.sklyanka.acceptance` разрешён правилом
 * `RussianTextInCode` именно поэтому.
 *
 * Разбор разметки текста общий с модульной проверкой `LargeFontTest`
 * (`ui/component/TextLayoutFacts.kt`): сценарий приёмки и проверка обязаны утверждать
 * одно и то же об одной и той же композиции, а не каждый о своём.
 *
 * Состояние сценария держится в самом классе: Cucumber создаёт по экземпляру на сценарий.
 */
@Suppress("TooManyFunctions")
class LegibilitySteps {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val temporary: File by lazy { Files.createTempDirectory("sklyanka-legible").toFile() }

    private var dark: Boolean = false
    private var foreign: Boolean = false
    private var scale: Float = 1f
    private var reading: SheetReading? = null
    private var ink: Int = 0
    private var ownerHeader: IntArray? = null
    private var foreignHeader: IntArray? = null
    private var missingGoldens: List<String> = emptyList()

    // --- Предыстория -----------------------------------------------------------

    @Допустим("лист среды собран из шапки, карточки приёма, поля, баннера и основной кнопки")
    fun theEnvironmentSheetIsComposed() {
        assertEquals(
            "the environment sheet must carry the header, the intake card, the banner and the button",
            PARTS.size,
            readTheSheet().parts,
        )
    }

    @Допустим("проверка идёт на снимочном стенде, без устройства")
    fun thereIsNoDevice() {
        assertTrue(
            "the screen run must happen on the JVM stand, not on a device",
            Build.FINGERPRINT.contains(ROBOLECTRIC, ignoreCase = true),
        )
    }

    // --- Крупный системный шрифт -----------------------------------------------

    @Допустим("лист среды собран в тёмной среде")
    fun theSheetIsDark() {
        dark = true
    }

    @Допустим("системный шрифт увеличен до {int} процентов")
    fun theSystemFontIsEnlargedTo(percent: Int) {
        scale = percent / PERCENT
    }

    @Когда("лист среды собирают при таком шрифте")
    fun theSheetIsComposedAtThatScale() {
        val read = readTheSheet()
        reading = read
        assertTrue("the sheet must contain text at font scale $scale", read.facts.isNotEmpty())
    }

    @Тогда("ни одна подпись не обрезана")
    fun nothingIsCutOff() {
        val cut = facts().filter { it.cut }
        assertTrue(cut.joinToString("\n") { "at font scale $scale ${it.complaint()}" }, cut.isEmpty())
    }

    @Тогда("ни одно слово не разорвано переносом")
    fun noWordIsBroken() {
        val broken = facts().filter { it.wordBroken }
        assertTrue(broken.joinToString("\n") { "at font scale $scale ${it.complaint()}" }, broken.isEmpty())
    }

    @Тогда("подпись главного действия карточки приёма занимает одну строку целиком")
    fun theMainActionLabelFitsOneLine() {
        val label = context.getString(R.string.action_taken)
        val found = facts().firstOrNull { it.text == label }
        assertTrue("the label \"$label\" is not on the sheet at all", found != null)
        val fact = requireNotNull(found)
        assertEquals(
            "the label \"$label\" is laid out as ${fact.lines} at font scale $scale",
            SINGLE_LINE,
            fact.lines.size,
        )
        assertTrue("the label \"$label\" is cut off at font scale $scale", !fact.cut)
    }

    @Тогда("каждая область нажатия не меньше минимальной цели нажатия")
    fun everyTouchTargetIsLargeEnough() {
        val read = requireNotNull(reading) { NOT_COMPOSED }
        assertTrue("the sheet must contain clickable elements", read.targets.isNotEmpty())
        val small = read.targets.filter { size -> size.width < read.minimum || size.height < read.minimum }
        assertTrue(
            small.joinToString("\n") { size ->
                "clickable area ${size.width}×${size.height} px is below ${read.minimum}"
            },
            small.isEmpty(),
        )
    }

    @Тогда("про каждую подпись известно, на какие строки её разложила вёрстка")
    fun theLineBreakdownOfEveryLabelIsKnown() {
        val lost = facts().filter { fact -> fact.lines.joinToString(separator = BLANK) != fact.text }
        assertTrue(
            "the line breakdown does not add up to the text itself: " +
                lost.joinToString("\n") { "\"${it.text}\" -> ${it.lines}" },
            lost.isEmpty(),
        )
        assertTrue("every label must have at least one line", facts().all { it.lines.isNotEmpty() })
    }

    // --- Эталоны ---------------------------------------------------------------

    @Когда("сверяют перечень сред с эталонными снимками")
    fun theEnvironmentsAreComparedToTheGoldens() {
        missingGoldens = ENVIRONMENTS.filterNot { name -> Snapshots.golden(ENVIRONMENT_GROUP, name).isFile }
    }

    @Тогда("у каждой среды есть эталонный снимок в репозитории")
    fun everyEnvironmentHasAGolden() {
        assertEquals(
            "these environments have no golden in the repository: $missingGoldens",
            emptyList<String>(),
            missingGoldens,
        )
    }

    // --- Тёмная среда ----------------------------------------------------------

    @Допустим("шапка экрана собрана в тёмной среде")
    fun theHeaderIsDark() {
        dark = true
    }

    @Допустим("шапка экрана собрана в светлой среде")
    fun theHeaderIsLight() {
        dark = false
    }

    @Допустим("шапка экрана собрана в контексте чужого профиля")
    fun theHeaderIsForeign() {
        foreign = true
    }

    @Когда("человек смотрит на заголовок экрана")
    fun thePersonLooksAtTheScreenTitle() {
        // Та же шапка с заголовком и с пустым заголовком: композиция и её габариты
        // совпадают, поэтому отличаться снимки могут только краской заголовка.
        val withTitle = captureHeader("with-title", context.getString(R.string.preview_screen_title))
        val withoutTitle = captureHeader("without-title", BLANK)
        ink = withTitle.indices.count { index -> visiblyDiffers(withTitle[index], withoutTitle[index]) }
    }

    @Тогда("заголовок оставляет краску на шапке")
    fun theTitleLeavesInk() {
        assertTrue(
            "the screen title leaves $ink visible points of ink on the header, fewer than $MIN_INK: " +
                "it is painted in the colour of the header itself and nobody can read it",
            ink >= MIN_INK,
        )
    }

    @Но("цветом фона шапки он не нарисован")
    fun theTitleIsNotTheColourOfTheHeader() {
        val colors = roles()
        assertNotEquals(
            "the title colour and the header background are the same: contrast is exactly 1.00",
            colors.profileHeader,
            colors.text,
        )
    }

    @Когда("сверяют фон шапки с фоном среды")
    fun theHeaderBackgroundIsComparedToTheEnvironment() = Unit

    @Тогда("фон шапки — это фон своей среды, а не отдельный цвет")
    fun theHeaderBackgroundIsTheBackgroundOfItsEnvironment() {
        val colors = roles()
        assertEquals(
            "the neutral header background must repeat the background of its environment",
            colors.background,
            colors.profileHeader,
        )
    }

    // --- Чужой профиль ---------------------------------------------------------

    @Когда("сверяют шапку чужого профиля с шапкой владельца")
    fun theForeignHeaderIsComparedToTheOwnerOne() {
        foreign = false
        ownerHeader = captureHeader("owner", context.getString(R.string.preview_screen_title))
        foreign = true
        foreignHeader = captureHeader("foreign", context.getString(R.string.preview_screen_title))
    }

    @Тогда("шапка чужого профиля выглядит иначе")
    fun theForeignHeaderLooksDifferent() {
        assertNotEquals(
            "a foreign profile renders the header exactly like the owner one: the indication is gone",
            requireNotNull(ownerHeader).toList(),
            requireNotNull(foreignHeader).toList(),
        )
    }

    // --- Обвязка ---------------------------------------------------------------

    /** Что прочитано с собранного листа среды за один заход. */
    private data class SheetReading(
        val facts: List<TextFacts>,
        val targets: List<IntSize>,
        val minimum: Int,
        val parts: Int,
    )

    private fun facts(): List<TextFacts> = requireNotNull(reading) { NOT_COMPOSED }.facts

    private fun roles(): SklyankaColors = if (dark) darkColors() else lightColors()

    @OptIn(ExperimentalTestApi::class)
    private fun readTheSheet(): SheetReading {
        var read: SheetReading? = null
        runComposeUiTest {
            setContent {
                Scaled { SnapshotSurface(profile = profile(), dark = dark) { EnvironmentSheet() } }
            }
            read = SheetReading(
                facts = textFacts(),
                targets = touchTargets(),
                minimum = with(density) { (SklyankaSizes.MinTouchTarget - TOLERANCE).roundToPx() },
                parts = PARTS.count { tag ->
                    onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                },
            )
        }
        return requireNotNull(read)
    }

    /** Снимок во временный каталог: эталоны эти шаги не трогают и не пишут. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureHeader(name: String, title: String): IntArray {
        val target = File(temporary, "${if (dark) "dark" else "light"}-$name.png")
        captureRoboImage(
            file = target,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            roborazziComposeOptions = Snapshots.options(),
        ) {
            SnapshotSurface(profile = profile(), dark = dark) {
                ScreenHeader(
                    labels = ScreenHeaderLabels(
                        title = title,
                        overline = context.getString(R.string.preview_date),
                    ),
                )
            }
        }
        assertTrue("nothing was rendered for '$name'", target.isFile)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(target.path)) { "'$name' is not an image" }
        return pixels(bitmap)
    }

    private fun profile(): ProfileAccent =
        if (foreign) ProfileAccent.Foreign(ProfileColor.Terracotta) else ProfileAccent.Owner

    /**
     * Точка отличается заметно, а не арифметически.
     *
     * Краска цветом фона всё равно даёт единицы точек расхождения на краях знаков: Skia
     * смешивает и округляет. Человеку такое расхождение не видно, и считать его
     * нарисованным заголовком нельзя.
     */
    private fun visiblyDiffers(first: Int, second: Int): Boolean =
        abs(Color.red(first) - Color.red(second)) >= VISIBLE_STEP ||
            abs(Color.green(first) - Color.green(second)) >= VISIBLE_STEP ||
            abs(Color.blue(first) - Color.blue(second)) >= VISIBLE_STEP

    private fun pixels(bitmap: Bitmap): IntArray {
        val out = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(out, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return out
    }

    @Composable
    private fun Scaled(content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = scale),
            content = content,
        )
    }

    private companion object {
        const val ROBOLECTRIC = "robolectric"
        const val NOT_COMPOSED = "the sheet has not been composed yet"
        const val PERCENT = 100f
        const val BLANK = ""
        const val SINGLE_LINE = 1

        /** Заметная разница канала: ниже неё расхождение видно только вычитанием. */
        const val VISIBLE_STEP = 16

        /**
         * Сколько точек краски обязан оставить заголовок экрана.
         *
         * Заголовок «Сегодня» стилем `TitleScreen` 30 sp занимает больше тысячи точек;
         * порог взят с запасом, чтобы шаг падал на невидимом заголовке, а не на
         * изменившемся начертании.
         */
        const val MIN_INK = 200

        const val ENVIRONMENT_GROUP = "environment"

        val PARTS = listOf(TAG_SCREEN_HEADER, TAG_INTAKE_CARD, TAG_BANNER, TAG_PRIMARY_BUTTON)

        /** Пять сред снимочного стенда: светлая, тёмная, чужой профиль и два масштаба. */
        val ENVIRONMENTS = listOf("light", "dark", "foreign-profile", "font-scale-130", "font-scale-200")
        val TOLERANCE = SklyankaSizes.BorderThin
    }
}
