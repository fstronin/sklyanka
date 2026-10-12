package ru.stohastika.sklyanka.acceptance.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.meds.card.MedCardIntent
import ru.stohastika.sklyanka.feature.meds.card.MedicationCardScreen
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_VALUE
import ru.stohastika.sklyanka.feature.today.R
import ru.stohastika.sklyanka.feature.today.TAG_STOCK_BANNER
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal

/**
 * Шаги сценарной приёмки перехода «Купить» → карточка лекарства (SKL-08, группа
 * `@composition`; заведены SKL-26).
 *
 * Прогон идёт в `app`, потому что сценарию нужны оба экрана сразу: «Сегодня» и карточка
 * лекарства живут в разных модулях, и только здесь они существуют одновременно
 * (`SKL-26/TDD.md §4.3`). Состояние «Сегодня» собирает [TodayFromWorld], состояние
 * карточки — [MedicationCardFromWorld]: оба — настоящим доменом, без второго ответа
 * на вопрос «сколько осталось» (SKL-12).
 *
 * `@composition` — не причина неисполнимости, а область прогона: тот же `@screen`,
 * которому нужны экраны нескольких модулей (SKL-26/TDD.md §7).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TodayStockCompositionSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val resources = context.withInterfaceLocale().resources

    private var intents: List<TodayIntent> = emptyList()
    private var openedMedication: String = ""

    // --- Предыстория и действие ------------------------------------------------

    @Допустим("^показана плашка «([^»]+)»$")
    fun thePlateIsShown(text: String) {
        val (medication, rest) = text.split(NAME_SEPARATOR, limit = 2)
        val name = world.medicationNamed(medication)
        // Предыстория задаёт остаток и порог: плашка показывается, когда прогноз запаса
        // не длиннее порога, а «хватит на 4 дня» при дозе 2 таблетки даёт остаток 9.
        world.medications.update(name) {
            it.copy(stockLeft = BigDecimal(STOCK_LEFT), stockThresholdDays = THRESHOLD_DAYS)
        }
        val expected = "${world.medications.get(name).name}$NAME_SEPARATOR$rest"
        onToday { test ->
            val plates = test.onAllNodesWithTag(TAG_STOCK_BANNER, useUnmergedTree = true)
            plates.assertCountEquals(1)
            val texts = textsOf(plates.fetchSemanticsNodes().single())
            // Число и единица в подписи не разрываются неразрывным пробелом (SKL-59 TDD §2.2),
            // а фраза сценария набрана обычными — сравниваем без него.
            val printed = texts.map { it.replace('\u00A0', ' ') }
            assertTrue(
                "the plate must read «$expected»: $texts",
                expected.replace('\u00A0', ' ') in printed,
            )
        }
    }

    @Когда("^человек нажимает «Купить»$")
    fun thePersonPressesBuy() {
        val label = resources.getString(R.string.today_stock_action)
        val collected = mutableListOf<TodayIntent>()
        onToday(collected) { test -> test.onNodeWithText(label).performClick() }
        intents = collected
    }

    // --- Проверки: куда ведёт действие и чего оно не предлагает ----------------

    @Тогда("^открывается карточка лекарства «([^»]+)» на блоке остатка$")
    fun theCardOpensAtItsStockBlock(medication: String) {
        val name = world.medicationNamed(medication)
        val id = world.medications.get(name).id
        val opened = intents.filterIsInstance<TodayIntent.OpenStock>().singleOrNull()
        assertNotNull("«Купить» must open the stock of a medication: $intents", opened)
        assertEquals("the stock of «$medication» must open", id, opened!!.medicationId)
        openedMedication = name
        // Приёмник действия — настоящая карточка лекарства, и на ней виден тот же блок
        // остатка, что считает SKL-12.
        val state = MedicationCardFromWorld.state(world, world.medications.get(name))
        runComposeUiTest {
            setContent {
                Pinned { MedicationCardScreen(state = state, onIntent = {}) }
            }
            onNodeWithTag(TAG_STOCK_BLOCK, useUnmergedTree = true).fetchSemanticsNode()
            val value = textsOf(onNodeWithTag(TAG_STOCK_VALUE, useUnmergedTree = true).fetchSemanticsNode())
            assertEquals(
                "the card must show the stock of «$medication»",
                resources.getQuantityString(
                    ru.stohastika.sklyanka.feature.meds.R.plurals.stock_left_tablets,
                    STOCK_LEFT,
                    STOCK_LEFT,
                ),
                value.single(),
            )
        }
    }

    @Тогда("^никакого заказа или покупки приложение не предлагает$")
    fun noOrderOrPurchaseIsOffered() {
        // Заказа в продукте нет ни в контракте намерений, ни на собранном экране:
        // покупка живёт вне приложения (НФТ-14 — порта сети в продукте не существует),
        // а «Купить» на «Сегодня» — переход к остатку, а не оформление заказа.
        listOf(MedCardIntent::class.java, TodayIntent::class.java).forEach { contract ->
            val variants = contract.declaredClasses.map { it.simpleName }
            assertTrue(
                "the contract ${contract.simpleName} must not offer a purchase: $variants",
                variants.none { it.hasPurchaseWord() },
            )
        }
        val state = MedicationCardFromWorld.state(world, world.medications.get(openedMedication))
        runComposeUiTest {
            setContent {
                Pinned { MedicationCardScreen(state = state, onIntent = {}) }
            }
            val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val labels = textsOf(root) + descriptionsOf(root)
            assertTrue(
                "a purchase affordance is on the card: ${labels.filter { it.hasPurchaseWord() }}",
                labels.none { it.hasPurchaseWord() },
            )
        }
    }

    // --- Обвязка ---------------------------------------------------------------

    /** Экран «Сегодня» в контексте языка интерфейса и активного профиля сценария. */
    private fun onToday(intents: MutableList<TodayIntent>? = null, assertion: (ComposeUiTest) -> Unit) {
        runComposeUiTest {
            setContent {
                Pinned {
                    TodayScreen(
                        state = TodayFromWorld.state(world),
                        onIntent = { intent -> intents?.add(intent) },
                    )
                }
            }
            assertion(this)
        }
    }

    /** Язык интерфейса закреплён: даты и склонения читаются по-русски при любом телефоне (НФТ-43). */
    @Composable
    private fun Pinned(content: @Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale()
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
        ) {
            SklyankaTheme { content() }
        }
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    private fun descriptionsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.children.flatMap { descriptionsOf(it) }

    private fun String.hasPurchaseWord(): Boolean = PURCHASE_WORDS.any { word -> contains(word, ignoreCase = true) }

    private companion object {
        const val NAME_SEPARATOR = " — "
        const val STOCK_LEFT = 9
        const val THRESHOLD_DAYS = 5

        /** Слова, которыми был бы назван заказ или покупка, — на любом языке продукта. */
        val PURCHASE_WORDS = listOf(
            "заказ", "оформ", "куп", "корзин", "оплат", "order", "purchase", "buy", "checkout", "cart", "shop",
        )
    }
}
