package ru.stohastika.sklyanka.acceptance.pro

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.pro.ProNotice
import ru.stohastika.sklyanka.domain.pro.ProOffers
import ru.stohastika.sklyanka.domain.pro.ProProduct
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.pro.ProSheetState
import ru.stohastika.sklyanka.domain.pro.ProSubscription
import ru.stohastika.sklyanka.domain.pro.StorePrice
import ru.stohastika.sklyanka.feature.pro.ProSheet
import ru.stohastika.sklyanka.feature.pro.ProShowcaseIntent
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_ACTIVE_CARD
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_BENEFITS
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_BUY
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_MANAGE
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_NOTICE
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_SHEET
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_TERMS
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_YEAR_CHEAPER
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDate
import java.util.Locale
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки SKL-122 — витрина «Склянки Про» на стенде.
 *
 * Шаги живут в `feature-pro`, потому что говорят об одной поверхности этого модуля
 * (`SKL-26/TDD.md §4.3`): шторка собирается из компонентов `core-ui`, и её состояние
 * приходит в неё целиком, а не пересчитывается здесь (Р-67). Состояние шаги набирают
 * **аргументами сценария**: цены, недоступный магазин, активная подписка, выбранный
 * период — всё это и есть то, что сценарий называет до проверки. Правила переходов
 * проверяет сценарный прогон (`Sk122ProSteps` в модуле приёмки), здесь — то, что видит
 * человек: слова, цены и доступность действия.
 *
 * **Почему выражения кончаются на «на стенде».** Шаги сценарного прогона приезжают в этот
 * прогон общим артефактом (`StepScope`), и одинаковое выражение в двух объявлениях одного
 * прогона — дубль. Поэтому у стенда своя формулировка для тех же состояний: «магазин
 * не отвечает на стенде», «на стенде обе цены магазина видны».
 *
 * **Почему стенд пересобирает композицию на каждую проверку.** Сессия Compose живёт внутри
 * блока `runComposeUiTest`, а шаги Gherkin — отдельные вызовы: держать блок открытым между
 * ними нечем (приём `Sk136HeaderSteps`). Поэтому состояние копится в полях шагов, а шторка
 * собирается заново в том шаге, который её спрашивает.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса; названия
 * товаров и цены — данные магазина, а не строки каталога (Р-68 п. 3).
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class Sk122ProSheetScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Цена месяца, названная сценарием: `null` — шторка собирается с ценой Предыстории. */
    private var monthlyPrice: String? = null

    /** Цена года, названная сценарием. */
    private var yearlyPrice: String? = null

    /** Магазин не ответил: цен нет, выбора нет, покупка недоступна (Р-68 п. 4). */
    private var silent: Boolean = false

    /** Активная подписка: состояние «активна» вместо витрины (Р-73 п. 1). */
    private var activeUntil: LocalDate? = null

    /** Выбранный период: «Оформить» доступна, только когда выбор сделан (Р-75 п. 1). */
    private var selected: ProProductId? = null

    /** Намерения, которые поверхность отдала за сценарий: ими проверяется, куда ведёт строка. */
    private val intents: MutableList<ProShowcaseIntent> = mutableListOf()

    // =======================================================================================
    // Предыстория стенда
    // =======================================================================================

    @Допустим("^шторка витрины открыта на стенде$")
    fun theShowcaseSheetIsOpenOnTheStand() {
        runComposeUiTest {
            showTheSheet()
            onNodeWithTag(TAG_PRO_SHEET, useUnmergedTree = true).assertExists()
        }
    }

    @И("^магазин называет цены «([^»]+)» и «([^»]+)»$")
    fun theStoreNamesThePrices(monthly: String, yearly: String) {
        monthlyPrice = monthly
        yearlyPrice = yearly
    }

    @И("^магазин не отвечает на стенде$")
    fun theStoreIsSilentOnTheStand() {
        silent = true
    }

    @И("^подписка активна до (.+)$")
    fun theSubscriptionIsActiveUntil(until: String) {
        activeUntil = world.dateOf(until)
    }

    @И("^на стенде выбран период «([^»]+)»$")
    fun thePeriodIsChosenOnTheStand(period: String) {
        selected = periodId(period)
    }

    @Когда("^на стенде человек нажимает «Условия и политика»$")
    fun thePersonPressesTheTermsRowOnTheStand() {
        runComposeUiTest {
            setContent {
                Stand {
                    ProSheet(
                        state = standState(),
                        onIntent = { intent -> intents += intent },
                    )
                }
            }
            onNodeWithTag(TAG_PRO_TERMS, useUnmergedTree = true).performClick()
            waitForIdle()
        }
    }

    @Тогда("^на стенде открывается документ, а не экран магазина$")
    fun theDocumentOpens() {
        assertEquals(
            "«Условия и политика» — документ, а не шаг покупки: в магазин ведут «Управление подпиской» и «Отменить подписку» (§5.9)",
            listOf(ProShowcaseIntent.OpenPolicy),
            intents,
        )
    }

    // =======================================================================================
    // Проверки: состав, цены, действие
    // =======================================================================================

    /** Шесть выгод названы словами каталога: витрина показывает состав во всех состояниях (Р-75 п. 3). */
    @Тогда("^на стенде видны шесть выгод «Про»$")
    fun theSixBenefitsAreVisibleOnTheStand() {
        val names = benefitNames()
        assertEquals("the showcase must name six benefits", SIX, names.size)
        runComposeUiTest {
            showTheSheet()
            onNodeWithTag(TAG_PRO_BENEFITS, useUnmergedTree = true).assertExists()
            names.forEach { name ->
                onNodeWithText(name, useUnmergedTree = true).assertExists()
            }
        }
    }

    @Тогда("^на стенде обе цены магазина видны$")
    fun bothStorePricesAreVisibleOnTheStand() {
        runComposeUiTest {
            showTheSheet()
            onNodeWithText(monthlyPriceOf(), useUnmergedTree = true).assertExists()
            onNodeWithText(yearlyPriceOf(), useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^кнопка «Оформить» недоступна на стенде$")
    fun theBuyButtonIsUnavailableOnTheStand() {
        runComposeUiTest {
            showTheSheet()
            onNodeWithTag(TAG_PRO_BUY, useUnmergedTree = true).assertIsNotEnabled()
        }
    }

    @Тогда("^кнопка «Оформить» доступна на стенде$")
    fun theBuyButtonIsAvailableOnTheStand() {
        runComposeUiTest {
            showTheSheet()
            onNodeWithTag(TAG_PRO_BUY, useUnmergedTree = true).assertIsEnabled()
        }
    }

    @Тогда("^в карточке года на стенде стоит «Год дешевле»$")
    fun theYearCardIsCheaperOnTheStand() {
        runComposeUiTest {
            showTheSheet()
            onNodeWithTag(TAG_PRO_YEAR_CHEAPER, useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^на стенде слов «Год дешевле» нет$")
    fun theYearCheaperClaimIsAbsentOnTheStand() {
        runComposeUiTest {
            showTheSheet()
            onAllNodesWithTag(TAG_PRO_YEAR_CHEAPER, useUnmergedTree = true).assertCountEquals(0)
            onAllNodesWithText(string(UiR.string.pro_year_cheaper), useUnmergedTree = true).assertCountEquals(0)
        }
    }

    /** Состояние «активна» — экран управления: карточка срока и «Управление подпиской» (Р-73 п. 1). */
    @Тогда("^на стенде карточка активной подписки с «Управление подпиской»$")
    fun theActiveCardAndTheManageActionAreOnTheStand() {
        runComposeUiTest {
            showTheSheet()
            onNodeWithTag(TAG_PRO_ACTIVE_CARD, useUnmergedTree = true).assertExists()
            onNodeWithTag(TAG_PRO_MANAGE, useUnmergedTree = true).assertExists()
            onAllNodesWithTag(TAG_PRO_BUY, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Тогда("^на стенде причина недоступного магазина названа словами$")
    fun theStoreUnavailabilityIsNamedOnTheStand() {
        val words = string(UiR.string.pro_store_unavailable)
        runComposeUiTest {
            showTheSheet()
            onNodeWithTag(TAG_PRO_NOTICE, useUnmergedTree = true)
                .assertExists()
                .assertTextEquals(words)
        }
    }

    // =======================================================================================
    // Стенд: состояние, поверхность, слова
    // =======================================================================================

    /** Состояние шторки целиком: поверхность не принимает ни одного решения сама (Р-67). */
    private fun standState(): ProSheetState = ProSheetState(
        visible = true,
        subscription = activeUntil?.let { until -> ProSubscription.Active(ProProductId.MONTHLY, until) }
            ?: ProSubscription.None,
        offers = offers(),
        selected = selected,
        // Причина недоступности стоит в нижнем блоке у самой кнопки (Р-68 п. 6).
        notice = if (silent) ProNotice.STORE_UNAVAILABLE else null,
    )

    private fun offers(): ProOffers = if (silent) {
        ProOffers.Unavailable
    } else {
        ProOffers.Available(
            listOf(
                product(ProProductId.MONTHLY, MONTHLY_TITLE, monthlyPriceOf()),
                product(ProProductId.YEARLY, YEARLY_TITLE, yearlyPriceOf()),
            ),
        )
    }

    private fun product(id: ProProductId, title: String, price: String): ProProduct = ProProduct(
        id = id,
        title = title,
        price = StorePrice(text = price, amountMicros = micros(price), currencyCode = CURRENCY),
    )

    /** Микроединицы из строки магазина: сравнивать «год дешевле» нечем без сопоставимой величины. */
    private fun micros(price: String): Long =
        (price.filter(Char::isDigit).takeIf { it.isNotEmpty() } ?: ZERO).toLong() * MICROS_PER_UNIT

    private fun monthlyPriceOf(): String = monthlyPrice ?: DEFAULT_MONTHLY_PRICE

    private fun yearlyPriceOf(): String = yearlyPrice ?: DEFAULT_YEARLY_PRICE

    private fun ComposeUiTest.showTheSheet() {
        setContent { Stand { ProSheet(state = standState(), onIntent = {}) } }
        waitForIdle()
    }

    /** Короткие имена шести выгод в порядке нормы состава (`docs/norm/01-product.md` §5.2). */
    private fun benefitNames(): List<String> = listOf(
        UiR.string.pro_benefit_schedules_short,
        UiR.string.pro_benefit_widgets_short,
        UiR.string.pro_benefit_report_short,
        UiR.string.pro_benefit_accent_short,
        UiR.string.pro_benefit_export_short,
        UiR.string.pro_benefit_profiles_short,
    ).map(::string)

    private fun periodId(period: String): ProProductId = when (period) {
        MONTH_PERIOD -> ProProductId.MONTHLY
        YEAR_PERIOD -> ProProductId.YEARLY
        else -> error("the scenario names an unknown period: $period")
    }

    private fun string(id: Int): String = context.withInterfaceLocale(RUSSIAN).getString(id)

    /**
     * Стенд: язык интерфейса сценария.
     *
     * Язык закреплён, потому что сценарии записаны по-русски, а телефон стенда мог быть
     * любым (НФТ-43); слова и даты тогда берутся из того же каталога, что у витрины.
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
        const val SIX = 6

        /** Микроединицы в рубле: `StorePrice.amountMicros` — миллионные доли единицы валюты. */
        const val MICROS_PER_UNIT = 1_000_000L

        const val CURRENCY = "RUB"

        const val ZERO = "0"

        /** Названия товаров — данные консоли магазина, а не строки интерфейса. */
        const val MONTHLY_TITLE = "Склянка Про · месяц"
        const val YEARLY_TITLE = "Склянка Про · год"

        /** Цены Предыстории сценария: витрина без названных цен показывает цены магазина стенда. */
        const val DEFAULT_MONTHLY_PRICE = "149 ₽"
        const val DEFAULT_YEARLY_PRICE = "990 ₽"

        const val MONTH_PERIOD = "Месяц"
        const val YEAR_PERIOD = "Год"

        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    }
}
