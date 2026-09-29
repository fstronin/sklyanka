package ru.stohastika.sklyanka.acceptance.today

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.today.R
import ru.stohastika.sklyanka.feature.today.TAG_DAY_PROGRESS
import ru.stohastika.sklyanka.feature.today.TAG_PRN_CARD
import ru.stohastika.sklyanka.feature.today.TAG_PRN_DIVIDER
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_EMPTY
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.ui.component.TAG_EMPTY_STATE
import ru.stohastika.sklyanka.ui.component.TAG_SCREEN_HEADER

/**
 * Экранные шаги приёмки пустого состояния «Сегодня» (SKL-08, группа `@empty`;
 * заведены SKL-26).
 *
 * Состояние собирает настоящий домен из мира сценария ([TodayStand]): первый запуск —
 * это отсутствие активных лекарств у профиля, а не пустой список приёмов (ОВ-34).
 * Нажатие идёт по собранной композиции, а намерение экрана разбирает маршрут `app`:
 * здесь проверяется контракт экрана.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TodayEmptyScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private var intents: List<TodayIntent> = emptyList()

    // --- Предыстория -----------------------------------------------------------

    @Допустим("^показано пустое состояние «([^»]+)»$")
    fun theEmptyStateIsShown(title: String) {
        // Предыстория доводит мир до состояния первого запуска: у профиля нет активных
        // лекарств, и потому домен собирает `Empty`, а не `Content` (ОВ-34). Лекарства
        // убираются из мира — у архива остался бы список, и состояние было бы другим.
        val owner = world.profiles.get(world.profiles.active).id
        world.medications.all().filter { it.profileId == owner }.forEach { world.medications.remove(nameOf(it.id)) }
        TodayScreenProbe.ask(world) { test -> test.onNodeWithText(title).assertIsDisplayed() }
    }

    // --- Действие --------------------------------------------------------------

    @Когда("^человек нажимает «Добавить лекарство»$")
    fun thePersonPressesAddMedication() {
        val label = context.getString(R.string.today_empty_action)
        assertEquals("the scenario and the product must name the same action", label, ADD_MEDICATION)
        intents = TodayScreenProbe.act(world) { test -> test.onNodeWithText(label).performClick() }
    }

    // --- Проверки: пустое состояние --------------------------------------------

    @Тогда("^в шапке остаются только дата «([^»]+)» и заголовок «([^»]+)»$")
    fun theHeaderKeepsOnlyTheDateAndTitle(date: String, title: String) {
        TodayScreenProbe.ask(world) { test ->
            val header = TodayScreenProbe.texts(test, TAG_SCREEN_HEADER)
            // Шапка набирает дату капителью — это типографика, а не другой текст.
            assertTrue("no date «$date» in the header: $header", header.any { it.equals(date, ignoreCase = true) })
            assertTrue("no title «$title» in the header: $header", header.any { it.equals(title, ignoreCase = true) })
            test.onAllNodesWithTag(TAG_DAY_PROGRESS).assertCountEquals(0)
        }
    }

    @Тогда("^показан заголовок «([^»]+)»$")
    fun theTitleIsShown(title: String) {
        // Заголовок ищется текстом, а не тегом состояния: у пустого состояния тегов два
        // (экранный и дизайн-системный), а сценарий говорит о том, что человек читает.
        TodayScreenProbe.ask(world) { test -> test.onNodeWithText(title).assertIsDisplayed() }
    }

    @Тогда("^пояснение «([^»]+)»$")
    fun theExplanationIsShown(text: String) {
        TodayScreenProbe.ask(world) { test -> test.onNodeWithText(text).assertIsDisplayed() }
    }

    @Тогда("^кнопка «([^»]+)»$")
    fun theButtonIsShown(label: String) {
        TodayScreenProbe.ask(world) { test -> test.onNodeWithText(label).assertIsDisplayed() }
    }

    @Тогда("^полосы прогресса дня на экране нет$")
    fun thereIsNoDayProgress() {
        TodayScreenProbe.ask(world) { test -> test.onAllNodesWithTag(TAG_DAY_PROGRESS).assertCountEquals(0) }
    }

    @Тогда("^открывается форма нового лекарства$")
    fun theMedicationFormOpens() {
        // Экран отдаёт намерение, а экран формы и маршрут живут в `app`: на этом уровне
        // проверяется контракт «Сегодня», а не подмена навигации (SKL-26, TDD §4.3).
        assertTrue("the empty state must offer the new medication form: $intents", TodayIntent.AddMedication in intents)
    }

    // --- Проверки: лекарство по потребности отменяет пустоту -------------------

    @Тогда("^пустое состояние «([^»]+)» не показывается$")
    fun theEmptyStateIsGone(title: String) {
        TodayScreenProbe.ask(world) { test ->
            test.onAllNodesWithTag(TAG_EMPTY_STATE).assertCountEquals(0)
            test.onAllNodesWithTag(TAG_TODAY_EMPTY).assertCountEquals(0)
            test.onAllNodesWithText(title).assertCountEquals(0)
        }
    }

    @Тогда("^показан разделитель «([^»]+)» с карточкой «([^»]+)»$")
    fun theDividerIsShownWithThatCard(divider: String, medication: String) {
        TodayScreenProbe.ask(world) { test ->
            val node = test.onNodeWithTag(TAG_PRN_DIVIDER).fetchSemanticsNode()
            assertTrue(
                "the divider must read «$divider»: ${TodayScreenProbe.textsOf(node)}",
                divider in TodayScreenProbe.textsOf(node),
            )
            test.onNode(
                hasTestTag(TAG_PRN_CARD) and hasAnyDescendant(hasText(TodayScreenProbe.displayName(world, medication))),
                useUnmergedTree = true,
            ).assertExists()
        }
    }

    // --- Обвязка ---------------------------------------------------------------

    private fun nameOf(medicationId: String): String =
        requireNotNull(world.medications.names().firstOrNull { world.medications.get(it).id == medicationId })

    private companion object {
        const val ADD_MEDICATION = "Добавить лекарство"
    }
}
