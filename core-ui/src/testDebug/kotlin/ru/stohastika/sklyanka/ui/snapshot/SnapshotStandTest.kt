package ru.stohastika.sklyanka.ui.snapshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.GolosText
import ru.stohastika.sklyanka.ui.token.Literata
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.io.File

/**
 * Проверка самого стенда (SKL-24, критерий 10, `TDD.md §6.4`).
 *
 * Главная ловушка снимочного стенда: пустое полотно, сверенное с таким же пустым
 * эталоном, даёт зелёный прогон и полную видимость проверки. Достаточно, чтобы
 * графический режим оказался не настоящим, — и ни один снимок ничего не проверяет,
 * не сообщая об этом.
 *
 * Поэтому здесь снимок снимается принудительной записью во временный каталог,
 * независимо от режима прогона, и утверждения делаются по пикселям:
 *
 * 1. на снимке есть краска, а не один фоновый цвет;
 * 2. кириллица, набранная Literata, отличается от той же строки системным шрифтом,
 *    и то же верно для Golos Text — значит, начертания не подменены;
 * 3. Literata и Golos Text отличаются друг от друга — значит, подхватились оба файла;
 * 4. размер снимка совпадает с запрошенным.
 */
@RunWith(AndroidJUnit4::class)
class SnapshotStandTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theStandDrawsInkAndNotAnEmptyCanvas() {
        val image = capture("ink") {
            SnapshotSurface {
                Text(
                    text = context.getString(R.string.preview_screen_title),
                    style = SklyankaType.Headline,
                    color = SklyankaTheme.colors.text,
                )
            }
        }

        val colours = distinctColours(image)
        assertTrue(
            "the stand produced a blank canvas: only $colours colour(s). " +
                "Check that Robolectric runs in NATIVE graphics mode (SKL-24, TDD 4.1)",
            colours > BACKGROUND_ONLY,
        )
    }

    @Test
    fun theSnapshotKeepsTheRequestedWidth() {
        val image = capture("width") { SnapshotSurface { Specimen(Literata) } }

        assertEquals("the snapshot width must follow the requested size", Snapshots.WIDTH, image.width)
        assertTrue("the snapshot must have a height", image.height > 0)
    }

    /** НФТ-42 не нарушается: строка образца приходит из ресурсов, а не из кода. */
    @Test
    fun cyrillicIsSetInLiterataAndGolosAndNotInTheSystemFont() {
        val literata = pixels(capture("literata") { SnapshotSurface { Specimen(Literata) } })
        val golos = pixels(capture("golos") { SnapshotSurface { Specimen(GolosText) } })
        val system = pixels(capture("system") { SnapshotSurface { Specimen(FontFamily.Default) } })

        assertNotEquals(
            "Literata is substituted by the system font: the two renderings are identical",
            system.toList(),
            literata.toList(),
        )
        assertNotEquals(
            "Golos Text is substituted by the system font: the two renderings are identical",
            system.toList(),
            golos.toList(),
        )
        assertNotEquals(
            "Literata and Golos Text render identically: one of the font files did not load",
            golos.toList(),
            literata.toList(),
        )
    }

    @Composable
    private fun Specimen(family: FontFamily) {
        Text(
            // Кириллический образец: если начертание не подхватилось, строка
            // отрисуется системным шрифтом, и снимки совпадут.
            text = context.getString(R.string.preview_screen_title),
            style = SklyankaType.Headline.copy(fontFamily = family),
            color = SklyankaTheme.colors.text,
        )
    }

    /**
     * Снимок принудительной записью во временный каталог.
     *
     * Задача типа Record задаётся явно, а не системным свойством прогона: проверка
     * стенда обязана работать и тогда, когда прогон сверяет эталоны, и тогда, когда
     * он их перезаписывает.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, content: @Composable () -> Unit): Bitmap {
        val target = File(temporaryFolder.root, "$name.png")
        captureRoboImage(
            file = target,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            roborazziComposeOptions = Snapshots.options(),
            content = content,
        )
        assertTrue("the stand wrote no file at all for '$name'", target.isFile)
        // Разбор PNG средствами платформы, а не java.awt: в classpath модульного теста
        // Android-модуля пакет javax.imageio перекрыт android.jar и недоступен.
        return requireNotNull(BitmapFactory.decodeFile(target.path)) {
            "the stand wrote a file that is not an image"
        }
    }

    private fun pixels(image: Bitmap): IntArray {
        val out = IntArray(image.width * image.height)
        image.getPixels(out, 0, image.width, 0, 0, image.width, image.height)
        return out
    }

    private fun distinctColours(image: Bitmap): Int = pixels(image).toHashSet().size

    private companion object {
        /** Один цвет — это фон и ничего больше. */
        const val BACKGROUND_ONLY = 1
    }
}
