package ru.stohastika.sklyanka.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.io.File
import java.time.ZoneId

/**
 * Эталонные снимки экрана разрешений (SKL-24).
 *
 * Снимок на состояние, а не на устройство: состояний у цепочки конечное число, и каждое
 * из них — отдельная картинка в ревью. Сняты: состояние макета, чистая установка,
 * пройденная цепочка, запасной путь с текстовой инструкцией, вход из настроек без
 * индикатора шагов и по кадру на профиль прошивки — Xiaomi, Samsung и общий Android (SKL-112).
 *
 * Профильные кадры рендерятся стендом: `Build.MANUFACTURER` на JVM неоткуда взять, и мир
 * сценария ставит профиль сам. Живое устройство подтверждает названия пунктов меню —
 * строки чек-листа SKL-110.
 *
 * Чего снимок НЕ доказывает: что на настоящем экране всё это помещается и читается —
 * это смотрят глазами на эталонном устройстве (`docs/archive/tasks/SKL-24/TDD.md §2.2`).
 */
@RunWith(AndroidJUnit4::class)
class PermissionsScreenSnapshotTest {

    @Test
    fun asTheMockUpDrawsIt() = capture("onboarding-permissions") {
        show(PermissionsScreenFixtures.asDrawn())
    }

    @Test
    fun onACleanInstallNothingIsGranted() = capture("onboarding-nothing-granted") {
        show(PermissionsScreenFixtures.nothingGranted())
    }

    @Test
    fun aCompleteChainStillCallsTheAutostartStepConfirmedByHand() = capture("onboarding-chain-complete") {
        show(PermissionsScreenFixtures.chainComplete())
    }

    @Test
    fun onAFirmwareWithoutTheSystemScreen() = capture("onboarding-instructions") {
        show(PermissionsScreenFixtures.withInstructions())
    }

    // --- Вендорские ветки: кадр на каждый профиль цепочки (SKL-112) ------------------------------

    @Test
    fun onXiaomiTheChainCarriesAutostartInsteadOfAppStartup() = capture("onboarding-xiaomi") {
        show(PermissionsScreenFixtures.xiaomi())
    }

    @Test
    fun onSamsungTheChainHasNoAutostartStep() = capture("onboarding-samsung") {
        show(PermissionsScreenFixtures.samsung())
    }

    @Test
    fun onAnUnknownFirmwareOnlyTheCommonStepsRemain() = capture("onboarding-android") {
        show(PermissionsScreenFixtures.unknownFirmware())
    }

    @Test
    fun onReEntryFromSettingsWithoutTheStepIndicator() = capture("onboarding-from-settings") {
        show(PermissionsScreenFixtures.fromSettings())
    }

    @Composable
    private fun show(state: OnboardingUiState) {
        PermissionsScreen(
            state = state,
            actions = PermissionsScreenFixtures.actions(),
            toLocal = { instant -> instant.atZone(ZoneId.of("Europe/Moscow")).toLocalDateTime() },
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

        /**
         * Высота больше окна телефона: экран длиннее 844 dp и в телефоне скроллится.
         * Снимок окна показал бы только его верх, то есть перестал бы замечать изменения
         * в трёх нижних блоках.
         */
        const val HEIGHT = 1400
    }
}
