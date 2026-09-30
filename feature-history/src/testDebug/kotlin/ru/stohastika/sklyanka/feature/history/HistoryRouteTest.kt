package ru.stohastika.sklyanka.feature.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Нажатие «Сохранить файлом» через настоящий маршрут истории (SKL-46).
 *
 * Проверяется решение маршрута, а не разметка шторки: без обработчика нажатие обязано
 * кончиться честным отказом и не запускать системный выбор места; с обработчиком —
 * запускать его и не показывать отказа. Настоящий `DocumentsUI` на JVM не существует,
 * и стенд `SavePlaceStand` представляет его регистрацией `ResolveInfo`, а запуск —
 * записью в реестре результатов.
 */
@RunWith(AndroidJUnit4::class)
class HistoryRouteTest {

    @Test
    fun aDeviceWithoutAPlaceChooserRefusesWithoutLaunching() {
        val stand = SavePlaceStand()
        stand.withoutPlaceChooser()
        stand.openReport()

        stand.pressSave()

        assertEquals(HistoryNotice.REPORT_FAILED, stand.notice)
        assertTrue("the chooser must not be launched", stand.lastPress?.launched?.isEmpty() == true)
        assertTrue("the report must stay open for a retry", stand.isReportOpen)
    }

    @Test
    fun aDeviceWithAPlaceChooserLaunchesIt() {
        val stand = SavePlaceStand()
        stand.withPlaceChooser()
        stand.openReport()

        stand.pressSave()

        assertEquals(listOf("report.pdf"), stand.lastPress?.launched)
        assertFalse("no refusal may be shown", stand.lastPress?.refused == true)
        assertNull(stand.notice)
    }
}
