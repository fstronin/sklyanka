package ru.stohastika.sklyanka.feature.pro

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.pro.ProOffers
import ru.stohastika.sklyanka.domain.pro.ProPoint
import ru.stohastika.sklyanka.domain.pro.ProProduct
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.pro.ProSheetState
import ru.stohastika.sklyanka.domain.pro.ProSubscription
import ru.stohastika.sklyanka.domain.pro.StorePrice
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.io.File
import java.time.LocalDate

/**
 * Эталонные снимки витрины «Склянки Про» (SKL-122, `docs/tasks/SKL-122/TDD.md` §10).
 *
 * Поверхность, где человек платит, обязана иметь эталон: шторка собирается из компонентов,
 * и без снимка правка компонента меняла бы её молча. Снимков два — по одному на состояние
 * из макета (`design/screens/Pro.dc.html`, фигуры 1 и 2): витрина с двумя ценами и
 * состояние «активна» с ценой продления. Состояние «магазин недоступен» показывает то же
 * полотно без карточек периодов и проверяется экранным прогоном и походом на устройстве.
 *
 * Цена в снимке — строка магазина, а не число из кода: тех же строк ждёт приёмка (Р-68 п. 3).
 */
@RunWith(AndroidJUnit4::class)
class ProSheetSnapshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun theShowcase() {
        captureSheet(fileName = "pro-sheet.png", state = showcase())
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun theActiveSubscription() {
        captureSheet(fileName = "pro-active.png", state = active())
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureSheet(fileName: String, state: ProSheetState) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "screen/$fileName"),
            roborazziComposeOptions = RoborazziComposeOptions { size(widthDp = WIDTH, heightDp = HEIGHT) },
        ) {
            SklyankaTheme {
                ProSheet(state = state, onIntent = {})
            }
        }
    }

    /** Витрина с покупкой: период не выбран, обе цены названы, действие недоступно (Р-75 п. 1). */
    private fun showcase(): ProSheetState = ProSheetState(
        visible = true,
        point = ProPoint.SETTINGS,
        subscription = ProSubscription.None,
        offers = offers(),
        selected = null,
    )

    /** Состояние «активна» — экран управления автопродлением (Р-73 п. 1). */
    private fun active(): ProSheetState = ProSheetState(
        visible = true,
        point = ProPoint.SETTINGS,
        subscription = ProSubscription.Active(ProProductId.YEARLY, LocalDate.of(2026, 11, 13)),
        offers = offers(),
        selected = null,
    )

    private fun offers(): ProOffers = ProOffers.Available(
        listOf(
            ProProduct(
                id = ProProductId.MONTHLY,
                title = "Sklyanka Pro · monthly",
                price = StorePrice(text = "149 ₽", amountMicros = 149_000_000, currencyCode = "RUB"),
            ),
            ProProduct(
                id = ProProductId.YEARLY,
                title = "Sklyanka Pro · yearly",
                price = StorePrice(text = "990 ₽", amountMicros = 990_000_000, currencyCode = "RUB"),
            ),
        ),
    )

    private companion object {
        const val WIDTH = 390

        const val HEIGHT = 844
    }
}
