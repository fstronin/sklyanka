package ru.heuri.sklyanka.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.ui.theme.ProfileAccent
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.ProfileColor
import java.io.File

/**
 * Эталонные снимки экрана «Сегодня» (SKL-24, критерий 5).
 *
 * Расклады берутся из тех же заглушек, что и проверки состава экрана
 * (`TodayScreenTest`): снимок и проверка говорят об одном экране, а не о двух
 * похожих. Четыре режима наполнения плюс контекст чужого профиля.
 *
 * Стенд — имитация Android, а не EMUI: снимок говорит, что вёрстка не изменилась,
 * и ничего не говорит о том, как экран выглядит на настоящем устройстве
 * (`docs/tasks/SKL-24/TDD.md §2`).
 */
@RunWith(AndroidJUnit4::class)
class TodayScreenSnapshotTest {

    @Test
    fun aPlainDay() = capture("plain-day") { TodayScreen(state = TodayFixtures.plainDay(), onIntent = {}) }

    @Test
    fun aGroupedTime() = capture("grouped-time") { TodayScreen(state = TodayFixtures.groupDay(), onIntent = {}) }

    @Test
    fun aDenseDay() = capture("dense-day") { TodayScreen(state = TodayFixtures.denseDay(), onIntent = {}) }

    @Test
    fun anExhaustedAsNeededLimit() = capture("exhausted-prn") {
        TodayScreen(
            state = TodayFixtures.plainDay().copy(prn = listOf(TodayFixtures.exhaustedPrn())),
            onIntent = {},
        )
    }

    @Test
    fun aForeignProfile() = capture(
        name = "foreign-profile",
        profile = ProfileAccent.Foreign(ProfileColor.Terracotta),
    ) {
        TodayScreen(state = TodayFixtures.plainDay(), onIntent = {})
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, profile: ProfileAccent = ProfileAccent.Owner, content: @Composable () -> Unit) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "$GROUP/$name.png"),
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = WIDTH, heightDp = HEIGHT)
            },
        ) {
            SklyankaTheme(profile = profile) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth().background(SklyankaTheme.colors.background),
                ) {
                    content()
                }
            }
        }
    }

    private companion object {
        const val GROUP = "screen"

        /** Ширина макетов и высота эталонного телефона: экран снимается целиком. */
        const val WIDTH = 390
        const val HEIGHT = 844
    }
}
