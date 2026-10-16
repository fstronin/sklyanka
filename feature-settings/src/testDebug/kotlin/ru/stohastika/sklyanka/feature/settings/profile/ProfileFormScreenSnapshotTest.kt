package ru.stohastika.sklyanka.feature.settings.profile

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.profile.ProfileCreationDecision
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfileColor
import java.io.File

/**
 * Эталонные снимки формы профиля — мастера и правки (SKL-121, §5.13–§5.14).
 *
 * Шесть состояний: мастер как его рисует макет; пустое имя, где кнопка недоступна и причина
 * названа; платная граница, где кнопка остаётся обычной (П-7, §5.10); предел пяти, где причина
 * предела закрывает завершение; правка владельца без цвета и дублирования; правка чужого
 * с выбранным цветом и включённым дублированием (ПРФ-26, ПРФ-27).
 *
 * Файлы снимков пишет сессия записи (`-Psklyanka.snapshots=record`), а не прогон проверки.
 */
@RunWith(AndroidJUnit4::class)
class ProfileFormScreenSnapshotTest {

    @Test
    fun theWizardAsTheMockupDrawsIt() = capture("profile-form-add") {
        ProfileFormScreen(state = wizard(name = "Бабушка", colour = ProfileColor.Olive), onIntent = {})
    }

    @Test
    fun theWizardWithABlankName() = capture("profile-form-add-blank") {
        ProfileFormScreen(state = wizard(name = "", colour = ProfileColor.Terracotta), onIntent = {})
    }

    /** Платная граница: причина названа, а кнопка не приглушена — нажатие ведёт в витрину. */
    @Test
    fun theWizardAtThePaidBoundary() = capture("profile-form-add-pro") {
        ProfileFormScreen(
            state = wizard(
                name = "Бабушка",
                colour = ProfileColor.Olive,
                creation = ProfileCreationDecision.BlockedByPro,
            ),
            onIntent = {},
        )
    }

    /** Предел пяти: причина предела названа над недоступной кнопкой (ПРФ-32, ПРФ-34). */
    @Test
    fun theWizardAtTheFiveProfileLimit() = capture("profile-form-add-limit") {
        ProfileFormScreen(
            state = wizard(
                name = "Бабушка",
                colour = ProfileColor.Olive,
                creation = ProfileCreationDecision.BlockedByLimit,
            ),
            onIntent = {},
        )
    }

    /** Правка владельца: только имя, ни цвета, ни дублирования — кнопка «Сохранить» (ПРФ-26). */
    @Test
    fun theOwnerEdit() = capture("profile-form-edit-owner") {
        ProfileFormScreen(
            state = ProfileFormUiState(mode = ProfileFormMode.Edit("self"), name = "Я", isSelf = true),
            onIntent = {},
        )
    }

    /** Правка чужого: имя, выбранный цвет и включённое дублирование (ПРФ-27, Р-29). */
    @Test
    fun theForeignEdit() = capture("profile-form-edit-foreign") {
        ProfileFormScreen(
            state = ProfileFormUiState(
                mode = ProfileFormMode.Edit("mother"),
                name = "Мама",
                colour = ProfileColor.Plum.argb,
                mirrorReminders = true,
            ),
            onIntent = {},
        )
    }

    /** Мастер с набранным именем и первым свободным цветом — без решения гейта (ПРФ-9). */
    private fun wizard(
        name: String,
        colour: ProfileColor,
        creation: ProfileCreationDecision = ProfileCreationDecision.Allowed,
    ): ProfileFormUiState = ProfileFormUiState(
        mode = ProfileFormMode.Add,
        name = name,
        colour = colour.argb,
        mirrorReminders = true,
        isSelf = false,
        creation = creation,
    )

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, content: @Composable () -> Unit) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "$GROUP/$name.png"),
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = WIDTH, heightDp = HEIGHT)
            },
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
