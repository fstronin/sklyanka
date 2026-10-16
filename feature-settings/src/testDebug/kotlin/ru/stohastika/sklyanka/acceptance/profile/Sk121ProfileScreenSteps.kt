package ru.stohastika.sklyanka.acceptance.profile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.profile.ProfileCreationGate
import ru.stohastika.sklyanka.feature.settings.ProfileRowState
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.SettingsScreen
import ru.stohastika.sklyanka.feature.settings.SettingsUiState
import ru.stohastika.sklyanka.feature.settings.TAG_ADD_PROFILE_ROW
import ru.stohastika.sklyanka.feature.settings.profile.ProfileFormMode
import ru.stohastika.sklyanka.feature.settings.profile.ProfileFormScreen
import ru.stohastika.sklyanka.feature.settings.profile.ProfileFormUiState
import ru.stohastika.sklyanka.feature.settings.profile.TAG_PROFILE_BLOCK
import ru.stohastika.sklyanka.feature.settings.profile.TAG_PROFILE_COLOURS
import ru.stohastika.sklyanka.feature.settings.profile.TAG_PROFILE_MIRROR
import ru.stohastika.sklyanka.feature.settings.profile.TAG_PROFILE_NAME_FIELD
import ru.stohastika.sklyanka.feature.settings.profile.TAG_PROFILE_SUBMIT
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import java.util.Locale
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки SKL-121 — мастер добавления профиля и строка «Добавить профиль» (§5.13).
 *
 * Шаги живут в `feature-settings`, потому что говорят об экранах этого модуля: теги формы
 * (`TAG_PROFILE_*`) и тег строки добавления (`TAG_ADD_PROFILE_ROW`) объявлены здесь, и увидеть
 * их может только прогон самого модуля (`SKL-26/TDD.md §4.3`).
 *
 * Состояние берётся из мира сценария, а не пересказывается: число профилей — `world.profiles.all()`,
 * доступ — `world.pro.status()`, а исход гейта считает тот же [ProfileCreationGate], что у продукта
 * (ПРФ-33, ПРФ-34). Стенд только собирает ту же форму и ту же строку, что продукт.
 *
 * Выражение кончается на «на стенде»: словарь шагов общий на прогон, и второе объявление
 * выражения сценарного шага из общего артефакта было бы дублем (`SKL-26/TDD.md §4.4`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class Sk121ProfileScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Имя, набранное в мастере: шаг «введено имя» кладёт его, а следующие собирают форму с ним. */
    private var name: String = ""

    /** Открытие мастера обнуляет набранное: форма начинается заново при каждом открытии. */
    @Когда("^на стенде открыт мастер добавления профиля$")
    fun theWizardIsOpenOnTheStand() {
        name = ""
    }

    /** Имя мастера словами сценария: у стенда нет поля ввода, его роль играет это состояние. */
    @И("^на стенде введено имя профиля$")
    fun theNameIsEnteredOnTheStand() {
        name = PROFILE_NAME
    }

    /**
     * Мастер собран из компонентов: поле имени, ряд цвета и вопрос о дублировании (§5.13).
     *
     * Ряд цвета держит ровно пять образцов закрытого набора (§2.2): образец узнаётся по имени
     * цвета в языке интерфейса, а не по порядку — так же, как его находит человек чтением с экрана.
     */
    @Тогда("^на стенде в мастере есть поле имени, выбор цвета и вопрос о дублировании$")
    fun theWizardCarriesItsBlocksOnTheStand() {
        composeForm {
            onNodeWithTag(TAG_PROFILE_NAME_FIELD, useUnmergedTree = true).assertExists()
            val colours = onNodeWithTag(TAG_PROFILE_COLOURS, useUnmergedTree = true).fetchSemanticsNode()
            val samples = colours.tagsDeep().count { it.startsWith(COLOUR_TAG_PREFIX) }
            assertEquals("ряд цвета — пять образцов закрытого набора (§2.2)", 5, samples)
            onNodeWithTag(TAG_PROFILE_MIRROR, useUnmergedTree = true).assertExists()
        }
    }

    /** Завершение мастера названо словом каталога: подпись сверяется с ресурсом, а не с литералом. */
    @Тогда("^на стенде завершение названо словами$")
    fun theWizardFinishIsNamedInWordsOnTheStand() {
        composeForm {
            val texts = textsOfNode(TAG_PROFILE_SUBMIT)
            assertTrue(
                "завершение названо «${string(R.string.profile_add_action)}»: $texts",
                texts.contains(string(R.string.profile_add_action)),
            )
        }
    }

    /**
     * Причина платной границы названа словами над кнопкой, а кнопка не приглушена (П-7, §5.10).
     *
     * Платная причина не переводит кнопку в недоступность: недоступное действие говорит «нельзя»,
     * а правда — «можно, по подписке», и нажатие ведёт в витрину.
     */
    @Тогда("^на стенде над кнопкой названа причина платной границы$")
    fun thePaidBoundaryReasonIsNamedOnTheStand() {
        composeForm {
            assertTrue(
                "над кнопкой названа причина платной границы",
                textsOfNode(TAG_PROFILE_BLOCK).contains(string(R.string.profile_add_blocked_pro)),
            )
        }
    }

    /**
     * Кнопка завершения на платной границе нажимается: причина названа, а действие не отнято.
     *
     * Спрашивается именно **приглушение**, а не действие в семантике: `clickable(enabled = false)`
     * оставляет узел с `OnClick` и добавляет `Disabled`, и «нет действия нажатия» недоступную
     * кнопку не отличает — отличает её признак `Disabled` (`assertIsNotEnabled`).
     */
    @Тогда("^на стенде кнопка завершения не приглушена$")
    fun theWizardFinishIsNotDimmedOnTheStand() {
        composeForm {
            onNodeWithTag(TAG_PROFILE_SUBMIT).assertIsEnabled()
        }
    }

    /** Предел пяти закрывает завершение: кнопка приглушена (`ButtonState.Disabled`) и зовёт слова. */
    @Тогда("^на стенде завершение недоступно$")
    fun theWizardFinishIsUnavailableOnTheStand() {
        composeForm {
            onNodeWithTag(TAG_PROFILE_SUBMIT).assertIsNotEnabled()
        }
    }

    /** Причина предела названа словами над кнопкой до нажатия (ПРФ-34). */
    @Тогда("^на стенде причина названа словами над кнопкой$")
    fun theReasonIsNamedAboveTheButtonOnTheStand() {
        composeForm {
            assertTrue(
                "причина названа словами над кнопкой",
                textsOfNode(TAG_PROFILE_BLOCK).contains(string(R.string.profile_add_blocked_limit)),
            )
        }
    }

    /**
     * Строка «Добавить профиль» несёт метку «Про»: следующее создание платное (Р-84 п. 3).
     *
     * Метка — слово каталога `core-ui`, и оно читается из текстов узла строки рекурсией,
     * не слитым деревом: строка нажимается, и слитая она спрятала бы слова внутри себя.
     */
    @Тогда("^на стенде строка «Добавить профиль» несёт метку «Про»$")
    fun theAddRowCarriesTheProMarkOnTheStand() {
        composeSettings {
            val texts = textsOfNode(TAG_ADD_PROFILE_ROW)
            assertTrue(
                "строка добавления названа «${string(R.string.profile_add_action)}»: $texts",
                texts.contains(string(R.string.profile_add_action)),
            )
            assertTrue(
                "строка добавления несёт метку «${string(UiR.string.pro_mark)}»: $texts",
                texts.contains(string(UiR.string.pro_mark)),
            )
        }
    }

    /** Без платного создания метки нет, а подпись строки остаётся: строка на месте, но не продаёт. */
    @Тогда("^на стенде строка «Добавить профиль» не несёт метки «Про»$")
    fun theAddRowCarriesNoProMarkOnTheStand() {
        composeSettings {
            val texts = textsOfNode(TAG_ADD_PROFILE_ROW)
            assertTrue(
                "строка добавления названа «${string(R.string.profile_add_action)}»: $texts",
                texts.contains(string(R.string.profile_add_action)),
            )
            assertTrue("без платного создания метки нет: $texts", !texts.contains(string(UiR.string.pro_mark)))
        }
    }

    // --- Стенды ---------------------------------------------------------------------------------

    /** Собрать мастер из состояния мира и задать вопрос его композиции. */
    private fun composeForm(question: ComposeUiTest.() -> Unit) = compose(question) {
        ProfileFormScreen(state = formState(), onIntent = {})
    }

    /** Собрать экран настроек из состояния мира и задать вопрос его композиции. */
    private fun composeSettings(question: ComposeUiTest.() -> Unit) = compose(question) {
        SettingsScreen(state = settingsState(), onIntent = {})
    }

    /**
     * Мастер из мира: имя шага, первый свободный цвет набора и исход гейта (ПРФ-33, ПРФ-34).
     *
     * Дублирование включено — норма при создании (Р-29); владельцем форма не открывается.
     */
    private fun formState(): ProfileFormUiState = ProfileFormUiState(
        mode = ProfileFormMode.Add,
        name = name,
        colour = freeColour(),
        mirrorReminders = true,
        isSelf = false,
        creation = ProfileCreationGate.decide(world.profiles.all().size, world.pro.status().included),
    )

    /** Экран настроек из мира: строки профилей и исход гейта, тем же правилом, что у ViewModel. */
    private fun settingsState(): SettingsUiState {
        val profiles = world.profiles.all()
        val rows = profiles.map { profile ->
            ProfileRowState(
                id = profile.id,
                name = profile.name,
                colorArgb = profile.color,
                isSelf = profile.isSelf,
                isActive = profile.name == world.profiles.active,
                medicationCount = 0,
                mirrorReminders = profile.mirrorReminders,
            )
        }
        return SettingsUiState(
            profiles = rows,
            pro = world.pro.status(),
            profileCreation = ProfileCreationGate.decide(profiles.size, world.pro.status().included),
            version = VERSION,
        )
    }

    /** Первый свободный цвет набора; набор занят целиком — первый цвет (ПЕР-19). */
    private fun freeColour(): Int {
        val used = world.profiles.all().map { it.color }.toSet()
        val free = ProfilePalette.all.firstOrNull { it.argb !in used }
        return (free ?: ProfilePalette.all.first()).argb
    }

    private fun compose(question: ComposeUiTest.() -> Unit, content: @Composable () -> Unit) {
        runComposeUiTest {
            setContent { Stand { content() } }
            waitForIdle()
            question(this)
        }
    }

    private fun string(id: Int): String = context.withInterfaceLocale(RUSSIAN).getString(id)

    /** Тексты узла рекурсией: вопрос задаётся собранной композиции, а не снимку состояния. */
    private fun ComposeUiTest.textsOfNode(tag: String): List<String> =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { textsOf(it) }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    /** Теги узлов рекурсией: ряд цвета не прячет образцы за слитым деревом (§5.13). */
    private fun SemanticsNode.tagsDeep(): List<String> =
        listOfNotNull(config.getOrNull(SemanticsProperties.TestTag)) + children.flatMap { it.tagsDeep() }

    /**
     * Стенд: язык интерфейса сценария.
     *
     * Язык закреплён, потому что сценарии записаны по-русски, а телефон стенда мог быть любым
     * (НФТ-43): и слова строк, и метка берутся из того же каталога, что у экрана.
     */
    @Composable
    private fun Stand(content: @Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale(RUSSIAN)
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
        ) {
            SklyankaTheme { content() }
        }
    }

    private companion object {

        /** Имя мастера словами сценария (канон SKL-121): у стенда нет поля ввода, его играет шаг. */
        const val PROFILE_NAME = "Бабушка"

        const val VERSION = "1.0"

        /** Начало тега образца цвета: `profile_colour_<имя цвета>` (`ProfileFormScreen`). */
        const val COLOUR_TAG_PREFIX = "profile_colour_"

        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    }
}
