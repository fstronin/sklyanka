package ru.stohastika.sklyanka.acceptance.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.history.HistoryIntent
import ru.stohastika.sklyanka.feature.meds.archive.ArchiveIntent
import ru.stohastika.sklyanka.feature.meds.card.MedCardIntent
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.list.MedsIntent
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.SettingsIntent
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.feature.today.TodayUiState
import java.io.File

/**
 * Экранные шаги композиции настроек (SKL-20, группа `@composition`).
 *
 * Переключение профиля идёт настоящим маршрутом продукта: `SettingsRoute` → `SelectProfile`
 * → `SwitchProfileUseCase`, после чего «Сегодня» собирается тем же путём, что на стенде
 * SKL-08. Раздел «Данные» проверяется на договоре намерений: строки ведут туда, куда
 * обещают, а вход в перенос данных в приложении ровно один.
 *
 * Строки ищутся по видимым подписям и описаниям для чтения с экрана: метки стенда —
 * внутренние для модуля настроек, и приёмка не подглядывает в них снаружи.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class SettingsCompositionSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Переключение профиля ------------------------------------------------------------------

    @Когда("^человек нажимает строку «Мама»$")
    fun thePersonTapsTheRowOfMother() {
        SettingsAppProbe.actSettings(world) {
            onNodeWithText(MOTHER, useUnmergedTree = true).performScrollTo().performClick()
        }
    }

    @Тогда("^отметка активного профиля стоит у строки «Мама»$")
    fun theActiveMarkStandsOnTheMotherRow() {
        SettingsAppProbe.askSettings(world) {
            val name = onNodeWithText(MOTHER, useUnmergedTree = true).fetchSemanticsNode()
            val mark = onNodeWithContentDescription(activeLabel(), useUnmergedTree = true).fetchSemanticsNode()
            // Строка профиля — нажимаемая область, содержащая имя: проверяется, что отметка
            // внутри той же строки, а не где-то ещё на экране.
            val row = onAllNodes(hasClickAction()).fetchSemanticsNodes()
                .first { it.boundsInRoot.contains(name.boundsInRoot.center) }
            assertTrue(
                row.boundsInRoot.contains(mark.boundsInRoot.center),
                "the active mark must stand on the row of «$MOTHER»",
            )
        }
    }

    @Но("^приёмы профиля «Я» на экране не появляются$")
    fun theOwnProfileIntakesDoNotAppear() {
        assertTrue(
            medicationsOf(OWN).isNotEmpty(),
            "«$OWN» must have a course of its own for the check to mean anything",
        )
        SettingsAppProbe.askToday(world) { _, state ->
            val shown = shownMedicationIds(state)
            assertEquals(
                emptySet<String>(),
                shown intersect medicationsOf(OWN),
                "the day of «$MOTHER» must not show the intakes of «$OWN»: $shown",
            )
        }
    }

    // --- Данные --------------------------------------------------------------------------------

    @Когда("^человек смотрит на блок «Данные»$")
    fun thePersonLooksAtTheDataBlock() {
        val texts = shownTexts()
        assertTrue(texts.contains(string(R.string.settings_history)), "the data block must carry the history row")
        assertTrue(texts.contains(string(R.string.transfer_title)), "the data block must carry the transfer row")
    }

    @Тогда("^в нём есть строка «История приёмов»$")
    fun theHistoryRowIsThere() {
        SettingsAppProbe.askSettings(world) {
            onNodeWithText(string(R.string.settings_history), useUnmergedTree = true).performScrollTo()
        }
    }

    @Тогда("^строка «Перенос данных» с подписью «(.+?)»$")
    fun theTransferRowCarriesTheCaption(caption: String) {
        assertEquals(caption, string(R.string.settings_transfer_caption))
        SettingsAppProbe.askSettings(world) {
            onNodeWithText(string(R.string.transfer_title), useUnmergedTree = true).performScrollTo()
            onNodeWithText(caption, useUnmergedTree = true).performScrollTo()
        }
    }

    /**
     * Другого способа добраться до переноса данных в приложении нет.
     *
     * Проверяются обе стороны: строка «Данные» — единственное намерение, ведущее туда
     * (среди договоров навигации всех экранов), а `MainActivity` ставит место перехода
     * ровно один раз — по этому намерению.
     */
    @Тогда("^других способов добраться до переноса данных в приложении нет$")
    fun thereIsNoOtherWayToTheTransfer() {
        SettingsAppProbe.actSettings(world) {
            onNodeWithText(string(R.string.transfer_title), useUnmergedTree = true).performScrollTo().performClick()
        }
        assertEquals(1, SettingsAppProbe.navigation.transfer, "the settings row must lead to the transfer")

        val transferIntents = INTENT_CONTRACTS.flatMap { contract ->
            contract.declaredClasses.map { nested -> "${contract.simpleName}.${nested.simpleName}" }
        }.filter { it.contains("Transfer") || it.contains("Data") }
        assertEquals(
            listOf("SettingsIntent.OpenDataTransfer"),
            transferIntents,
            "the transfer must have exactly one entry in the whole product",
        )

        val main = File(RepositoryLayout.root, MAIN_ACTIVITY).readText()
        assertEquals(
            1,
            Regex("SettingsDestination\\.TRANSFER").findAll(main).count(),
            "MainActivity must set the transfer destination from one place only",
        )
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun shownTexts(): List<String> {
        var texts: List<String> = emptyList()
        SettingsAppProbe.askSettings(world) {
            texts = textsOf(onRoot(useUnmergedTree = true).fetchSemanticsNode())
        }
        return texts
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    private fun medicationsOf(profileName: String): Set<String> {
        val profileId = world.profiles.get(profileName).id
        return world.medications.all().filter { it.profileId == profileId }.map { it.id }.toSet()
    }

    private fun shownMedicationIds(state: TodayUiState): Set<String> = when (state) {
        is TodayUiState.Content -> state.layout.groups.flatMap { group -> group.items.map { it.medication.id } }.toSet()
        else -> emptySet()
    }

    private fun activeLabel(): String = string(R.string.cd_settings_profile_active)

    private fun string(id: Int): String = context.getString(id)

    private companion object {
        const val OWN = "Я"
        const val MOTHER = "Мама"
        const val MAIN_ACTIVITY = "app/src/main/kotlin/ru/stohastika/sklyanka/MainActivity.kt"

        /**
         * Договоры навигации всех экранов: в них не должно быть второй дороги к переносу
         * данных. Сам экран переноса (`TransferIntent`) сюда не входит — это его
         * собственные намерения, а не вход в него.
         */
        val INTENT_CONTRACTS = listOf(
            TodayIntent::class.java,
            MedsIntent::class.java,
            MedCardIntent::class.java,
            MedFormIntent::class.java,
            ArchiveIntent::class.java,
            HistoryIntent::class.java,
            SettingsIntent::class.java,
        )
    }
}
