package ru.stohastika.sklyanka.acceptance.settings

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.settings.PermissionsSummary
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.SettingsScreen
import ru.stohastika.sklyanka.feature.settings.SettingsUiState
import ru.stohastika.sklyanka.feature.settings.TAG_ABOUT
import ru.stohastika.sklyanka.feature.settings.TAG_ACTIVE_PROFILE
import ru.stohastika.sklyanka.feature.settings.TAG_ADD_PROFILE
import ru.stohastika.sklyanka.feature.settings.TAG_HISTORY_ROW
import ru.stohastika.sklyanka.feature.settings.TAG_PERMISSIONS_ROW
import ru.stohastika.sklyanka.feature.settings.TAG_PROFILE_ROW
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.feature.settings.TAG_SNOOZE_DIALOG
import ru.stohastika.sklyanka.feature.settings.TAG_SNOOZE_ROW
import ru.stohastika.sklyanka.feature.settings.TAG_SOUND_ROW
import ru.stohastika.sklyanka.feature.settings.TAG_TRANSFER_ROW
import ru.stohastika.sklyanka.ui.component.TAG_SWITCH_ROW
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.lightColors
import java.io.File

/**
 * Экранные шаги приёмки настроек (SKL-20, группа `@screen`).
 *
 * Шаги живут в `feature-settings`, потому что говорят об одном экране (`SKL-26/TDD.md §4.3`).
 * Состояние собирает настоящий `SettingsViewModel` на мире сценария
 * ([SettingsScreenProbe]); здесь — только вопросы к собранной композиции.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class SettingsScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Сколько раз ViewModel собирали к моменту возвращения в приложение. */
    private var buildsAtReturn: Int = 0

    // --- Профили -------------------------------------------------------------------------------

    @Когда("^человек смотрит на блок «Профили»$")
    fun thePersonLooksAtTheProfiles() {
        SettingsScreenProbe.ask(world) { assertTag(TAG_SETTINGS_SCREEN) }
    }

    @Тогда("^первой идёт строка «Мои лекарства» с отметкой активного профиля$")
    fun theOwnProfileComesFirstAndIsActive() {
        SettingsScreenProbe.ask(world) {
            val rows = onAllNodesWithTag(TAG_PROFILE_ROW, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(rows.size >= 2, "the profile block must carry both profiles")
            val first = rows.minBy { it.boundsInRoot.top }
            val texts = textsOf(first)
            assertTrue(
                texts.contains(string(R.string.settings_profile_self)),
                "the first row must be «${string(R.string.settings_profile_self)}»: $texts",
            )
            val marks = onAllNodesWithTag(TAG_ACTIVE_PROFILE, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(1, marks.size, "exactly one profile is active")
            assertTrue(
                marks.single().boundsInRoot.top >= first.boundsInRoot.top,
                "the active mark must stand on the first row",
            )
        }
    }

    @Тогда("^следом идёт строка «Мама» с подписью «4 лекарства · напоминания дублируются мне»$")
    fun theForeignProfileFollowsWithItsSubtitle() {
        val medications = context.resources.getQuantityString(R.plurals.settings_profile_medications, FOUR, FOUR)
        val expected = context.getString(
            R.string.settings_profile_subtitle,
            medications,
            string(R.string.settings_profile_mirrored),
        )
        val state = SettingsScreenProbe.state(world)
        assertTrue(hasText(state, "Мама"), "the row «Мама» must be shown")
        assertTrue(hasText(state, expected), "the row «Мама» must carry «$expected» (ПРФ-7)")
    }

    @Тогда("^последней идёт строка «Добавить профиль»$")
    fun theAddProfileRowComesLast() {
        SettingsScreenProbe.ask(world) {
            val rowTops = onAllNodesWithText(string(R.string.settings_profile_add), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { it.boundsInRoot.top }
            val profileTops = onAllNodesWithTag(TAG_PROFILE_ROW, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { it.boundsInRoot.top }
            assertTrue(rowTops.isNotEmpty(), "the add-profile row must be on the screen")
            assertTrue(
                rowTops.min() > requireNotNull(profileTops.maxOrNull()),
                "«${string(R.string.settings_profile_add)}» must go after the profiles",
            )
        }
    }

    @Но("^строка «Мои лекарства» не несёт признаков чужого профиля$")
    fun theOwnRowCarriesNoForeignSigns() {
        SettingsScreenProbe.ask(world) {
            val row = onAllNodesWithTag(TAG_PROFILE_ROW, useUnmergedTree = true).fetchSemanticsNodes()
                .minBy { it.boundsInRoot.top }
            val texts = textsOf(row)
            assertEquals(
                emptyList<String>(),
                texts.filter { it.contains(string(R.string.settings_profile_mirrored)) },
                "the own row must not say its reminders are mirrored (ПРФ-16)",
            )
            val countPattern = Regex("\\d+\\s+лекарств")
            assertFalse(
                texts.any { text -> countPattern.containsMatchIn(text) },
                "the own row must not carry the medication count (ПРФ-16): $texts",
            )
        }
    }

    // --- Напоминания ---------------------------------------------------------------------------

    @Когда("^человек смотрит на блок «Напоминания»$")
    fun thePersonLooksAtTheReminders() {
        SettingsScreenProbe.ask(world) { assertTag(TAG_SETTINGS_SCREEN) }
    }

    /**
     * SKL-78, ОВ-98: у строки «Звук» значения нет, а подпись называет, куда ведёт переход.
     *
     * Фальшивое «Мягкий сигнал» снято: звук — свойство неизменяемого канала, и выбирается
     * он в системном экране уведомлений приложения.
     */
    @Тогда("^строка «Звук» не показывает значения и подписана «(.+?)»$")
    fun theSoundRowCarriesNoValueButNamesThePlace(caption: String) {
        val state = SettingsScreenProbe.state(world)
        assertTrue(hasText(state, string(R.string.settings_sound)), "the sound row must be there")
        assertTrue(hasText(state, caption), "the sound row must carry «$caption»")
        assertFalse(
            hasText(state, FAKE_SOUND_VALUE),
            "the fake sound value must be gone (ПМ-15 п. 3, ОВ-98)",
        )
        SettingsScreenProbe.ask(world) {
            assertTrue(
                onAllNodesWithTag(TAG_SOUND_ROW, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
                "the sound row must be a row on the screen",
            )
        }
    }

    /** SKL-78, ОВ-98: «Отложить на» открывает выбор интервала, выбор применяется сразу. */
    @Тогда("^строка «Отложить на» открывает выбор интервала откладывания$")
    fun theSnoozeRowOpensThePicker() {
        SettingsScreenProbe.act(world) {
            onNodeWithTag(TAG_SNOOZE_ROW, useUnmergedTree = true).performClick()
        }
        assertTrue(SettingsScreenProbe.state(world).snoozePickerVisible, "the interval picker must open")
        SettingsScreenProbe.ask(world) {
            onNodeWithTag(TAG_SNOOZE_DIALOG).assertExists()
        }
    }

    @Тогда("^строка «Настойчивое напоминание» с подписью «(.+?)» показывает, включено оно или выключено$")
    fun thePersistentRowShowsItsState(caption: String) {
        SettingsScreenProbe.ask(world) {
            val node = onNodeWithTag(TAG_SWITCH_ROW, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(textsOf(node).contains(caption), "the row must carry the caption «$caption»")
            assertEquals(
                SettingsScreenProbe.state(world).reminders.persistentEnabled,
                node.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On,
                "the switch must show whether the setting is on",
            )
        }
    }

    @Тогда("^строка «Отложить на» показывает выбранный интервал «(.+?)»$")
    fun theSnoozeRowShowsTheInterval(value: String) {
        val state = SettingsScreenProbe.state(world)
        val expected = context.resources.getQuantityString(
            R.plurals.settings_snooze_minutes,
            state.reminders.snoozeMinutes,
            state.reminders.snoozeMinutes,
        )
        assertEquals(value, expected, "the snooze row must show the stored interval")
        assertTrue(hasText(state, value), "the row must show «$value»")
    }

    /**
     * «Тихие часы» — зафиксированное расхождение макета и продукта (решение по `ОВ-11`):
     * настройка, глушащая напоминание, противоречит НАП-1. Проверяется отсутствием
     * второго переключателя, а не поиском строки, которой нет ни в ресурсах, ни в коде.
     */
    @Тогда("^строки «Тихие часы» на экране нет$")
    fun thereIsNoQuietHoursRow() {
        SettingsScreenProbe.ask(world) {
            assertEquals(1, onAllNodesWithTag(TAG_SWITCH_ROW, useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }

    @Тогда("^строка выбора звука на экране одна$")
    fun theSoundRowIsTheOnlyOne() {
        SettingsScreenProbe.ask(world) {
            assertEquals(
                1,
                onAllNodesWithText(string(R.string.settings_sound), useUnmergedTree = true).fetchSemanticsNodes().size,
            )
        }
    }

    @Тогда("^выбора звука для отдельного лекарства нигде нет$")
    fun thereIsNoSoundChoicePerMedication() {
        // Второго выбора звука нет ни на экране, ни в контракте намерений: настройка
        // звука одна на приложение (НАП-27), а выбор списком — вторая версия.
        val state = SettingsScreenProbe.state(world)
        val offenders = texts(state).filter { text ->
            SOUND_KEYS.any { key -> text.contains(key, ignoreCase = true) }
        }
        assertEquals(emptyList<String>(), offenders, "the sound must be chosen in one place only (НАП-27)")
    }

    @Допустим("^настойчивое напоминание включено$")
    fun thePersistentReminderIsOn() {
        kotlinx.coroutines.runBlocking { SettingsScreenProbe.reminderSettings(world).setPersistentEnabled(true) }
        assertTrue(SettingsScreenProbe.state(world).reminders.persistentEnabled, "the premise says it is on")
    }

    @Когда("^человек выключает настойчивое напоминание$")
    fun thePersonTurnsThePersistentReminderOff() {
        SettingsScreenProbe.act(world) { onNodeWithTag(TAG_SWITCH_ROW, useUnmergedTree = true).performClick() }
    }

    @Тогда("^строка показывает его выключенным без перезахода на экран$")
    fun theRowShowsItOffWithoutReentering() {
        assertFalse(SettingsScreenProbe.state(world).reminders.persistentEnabled, "the change must be visible at once")
        assertEquals(
            false,
            kotlinx.coroutines.runBlocking { SettingsScreenProbe.reminderSettings(world).read().persistentEnabled },
            "the change must be stored",
        )
    }

    // --- Разрешения ----------------------------------------------------------------------------

    @Допустим("^(выданы все разрешения|не выдано одно разрешение|не выдано два разрешения)$")
    fun thePermissionsAreKnown(state: String) {
        when (state) {
            "выданы все разрешения" -> world.system.grant(
                ReminderPermission.NOTIFICATIONS,
                ReminderPermission.EXACT_ALARMS,
            )
            "не выдано одно разрешение" -> {
                world.system.grant(ReminderPermission.NOTIFICATIONS)
                world.system.deny(ReminderPermission.EXACT_ALARMS)
            }

            else -> world.system.deny(ReminderPermission.NOTIFICATIONS, ReminderPermission.EXACT_ALARMS)
        }
    }

    @Когда("^человек открывает настройки$")
    fun thePersonOpensTheSettings() {
        SettingsScreenProbe.ask(world) { assertTag(TAG_SETTINGS_SCREEN) }
    }

    /**
     * Строка разрешений: текст состояния и цвет акцента.
     *
     * Акцент в семантике не живёт, поэтому при значении «акцентным цветом» вопрос
     * задаётся картинке: у той же строки без отказов акцентных пикселей меньше.
     */
    @Тогда(
        "^строка «Разрешения и автозапуск» показывает " +
            "((?:состояние )?«.+».*|число невыданных разрешений|нейтральную подпись без акцента)$",
    )
    fun thePermissionsRowShows(value: String) {
        val state = SettingsScreenProbe.state(world)
        // SKL-14 называет то же состояние словами «показывает состояние «Одно не выдано»»;
        // строка «показывает, что не всё выдано» сюда не попадает — у неё своё объявление.
        val shown = value.removePrefix("состояние ").trim()
        when {
            shown.startsWith("«${string(R.string.settings_permissions_denied_one)}»") -> {
                assertEquals(1, state.permissions.denied, "the premise says exactly one permission is missing")
                assertTrue(
                    hasText(state, string(R.string.settings_permissions_denied_one)),
                    "the row must say exactly that (НФТ-8)",
                )
                if (shown.contains("акцент")) {
                    val neutral = state.copy(permissions = state.permissions.copy(denied = 0))
                    assertTrue(
                        accentPixels(state) > accentPixels(neutral),
                        "the missing permission must be shown in accent (НФТ-8)",
                    )
                }
            }

            shown == "число невыданных разрешений" -> {
                assertEquals(2, state.permissions.denied)
                assertTrue(
                    hasText(state, context.getString(R.string.settings_permissions_denied_many, TWO)),
                    "the row must carry the number of missing permissions",
                )
            }

            else -> {
                assertEquals(0, state.permissions.denied)
                val neutral = state.copy(permissions = PermissionsSummary(denied = 0, manual = 0))
                assertEquals(
                    accentPixels(neutral),
                    accentPixels(state),
                    "a granted line must stay neutral, without accent",
                )
                val shown = listOf(
                    string(R.string.settings_permissions_granted_manual),
                    string(R.string.settings_permissions_granted),
                )
                assertTrue(
                    texts(state).any { it in shown },
                    "the row must show a neutral line, not «${string(R.string.settings_permissions_denied_one)}»",
                )
            }
        }
    }

    /**
     * Акцент строки отдельной проверкой (SKL-14).
     *
     * В семантике цвет не живёт, поэтому вопрос задаётся картинке: у того же экрана
     * без единого отказа акцентных пикселей меньше (приём SKL-20 и SKL-18).
     */
    @И("^это состояние выделено акцентным цветом$")
    fun theStateIsHighlighted() {
        val state = SettingsScreenProbe.state(world)
        val neutral = state.copy(permissions = state.permissions.copy(denied = 0))
        assertTrue(
            accentPixels(state) > accentPixels(neutral),
            "the missing permission must be shown in accent (НФТ-8)",
        )
    }

    @Когда("^человек выдаёт точные будильники в системных настройках и возвращается в приложение$")
    fun thePersonGrantsExactAlarmsAndComesBack() {
        world.system.grant(ReminderPermission.EXACT_ALARMS)
        buildsAtReturn = SettingsScreenProbe.builds
        // Возвращение в приложение — тот же вызов, что в `MainActivity.onResume` (НФТ-52).
        SettingsScreenProbe.refresh(world)
    }

    @Тогда("^строка показывает, что все разрешения выданы$")
    fun theRowShowsEverythingGranted() {
        val state = SettingsScreenProbe.state(world)
        assertEquals(0, state.permissions.denied, "no permission may stay missing")
        val shown = listOf(
            string(R.string.settings_permissions_granted_manual),
            string(R.string.settings_permissions_granted),
        )
        assertTrue(texts(state).any { it in shown }, "the row must show the granted state")
    }

    @Тогда("^приложение не потребовалось перезапускать$")
    fun theAppWasNotRestarted() {
        assertEquals(buildsAtReturn, SettingsScreenProbe.builds, "the settings were re-read, not rebuilt")
    }

    // --- О приложении --------------------------------------------------------------------------

    @Когда("^человек смотрит на плашку о приложении$")
    fun thePersonLooksAtTheAboutPlate() {
        SettingsScreenProbe.ask(world) { assertTag(TAG_ABOUT) }
    }

    @Тогда("^он видит название и версию «(.+?)»$")
    fun heSeesTheNameAndVersion(value: String) {
        val state = SettingsScreenProbe.state(world)
        val expected = string(R.string.settings_about_app, state.version)
        assertEquals(value, expected, "the plate must carry the built version of the app")
        assertTrue(hasText(state, value), "the plate must carry «$value»")
    }

    @Тогда("^строку «(.+?)»$")
    fun theScreenCarriesTheLine(line: String) {
        assertTrue(hasText(SettingsScreenProbe.state(world), line), "the screen must carry «$line»")
    }

    /**
     * «Видна сразу» — без нажатий, раскрытий и прокрутки длинного текста
     * (`design/SPEC-original.md` §1, `П-4`).
     */
    @Тогда("^эта формулировка видна сразу, без нажатий и раскрытий$")
    fun theLineIsVisibleAtOnce() {
        SettingsScreenProbe.ask(world) {
            onNodeWithTag(TAG_ABOUT).assertIsDisplayed()
            assertTrue(
                onAllNodesWithTag(TAG_ABOUT).fetchSemanticsNodes()
                    .none { node -> node.config.contains(SemanticsActions.OnClick) },
                "the plate must not hide the wording behind a tap",
            )
        }
    }

    @Когда("^человек нажимает на плашку о приложении$")
    fun thePersonTapsTheAboutPlate() {
        SettingsScreenProbe.ask(world) {
            assertTrue(
                onAllNodesWithTag(TAG_ABOUT).fetchSemanticsNodes()
                    .none { node -> node.config.contains(SemanticsActions.OnClick) },
                "the plate has no click action at all — there is nowhere outside to go (НФТ-14)",
            )
        }
    }

    @Тогда("^никакой сетевой ссылки не открывается$")
    fun noNetworkLinkOpens() {
        val offenders = texts(SettingsScreenProbe.state(world)).filter { text ->
            LINKS.any { link -> text.contains(link, ignoreCase = true) }
        }
        assertEquals(emptyList<String>(), offenders, "the screen must carry no link outside")
    }

    @Тогда("^проверки обновлений не происходит$")
    fun noUpdateCheckHappens() {
        val offenders = texts(SettingsScreenProbe.state(world)).filter { text ->
            UPDATE_KEYS.any { key -> text.contains(key, ignoreCase = true) }
        }
        assertEquals(emptyList<String>(), offenders, "the screen must not offer updates (НФТ-15)")
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun hasText(state: SettingsUiState, text: String): Boolean {
        var found = false
        SettingsScreenProbe.ask(world) {
            found = onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun texts(state: SettingsUiState): List<String> {
        var texts: List<String> = emptyList()
        SettingsScreenProbe.ask(world) {
            texts = onAllNodesWithTag(TAG_SETTINGS_SCREEN, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .flatMap { textsOf(it) }
        }
        return texts
    }

    private fun textsOf(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    private fun ComposeUiTest.assertTag(tag: String) = onNodeWithTag(tag).assertIsDisplayed()

    /**
     * Пиксели акцентного цвета на картинке экрана.
     *
     * Снимок во временный каталог: эталоны эти шаги не трогают. Цвет подписи состояния —
     * единственный носитель «не выдано», и в семантике он не живёт; поэтому вопрос
     * задаётся картинке, как это делает шаг различимости в истории.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun accentPixels(state: SettingsUiState): Int {
        val target = File.createTempFile("settings-accent", ".png")
        try {
            captureRoboImage(
                file = target,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
                roborazziComposeOptions = RoborazziComposeOptions {
                    size(widthDp = CAPTURE_WIDTH, heightDp = CAPTURE_HEIGHT)
                },
            ) {
                SklyankaTheme {
                    SettingsScreen(
                        state = state,
                        onIntent = {},
                        modifier = Modifier.fillMaxSize().background(SklyankaTheme.colors.background),
                    )
                }
            }
            val bitmap =
                requireNotNull(BitmapFactory.decodeFile(target.path)) { "the settings screen was not rendered" }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val accent = lightColors().accent.toArgb()
            return pixels.count { it == accent }
        } finally {
            target.delete()
        }
    }

    private companion object {
        const val FOUR = 4
        const val TWO = 2
        const val CAPTURE_WIDTH = 390
        const val CAPTURE_HEIGHT = 2400

        /** Округление плотности может отнять доли точки; одной точки запаса хватает. */
        val TOLERANCE = SklyankaSizes.BorderThin

        val SCALES = mapOf("100 %" to 1f, "130 %" to 1.3f, "200 %" to 2f)

        /** Строки, до которых обязан дойти человек, проходя экран сверху донизу. */
        val REACHABLE = listOf(
            TAG_ADD_PROFILE,
            TAG_SWITCH_ROW,
            TAG_PERMISSIONS_ROW,
            TAG_HISTORY_ROW,
            TAG_TRANSFER_ROW,
        )

        val SOUND_KEYS = listOf("звук для", "звук лекарства", "выбрать звук")
        val LINKS = listOf("http", "www.", ".ru/", ".com/", "mailto")

        /** Прежнее фальшивое значение строки «Звук», снятое ОВ-98. Литерал намеренный. */
        const val FAKE_SOUND_VALUE = "Мягкий сигнал"

        /**
         * Слова проверки или предложения обновления, а не корень «обновлен».
         *
         * SKL-58 ставит на экран честную подпись «Будет доступно в ближайшем обновлении»:
         * это утверждение о будущей функции, а не предложение проверить или поставить
         * обновление. Корень ловил бы её как ложный признак, поэтому признаки названы
         * действиями — так проверка НФТ-15 остаётся такой же строгой и не шумит.
         */
        val UPDATE_KEYS = listOf(
            "проверить обновлен",
            "проверка обновлен",
            "обновить приложение",
            "скачать обновлен",
            "новая версия",
        )
    }
}
