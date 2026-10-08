package ru.stohastika.sklyanka.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfileColor
import java.io.File
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Эталонные снимки экрана «Сегодня» (SKL-24, критерий 5).
 *
 * Расклады берутся из тех же заглушек, что и проверки состава экрана
 * (`TodayScreenTest`): снимок и проверка говорят об одном экране, а не о двух
 * похожих. Пять режимов наполнения плюс контекст чужого профиля: подтверждение
 * групповой отметки — второе состояние батч-кнопки (ОВ-32).
 *
 * Стенд — имитация Android, а не EMUI: снимок говорит, что вёрстка не изменилась,
 * и ничего не говорит о том, как экран выглядит на настоящем устройстве
 * (`docs/archive/tasks/SKL-24/TDD.md §2`).
 */
@RunWith(AndroidJUnit4::class)
class TodayScreenSnapshotTest {

    @Test
    fun aPlainDay() = capture("plain-day") { TodayScreen(state = TodayFixtures.plainDay(), onIntent = {}) }

    @Test
    fun aGroupedTime() = capture("grouped-time") { TodayScreen(state = TodayFixtures.groupDay(), onIntent = {}) }

    /** Подтверждение групповой отметки: кнопка-вопрос акцентна (ОВ-32). */
    @Test
    fun aConfirmingGroup() = capture("group-confirm") {
        TodayScreen(
            state = TodayFixtures.groupDay().copy(confirmingGroup = TodayFixtures.GROUP_TIME),
            onIntent = {},
        )
    }

    @Test
    fun aDenseDay() = capture("dense-day") { TodayScreen(state = TodayFixtures.denseDay(), onIntent = {}) }

    @Test
    fun anExhaustedAsNeededLimit() = capture("exhausted-prn") {
        TodayScreen(
            state = TodayFixtures.plainDay().copy(prn = listOf(TodayFixtures.exhaustedPrn())),
            onIntent = {},
        )
    }

    /**
     * Экран в чужом профиле — все признаки разом (ПРФ-11).
     *
     * Снимается вместе с `ProfileChrome`, а не с одной лишь подменённой темой: оформление
     * профиля живёт в корне приложения (SKL-15), и снимок обязан показывать то, что видит
     * человек, — полосу, подкрашенную шапку, надпись «Профиль · Имя», баннер, акцент
     * действий и подпись «отметили вы» у отмеченного приёма.
     */
    @Test
    fun aForeignProfile() = capture(name = "foreign-profile", foreign = true) {
        TodayScreen(state = TodayFixtures.plainDay(markedByYou = true), onIntent = {})
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, foreign: Boolean = false, content: @Composable () -> Unit) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "$GROUP/$name.png"),
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = WIDTH, heightDp = HEIGHT)
            },
        ) {
            if (foreign) {
                ProfileChrome(
                    ProfileChromeState.Foreign(
                        name = stringResource(UiR.string.preview_profile_name),
                        colour = ProfileColor.Terracotta,
                    ),
                    content = content,
                )
            } else {
                SklyankaTheme {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth().background(SklyankaTheme.colors.background),
                    ) {
                        content()
                    }
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
