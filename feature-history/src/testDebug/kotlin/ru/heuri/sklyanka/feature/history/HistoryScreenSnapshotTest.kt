package ru.heuri.sklyanka.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.ui.profile.ProfileChrome
import ru.heuri.sklyanka.ui.profile.ProfileChromeState
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.ProfileColor
import java.io.File

/**
 * Эталонные снимки экрана истории (SKL-24, критерий 5).
 *
 * По снимку на состояние: обычный месяц, пустой месяц, чужой профиль, открытый отчёт
 * и увеличенный системный шрифт. Числа те же, что в проверках состава экрана, чтобы
 * снимок и проверка говорили об одном экране, а не о двух похожих.
 *
 * Снимок ловит **изменение**, а не правильность: как экран выглядит на настоящем EMUI,
 * по-прежнему смотрят глазами на устройстве (`docs/tasks/SKL-24/TDD.md §2.2`).
 */
@RunWith(AndroidJUnit4::class)
class HistoryScreenSnapshotTest {

    @Test
    fun theHistoryOfTheMonth() = capture("history") {
        HistoryScreen(state = HistoryStates.september(), onIntent = {})
    }

    @Test
    fun theMonthWithoutRecords() = capture("history-empty") {
        HistoryScreen(state = HistoryStates.emptyMonth(), onIntent = {})
    }

    /**
     * Чужой профиль: все признаки сразу (ПРФ-11).
     *
     * Снимок валится, если хотя бы один из них снимут, — именно за этим он здесь и стоит:
     * на макете `History.dc.html` индикации нет вовсе, и без снимка её исчезновение
     * заметил бы только тот, кто помнит, что она должна быть.
     */
    @Test
    fun theHistoryOfAForeignProfile() = capture("history-foreign-profile", wrapped = false) {
        ProfileChrome(ProfileChromeState.Foreign(name = PROFILE_NAME, colour = ProfileColor.Terracotta)) {
            HistoryScreen(state = HistoryStates.foreignProfile(), onIntent = {})
        }
    }

    @Test
    fun theDoctorReportOnTheScreen() = capture("history-report") {
        HistoryScreen(state = HistoryStates.withReport(content = REPORT_SAMPLE), onIntent = {})
    }

    /**
     * Системный шрифт 200 % (НФТ-31).
     *
     * Ровно та проверка, которую спецификация предписывает делать руками и которую
     * сегодня не делает никто. Снимок замечает изменение вёрстки; обрезан ли текст
     * на настоящем экране — смотрят глазами.
     */
    @Test
    fun theHistoryAtDoubleFontScale() = capture("history-font-scale-200", heightDp = TALLER, fontScale = SCALE_200) {
        HistoryScreen(state = HistoryStates.september(), onIntent = {})
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        heightDp: Int = HEIGHT,
        fontScale: Float = 1f,
        wrapped: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "$GROUP/$name.png"),
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = WIDTH, heightDp = heightDp)
                fontScale(fontScale)
            },
        ) {
            if (wrapped) {
                SklyankaTheme {
                    Box(modifier = Modifier.fillMaxWidth().background(SklyankaTheme.colors.background)) {
                        content()
                    }
                }
            } else {
                // Оформление профиля само ставит тему и фон: вторая обёртка закрасила бы
                // подкраску, ради которой снимок и снимается.
                content()
            }
        }
    }

    private companion object {
        const val GROUP = "screen"
        const val WIDTH = 390

        /** Экран длиннее телефона: снимок берёт его целиком. */
        const val HEIGHT = 1100

        /** На двойном шрифте блоки растут по высоте. */
        const val TALLER = 2000
        const val SCALE_200 = 2.0f

        const val PROFILE_NAME = "profile-b"

        /** Заглушка документа: состав отчёта проверяет `DoctorReportRendererTest`. */
        const val REPORT_SAMPLE = "report-line-1\nreport-line-2\nreport-line-3"
    }
}
