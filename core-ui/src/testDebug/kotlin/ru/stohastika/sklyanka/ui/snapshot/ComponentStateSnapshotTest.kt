package ru.stohastika.sklyanka.ui.snapshot

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
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.component.ButtonState
import ru.stohastika.sklyanka.ui.component.ChipState
import ru.stohastika.sklyanka.ui.component.EmptyState
import ru.stohastika.sklyanka.ui.component.IntakeCard
import ru.stohastika.sklyanka.ui.component.IntakeCardActions
import ru.stohastika.sklyanka.ui.component.IntakeCardLabels
import ru.stohastika.sklyanka.ui.component.IntakeCardState
import ru.stohastika.sklyanka.ui.component.OnboardingSteps
import ru.stohastika.sklyanka.ui.component.PinnedHeaderBar
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.PrimaryButtonKind
import ru.stohastika.sklyanka.ui.component.ProfileStripe
import ru.stohastika.sklyanka.ui.component.ProgressPurpose
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.QuietButtonKind
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderKind
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.ScreenHeaderState
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.component.SklyankaChip
import ru.stohastika.sklyanka.ui.component.SklyankaNumberField
import ru.stohastika.sklyanka.ui.component.SklyankaProgressBar
import ru.stohastika.sklyanka.ui.component.SklyankaSwitch
import ru.stohastika.sklyanka.ui.component.SklyankaSwitchRow
import ru.stohastika.sklyanka.ui.component.SklyankaTextField
import ru.stohastika.sklyanka.ui.component.SwitchState
import ru.stohastika.sklyanka.ui.component.TextFieldState
import ru.stohastika.sklyanka.ui.profile.LocalProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileAvatar
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.theme.ProfileAccent
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.SklyankaShapes

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
        // Состояние «принято» снимается ниже кадром «отмечено вами»: отдельный кадр был
        // его побайтовой копией и снят решением Р-61 (SKL-112) — имя кадра несёт состояние.
        IntakeCardState.entries.filter { it != IntakeCardState.Taken }.forEach { state ->
            capture("intake-card-${state.name.lowercase()}") {
                IntakeCard(
                    state = state,
                    labels = cardLabels(),
                    actions = IntakeCardActions(onMark = {}, onSkip = {}),
                )
            }
        }
        // Шестое состояние таблицы §4.1: тот же приём, отмеченный из чужого профиля.
        // Имя несёт состояние карточки: кадр `intake-card-taken` был его побайтовой копией
        // и снят решением Р-61 (SKL-112).
        capture("intake-card-taken-marked-by-you") {
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
        // Три вида тихой кнопки названы своими именами: проверка «у каждого состояния
        // есть эталон» ищет снимок по имени вида (SKL-24).
        capture("button-quiet-accent") { QuietButton(text = quiet, onClick = {}) }
        capture("button-quiet-muted") { QuietButton(text = quiet, onClick = {}, kind = QuietButtonKind.Muted) }
        // Опасная тихая — вид той же кнопки (ОВ-124, §4.3): «Удалить» на карточке лекарства.
        capture("button-quiet-danger") { QuietButton(text = quiet, onClick = {}, kind = QuietButtonKind.Danger) }
        // Предупреждающий вид — действие баннера-предупреждения (§4.6).
        capture("button-quiet-warning") { QuietButton(text = quiet, onClick = {}, kind = QuietButtonKind.Warning) }
    }

    /**
     * Опасный вид основной кнопки — в состояниях, названных дизайн-системой поимённо.
     *
     * Нажатое и «в работе» не снимаются: потолок эталонов — сто файлов (SKL-24,
     * критерий 9), а затемнение и дорожка у опасного вида те же, что у основной кнопки,
     * и уже закреплены её снимками. Каталог показывает все четыре состояния.
     */
    @Test
    fun dangerButtonInTheNamedStates() {
        val danger = context.getString(R.string.preview_danger_action)
        listOf(ButtonState.Normal, ButtonState.Disabled).forEach { state ->
            capture("button-danger-${state.name.lowercase()}") {
                PrimaryButton(text = danger, onClick = {}, state = state, kind = PrimaryButtonKind.Danger)
            }
        }
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
        // Ошибка компактного поля: рамка 1,5 dp цветом danger (ОВ-38), без подписи —
        // пояснение рисует блок, в котором стоит поле (SKL-34).
        capture("number-field-error") {
            SklyankaNumberField(
                value = context.getString(R.string.preview_time),
                onValueChange = {},
                error = true,
            )
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
        // «День», «Курс» и «Остаток» рисуются одинаково: назначение меняет цвет только
        // у критического остатка. Кадр на каждое назначение был одним и тем же файлом
        // восемь раз — решение Р-61 оставляет кадр на каждый различимый вид (SKL-112).
        val distinctLooks = ProgressPurpose.entries.filter {
            it == ProgressPurpose.Course || it == ProgressPurpose.StockCritical
        }
        distinctLooks.forEach { purpose ->
            capture("progress-${purpose.name.lowercase()}") {
                SklyankaProgressBar(progress = PROGRESS, label = label, purpose = purpose)
            }
        }
        capture("progress-onboarding-steps") { OnboardingSteps(total = STEPS, current = STEP) }
    }

    /**
     * Сжатый закреплённый ряд (§7): компактный заголовок и действие справа.
     *
     * Состояние новое в SKL-105, и без эталона оно не закреплено ничем: порог по геометрии
     * проверяется тестом, но вид ряда — только кадром.
     */
    @Test
    fun thePinnedRowCollapsed() {
        capture("screen-header-pinned-collapsed") {
            PinnedHeaderBar(
                labels = ScreenHeaderLabels(title = context.getString(R.string.preview_screen_title)),
                state = collapsedHeader(),
                trailing = { ProfileAvatar(onOpenProfiles = {}) },
            )
        }
    }

    /** Ряд, у которого то, что обязано уйти под него, ушло: порог §7 измеряется геометрией. */
    private fun collapsedHeader() = ScreenHeaderState().apply {
        reportBarBottom(0)
        reportCollapsibleBottom(0)
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
    private fun capture(
        name: String,
        profile: ProfileAccent = ProfileAccent.Owner,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage(
            file = Snapshots.golden(GROUP, name),
            roborazziComposeOptions = Snapshots.options(fontScale = fontScale),
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

        /** Системный шрифт 200 %: тот же множитель, что у снимка среды (`§6.3`). */
        const val SCALE_200 = 2.0f
    }
}
