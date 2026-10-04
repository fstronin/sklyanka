package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Locale

/**
 * Кадры матрицы «шесть локалей × 100/130/200 %» для визревью тёмной формы кнопок
 * (ОВ-146).
 *
 * Это артефакт ревью, а не эталон: восемнадцать кадров не влезают в потолок 110
 * эталонов SKL-24, и складывать их в `screenshots/` значило бы вытеснить чужие
 * состояния. Кадры собираются только явной опцией `-Psklyanka.visualReview=true`
 * в `feature-reminder/build/visual-review/reminder/` (тем же свойством, что и
 * контактные листы SKL-91); пригодность вёрстки читают по ним, а исход ряда
 * проверяет `ReminderActionsLocaleMatrixTest`.
 */
@RunWith(AndroidJUnit4::class)
class ReminderLocaleReviewSheetTest {

    @Test
    fun everyLocaleAndScaleIsCapturedForVisualReview() {
        assumeTrue(
            "the review sheets are an artifact: pass -Psklyanka.visualReview=true to build them",
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
        InterfaceLocale.SUPPORTED.forEach { locale ->
            SCALES.forEach { scale ->
                val target = File(outputRoot, "reminder-${locale.toLanguageTag()}-${(scale * PERCENT).toInt()}.png")
                requireNotNull(target.parentFile).mkdirs()
                capture(locale, scale, target)
                built += target
            }
        }
        val missing = built.filterNot { file -> file.isFile && file.length() > 0L }
        assertTrue("these review sheets were not written: $missing", missing.isEmpty())
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(locale: Locale, fontScale: Float, target: File) {
        captureRoboImage(
            file = target,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = WIDTH_DP, heightDp = HEIGHT_DP)
            },
        ) {
            PinnedLocale(locale) { Show(fontScale) }
        }
    }

    @Composable
    private fun Show(fontScale: Float) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
        ) {
            SklyankaTheme(dark = true) {
                ReminderScreen(state = state(), actions = noActions)
            }
        }
    }

    @Composable
    private fun PinnedLocale(locale: Locale, content: @Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale(locale)
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
        ) {
            content()
        }
    }

    private fun state() = ReminderUiState.Card(
        scheduledAt = LocalDateTime.of(2026, 9, 25, 14, 0),
        items = listOf(
            ReminderRow(
                intakeId = "intake-1",
                name = "med-a",
                form = MedicationForm.TABLET,
                doseAmount = BigDecimal("2"),
                strength = BigDecimal("48"),
                strengthUnit = "mg",
            ),
        ),
        snoozeMinutes = SNOOZE_MINUTES,
        mirroredToWatch = false,
        foreignProfileName = null,
    )

    private companion object {
        const val VISUAL_REVIEW_PROPERTY = "sklyanka.visualReview"
        const val ENABLED = "true"
        const val ROBORAZZI_OUTPUT_DIR = "roborazzi.output.dir"
        const val REVIEW_DIRECTORY = "build/visual-review/reminder"
        const val WIDTH_DP = 390
        const val HEIGHT_DP = 844
        const val SNOOZE_MINUTES = 60
        const val PERCENT = 100f

        val SCALES = listOf(1f, 1.3f, 2f)

        val noActions = ReminderActions(
            onTaken = {},
            onSkipped = {},
            onTakenAll = {},
            onSkippedAll = {},
            onSnooze = {},
        )
    }
}
