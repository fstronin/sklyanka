package ru.heuri.sklyanka.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.ui.R
import ru.heuri.sklyanka.ui.component.ScreenHeader
import ru.heuri.sklyanka.ui.component.ScreenHeaderLabels
import ru.heuri.sklyanka.ui.snapshot.SnapshotSurface
import ru.heuri.sklyanka.ui.snapshot.Snapshots
import java.io.File
import java.nio.file.Files
import kotlin.math.abs

/**
 * Шапка экрана видна в обеих средах (SKL-25, дефект 2).
 *
 * Утверждение доказывается сравнением двух отрисовок одной и той же шапки: с
 * заголовком и с пустым заголовком. Композиция и её габариты совпадают — пустая
 * строка занимает ту же строку, — поэтому отличаться снимки могут только краской
 * заголовка. Совпали пиксель в пиксель — значит, заголовок не нарисован ничем, что
 * видно глазу.
 *
 * Так и выглядел дефект: фон шапки брался из нейтральной роли профиля, которая в
 * тёмном наборе оставалась светлой, а заголовок рисовался цветом текста тёмной
 * среды — то есть цветом фона шапки. Контраст ровно 1,00, заголовка нет. Снимок
 * `core-ui/screenshots/environment/dark.png` это состояние честно показывал и
 * честно же пропускал: эталон сверяется сам с собой.
 *
 * Арифметическую половину того же утверждения — что контраст пары «заголовок на
 * фоне шапки» проходит AA — делает `ContrastTest` по токенам, без композиции.
 */
@RunWith(AndroidJUnit4::class)
class DarkHeaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val temporary: File by lazy { Files.createTempDirectory("sklyanka-dark-header").toFile() }

    @Test
    fun theScreenTitleIsVisibleInTheDarkEnvironment() = assertTheTitleIsVisible(dark = true)

    @Test
    fun theScreenTitleIsVisibleInTheLightEnvironment() = assertTheTitleIsVisible(dark = false)

    private fun assertTheTitleIsVisible(dark: Boolean) {
        val environment = if (dark) "dark" else "light"
        val withTitle = capture("$environment-with-title", dark, context.getString(R.string.preview_screen_title))
        val withoutTitle = capture("$environment-without-title", dark, BLANK)
        val ink = withTitle.indices.count { index -> visiblyDiffers(withTitle[index], withoutTitle[index]) }
        assertTrue(
            "the screen title leaves $ink visible points of ink on the $environment header, fewer than " +
                "$MIN_INK: it is painted in the colour of the header itself, so nobody can read it " +
                "(NFR-31, NFR-32)",
            ink >= MIN_INK,
        )
    }

    /**
     * Точка отличается заметно, а не арифметически.
     *
     * Краска цветом фона всё равно даёт единицы точек расхождения на краях знаков:
     * Skia смешивает и округляет. Такое расхождение видно только вычитанием картинок,
     * а человеку — нет, и считать его нарисованным заголовком нельзя.
     */
    private fun visiblyDiffers(first: Int, second: Int): Boolean =
        abs(Color.red(first) - Color.red(second)) >= VISIBLE_STEP ||
            abs(Color.green(first) - Color.green(second)) >= VISIBLE_STEP ||
            abs(Color.blue(first) - Color.blue(second)) >= VISIBLE_STEP

    /** Снимок во временный каталог: эталоны эта проверка не трогает и не пишет. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, dark: Boolean, title: String): IntArray {
        val target = File(temporary, "$name.png")
        captureRoboImage(
            file = target,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            roborazziComposeOptions = Snapshots.options(),
        ) {
            Header(dark, title)
        }
        assertTrue("nothing was rendered for '$name'", target.isFile)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(target.path)) { "'$name' is not an image" }
        return pixels(bitmap)
    }

    @Composable
    private fun Header(dark: Boolean, title: String) {
        SnapshotSurface(dark = dark) {
            ScreenHeader(
                labels = ScreenHeaderLabels(
                    title = title,
                    overline = context.getString(R.string.preview_date),
                ),
            )
        }
    }

    private fun pixels(bitmap: Bitmap): IntArray {
        val out = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(out, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return out
    }

    private companion object {
        const val BLANK = ""

        /** Заметная разница канала: ниже неё расхождение видно только вычитанием. */
        const val VISIBLE_STEP = 16

        /**
         * Сколько точек краски обязан оставить заголовок экрана.
         *
         * Заголовок «Сегодня» стилем `TitleScreen` 30 sp занимает больше тысячи точек;
         * порог взят с многократным запасом, чтобы проверка падала на невидимом
         * заголовке, а не на изменившемся начертании.
         */
        const val MIN_INK = 200
    }
}
