package ru.stohastika.sklyanka.acceptance.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.profile.SwitchReason
import ru.stohastika.sklyanka.feature.settings.ProfileRowState
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.SettingsIntent
import ru.stohastika.sklyanka.feature.settings.SettingsScreen
import ru.stohastika.sklyanka.feature.settings.SettingsUiState
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.ui.locale.InterfaceLocale
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Экранные шаги SKL-15: список профилей и переключение из настроек (группа `@switching`;
 * заведены SKL-26).
 *
 * Модуль выбран по правилу `TDD.md §4.3`: сценарий говорит и о настройках, и о «Сегодня»,
 * а оба экрана существуют одновременно только в `app`. Шаг про один экран жил бы
 * в модуле этого экрана, но здесь их два — и оба нужны одному сценарию.
 *
 * Состояние списка собирается из мира сценария по тем же правилам, что в
 * `SettingsViewModel`: свой профиль первым, число лекарств — из активных лекарств
 * профиля, дублирование — свойством чужого профиля, удаляемый профиль — только
 * активный чужой (ОВ-7, SKL-36). Свой профиль не убирается: его состояние удаляемого
 * профиля не знает — и шаг это утверждает, а не создаёт.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class ProfileSettingsSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val resources = context.withInterfaceLocale().resources

    private var state: SettingsUiState = SettingsUiState()
    private var texts: List<String> = emptyList()
    private var descriptions: List<String> = emptyList()
    private var actions: List<String> = emptyList()

    // --- Действия --------------------------------------------------------------

    @Когда("^человек открывает настройки (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonOpensSettings(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        openSettings()
    }

    @Когда("^человек открывает список профилей в настройках$")
    fun thePersonOpensTheProfileListInSettings() {
        openSettings()
    }

    @Когда("^он выбирает профиль «([^»]+)»$")
    fun heSelectsThatProfile(profile: String) {
        val intents = mutableListOf<SettingsIntent>()
        runComposeUiTest {
            setContent {
                val pinned = LocalContext.current.withInterfaceLocale()
                CompositionLocalProvider(
                    LocalContext provides pinned,
                    LocalConfiguration provides pinned.resources.configuration,
                ) {
                    SklyankaTheme { SettingsScreen(state = settingsState(), onIntent = intents::add) }
                }
            }
            onNodeWithText(profile).performClick()
            waitForIdle()
        }
        val selected = requireNotNull(intents.filterIsInstance<SettingsIntent.SelectProfile>().singleOrNull()) {
            "the row of «$profile» gave no profile selection: $intents"
        }
        // Переключение — то же действие продукта: настоящий сценарий домена, а не запись
        // активного профиля в обход правила (ПРФ-19).
        world.profileSession.switchTo(nameOf(selected.profileId), SwitchReason.USER_SETTINGS)
    }

    // --- Проверки: список профилей ---------------------------------------------

    @Тогда("^он видит список профилей, в котором «([^»]+)» показан первым с подписью «([^»]+)»$")
    fun heSeesThatProfileFirstWithThatCaption(profile: String, caption: String) {
        val first = state.profiles.firstOrNull()
        assertTrue("the list of profiles is empty", first != null)
        assertTrue("«$profile» must be the first row of the list", first!!.isSelf)
        val foreign = requireNotNull(state.profiles.getOrNull(1)) { "the list must show the foreign row too" }
        val foreignIndex = texts.indexOf(foreign.name)
        assertTrue("the foreign profile «${foreign.name}» is not on the screen: $texts", foreignIndex > 0)
        assertTrue(
            "«$profile» must be shown before «${foreign.name}»: $texts",
            texts.indexOf(profile) in 0 until foreignIndex,
        )
        assertTrue("no caption «$caption» on the screen: $texts", caption in texts)
        assertTrue(
            "the caption «$caption» must belong to the first row, before «${foreign.name}»: $texts",
            texts.indexOf(caption) in 0 until foreignIndex,
        )
    }

    @Тогда("^видит строку «([^»]+)»$")
    fun heSeesThatRow(rowText: String) {
        val row = requireNotNull(state.profiles.firstOrNull { !it.isSelf }) {
            "the scenario speaks about the row of a foreign profile, and the world has none"
        }
        assertEquals("the scenario and the product must name the row the same way", rowText, rowOf(row))
        assertTrue("no title «${row.name}» on the screen: $texts", row.name in texts)
        assertTrue("no subtitle «${subtitleOf(row)}» on the screen: $texts", subtitleOf(row) in texts)
    }

    // --- Проверки: переключение ------------------------------------------------

    @Тогда("^активным становится профиль «([^»]+)»$")
    fun thatProfileBecomesActive(profile: String) {
        assertEquals("the selected profile must become the active one", profile, world.profileSession.context().name)
    }

    @Тогда("^экран «Сегодня» показывает приёмы профиля «([^»]+)»$")
    fun todayShowsTheIntakesOfThatProfile(profile: String) {
        val owner = world.profiles.get(profile)
        assertEquals("«$profile» must be the profile the screen is in", owner.id, world.profileSession.context().id)
        val shown = shownMedicationIds(TodayFromWorld.state(world))
        val expected = world.intakes.ofProfile(world.profiles, profile)
            .filter { it.scheduledAt.toLocalDate() == world.clock.today() }
            .map { it.medicationId }
            .toSet()
        assertTrue("the scenario has no intakes of «$profile» on this day", expected.isNotEmpty())
        assertTrue("the screen does not show the intakes of «$profile»: $shown", shown.containsAll(expected))
        assertTrue(
            "the screen shows a medication of another profile: $shown",
            shown.all { id -> world.medications.byId(id)?.profileId == owner.id },
        )
    }

    // --- Проверки: свой профиль не убирается (ПРФ-28) --------------------------

    /**
     * Свой профиль не убирается ничем (ПРФ-28, ОВ-7).
     *
     * Проверка идёт по четырём местам, где действие могло бы жить: подписи на экране,
     * действия семантики, намерения, которые экран шлёт по нажатиям, и поля модели
     * профиля. Удаление чужого профиля в продукте есть (ОВ-7, SKL-36) — но у своего
     * профиля удаляемого профиля нет вовсе: строки удаления состояние не содержит, и ни
     * одно нажатие на экране не просит удаление.
     */
    @Тогда("^для профиля «([^»]+)» нет действия удаления или скрытия$")
    fun thereIsNoRemovalActionForThatProfile(profile: String) {
        assertEquals("the scenario must speak about the active profile", profile, world.profiles.active)
        assertTrue("the self row is not on the screen at all: $texts", selfCaption() in texts)
        val labels = texts + descriptions
        assertTrue(
            "a removal affordance is on the screen: ${labels.filter { it.hasRemovalWord() }}",
            labels.none { it.hasRemovalWord() },
        )
        assertTrue(
            "a removal action is registered on the screen: ${actions.filter { it.hasRemovalWord() }}",
            actions.none { it.hasRemovalWord() },
        )
        assertTrue(
            "the self-profile state offers a profile to delete: ${state.deletableProfile}",
            state.deletableProfile == null,
        )
        val emitted = clicksOnTheScreen().map { it::class.java.simpleName }
        assertTrue(
            "the self-profile screen can ask for a removal: ${emitted.filter { it.hasRemovalWord() }}",
            emitted.none { it.hasRemovalWord() },
        )
        val fields = Profile::class.java.declaredFields.map { it.name }
        assertTrue(
            "the profile model can hold a hidden or removed state: ${fields.filter { it.hasRemovalWord() }}",
            fields.none { it.hasRemovalWord() },
        )
    }

    // --- Обвязка ---------------------------------------------------------------

    private fun openSettings() {
        state = settingsState()
        runComposeUiTest {
            setContent {
                val pinned = LocalContext.current.withInterfaceLocale()
                CompositionLocalProvider(
                    LocalContext provides pinned,
                    LocalConfiguration provides pinned.resources.configuration,
                ) {
                    SklyankaTheme { SettingsScreen(state = state, onIntent = {}) }
                }
            }
            val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
            texts = textsOf(root)
            descriptions = descriptionsOf(root)
            actions = actionNamesOf(root)
        }
    }

    /**
     * Намерения, которые экран шлёт, если нажать всё нажимаемое.
     *
     * Строка удаления — такая же нажимаемая строка, как прочие: проверка «её нет»
     * остаётся проверкой только тогда, когда перебраны все нажатия, а не когда
     * спрошен список намерений в отрыве от экрана.
     */
    private fun clicksOnTheScreen(): List<SettingsIntent> {
        val intents = mutableListOf<SettingsIntent>()
        runComposeUiTest {
            setContent {
                val pinned = LocalContext.current.withInterfaceLocale()
                CompositionLocalProvider(
                    LocalContext provides pinned,
                    LocalConfiguration provides pinned.resources.configuration,
                ) {
                    SklyankaTheme { SettingsScreen(state = state, onIntent = intents::add) }
                }
            }
            val nodes = onAllNodes(hasClickAction(), useUnmergedTree = true)
            repeat(nodes.fetchSemanticsNodes().size) { index -> nodes[index].performClick() }
            waitForIdle()
        }
        return intents
    }

    /**
     * Список профилей из мира сценария — по правилам `SettingsViewModel`.
     *
     * Число лекарств берётся у профиля, а не у строки сценария: сценарий называет только
     * те лекарства, о которых говорит, и недостающие до названного Предысторией числа
     * заводит [ru.stohastika.sklyanka.acceptance.world.ScenarioMedications.ensureCount].
     */
    private fun settingsState(): SettingsUiState {
        val ordered = world.profiles.all()
            .sortedWith(compareByDescending<Profile> { it.isSelf }.thenBy { it.name })
        val rows = ordered.map { profile ->
            world.profiles.declaredMedicationCount(profile.name)?.let { declared ->
                world.medications.ensureCount(world.profiles, profile.name, declared)
            }
            ProfileRowState(
                id = profile.id,
                name = profile.name,
                colorArgb = profile.color,
                isSelf = profile.isSelf,
                isActive = profile.name == world.profiles.active,
                medicationCount = world.medications.all().count { it.profileId == profile.id && !it.isArchived },
                // У владельца признак дублирования не показывается (ПРФ-16) — как в SettingsViewModel.
                mirrorReminders = !profile.isSelf && profile.mirrorReminders,
            )
        }
        return SettingsUiState(
            profiles = rows,
            // Строка удаления — только у активного чужого профиля (ОВ-7, SKL-36):
            // то же правило, что в SettingsViewModel. Свой профиль её не получает (ПРФ-28).
            deletableProfile = rows.firstOrNull { it.isActive && !it.isSelf },
            version = VERSION,
        )
    }

    private fun subtitleOf(row: ProfileRowState): String {
        val medications = resources.getQuantityString(
            R.plurals.settings_profile_medications,
            row.medicationCount,
            row.medicationCount,
        )
        val mirrored = resources.getString(R.string.settings_profile_mirrored)
        return resources.getString(R.string.settings_profile_subtitle, medications, mirrored)
    }

    /** Строка чужого профиля целиком: «М · Мама · 4 лекарства · напоминания дублируются мне». */
    private fun rowOf(row: ProfileRowState): String {
        val initial = row.name.trim().take(1).uppercase(InterfaceLocale.VALUE)
        return listOf(initial, row.name, subtitleOf(row)).joinToString(" · ")
    }

    private fun selfCaption(): String = resources.getString(R.string.settings_profile_self)

    private fun shownMedicationIds(state: TodayUiState): Set<String> = when (state) {
        is TodayUiState.Content ->
            state.layout.groups.flatMap { group -> group.items.map { it.medication.id } }.toSet()

        else -> emptySet()
    }

    private fun nameOf(profileId: String): String =
        requireNotNull(world.profiles.byId(profileId)?.name) { "unknown profile: $profileId" }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    private fun descriptionsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.children.flatMap { descriptionsOf(it) }

    private fun actionNamesOf(node: SemanticsNode): List<String> =
        node.config.toList().map { it.key.name } + node.children.flatMap { actionNamesOf(it) }

    private fun String.hasRemovalWord(): Boolean = REMOVAL_WORDS.any { word -> contains(word, ignoreCase = true) }

    private companion object {
        const val VERSION = "0.1.0"

        /** Слова, которыми было бы названо удаление или скрытие — на любом языке продукта. */
        val REMOVAL_WORDS = listOf("удал", "скры", "delete", "remove", "hide")
    }
}
