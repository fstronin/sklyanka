package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_EMPTY
import ru.stohastika.sklyanka.screen.support.DeviceActivities
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import java.math.BigDecimal
import ru.stohastika.sklyanka.feature.today.R as TodayR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Враждебный проход по «Сегодня» (SKL-140, реестр походов № 15).
 *
 * Человек пробует сломать главный экран: пустой день, двойное «Принято», повторная «Отмена»,
 * уход с окна отмены и системная «Назад». Поход идёт на настоящем файле базы — отметка,
 * списание и возврат дозы обязаны жить там же, где живёт у человека, и утверждаются не
 * подписью на карточке, а следом в журнале операций.
 *
 * **Расхождение со сценарием, названное честно.** Порядок пунктов сценария — «двойное Принято»,
 * «карточка в момент окна отмены», «двойная Отмена» — в одном окне неисполним: уход на карточку
 * закрывает окно (`TodayRoute.onScreenLeft`, SKL-30/43), и после пункта «карточка» нажимать
 * «Отменить» было бы уже не на чем. Поэтому обе пробы одной поверхности разведены по своим
 * окнам: сначала отметка → двойная «Отмена» (доза возвращена один раз), затем новая отметка →
 * карточка в живом окне (окно закрыто, приём остался принятым). Порядок проб изменён, ни один
 * утверждаемый факт не потерян.
 *
 * **Подпись действия.** Кнопка наступившего приёма подписана «Принято»
 * (`UiR.string.action_taken`), а не «Принять» сценария: «Принять» — подпись лекарства
 * «по потребности». Двойное нажатие — два тапа подряд без простоя между ними
 * (`performTouchInput { doubleClick() }`), а не два `performClick` с ожиданием: второе
 * нажатие обязано прийти в то же окно, где человек его и делает.
 */
@HiltAndroidTest
class HostileTodayTest : JourneyTest() {

    private val name: String get() = testContext.getString(R.string.device_test_medication_name, NAME_SUFFIX)

    @Test
    fun thePersonCannotBreakTheDay() {
        // Пустое состояние: у профиля нет ни одного лекарства.
        launchApp()
        awaitRoot()
        waitForTag(TAG_TODAY_EMPTY)
        assertTrue(
            "the empty day must invite the person to add a medication",
            textExists(ui(TodayR.string.today_empty_action)),
        )
        assertTrue("the empty day must hold no intakes", dayIntakes().isEmpty())
        capture("empty")

        // Сид после пустого состояния: лекарство с приёмом в назначенное время и остатком.
        seedJourney()
        relaunchApp()
        awaitRoot()
        waitUntil(SCREEN_TIMEOUT_MS, "карточки приёма «$name»") { textExists(name) }

        // Двойное «Принято»: отметка одна, второй записи нет, доза списана один раз.
        doublePress(ui(UiR.string.action_taken))
        waitForTag(TAG_SNACKBAR)
        waitUntil(SCREEN_TIMEOUT_MS, "приёма, отмеченного принятым") {
            dayIntakes().count { it.status == IntakeStatus.TAKEN } == 1
        }
        assertEquals("the day must hold a single intake", 1, dayIntakes().size)
        assertEquals("a repeated press must not add a second mark", 1, opLogCount(OpEvent.INTAKE_MARKED))
        assertEquals("the dose must be written off exactly once", 1, opLogCount(OpEvent.STOCK_DECREMENTED))
        capture("marked")

        // Двойная «Отмена» в живом окне: доза возвращена один раз, окно закрыто.
        doublePress(ui(TodayR.string.today_undo_action))
        waitUntil(SCREEN_TIMEOUT_MS, "возврата приёма в ожидание") {
            !exists(TAG_SNACKBAR) && dayIntakes().none { it.status == IntakeStatus.TAKEN }
        }
        assertEquals("a repeated undo must restore the dose once", 1, opLogCount(OpEvent.INTAKE_MARK_UNDONE))
        assertEquals("the dose must be returned exactly once", 1, opLogCount(OpEvent.STOCK_RESTORED))
        capture("undone")

        // Карточка открыта в живом окне отмены: окно закрыто, приём остался принятым.
        markOnce()
        waitForTag(TAG_SNACKBAR)
        openMedications()
        openMedicationCard(name)
        pressBack()
        openToday()
        assertFalse("leaving the day must end the undo window", exists(TAG_SNACKBAR))
        assertEquals("the intake must stay taken", 1, dayIntakes().count { it.status == IntakeStatus.TAKEN })
        capture("window-closed")

        // Системная «Назад» на «Сегодня» завершает приложение, а не уводит на чужой экран.
        pressBack()
        waitUntil(LAUNCH_TIMEOUT_MS, "завершения приложения") { DeviceActivities.resumedActivity() == null }
    }

    /**
     * Сид похода: лекарство с приёмом в назначенное время, остатком и упаковкой.
     *
     * Остаток обязателен: без него `StockLedger` нечего списывать, и следа дозы в журнале
     * (`STOCK_DECREMENTED` / `STOCK_RESTORED`) не появилось бы — «доза списана один раз»
     * осталось бы словом экрана.
     */
    private fun seedJourney() {
        seedMedication(
            id = MEDICATION_ID,
            name = name,
            times = listOf(DeviceTestClock.scheduledTime),
            stockLeft = BigDecimal(STOCK_LEFT),
            packageSize = BigDecimal(PACKAGE_SIZE),
        )
        planIntakes()
    }

    /** Двойное нажатие подписи: два тапа в одном окне, без простоя между ними (враждебный приём). */
    private fun doublePress(label: String) {
        waitUntil(SCREEN_TIMEOUT_MS, "узла «$label»") { textExists(label) }
        compose.onNodeWithText(label, useUnmergedTree = true).performTouchInput { doubleClick() }
        compose.waitForIdle()
    }

    /** Одинарное «Принято»: новый заход человека снова открывает окно отмены для пробы карточкой. */
    private fun markOnce() {
        val label = ui(UiR.string.action_taken)
        waitUntil(SCREEN_TIMEOUT_MS, "кнопки «$label»") { textExists(label) }
        compose.onNodeWithText(label, useUnmergedTree = true).performClick()
    }

    private companion object {
        const val MEDICATION_ID = "journey-hostile-today"
        const val NAME_SUFFIX = "hostile-today"
        const val STOCK_LEFT = "9"
        const val PACKAGE_SIZE = "60"
    }
}
