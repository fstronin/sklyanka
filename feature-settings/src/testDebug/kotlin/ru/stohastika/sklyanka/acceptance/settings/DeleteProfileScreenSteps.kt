package ru.stohastika.sklyanka.acceptance.settings

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.profile.ProfileLosses
import ru.stohastika.sklyanka.domain.transfer.ExportOutcome
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.TAG_ADD_PROFILE
import ru.stohastika.sklyanka.feature.settings.TAG_DELETE_DIVIDER
import ru.stohastika.sklyanka.feature.settings.TAG_DELETE_PROFILE_ROW
import ru.stohastika.sklyanka.feature.settings.profile.DeleteProfileIntent
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_DONE
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_EXPORT
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_EXPORT_FACT
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_WARNING
import ru.stohastika.sklyanka.ui.component.TAG_TEXT_FIELD_ERROR

/**
 * Экранные шаги удаления профиля (SKL-36, группа `@screen`).
 *
 * Шаги живут в `feature-settings`, потому что говорят об экранах настроек и удаления
 * (`SKL-26/TDD.md §4.3`). Состояние собирают настоящие `SettingsViewModel`
 * и `DeleteProfileViewModel` на мире сценария; здесь — вопросы к собранной композиции
 * и факты о мире.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions", "LargeClass")
@OptIn(ExperimentalTestApi::class)
class DeleteProfileScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Числа потерь и профиль на момент открытия: ими проверяется «не изменились». */
    private var lossesAtOpen: ProfileLosses? = null
    private var profileAtOpen: String? = null

    // --- Строка удаления в настройках ----------------------------------------------------------

    @Тогда("^последней строкой раздела «Профили», за разделителем, стоит «(.+)»$")
    fun theDeletionRowIsTheLastOfTheProfilesBlock(title: String) {
        SettingsScreenProbe.ask(world) {
            onNodeWithTag(TAG_DELETE_PROFILE_ROW).assertIsDisplayed()
            val delete = onNodeWithTag(TAG_DELETE_PROFILE_ROW).fetchSemanticsNode()
            val add = onNodeWithTag(TAG_ADD_PROFILE).fetchSemanticsNode()
            val divider = onNodeWithTag(TAG_DELETE_DIVIDER).fetchSemanticsNode()
            assertTrue(
                delete.boundsInRoot.top > divider.boundsInRoot.top,
                "the deletion row must stand behind a divider (ОВ-7)",
            )
            assertTrue(
                delete.boundsInRoot.top > add.boundsInRoot.top,
                "the deletion row must be the last of the block (ОВ-7)",
            )
        }
        assertTrue(settingsCarries(title), "the settings must carry «$title»")
    }

    @Тогда("^строки удаления профиля в настройках нет$")
    fun theSettingsCarryNoDeletionRow() {
        SettingsScreenProbe.ask(world) { onAllNodesWithTag(TAG_DELETE_PROFILE_ROW).assertCountEquals(0) }
    }

    // --- Экран подтверждения --------------------------------------------------------------------

    @Допустим("^у профиля «([^»]+)» (\\d+) записи истории$")
    fun theProfileHasHistoryRecords(profileName: String, count: String) {
        val owner = world.profiles.get(profileName)
        val medication = requireNotNull(world.medications.all().firstOrNull { it.profileId == owner.id }) {
            "the profile must have a medication to carry history"
        }
        repeat(count.toInt()) { index ->
            world.intakes.store(
                Intake(
                    id = "deletion-history-$profileName-$index",
                    medicationId = medication.id,
                    scheduledAt = world.clock.today().minusDays(index + 1L).atTime(HISTORY_HOUR, 0),
                    status = IntakeStatus.TAKEN,
                    actedAt = world.clock.now(),
                    source = IntakeSource.PHONE,
                    actorProfileId = owner.id,
                ),
            )
        }
    }

    @Когда("^человек открывает удаление профиля «([^»]+)»$")
    fun thePersonOpensTheDeletion(profileName: String) {
        assertEquals(
            profileName,
            world.profileSession.context().name,
            "the deletion is opened for the profile in whose context the person is (ОВ-7)",
        )
        DeleteProfileScreenProbe.open(world)
        val state = DeleteProfileScreenProbe.state(world)
        assertEquals(profileName, state.profileName, "the screen must name the profile being deleted")
        lossesAtOpen = state.losses
        profileAtOpen = profileName
    }

    @Тогда("^экран озаглавлен «(.+)»$")
    fun theScreenIsTitled(title: String) {
        assertTrue(shown(title), "the screen must be titled «$title»")
    }

    @Тогда("^сказано «(.+)»$")
    fun itIsSaid(text: String) {
        assertTrue(shown(text), "the screen must say «$text»")
    }

    @Тогда("^показано «(.+)»$")
    fun itIsShown(text: String) {
        assertTrue(shown(text), "the screen must show «$text»")
    }

    @Тогда("^показано предупреждение «(.+)»$")
    fun theWarningIsShown(text: String) {
        assertTrue(
            DeleteProfileScreenProbe.hasTag(DeleteProfileScreenProbe.state(world), TAG_DELETE_WARNING),
            "the warning banner must be on the screen",
        )
        assertTrue(shown(text), "the warning must say «$text»")
    }

    // --- Слово-подтверждение --------------------------------------------------------------------

    @Когда("^человек набирает слово «(.+)»$")
    fun thePersonTypesTheWord(word: String) {
        DeleteProfileScreenProbe.typeWord(world, word)
    }

    @Но("^пока слово не введено верно, кнопка «([^»]+)» недоступна$")
    fun theButtonIsUnavailableUntilTheWord(label: String) {
        assertDeleteButtonLabel(label)
        DeleteProfileScreenProbe.assertConfirmAvailability(world, enabled = false)
    }

    @Тогда("^кнопка «([^»]+)» (доступна|недоступна)$")
    fun theButtonIs(label: String, state: String) {
        assertDeleteButtonLabel(label)
        DeleteProfileScreenProbe.assertConfirmAvailability(world, enabled = state == "доступна")
    }

    @Тогда("^поле ввода слова не подсвечено как ошибка$")
    fun theWordFieldCarriesNoError() {
        DeleteProfileScreenProbe.ask(world) { onNodeWithTag(TAG_TEXT_FIELD_ERROR).assertDoesNotExist() }
    }

    // --- Выгрузка файла переноса ----------------------------------------------------------------

    @Когда("^человек нажимает «Сохранить файл переноса»$")
    fun thePersonSavesTheTransferFile() {
        assertTrue(shown(string(R.string.profile_delete_export)), "the export button must be on the screen")
        DeleteProfileScreenProbe.pressSave(world)
        DeleteProfileScreenProbe.chooseExportTarget(world)
    }

    @Когда("^человек отменяет системный выбор места сохранения$")
    fun thePersonCancelsThePicker() {
        assertTrue(shown(string(R.string.profile_delete_export)), "the export button must be on the screen")
        DeleteProfileScreenProbe.pressSave(world)
        // Отмена диалога не шлёт намерения вовсе: экран о выбранном месте не узнаёт.
    }

    @Тогда("^сохранён тот же файл, что даёт выгрузка на экране «Перенос данных»$")
    fun theSameFileAsTheTransferExportIsSaved() {
        val saved = DeleteProfileScreenProbe.exportedContent(world)
        val direct = world.transfer.export(handle = COMPARE_HANDLE)
        assertTrue(direct is ExportOutcome.Saved, "the direct export must succeed: $direct")
        assertEquals(
            world.transfer.files.documents[COMPARE_HANDLE],
            saved,
            "the deletion screen must reuse the transfer export (ОВ-7)",
        )
    }

    @Тогда("^на экране появился факт «(.+)»$")
    fun theExportFactIsShown(fact: String) {
        assertTrue(shown(fact), "the export fact must be on the screen: «$fact»")
    }

    @Тогда("^факта о сохранённом файле на экране нет$")
    fun thereIsNoExportFact() {
        DeleteProfileScreenProbe.ask(world) { onNodeWithTag(TAG_DELETE_EXPORT_FACT).assertDoesNotExist() }
    }

    @Тогда("^кнопка «([^»]+)» остаётся доступной$")
    fun theButtonStaysAvailable(label: String) {
        assertEquals(string(R.string.profile_delete_export), label, "the step must speak about the export button")
        DeleteProfileScreenProbe.ask(world) { onNodeWithTag(TAG_DELETE_EXPORT).assertIsEnabled() }
    }

    @Но("^профиль «([^»]+)» и его данные не изменились$")
    fun theProfileAndItsDataAreIntact(profileName: String) {
        assertEquals(profileName, profileAtOpen, "the scenario must open the deletion of «$profileName» first")
        assertEquals(
            lossesAtOpen,
            world.profileDeletion.lossesOf(profileName),
            "nothing may be erased before the confirmation (ОВ-7)",
        )
    }

    // --- Итог ---

    @Когда("^человек подтверждает удаление словом «(.+)»$")
    fun thePersonConfirmsWithTheWord(word: String) {
        DeleteProfileScreenProbe.typeWord(world, word)
        DeleteProfileScreenProbe.assertConfirmAvailability(world, enabled = true)
        DeleteProfileScreenProbe.onIntent(world, DeleteProfileIntent.Confirm)
    }

    @Тогда("^на экране есть кнопка «Готово»$")
    fun theDoneButtonIsOnScreen() {
        DeleteProfileScreenProbe.ask(world) { onNodeWithTag(TAG_DELETE_DONE).assertIsDisplayed() }
    }

    @Когда("^человек нажимает «Готово»$")
    fun thePersonPressesDone() {
        DeleteProfileScreenProbe.pressDone(world)
    }

    @Тогда("^экран возвращает в настройки$")
    fun theScreenReturnsToSettings() {
        assertEquals(1, DeleteProfileScreenProbe.doneCalls, "«Готово» must return to the settings")
    }

    @Тогда("^профиля «([^»]+)» в настройках больше нет$")
    fun theProfileIsGoneFromTheSettings(profileName: String) {
        // Список строк читается заново: после удаления мир изменился, а стенд настроек
        // до этого держал прежний снимок — как поток продукта, который переизлучает строки.
        SettingsScreenProbe.restart(world)
        var found = false
        SettingsScreenProbe.ask(world) {
            found = onAllNodesWithText(profileName, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(found, "«$profileName» must be gone from the settings")
    }

    // --- Обвязка --------------------------------------------------------------------------------

    private fun shown(text: String): Boolean =
        DeleteProfileScreenProbe.hasText(DeleteProfileScreenProbe.state(world), text)

    private fun settingsCarries(text: String): Boolean {
        var found = false
        SettingsScreenProbe.ask(world) {
            found = onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return found
    }

    private fun assertDeleteButtonLabel(label: String) {
        assertEquals(string(R.string.profile_delete_confirm), label, "the step must speak about the confirmation button")
    }

    private fun string(id: Int): String = context.getString(id)

    private companion object {
        const val HISTORY_HOUR = 8
        const val COMPARE_HANDLE = "deletion-compare-handle"
    }
}
