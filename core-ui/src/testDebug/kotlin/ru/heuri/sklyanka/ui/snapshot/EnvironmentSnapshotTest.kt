package ru.heuri.sklyanka.ui.snapshot

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.ui.R
import ru.heuri.sklyanka.ui.component.Banner
import ru.heuri.sklyanka.ui.component.BannerKind
import ru.heuri.sklyanka.ui.component.IntakeCard
import ru.heuri.sklyanka.ui.component.IntakeCardActions
import ru.heuri.sklyanka.ui.component.IntakeCardLabels
import ru.heuri.sklyanka.ui.component.IntakeCardState
import ru.heuri.sklyanka.ui.component.PrimaryButton
import ru.heuri.sklyanka.ui.component.ProfileStripe
import ru.heuri.sklyanka.ui.component.ScreenHeader
import ru.heuri.sklyanka.ui.component.ScreenHeaderLabels
import ru.heuri.sklyanka.ui.component.SklyankaTextField
import ru.heuri.sklyanka.ui.theme.ProfileAccent
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.GolosText
import ru.heuri.sklyanka.ui.token.Literata
import ru.heuri.sklyanka.ui.token.ProfileColor
import ru.heuri.sklyanka.ui.token.SklyankaType

/**
 * Среда, профиль и увеличенный системный шрифт (SKL-24, критерии 7–9).
 *
 * Масштабы 130 % и 200 % — ровно те проверки `НФТ-31`, которые спецификация предписывает
 * делать руками и которые сегодня не делает никто. Снимок их не отменяет: он замечает
 * ИЗМЕНЕНИЕ вёрстки при масштабе, а обрезан ли текст на настоящем экране — по-прежнему
 * смотрят глазами на устройстве (`TDD.md §2.2`).
 *
 * На одном листе собрано то, на чём среда видна: шапка, карточка приёма, кнопка, поле
 * и баннер. Лист один, сред пять — пять снимков вместо пяти на каждый компонент.
 */
@RunWith(AndroidJUnit4::class)
class EnvironmentSnapshotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theLightEnvironment() = capture("light")

    @Test
    fun theDarkEnvironment() = capture("dark", dark = true)

    @Test
    fun theForeignProfile() = capture("foreign-profile", profile = ProfileAccent.Foreign(ProfileColor.Terracotta))

    @Test
    fun theSystemFontAt130Percent() = capture("font-scale-130", fontScale = SCALE_130)

    @Test
    fun theSystemFontAt200Percent() = capture("font-scale-200", fontScale = SCALE_200)

    /**
     * Образец начертаний: кириллица в Literata и Golos Text рядом с системным шрифтом.
     *
     * Подмена начертания системным шрифтом видна ровно здесь: если файл шрифта не
     * подхватился, три строки становятся одинаковыми. Пиксельную проверку того же
     * утверждения делает `SnapshotStandTest`.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun theTypefaceSpecimen() {
        captureRoboImage(
            file = Snapshots.golden(GROUP, "typeface-specimen"),
            roborazziComposeOptions = Snapshots.options(),
        ) {
            SnapshotSurface {
                val sample = context.getString(R.string.preview_screen_title)
                Text(
                    text = sample,
                    style = SklyankaType.Headline.copy(fontFamily = Literata),
                    color = SklyankaTheme.colors.text,
                )
                Text(
                    text = sample,
                    style = SklyankaType.Headline.copy(fontFamily = GolosText),
                    color = SklyankaTheme.colors.text,
                )
                Text(
                    text = sample,
                    style = SklyankaType.Headline.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Default),
                    color = SklyankaTheme.colors.textMuted,
                )
            }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        profile: ProfileAccent = ProfileAccent.Owner,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        captureRoboImage(
            file = Snapshots.golden(GROUP, name),
            roborazziComposeOptions = Snapshots.options(fontScale = fontScale),
        ) {
            SnapshotSurface(profile = profile, dark = dark) { EnvironmentSheet() }
        }
    }

    @Composable
    private fun EnvironmentSheet() {
        ProfileStripe()
        ScreenHeader(
            labels = ScreenHeaderLabels(
                title = context.getString(R.string.preview_screen_title),
                overline = context.getString(R.string.preview_date),
            ),
        )
        IntakeCard(
            state = IntakeCardState.DueNow,
            labels = IntakeCardLabels(
                medication = context.getString(R.string.preview_medication),
                dose = context.getString(R.string.preview_dose),
                time = context.getString(R.string.preview_marked_time),
            ),
            actions = IntakeCardActions(onMark = {}, onSkip = {}),
        )
        SklyankaTextField(
            value = context.getString(R.string.preview_field_value),
            onValueChange = {},
            hint = context.getString(R.string.preview_field_label),
        )
        Banner(
            text = context.getString(R.string.preview_banner_warning),
            kind = BannerKind.Warning,
            actionLabel = context.getString(R.string.preview_quiet_action),
            onAction = {},
        )
        PrimaryButton(text = context.getString(R.string.preview_primary_action), onClick = {})
    }

    private companion object {
        const val GROUP = "environment"
        const val SCALE_130 = 1.3f
        const val SCALE_200 = 2.0f
    }
}
