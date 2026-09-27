package ru.heuri.sklyanka.ui.snapshot

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.ui.R
import ru.heuri.sklyanka.ui.component.Banner
import ru.heuri.sklyanka.ui.component.BannerKind
import ru.heuri.sklyanka.ui.component.ButtonState
import ru.heuri.sklyanka.ui.component.ChipState
import ru.heuri.sklyanka.ui.component.EmptyState
import ru.heuri.sklyanka.ui.component.IntakeCard
import ru.heuri.sklyanka.ui.component.IntakeCardActions
import ru.heuri.sklyanka.ui.component.IntakeCardLabels
import ru.heuri.sklyanka.ui.component.IntakeCardState
import ru.heuri.sklyanka.ui.component.OnboardingSteps
import ru.heuri.sklyanka.ui.component.PrimaryButton
import ru.heuri.sklyanka.ui.component.ProfileStripe
import ru.heuri.sklyanka.ui.component.ProgressPurpose
import ru.heuri.sklyanka.ui.component.QuietButton
import ru.heuri.sklyanka.ui.component.ScreenHeader
import ru.heuri.sklyanka.ui.component.ScreenHeaderKind
import ru.heuri.sklyanka.ui.component.ScreenHeaderLabels
import ru.heuri.sklyanka.ui.component.SecondaryButton
import ru.heuri.sklyanka.ui.component.SklyankaChip
import ru.heuri.sklyanka.ui.component.SklyankaNumberField
import ru.heuri.sklyanka.ui.component.SklyankaProgressBar
import ru.heuri.sklyanka.ui.component.SklyankaSwitch
import ru.heuri.sklyanka.ui.component.SklyankaSwitchRow
import ru.heuri.sklyanka.ui.component.SklyankaTextField
import ru.heuri.sklyanka.ui.component.SwitchState
import ru.heuri.sklyanka.ui.component.TextFieldState
import ru.heuri.sklyanka.ui.profile.LocalProfileChrome
import ru.heuri.sklyanka.ui.profile.ProfileChromeState
import ru.heuri.sklyanka.ui.theme.ProfileAccent
import ru.heuri.sklyanka.ui.token.ProfileColor
import ru.heuri.sklyanka.ui.token.SklyankaShapes

/**
 * Эталонный снимок на состояние компонента (SKL-24, критерий 4).
 *
 * Состав снимков задаётся обходом `entries` самих перечислений состояний
 * (`ComponentStates.kt`), а не списком в тесте, — по тому же принципу, по которому
 * собран каталог. Добавленное состояние появляется в снимках само и валит сверку
 * отсутствующим эталоном; состояния, которое есть в коде и не имеет снимка, не бывает.
 *
 * Снимок на СОСТОЯНИЕ, а не на устройство: состояний конечное число, устройств
 * бесконечно много, и ни одно из них стенд не изображает (`TDD.md §2`).
 */
@RunWith(AndroidJUnit4::class)
class ComponentStateSnapshotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun intakeCardInEveryState() {
        IntakeCardState.entries.forEach { state ->
            capture("intake-card-${state.name.lowercase()}") {
                IntakeCard(
                    state = state,
                    labels = cardLabels(),
                    actions = IntakeCardActions(onMark = {}, onSkip = {}),
                )
            }
        }
        // Шестое состояние таблицы §4.1: тот же приём, отмеченный из чужого профиля.
        capture("intake-card-marked-by-you") {
            IntakeCard(state = IntakeCardState.Taken, labels = cardLabels(), markedByYou = true)
        }
    }

    @Test
    fun chipInEveryState() {
        val label = context.getString(R.string.preview_chip_label)
        ChipState.entries.forEach { state ->
            capture("chip-${state.name.lowercase()}") {
                SklyankaChip(text = label, state = state, onClick = {})
            }
        }
        capture("chip-time-pill") {
            SklyankaChip(
                text = context.getString(R.string.preview_time),
                state = ChipState.Info,
                shape = SklyankaShapes.Pill,
            )
        }
    }

    @Test
    fun buttonsInEveryState() {
        val primary = context.getString(R.string.preview_primary_action)
        val secondary = context.getString(R.string.preview_secondary_action)
        val quiet = context.getString(R.string.preview_quiet_action)
        ButtonState.entries.forEach { state ->
            capture("button-primary-${state.name.lowercase()}") {
                PrimaryButton(text = primary, onClick = {}, state = state)
            }
        }
        // «В работе» есть только у основной кнопки: у остальных этого состояния нет.
        ButtonState.entries.filter { it != ButtonState.Busy }.forEach { state ->
            capture("button-secondary-${state.name.lowercase()}") {
                SecondaryButton(text = secondary, onClick = {}, state = state)
            }
            capture("button-quiet-${state.name.lowercase()}") {
                QuietButton(text = quiet, onClick = {}, state = state)
            }
        }
        capture("button-quiet-muted") { QuietButton(text = quiet, onClick = {}, muted = true) }
    }

    @Test
    fun textFieldInEveryState() {
        TextFieldState.entries.forEach { state ->
            capture("text-field-${state.name.lowercase()}") {
                SklyankaTextField(
                    value = if (state == TextFieldState.Empty) "" else context.getString(R.string.preview_field_value),
                    onValueChange = {},
                    hint = context.getString(R.string.preview_field_label),
                    unitLabel = if (state == TextFieldState.Disabled) {
                        context.getString(R.string.preview_field_unit)
                    } else {
                        null
                    },
                    errorText = if (state == TextFieldState.Error) {
                        context.getString(R.string.preview_field_error)
                    } else {
                        null
                    },
                    forcedState = state,
                )
            }
        }
        capture("text-field-number") {
            SklyankaNumberField(value = context.getString(R.string.preview_time), onValueChange = {})
        }
    }

    @Test
    fun switchInEveryState() {
        SwitchState.entries.forEach { state ->
            capture("switch-${state.name.lowercase()}") { SklyankaSwitch(state = state) }
        }
        capture("switch-row-with-caption") {
            SklyankaSwitchRow(
                label = context.getString(R.string.preview_switch_label),
                caption = context.getString(R.string.preview_switch_caption),
                checked = true,
                onCheckedChange = {},
            )
        }
        capture("switch-row-plain") {
            SklyankaSwitchRow(
                label = context.getString(R.string.preview_switch_label),
                checked = false,
                onCheckedChange = {},
            )
        }
    }

    @Test
    fun bannerInEveryKind() {
        BannerKind.entries.forEach { kind ->
            capture("banner-${kind.name.lowercase()}") {
                Banner(
                    text = context.getString(R.string.preview_banner_warning),
                    kind = kind,
                    actionLabel = context.getString(R.string.preview_quiet_action),
                    onAction = {},
                )
            }
        }
    }

    @Test
    fun progressInEveryPurpose() {
        val label = context.resources.getQuantityString(
            R.plurals.intakes_taken_of_total,
            TOTAL,
            TAKEN,
            TOTAL,
        )
        ProgressPurpose.entries.forEach { purpose ->
            capture("progress-${purpose.name.lowercase()}") {
                SklyankaProgressBar(progress = PROGRESS, label = label, purpose = purpose)
            }
        }
        capture("progress-onboarding-steps") { OnboardingSteps(total = STEPS, current = STEP) }
    }

    @Test
    fun screenHeaderInEveryKind() {
        val title = context.getString(R.string.preview_screen_title)
        capture("screen-header-root-with-date") {
            ScreenHeader(
                labels = ScreenHeaderLabels(
                    title = title,
                    overline = context.getString(R.string.preview_date),
                ),
            )
        }
        ScreenHeaderKind.entries.filter { it != ScreenHeaderKind.Root }.forEach { kind ->
            capture("screen-header-${kind.name.lowercase()}") {
                ScreenHeader(labels = ScreenHeaderLabels(title = title), kind = kind, onBack = {})
            }
        }
        // Шапка чужого профиля снимается вместе со своим оформлением: надпись
        // «Профиль · Имя» и баннер приходят из окружения и порознь не существуют (ПРФ-11).
        capture(
            name = "screen-header-foreign-profile",
            profile = ProfileAccent.Foreign(ProfileColor.Terracotta),
        ) {
            CompositionLocalProvider(
                LocalProfileChrome provides ProfileChromeState.Foreign(
                    name = context.getString(R.string.preview_profile_name),
                    colour = ProfileColor.Terracotta,
                ),
            ) {
                ScreenHeader(labels = ScreenHeaderLabels(title = title))
            }
        }
    }

    @Test
    fun emptyStateAndProfileStripe() {
        capture("empty-state") {
            EmptyState(
                title = context.getString(R.string.preview_empty_title),
                text = context.getString(R.string.preview_empty_text),
                actionLabel = context.getString(R.string.preview_primary_action),
                onAction = {},
            )
        }
        // Полоса профиля владельца не рисует ничего, и это состояние тоже эталонное:
        // появившаяся у владельца полоса — дефект, а не улучшение (ПРФ-11).
        capture("profile-stripe-owner") { ProfileStripe() }
        capture(
            name = "profile-stripe-foreign",
            profile = ProfileAccent.Foreign(ProfileColor.Terracotta),
        ) { ProfileStripe() }
    }

    private fun cardLabels() = IntakeCardLabels(
        medication = context.getString(R.string.preview_medication),
        dose = context.getString(R.string.preview_dose),
        time = context.getString(R.string.preview_marked_time),
    )

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, profile: ProfileAccent = ProfileAccent.Owner, content: @Composable () -> Unit) {
        captureRoboImage(
            file = Snapshots.golden(GROUP, name),
            roborazziComposeOptions = Snapshots.options(),
        ) {
            SnapshotSurface(profile = profile) {
                // Компоненты, которые в каталоге стоят в строку, в снимке остаются
                // в строке: перенос в столбик изменил бы то, что проверяется.
                Row { content() }
            }
        }
    }

    private companion object {
        const val GROUP = "component"
        const val PROGRESS = 0.4f
        const val TAKEN = 2
        const val TOTAL = 5
        const val STEPS = 3
        const val STEP = 2
    }
}
