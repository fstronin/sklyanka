package ru.stohastika.sklyanka.feature.settings.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.profile.ProfileLosses
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_WORD_LABEL
import ru.stohastika.sklyanka.ui.component.TAG_TEXT_FIELD_ERROR
import ru.stohastika.sklyanka.ui.component.TAG_TEXT_FIELD_INPUT
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Экран удаления профиля: состав, слово и итог (ОВ-7, SKL-36).
 *
 * Идёт на JVM под Robolectric (SKL-24): состав экрана и доступность кнопки устройства
 * не требуют. Цвет кнопки опасного действия проверен снимками дизайн-системы SKL-39 —
 * здесь проверяется поведение: пока слово не введено верно, стереть нельзя.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: числа, слово и кнопки иначе не собираются.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class DeleteProfileScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theConfirmationListsWhatWillBeLostAndThatItIsIrreversible() {
        show()

        val title = string(R.string.profile_delete_title)
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DELETE_INTRO).assertIsDisplayed()
        composeRule.onNodeWithText(
            string(R.string.profile_delete_medications, MEDICATIONS, ARCHIVED),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.profile_delete_history, INTAKES)).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DELETE_REMINDERS).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DELETE_WARNING).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DELETE_EXPORT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DELETE_EXPORT_NOTE).assertIsDisplayed()
    }

    /** Нулевые числа показываются как есть — прочерков и прятанья строк не заводится. */
    @Test
    fun zerosAreShownAsIs() {
        show(fixture().copy(losses = ProfileLosses(medications = 0, archived = 0, intakes = 0)))

        composeRule.onNodeWithText(string(R.string.profile_delete_medications, 0, 0)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.profile_delete_history, 0)).assertIsDisplayed()
    }

    @Test
    fun theConfirmationWordOpensTheDangerAction() {
        showTyping()

        composeRule.onNodeWithTag(TAG_CONFIRM_WORD_LABEL).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DELETE_CONFIRM).assertIsNotEnabled()

        composeRule.onNodeWithTag(TAG_TEXT_FIELD_INPUT).performTextReplacement("Udalit")
        composeRule.onNodeWithTag(TAG_DELETE_CONFIRM).assertIsNotEnabled()
        composeRule.onNodeWithTag(TAG_TEXT_FIELD_ERROR).assertDoesNotExist()

        val word = string(R.string.profile_delete_word)
        composeRule.onNodeWithTag(TAG_TEXT_FIELD_INPUT).performTextReplacement("  ${word.uppercase()}  ")
        composeRule.onNodeWithTag(TAG_DELETE_CONFIRM).assertIsEnabled()
        composeRule.onNodeWithTag(TAG_TEXT_FIELD_ERROR).assertDoesNotExist()
    }

    @Test
    fun theFileSaveFactIsShownAfterTheExport() {
        show(
            fixture().copy(
                exportedAt = Instant.parse("2026-09-30T09:34:00Z"),
                zone = ZoneId.of("Europe/Moscow"),
            ),
        )

        composeRule
            .onNodeWithText(string(R.string.profile_delete_export_saved, savedAtInMoscow()))
            .assertIsDisplayed()
    }

    @Test
    fun anExportFailureIsNamed() {
        show(fixture().copy(failure = DeleteProfileFailure.ExportFailed))

        composeRule.onNodeWithTag(TAG_DELETE_FAILURE).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.transfer_error_export_failed)).assertIsDisplayed()
    }

    @Test
    fun theButtonsSendTheirIntents() {
        val intents = mutableListOf<DeleteProfileIntent>()
        var saves = 0
        show(
            state = fixture().copy(word = string(R.string.profile_delete_word)),
            onIntent = intents::add,
            onSaveFile = { saves++ },
        )

        composeRule.onNodeWithTag(TAG_DELETE_EXPORT).performClick()
        composeRule.onNodeWithTag(TAG_DELETE_CONFIRM).performClick()

        assertEquals(1, saves)
        assertEquals(listOf(DeleteProfileIntent.Confirm), intents)
    }

    @Test
    fun theResultNamesTheDeletedProfileAndTheOwner() {
        var done = 0
        show(fixture().copy(step = DeleteProfileStep.RESULT), onDone = { done++ })

        composeRule
            .onNodeWithText(string(R.string.profile_delete_result_title, PROFILE_NAME))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.profile_delete_result_active)).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DELETE_DONE).performClick()

        assertEquals(1, done)
    }

    private fun show(
        state: DeleteProfileUiState = fixture(),
        onIntent: (DeleteProfileIntent) -> Unit = {},
        onSaveFile: () -> Unit = {},
        onDone: () -> Unit = {},
    ) {
        composeRule.setContent {
            SklyankaTheme {
                DeleteProfileScreen(state = state, onIntent = onIntent, onSaveFile = onSaveFile, onDone = onDone)
            }
        }
    }

    /** Ввод слова: поле без собственного состояния, поэтому стенд зеркалит его локально. */
    private fun showTyping() {
        composeRule.setContent {
            var typed by remember { mutableStateOf("") }
            SklyankaTheme {
                DeleteProfileScreen(
                    state = fixture().copy(word = typed),
                    onIntent = { intent -> if (intent is DeleteProfileIntent.WordChanged) typed = intent.text },
                    onSaveFile = {},
                    onDone = {},
                )
            }
        }
    }

    private fun fixture() = DeleteProfileUiState(
        profileId = PROFILE_ID,
        profileName = PROFILE_NAME,
        losses = ProfileLosses(medications = MEDICATIONS, archived = ARCHIVED, intakes = INTAKES),
        exportFileName = "sklyanka-2026-09-30.json",
        zone = ZoneId.of("Europe/Moscow"),
    )

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** Тот же момент, что покажет экран: формат — из тех же ресурсов, локаль — из конфигурации. */
    private fun savedAtInMoscow(): String {
        val locale = context.resources.configuration.locales[0]
        val date = DateTimeFormatter
            .ofPattern(string(R.string.transfer_date_pattern_full), locale)
            .format(LocalDate.of(2026, 9, 30))
        val time = DateTimeFormatter
            .ofPattern(string(R.string.transfer_time_pattern), locale)
            .format(LocalTime.of(12, 34))
        return string(R.string.transfer_summary_moment, date, time)
    }

    private companion object {
        const val PROFILE_ID = "mother"
        const val PROFILE_NAME = "Mother"
        const val MEDICATIONS = 2
        const val ARCHIVED = 1
        const val INTAKES = 2
    }
}
