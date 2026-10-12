package ru.stohastika.sklyanka.ui.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.profile.LocalProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.snapshot.Snapshots
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.SklyankaColors
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.lightColors
import ru.stohastika.sklyanka.ui.token.tintedColors
import java.io.File
import java.nio.file.Files

/**
 * Краска и непрозрачность полосы закреплённого ряда (SKL-104, критерии 7 и 11).
 *
 * Отдельным классом без `createComposeRule`: пиксельный снимок здесь берёт `captureRoboImage`,
 * который рисует композицию под собственным хостом — общий приём с `DarkHeaderTest`.
 * Цвет и непрозрачность ловит только растеризатор: дерево семантики скажет, что модификатор
 * `background` на месте, но не скажет, закрыт ли им контент под рядом.
 *
 * Полоса ряда — верхние 56 dp окна; там, где нет ни текста, ни кнопки (левое поле `Section`),
 * пиксель обязан быть ровно ролью `profileHeader`.
 */
@RunWith(AndroidJUnit4::class)
class ScreenFrameRowPaintTest {

    private val temporary: File by lazy { Files.createTempDirectory("sklyanka-screen-frame-band").toFile() }

    @Test
    fun thePipelinePaintsWhatTheStandComposes() {
        // Страховка от «пустого полотна, которое проходит сверку с таким же пустым» —
        // ровно тот дефект, который разобран в `DarkHeaderTest`: сначала проверяем, что
        // снимок вообще доносит краску стенда.
        val frame = capture {
            Box(modifier = Modifier.fillMaxSize().background(lightColors().background)) {
                Box(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT.dp).background(lightColors().accent))
            }
        }

        assertEquals(
            "the stand's own colour never reached the screenshot",
            lightColors().accent.toArgb(),
            frame.pixel(x = FRAME_WIDTH / 2, y = ROW_HEIGHT / 2),
        )
    }

    @Test
    fun theRowBandPaintsTheHeaderRoleOfTheOwner() {
        val frame = capture { Stand(foreign = false) }

        assertEquals(
            "the pinned row of the owner must paint the background role (§9.1)",
            lightColors().profileHeader.toArgb(),
            frame.pixel(x = LEFT_FIELD / 2, y = ROW_HEIGHT / 2),
        )
    }

    @Test
    fun theRowBandPaintsTheTintedHeaderRoleOfAForeignProfile() {
        val frame = capture { Stand(foreign = true) }

        assertEquals(
            "in a foreign profile the row must inherit the tinted header role (§9.1, критерий 11)",
            tintedForForeignProfile().profileHeader.toArgb(),
            frame.pixel(x = LEFT_FIELD / 2, y = ROW_HEIGHT / 2),
        )
    }

    @Test
    fun theRowBandHidesTheContentThatScrollsUnderIt() {
        // Две отрисовки одного ряда под разной краской: полоса обязана быть одинаковой,
        // а всё ниже неё — разным. Отличие в полосе означало бы, что карточка просвечивает
        // под текстом (§9.1).
        val underAccent = capture { Stand(under = lightColors().accent) }
        val underSurface = capture { Stand(under = lightColors().surface) }

        assertEquals(
            "the row band shows the card behind it, so its background is not opaque (§9.1)",
            underSurface.band(),
            underAccent.band(),
        )
        assertTrue(
            "the stand must really put different content under the band, otherwise the check proves nothing",
            underAccent.belowBand() != underSurface.belowBand(),
        )
    }

    /**
     * Стенд: каркас вложенного экрана, под полосу ряда задвинута карточка.
     *
     * Карточка задвигается начальным смещением скроллера: ряд вложенного экрана постоянен
     * с первого кадра, поэтому съёмке не нужно пересечение порога: полоса ряда на своём
     * месте с самого начала, и под неё задвигается только карточка.
     */
    @Composable
    private fun Stand(foreign: Boolean = false, under: Color = lightColors().accent) {
        val state = if (foreign) {
            ProfileChromeState.Foreign(name = PROFILE_NAME, colour = ProfileColor.Terracotta)
        } else {
            ProfileChromeState.Owner
        }
        SklyankaTheme(profile = state.accent) {
            CompositionLocalProvider(LocalProfileChrome provides state) {
                ScreenFrame(
                    labels = ScreenHeaderLabels(title = TITLE),
                    modifier = Modifier.fillMaxSize(),
                    kind = ScreenHeaderKind.Nested,
                    onBack = {},
                ) {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState(CARD_SHIFT))) {
                        ExpandedHeader()
                        Box(modifier = Modifier.fillMaxWidth().height(CARD_HEIGHT.dp).background(under))
                        repeat(BLOCKS) { index -> Text(text = "$BLOCK $index") }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(content: @Composable () -> Unit): Frame {
        val target = File(temporary, "band-" + System.nanoTime() + ".png")
        captureRoboImage(
            file = target,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            roborazziComposeOptions = Snapshots.options(widthDp = FRAME_WIDTH, heightDp = FRAME_HEIGHT),
        ) { content() }
        assertTrue("nothing was rendered into $target", target.isFile)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(target.path)) { "$target is not an image" }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return Frame(pixels, bitmap.width)
    }

    private fun tintedForForeignProfile(): SklyankaColors = tintedColors(lightColors(), ProfileColor.Terracotta.color)

    /** Пиксели снимка с доступом по координатам: плотность стенда — одна точка на пиксель. */
    private data class Frame(val pixels: IntArray, val width: Int) {

        fun pixel(x: Int, y: Int): Int = pixels[y * width + x]

        /** Полоса ряда: 56 dp от верхнего края окна. */
        fun band(): List<Int> = (0 until ROW_HEIGHT).flatMap { y -> (0 until width).map { x -> pixel(x, y) } }

        /** Всё, что ниже полосы ряда. */
        fun belowBand(): List<Int> {
            val height = pixels.size / width
            return (ROW_HEIGHT until height).flatMap { y -> (0 until width).map { x -> pixel(x, y) } }
        }

        override fun equals(other: Any?): Boolean =
            this === other || (other is Frame && width == other.width && pixels.contentEquals(other.pixels))

        override fun hashCode(): Int = 31 * pixels.contentHashCode() + width
    }

    private companion object {
        const val TITLE = "Screen"
        const val BLOCK = "Block"
        const val PROFILE_NAME = "Name"
        const val FRAME_WIDTH = 390
        const val FRAME_HEIGHT = 300
        const val CARD_HEIGHT = 240
        const val BLOCKS = 12

        /** Смещение карточки под полосу ряда, пиксели начального скролла. */
        const val CARD_SHIFT = 260

        /**
         * Полоса ряда и левое её поле в пикселях снимка: `Snapshots.options` рисует окно
         * макета при плотности 1, поэтому точки токенов совпадают с пикселями.
         */
        val ROW_HEIGHT: Int = SklyankaSizes.HeaderCompactRowHeight.value.toInt()
        val LEFT_FIELD: Int = SklyankaSpacing.Section.value.toInt()
    }
}
