package ru.stohastika.sklyanka.acceptance.locale.matrix

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import java.io.File
import java.util.Locale

/**
 * Контактные листы для визревью дизайнера (SKL-91).
 *
 * Матрица доказывает свойство, но дизайнеру нужен взгляд: листы складываются по языкам
 * в `build/visual-review/<язык>/<экран>.png` — артефакт ревью, который не коммитится
 * и не сверяется с эталоном. Собираются они только явной опцией
 * `-Psklyanka.visualReview=true`: обычный прогон проверок их не рисует, и это
 * проверяется шагом приёмки.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h27000dp-mdpi")
class LocalizationContactSheetTest {

    @Test
    fun everyScreenAndLanguageIsCapturedForVisualReview() {
        assumeTrue(
            "the contact sheets are a review artifact: pass -Psklyanka.visualReview=true to build them",
            System.getProperty(VISUAL_REVIEW_PROPERTY) == ENABLED,
        )
        val outputDirectory = requireNotNull(System.getProperty(ROBORAZZI_OUTPUT_DIR)) {
            "the screen test convention must set $ROBORAZZI_OUTPUT_DIR"
        }
        val moduleDirectory = requireNotNull(File(outputDirectory).parentFile) {
            "$ROBORAZZI_OUTPUT_DIR must point inside the module"
        }
        val outputRoot = File(moduleDirectory, REVIEW_DIRECTORY)
        val built = mutableListOf<File>()
        LocalizationScreenCatalog.screens.forEach { screen ->
            InterfaceLocale.SUPPORTED.forEach { locale ->
                val target = File(outputRoot, "${matrixLocaleTag(locale)}/${screen.id}.png")
                requireNotNull(target.parentFile).mkdirs()
                capture(screen, locale, target)
                built += target
            }
        }
        val missing = built.filterNot { file -> file.isFile && file.length() > 0L }
        assertTrue("these contact sheets were not written: $missing", missing.isEmpty())
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(screen: MatrixScreen, locale: Locale, target: File) {
        captureRoboImage(
            file = target,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = SHEET_WIDTH_DP, heightDp = screen.heightDp)
            },
        ) {
            PinnedInterfaceLocale(locale) { screen.content() }
        }
    }

    private companion object {
        const val VISUAL_REVIEW_PROPERTY = "sklyanka.visualReview"
        const val ENABLED = "true"
        const val ROBORAZZI_OUTPUT_DIR = "roborazzi.output.dir"
        const val REVIEW_DIRECTORY = "build/visual-review"
        const val SHEET_WIDTH_DP = 390
    }
}
