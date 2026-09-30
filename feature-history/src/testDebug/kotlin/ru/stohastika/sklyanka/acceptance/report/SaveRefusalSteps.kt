package ru.stohastika.sklyanka.acceptance.report

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.history.HistoryNotice
import ru.stohastika.sklyanka.feature.history.SavePlaceStand

/**
 * Экранные шаги честного отказа при отсутствии системного выбора места (SKL-46,
 * группа `@screen`).
 *
 * Каждый шаг идёт через стенд `SavePlaceStand`: настоящий маршрут истории, настоящая
 * модель состояния и записывающий реестр результатов. Обработчик включается
 * регистрацией `ResolveInfo` — тем же ответом, каким система отвечает на `resolveActivity`.
 *
 * Мир сценария шагам не нужен: сценарий говорит только про нажатие. Параметр оставлен,
 * потому что фабрика Cucumber раздаёт мир всем классам шагов сценария.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("UnusedPrivateMember")
class SaveRefusalSteps(private val world: AcceptanceWorld) {

    private val stand = SavePlaceStand()

    @Допустим("^в устройстве нет обработчика системного выбора места$")
    fun theDeviceHasNoPlaceChooser() {
        stand.withoutPlaceChooser()
    }

    @Допустим("^в устройстве есть обработчик системного выбора места$")
    fun theDeviceHasAPlaceChooser() {
        stand.withPlaceChooser()
    }

    @Допустим("^отчёт врачу за сентябрь 2026 открыт$")
    fun theReportIsOpen() {
        stand.openReport()
    }

    @Когда("^человек нажимает «Сохранить файлом»$")
    fun thePersonPressesSave() {
        stand.pressSave()
    }

    @Тогда("^показано «Отчёт не сохранён»$")
    fun theRefusalIsShown() {
        assertTrue(stand.lastPress?.refused == true, "the refusal banner must be shown")
        assertEquals(HistoryNotice.REPORT_FAILED, stand.notice)
    }

    @И("^системный выбор места не открывался$")
    fun thePlaceChooserWasNotLaunched() {
        assertTrue(
            stand.lastPress?.launched?.isEmpty() == true,
            "the place chooser must not be launched: ${stand.lastPress?.launched}",
        )
    }

    @Но("^отчёт врачу остаётся открытым$")
    fun theReportStaysOpen() {
        assertTrue(stand.isReportOpen, "the report must stay open for a retry")
    }

    @Тогда("^системный выбор места запрошен для файла отчёта$")
    fun thePlaceChooserWasRequested() {
        assertEquals(listOf("report.pdf"), stand.lastPress?.launched)
    }

    @И("^сообщения «Отчёт не сохранён» нет$")
    fun theRefusalIsNotShown() {
        assertFalse(stand.lastPress?.refused == true, "no refusal may be shown when the chooser exists")
    }
}
