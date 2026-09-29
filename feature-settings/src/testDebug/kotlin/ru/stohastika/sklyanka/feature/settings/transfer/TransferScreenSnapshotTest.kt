package ru.stohastika.sklyanka.feature.settings.transfer

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.io.File

/**
 * Эталонные снимки экрана переноса данных (SKL-24, критерий 5).
 *
 * Обзор, разбор совпадений и отчёт — три состояния, на которых экран живёт.
 * Заготовки те же, что в проверках состава (`TransferScreenFixtures`).
 */
@RunWith(AndroidJUnit4::class)
class TransferScreenSnapshotTest {

    @Test
    fun theOverview() = capture("transfer-overview") {
        DataTransferScreen(
            state = TransferScreenFixtures.overview(),
            onIntent = {},
            onSave = {},
            onPickFile = {},
            onBack = {},
        )
    }

    @Test
    fun theMatchReview() = capture("transfer-matches") {
        MatchReviewScreen(
            state = TransferScreenFixtures.overview().copy(
                fileCounts = TransferScreenFixtures.FILE_COUNTS,
                matches = listOf(TransferScreenFixtures.match()),
            ),
            onIntent = {},
            onCancel = {},
        )
    }

    /** Подтверждение замены без слова: опасная кнопка недоступна (ОВ-67). */
    @Test
    fun theReplaceConfirmation() = capture("transfer-replace-confirm") {
        replaceConfirmation(word = "")
    }

    /** Подтверждение замены с верным словом: опасная кнопка доступна. */
    @Test
    fun theReplaceConfirmationWithTheWord() = capture("transfer-replace-confirm-word") {
        replaceConfirmation(word = ApplicationProvider.getApplicationContext<Context>().getString(R.string.transfer_replace_word))
    }

    @Composable
    private fun replaceConfirmation(word: String) {
        ReplaceConfirmScreen(
            device = TransferScreenFixtures.DEVICE_COUNTS,
            file = TransferScreenFixtures.FILE_COUNTS,
            safetyCopyName = TransferScreenFixtures.SAFETY_COPY,
            confirmationWord = word,
            onConfirmationWordChange = {},
            onConfirm = {},
            onCancel = {},
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, content: @Composable () -> Unit) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "$GROUP/$name.png"),
            roborazziComposeOptions = RoborazziComposeOptions { size(widthDp = WIDTH, heightDp = HEIGHT) },
        ) {
            SklyankaTheme { content() }
        }
    }

    private companion object {
        const val GROUP = "screen"
        const val WIDTH = 390
        const val HEIGHT = 1400
    }
}
