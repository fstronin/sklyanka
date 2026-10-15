package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_ACTIVE_CARD
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_BENEFITS
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_BUY
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_CANCEL
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_MANAGE
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_NOTICE
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_PERIOD_MONTH
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_PERIOD_YEAR
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_SHEET
import ru.stohastika.sklyanka.feature.pro.TAG_PRO_YEAR_CHEAPER
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestStores
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Поход «Витрина “Склянка Про”» (SKL-122, SKL-140).
 *
 * Человек находит строку блока «Склянка Про» в настройках, открывает витрину и читает
 * состав «Про»; затем возвращается системной «Назад» и остаётся там же, откуда пришёл.
 *
 * **Поход утверждает то состояние, которое на стенде достижимо.** Образ AOSP, на котором
 * идёт поход, не несёт HMS Core, поэтому клиент AppGallery не отвечает, и
 * `AppGalleryProStore.offers()` возвращает `ProOffers.Unavailable` — исключение обращения
 * к службе магазина не ответ, а его отсутствие (`SKL-122/TDD.md` §10: «магазин недоступен
 * на стенде (без HMS Core) → причина словами»). Из этого состояния витрина собирается
 * по правилам домена, а не по вкусу проверки: цен нет, выбора периода нет (`ProSheet` рисует
 * выгоды и на недоступном магазине возвращается до карточек), рядом с кнопкой стоят слова
 * причины `ProNotice.STORE_UNAVAILABLE`, а кнопка показана недоступной — `buyEnabled`
 * в этом состоянии ложно. Покупка, восстановление и отмена в песочнице AppGallery —
 * ручная проверка заказчика (`@manual @device`, Р-49), и поход их не имитирует (П-7).
 *
 * Строка настроек помечена `internal` в чужом модуле, а `internal` app-проверке не виден:
 * строка адресуется своим названием — тем же, что видит человек (тот же приём, что
 * у похода «История и отчёт врачу»).
 */
@HiltAndroidTest
class ProShowcaseJourneyTest : JourneyTest() {

    /** Дата, до которой подписка активна в состоянии «активна». */
    private var activeUntil: LocalDate = DeviceTestClock.today

    @Test
    fun thePersonOpensTheProShowcaseFromTheSettingsRow() {
        launchApp()
        awaitRoot()
        openSettings()
        openShowcaseFromSettings()
        waitForTag(TAG_PRO_SHEET)
        assertTheCompositionIsShown()
        assertWithoutTheStoreTheShowcaseNamesTheReason()
        capture("pro-sheet")

        pressBack()
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия витрины и возврата в настройки") {
            !exists(TAG_PRO_SHEET) && exists(TAG_SETTINGS_SCREEN)
        }
    }

    /**
     * Состояние «активна» — экран управления автопродлением (Р-73 п. 1) — на устройстве.
     *
     * На стенде магазина нет, поэтому подписку подкладывает сама проверка: она пишет в кэш
     * тот же ответ, что дал бы магазин (ключ и формат — из `ProSubscriptionDataStore`).
     * Это не имитация покупки (П-7), а состояние доступа, которое гейты читают из кэша
     * и которое иначе на устройстве не поднять вовсе.
     *
     * Цены продления здесь нет и быть не может: её называет магазин, а он не ответил;
     * прочерка и нуля на её месте не бывает (`docs/norm/06-design.md` §5.9).
     */
    @Test
    fun theActiveSubscriptionShowsTheManagementState() {
        launchApp()
        awaitRoot()
        openSettings()
        seedActiveSubscription()
        openShowcaseFromSettings()

        waitForTag(TAG_PRO_ACTIVE_CARD)
        assertTrue("the state must name the date", textExists(activeUntilText()))
        assertTrue("the management action must be shown", exists(TAG_PRO_MANAGE))
        assertTrue("the cancel row must be shown", exists(TAG_PRO_CANCEL))
        assertTrue(
            "the renewal words must be shown",
            textExists(ui(UiR.string.pro_renew_auto)),
        )
        assertFalse("the purchase action belongs to the offer", exists(TAG_PRO_BUY))
        capture("pro-active")
    }

    /**
     * Строка блока называет новое состояние после закрытия шторки (SKL-122).
     *
     * Пока шторка открыта, доступ мог появиться (в жизни — покупкой или восстановлением,
     * на стенде — записью в кэш, раз магазина нет). Человек закрывает шторку, **не покидая
     * экран настроек**, и строка обязана назвать новое состояние по закрытию: `onResume`
     * в этом пути не наступает, и без перечитывания строка осталась бы «Не оформлена».
     */
    @Test
    fun theSettingsRowNamesTheNewStateAfterTheShowcaseCloses() {
        launchApp()
        awaitRoot()
        openSettings()
        scrollToShowcaseRow()

        val inactive = ui(UiR.string.pro_state_inactive)
        assertTrue("until the purchase the row says the subscription is not taken", textExists(inactive))

        seedActiveSubscription()
        openShowcaseFromSettings()
        waitForTag(TAG_PRO_ACTIVE_CARD)

        pressBack()
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия витрины и возврата в настройки") {
            !exists(TAG_PRO_SHEET) && exists(TAG_SETTINGS_SCREEN)
        }

        assertTrue("the row must name the new state", textExists(activeUntilText()))
    }

    /**
     * Подписка в кэше: ответ магазина, который на стенде некому дать.
     *
     * Ключ и формат записи повторяют `ProSubscriptionDataStore` (`active|<товар>|<день>`):
     * другого способа поднять состояние «активна» без живого магазина у проверки нет.
     */
    private fun seedActiveSubscription() {
        activeUntil = DeviceTestClock.today.plusDays(ACTIVE_DAYS)
        runBlocking {
            DeviceTestStores.settings(targetContext).edit { preferences ->
                preferences[PRO_SUBSCRIPTION] =
                    "active|${ProProductId.YEARLY.storeId}|${activeUntil.toEpochDay()}"
            }
        }
    }

    /** «Активна до <дата>» — тем же форматтером с локалью интерфейса, что и экран (НФТ-43а). */
    private fun activeUntilText(): String {
        val locale = targetContext.resources.configuration.locales[0]
        val pattern = targetContext.getString(UiR.string.pro_date_pattern)
        return targetContext.getString(
            UiR.string.pro_state_active_until,
            DateTimeFormatter.ofPattern(pattern, locale).format(activeUntil),
        )
    }

    /**
     * Открыть витрину со строки блока в настройках.
     *
     * Прокрутка идёт по тексту, а не по тегу: строка блока помечена `internal` в
     * `feature-settings` и app-проверке не видна, а название строки — то же слово
     * «Склянка Про», что стоит в шапке шторки (`ProNotice.SHEET_TITLE`).
     */
    private fun openShowcaseFromSettings() {
        scrollToShowcaseRow()
        compose.onNodeWithText(ui(UiR.string.pro_sheet_title)).performClick()
    }

    /** Строка блока — внизу списка настроек: до неё доводят прокруткой. */
    private fun scrollToShowcaseRow() {
        val row = ui(UiR.string.pro_sheet_title)
        compose.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag(TAG_SETTINGS_SCREEN)),
            useUnmergedTree = true,
        ).performScrollToNode(hasText(row))
    }

    /** Состав «Про» и главное действие: витрина показывает то, что продаёт, ещё до цен. */
    private fun assertTheCompositionIsShown() {
        waitForTag(TAG_PRO_BENEFITS)
        val benefits = listOf(
            UiR.string.pro_benefit_schedules_short,
            UiR.string.pro_benefit_widgets_short,
            UiR.string.pro_benefit_report_short,
            UiR.string.pro_benefit_accent_short,
            UiR.string.pro_benefit_export_short,
            UiR.string.pro_benefit_profiles_short,
        ).map { key -> ui(key) }
        benefits.forEach { name ->
            assertTrue("the showcase must name the benefit «$name»", textExists(name))
        }
        assertTrue(
            "the showcase must offer the purchase",
            textExists(ui(UiR.string.pro_action_buy)),
        )
        waitForTag(TAG_PRO_BUY)
        compose.onNodeWithTag(TAG_PRO_BUY, useUnmergedTree = true).assertIsNotEnabled()
    }

    /**
     * Магазин недоступен: причина названа словами, а цен и выбора периода нет.
     *
     * Отсутствие карточек периодов — не пробел оформления, а следствие состояния: без ответа
     * магазина цены взять неоткуда, а подставить последнюю известную запрещено (Р-68 п. 4).
     * Вместе с карточками уходит и утверждение о выгоде года: проверять его не на чем (Р-68 п. 5).
     */
    private fun assertWithoutTheStoreTheShowcaseNamesTheReason() {
        waitForTag(TAG_PRO_NOTICE)
        assertEquals(
            "the showcase must name the reason in words",
            ui(UiR.string.pro_store_unavailable),
            nodeText(TAG_PRO_NOTICE),
        )
        assertFalse("no prices without the store", exists(TAG_PRO_PERIOD_MONTH))
        assertFalse("no prices without the store", exists(TAG_PRO_PERIOD_YEAR))
        assertFalse("no year claim to check without prices", exists(TAG_PRO_YEAR_CHEAPER))
    }

    private companion object {
        /** Сколько дней до конца подписки в состоянии «активна». */
        const val ACTIVE_DAYS = 30L

        /** Ключ кэша состояния подписки: то же имя, что в `ProSubscriptionDataStore`. */
        val PRO_SUBSCRIPTION = stringPreferencesKey("pro_subscription")
    }
}
