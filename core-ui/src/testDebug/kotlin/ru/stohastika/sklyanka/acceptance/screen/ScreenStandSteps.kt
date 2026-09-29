package ru.stohastika.sklyanka.acceptance.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.size
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.component.ButtonState
import ru.stohastika.sklyanka.ui.component.ChipState
import ru.stohastika.sklyanka.ui.component.IntakeCard
import ru.stohastika.sklyanka.ui.component.IntakeCardLabels
import ru.stohastika.sklyanka.ui.component.IntakeCardState
import ru.stohastika.sklyanka.ui.component.ProfileStripe
import ru.stohastika.sklyanka.ui.component.SwitchState
import ru.stohastika.sklyanka.ui.component.TextFieldState
import ru.stohastika.sklyanka.ui.snapshot.SnapshotSurface
import ru.stohastika.sklyanka.ui.snapshot.Snapshots
import ru.stohastika.sklyanka.ui.theme.ProfileAccent
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.GolosText
import ru.stohastika.sklyanka.ui.token.Literata
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.io.File
import java.nio.file.Files

/**
 * Шаги экранной группы приёмки SKL-24 (`@screen`).
 *
 * Эти шаги — ответ на `ОВ-74` действием, а не рассуждением: сценарный прогон Cucumber
 * запускается изнутри модульного теста под Robolectric, и здесь ему доступны ресурсы
 * модуля, настоящая композиция Compose и снятие снимков. То есть экранные шаги
 * приёмки написать можно — но не в модуле `acceptance`, который по `ADR-0007` остаётся
 * чистым JVM (разбор — `docs/tasks/SKL-24/TDD.md §8`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса: шаг
 * обязан совпадать с документом дословно. Пакет `ru.stohastika.sklyanka.acceptance`
 * разрешён правилом `RussianTextInCode` именно поэтому.
 *
 * Состояние сценария держится в самом классе: Cucumber создаёт по экземпляру
 * на сценарий.
 */
@Suppress("TooManyFunctions")
class ScreenStandSteps {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val temporary: File by lazy { Files.createTempDirectory("sklyanka-screen").toFile() }

    private var light: IntArray? = null
    private var dark: IntArray? = null
    private var owner: IntArray? = null
    private var foreign: IntArray? = null
    private var plainScale: IntArray? = null
    private var scaled: IntArray? = null
    private var requestedScale: Float = 1f
    private var literata: IntArray? = null
    private var golos: IntArray? = null
    private var system: IntArray? = null

    // --- Предыстория -----------------------------------------------------------

    @Допустим("сегодня 27 сентября 2026")
    fun theDateIs() = Unit

    @Допустим("в среде сборки нет ни устройства, ни эмулятора")
    fun thereIsNoDevice() {
        // Прогон идёт на имитации Android, и шаг это подтверждает: если бы он шёл
        // на устройстве, вывод о применимости стенда был бы о другом прогоне.
        assertTrue(
            "the screen run must happen on the JVM stand, not on a device",
            Build.FINGERPRINT.contains(ROBOLECTRIC, ignoreCase = true),
        )
    }

    @Допустим("дизайн-система описывает каждый компонент перечнем состояний")
    fun theDesignSystemListsStates() {
        assertTrue("IntakeCardState must list the states", IntakeCardState.entries.isNotEmpty())
        assertTrue("ChipState must list the states", ChipState.entries.isNotEmpty())
        assertTrue("ButtonState must list the states", ButtonState.entries.isNotEmpty())
        assertTrue("TextFieldState must list the states", TextFieldState.entries.isNotEmpty())
        assertTrue("SwitchState must list the states", SwitchState.entries.isNotEmpty())
    }

    // --- Тёмная среда ----------------------------------------------------------

    @Допустим("компонент собран в светлой среде")
    fun theComponentInTheLightEnvironment() {
        light = capture("light") { SnapshotSurface(dark = false) { Sample() } }
    }

    @Когда("тот же компонент собирают в тёмной среде")
    fun theSameComponentInTheDarkEnvironment() {
        dark = capture("dark") { SnapshotSurface(dark = true) { Sample() } }
    }

    @Тогда("на снимке тёмной среды фон и акцент отличаются от светлой")
    fun theDarkEnvironmentDiffers() {
        assertNotEquals(
            "the dark environment renders exactly like the light one: the theme is not applied",
            requireNotNull(light).toList(),
            requireNotNull(dark).toList(),
        )
    }

    // --- Чужой профиль ---------------------------------------------------------

    @Допустим("компонент собран в профиле владельца")
    fun theComponentInTheOwnerProfile() {
        owner = capture("owner") { SnapshotSurface { ProfileSample() } }
    }

    @Когда("тот же компонент собирают в контексте чужого профиля")
    fun theSameComponentInAForeignProfile() {
        foreign = capture("foreign") {
            SnapshotSurface(profile = ProfileAccent.Foreign(ProfileColor.Terracotta)) { ProfileSample() }
        }
    }

    @Тогда("на снимке чужого профиля видна избыточная индикация чужого контекста")
    fun theForeignProfileIsVisible() {
        assertNotEquals(
            "a foreign profile renders exactly like the owner: the indication is gone (PROFILE-11)",
            requireNotNull(owner).toList(),
            requireNotNull(foreign).toList(),
        )
    }

    // --- Увеличенный системный шрифт -------------------------------------------

    @Допустим("системный масштаб шрифта — {int} %")
    fun theSystemFontScaleIs(percent: Int) {
        requestedScale = percent / PERCENT
    }

    @Когда("экран собирают при этом масштабе")
    fun theScreenIsComposedAtThatScale() {
        plainScale = capture("scale-plain", scale = 1f) { SnapshotSurface { Sample() } }
        scaled = capture("scale-applied", scale = requestedScale) { SnapshotSurface { Sample() } }
    }

    @Тогда("вёрстка при этом масштабе отличается от вёрстки при обычном шрифте")
    fun theLayoutFollowsTheScale() {
        assertNotEquals(
            "the font scale ${requestedScale}x did not reach the layout",
            requireNotNull(plainScale).toList(),
            requireNotNull(scaled).toList(),
        )
    }

    @Тогда("снимок этого масштаба хранится эталоном")
    fun theScaleHasAGolden() {
        val name = "font-scale-${(requestedScale * PERCENT).toInt()}"
        val golden = Snapshots.golden("environment", name)
        assertTrue("the golden for $name is missing: ${golden.path}", golden.isFile)
    }

    // --- Начертания ------------------------------------------------------------

    @Допустим("заголовки набираются Literata, а интерфейс — Golos Text")
    fun theTypefacesAreDeclared() {
        assertNotEquals("Literata and Golos Text must be different families", GolosText, Literata)
    }

    @Когда("кириллический образец собирают этими начертаниями")
    fun theSpecimenIsComposed() {
        literata = capture("literata") { SnapshotSurface { Specimen(Literata) } }
        golos = capture("golos") { SnapshotSurface { Specimen(GolosText) } }
        system = capture("system") { SnapshotSurface { Specimen(FontFamily.Default) } }
    }

    @Тогда("образец отрисован Literata и Golos Text")
    fun theSpecimenUsesBothFamilies() {
        assertNotEquals(
            "Literata and Golos Text render identically: one of the font files did not load",
            requireNotNull(golos).toList(),
            requireNotNull(literata).toList(),
        )
    }

    @Но("системным шрифтом он не подменён")
    fun theSpecimenIsNotSubstituted() {
        val plain = requireNotNull(system).toList()
        assertNotEquals("Literata is substituted by the system font", plain, requireNotNull(literata).toList())
        assertNotEquals("Golos Text is substituted by the system font", plain, requireNotNull(golos).toList())
    }

    // --- Статус без цвета ------------------------------------------------------

    private var missedCaption: String? = null

    @Допустим("карточка приёма собрана в состоянии «пропущено»")
    fun theMissedCard() {
        missedCaption = context.getString(R.string.status_missed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Когда("человек читает карточку, не различая цвета")
    fun thePersonReadsTheCardWithoutColour() {
        val caption = requireNotNull(missedCaption)
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    IntakeCard(state = IntakeCardState.Missed, labels = labels())
                }
            }
            // Состояние обязано быть названо словами, а не одним лишь цветом (НФТ-33).
            onNodeWithText(caption).assertIsDisplayed()
        }
    }

    @Тогда("состояние названо словами")
    fun theStateIsSpelledOut() {
        assertTrue("the caption must come from resources", requireNotNull(missedCaption).isNotEmpty())
    }

    // --- Обвязка ---------------------------------------------------------------

    @Composable
    private fun Sample() {
        IntakeCard(state = IntakeCardState.DueNow, labels = labels())
    }

    @Composable
    private fun ProfileSample() {
        ProfileStripe()
        IntakeCard(state = IntakeCardState.DueNow, labels = labels())
    }

    @Composable
    private fun Specimen(family: FontFamily) {
        Text(
            text = context.getString(R.string.preview_screen_title),
            style = SklyankaType.Headline.copy(fontFamily = family),
            color = SklyankaTheme.colors.text,
        )
    }

    private fun labels() = IntakeCardLabels(
        medication = context.getString(R.string.preview_medication),
        dose = context.getString(R.string.preview_dose),
        time = context.getString(R.string.preview_marked_time),
    )

    /**
     * Снимок во временный каталог принудительной записью.
     *
     * Эталоны шаг не трогает: он сравнивает снимки между собой, а не с репозиторием.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, scale: Float = 1f, content: @Composable () -> Unit): IntArray {
        val target = File(temporary, "$name.png")
        captureRoboImage(
            file = target,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = Snapshots.WIDTH, heightDp = 0)
                fontScale(scale)
            },
            content = content,
        )
        assertTrue("the stand wrote nothing for '$name'", target.isFile)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(target.path)) { "'$name' is not an image" }
        assertEquals("the snapshot width must follow the request", Snapshots.WIDTH, bitmap.width)
        return pixels(bitmap)
    }

    private fun pixels(bitmap: Bitmap): IntArray {
        val out = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(out, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return out
    }

    private companion object {
        const val ROBOLECTRIC = "robolectric"
        const val PERCENT = 100f
    }
}
