package ru.heuri.sklyanka.acceptance.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import ru.heuri.sklyanka.acceptance.steps.dateOf
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.ui.component.TAG_PROFILE_LABEL
import ru.heuri.sklyanka.ui.component.TAG_PROFILE_STRIPE
import ru.heuri.sklyanka.ui.profile.ProfileChrome
import ru.heuri.sklyanka.ui.profile.ProfileChromeState
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.ProfileColor
import ru.heuri.sklyanka.ui.token.ProfilePalette
import java.time.LocalTime
import java.util.Locale

/**
 * Экранные шаги приёмки профилей (SKL-15, группа `@screen`; заведены SKL-26).
 *
 * Шаги живут в `app`, а не в модуле экрана, потому что `Структура сценария` «Признаки
 * чужого профиля есть на всех экранах профиля» обходит шесть экранов трёх разных модулей,
 * а один сценарий не может исполняться в трёх модулях (`SKL-26/TDD.md §4.3`).
 *
 * Состояние сценария — общий мир приёмки: профили и лекарства заводит Предыстория
 * доменными словами, и второй копии этих шагов здесь нет (`SKL-26/TDD.md §4.5`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса. Пакет
 * `ru.heuri.sklyanka.acceptance` разрешён правилом `RussianTextInCode` именно поэтому,
 * и SKL-26 область правила не расширяет.
 *
 * Проверка идёт по собранной композиции: каждое утверждение открывает сессию стенда,
 * собирает тот же экран в том же контексте профиля и спрашивает у него. Пересборка на
 * утверждение дешевле, чем одна сессия на сценарий: сессия стенда живёт внутри блока,
 * а шаги Gherkin — отдельные вызовы, и держать блок открытым между ними нечем.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class ProfileIndicationSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Экран, который человек открыл. По умолчанию — «Сегодня». */
    private var opened: ProfileScreen = ProfileScreen.Today

    // --- Действия --------------------------------------------------------------

    // Переключение профиля объявлено в общем артефакте (`ProfileSwitchingSteps`): та же
    // строка стоит в доменном сценарии разделения профилей, и два объявления одного
    // выражения встретились бы в этом прогоне и погасили бы его целиком.

    @Когда(
        "^человек открывает (экран «Сегодня»|список лекарств|карточку лекарства|архив|историю приёмов|форму нового лекарства)$",
    )
    fun thePersonOpensThatScreen(screen: String) {
        opened = ProfileScreen.of(screen)
    }

    @Когда("^человек открывает экран «Сегодня» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonOpensTodayAt(date: String, time: String) {
        world.clock.moveTo(world.dateOf(date).atTime(LocalTime.parse(time)))
        opened = ProfileScreen.Today
    }

    @Когда("^человек прокручивает экран «Сегодня» до конца и обратно$")
    fun thePersonScrollsTheScreen() {
        opened = ProfileScreen.Today
        onScreen { test ->
            // Прокрутка внутри одной сессии: между шагами сессия не живёт, поэтому
            // и действие, и проверка «баннер остался виден» делаются здесь же.
            test.onNode(hasScrollAction(), useUnmergedTree = true).performTouchInput { swipeUp() }
            test.waitForIdle()
            test.onNodeWithText(banner()).assertIsDisplayed()
            test.onNode(hasScrollAction(), useUnmergedTree = true).performTouchInput { swipeDown() }
            test.waitForIdle()
            test.onNodeWithText(banner()).assertIsDisplayed()
        }
    }

    @Когда("^человек смотрит на экран «Сегодня» в монохромном режиме$")
    fun thePersonLooksAtTheScreenWithoutColour() {
        opened = ProfileScreen.Today
    }

    // --- Признаки чужого профиля ----------------------------------------------

    @Тогда("^вверху экрана видна цветная полоса цвета профиля$")
    fun theStripeOfTheProfileColourIsAtTheTop() = assertStripe()

    @Тогда("^вверху видна цветная полоса цвета профиля$")
    fun theStripeIsAtTheTop() = assertStripe()

    @Тогда("^шапка экрана подкрашена$")
    fun theScreenHeaderIsTinted() = assertHeaderTinted()

    @Тогда("^шапка подкрашена$")
    fun theHeaderIsTinted() = assertHeaderTinted()

    @Тогда("^над заголовком «(.+?)» стоит надпись «(.+?)»$")
    fun theLabelStandsAboveTheTitle(title: String, label: String) {
        // Заголовок экрана и подпись вкладки нижней панели совпадают дословно: проверяется,
        // что заголовок есть, а не что он единственный на экране.
        onScreen { test ->
            assertTrue(
                "the title «$title» is not on the screen",
                test.onAllNodesWithText(title, ignoreCase = true).fetchSemanticsNodes().isNotEmpty(),
            )
        }
        assertLabel(label)
    }

    @Тогда("^видна надпись «(.+?)»$")
    fun theLabelIsVisible(label: String) = assertLabel(label)

    @Тогда("^виден баннер «(.+?)»$")
    fun theBannerIsVisible(text: String) = assertBanner(text)

    @Тогда("^баннер «(.+?)» остаётся виден$")
    fun theBannerStaysVisible(text: String) = assertBanner(text)

    @Тогда("^у баннера нет способа его закрыть$")
    fun theBannerHasNoWayToDismissIt() {
        // Проверяется отсутствие действия у самого баннера: кликабельный баннер — это
        // способ его убрать, а убираемая индикация не индикация (ПРФ-13).
        onScreen { test ->
            val nodes = test.onAllNodesWithText(banner(), useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals("the banner must be shown exactly once", 1, nodes.size)
            val actions = nodes.first().config.toList().map { it.key.name }
            assertTrue(
                "the banner must carry no action that dismisses it: $actions",
                actions.none { it.contains("OnClick") || it.contains("Dismiss") },
            )
        }
    }

    @Тогда("^кнопка «(.+?)» окрашена в цвет профиля$")
    fun theButtonCarriesTheProfileColour(button: String) {
        assertActionLabel(button)
        assertEquals(
            "the accent of a foreign profile must be its own colour (ПРФ-11)",
            FOREIGN_COLOUR.color,
            accentColour(),
        )
    }

    // --- Отсутствие признаков в своём профиле ---------------------------------

    @Тогда("^цветной полосы вверху экрана нет$")
    fun thereIsNoStripe() {
        onScreen { test -> test.onAllNodesWithTag(TAG_PROFILE_STRIPE).assertCountEquals(0) }
    }

    @Тогда("^шапка не подкрашена$")
    fun theHeaderIsNotTinted() {
        assertEquals(
            "the own profile must not tint the header (ПРФ-16)",
            headerColour(ProfileChromeState.Owner),
            headerColour(chrome()),
        )
    }

    @Тогда("^надписи «(.+?)» нет$")
    fun thereIsNoLabel(label: String) {
        onScreen { test ->
            test.onAllNodesWithTag(TAG_PROFILE_LABEL, useUnmergedTree = true).assertCountEquals(0)
            test.onAllNodesWithText(label, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Тогда("^баннера нет$")
    fun thereIsNoBanner() {
        onScreen { test ->
            test.onAllNodesWithText(banner(), useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Тогда("^кнопка «(.+?)» окрашена основным акцентом приложения$")
    fun theButtonCarriesTheApplicationAccent(button: String) {
        assertActionLabel(button)
        assertEquals(
            "the own profile keeps the application accent (ПРФ-16)",
            ProfilePalette.ownerAccent,
            accentColour(),
        )
    }

    @Тогда("^цветной полосы, подкрашенной шапки, надписи «(.+?)» и баннера нет$")
    fun noSignOfAForeignProfileIsLeft(label: String) {
        thereIsNoStripe()
        theHeaderIsNotTinted()
        thereIsNoLabel(label)
        thereIsNoBanner()
    }

    @Тогда("^акцентный цвет действий возвращается к основному акценту приложения$")
    fun theAccentReturnsToTheApplicationAccent() {
        assertEquals(
            "returning to the own profile must return the accent too (ПРФ-16)",
            ProfilePalette.ownerAccent,
            accentColour(),
        )
    }

    // --- Различимость без цвета (НФТ-33) --------------------------------------

    @Тогда("^надпись «(.+?)» читается$")
    fun theLabelIsLegible(label: String) {
        // Монохромный режим меняет цвет, а не текст: проверяется, что признак назван
        // словами и потому переживает потерю цвета (НФТ-33).
        assertLabel(label)
    }

    @Тогда("^баннер «(.+?)» читается$")
    fun theBannerIsLegible(text: String) = assertBanner(text)

    // --- Обвязка ---------------------------------------------------------------

    /**
     * Действие, о котором говорит сценарий, — то самое действие продукта.
     *
     * Цвет кнопки проверяется ролью темы, а не пикселем на экране, и это не обход:
     * кнопка читает `SklyankaTheme.colors.accent`, а чем окажется акцент, решает тема
     * (`ProfileChrome`). Сама кнопка при этом есть не на всяком кадре — у приёма, время
     * которого ещё не наступило, вместо неё круглая кнопка-галочка (SKL-08), а момент
     * сценария назначает Предыстория. Проверять «кнопка видна» здесь означало бы
     * проверять состав экрана вместо цвета действия — этим занята приёмка SKL-08.
     */
    private fun assertActionLabel(button: String) {
        assertEquals(
            "the scenario and the product must name the same action",
            context.getString(ru.heuri.sklyanka.ui.R.string.action_taken),
            button,
        )
    }

    private fun assertStripe() {
        onScreen { test -> test.onNodeWithTag(TAG_PROFILE_STRIPE).assertIsDisplayed() }
    }

    private fun assertHeaderTinted() {
        assertNotEquals(
            "a foreign profile must tint the header (ПРФ-11)",
            headerColour(ProfileChromeState.Owner),
            headerColour(chrome()),
        )
    }

    /**
     * Надпись «Профиль · Имя».
     *
     * Сравнение без учёта регистра: дизайн-система набирает надпись капителью
     * (`ScreenHeader`), и это типографика, а не другой текст.
     */
    private fun assertLabel(label: String) {
        onScreen { test ->
            val nodes = test.onAllNodesWithTag(TAG_PROFILE_LABEL, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals("the profile label must be shown exactly once on ${opened.name}", 1, nodes.size)
            val shown = nodes.first().config.getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .joinToString(" ") { it.text }
            assertEquals(
                "the label of the profile must be spelled out",
                label.lowercase(Locale.ROOT),
                shown.lowercase(Locale.ROOT),
            )
        }
    }

    private fun assertBanner(text: String) {
        assertEquals("the scenario and the product must name the same banner", banner(), text)
        onScreen { test -> test.onNodeWithText(text).assertIsDisplayed() }
    }

    /** Текст несворачиваемого баннера чужого профиля — из ресурсов, а не из шага. */
    private fun banner(): String = context.getString(
        ru.heuri.sklyanka.ui.R.string.profile_banner,
        world.profiles.active,
    )

    /** Оформление по активному профилю сценария — тем же правилом, что в корне приложения. */
    private fun chrome(): ProfileChromeState {
        val active = world.profiles.get(world.profiles.active)
        return if (active.isSelf) {
            ProfileChromeState.Owner
        } else {
            // Палитра профилей закрыта (ПРФ-15), а сценарий говорит лишь «с цветом
            // профиля»: конкретный цвет здесь не проверяется и взят первым из палитры.
            ProfileChromeState.Foreign(name = active.name, colour = FOREIGN_COLOUR)
        }
    }

    /** Открыть экран сценария в контексте активного профиля и спросить у композиции. */
    private fun onScreen(assertion: (ComposeUiTest) -> Unit) {
        val state = chrome()
        val today = TodayFromWorld.state(world)
        runComposeUiTest {
            setContent { ProfileChrome(state) { opened.Content(today) } }
            assertion(this)
        }
    }

    private fun headerColour(state: ProfileChromeState): Color = probe(state) { SklyankaTheme.colors.profileHeader }

    private fun accentColour(): Color = probe(chrome()) { SklyankaTheme.colors.accent }

    /** Значение роли цвета в этом контексте профиля: роль читается из темы, а не считается. */
    private fun probe(state: ProfileChromeState, role: @Composable () -> Color): Color {
        var captured: Color? = null
        runComposeUiTest {
            setContent { ProfileChrome(state) { captured = role() } }
        }
        return requireNotNull(captured) { "the theme did not provide the colour role" }
    }

    private companion object {
        val FOREIGN_COLOUR = ProfileColor.Terracotta
    }
}
