package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.feature.meds.card.TAG_EDIT
import ru.stohastika.sklyanka.feature.meds.card.TAG_MEDICATION_CARD_SCREEN
import ru.stohastika.sklyanka.feature.meds.form.TAG_DOSE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIME_ROW
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_CONFIRM
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_DIALOG
import ru.stohastika.sklyanka.ui.component.TAG_DATE_TIME_PICKER
import ru.stohastika.sklyanka.ui.component.TAG_TIME_INPUT
import java.time.LocalTime
import ru.stohastika.sklyanka.feature.meds.R as MedsR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Поход «Правка лекарства» (SKL-140, реестр походов № 10).
 *
 * Человек открывает карточку «Магния B6», уходит в правку, меняет время приёма и дозу,
 * сохраняет — и видит новое расписание в карточке и в дне. Вторая правка остаётся
 * несохранённой: расписание и доза прежние. Хранилище здесь настоящее, потому что
 * расписание — это записи приёмов: перепланирование проверяется по ним, а не по картинке.
 *
 * **Два расхождения со сценарием, названные честно.**
 *
 * 1. Часы похода сдвинуты на утро **следующих** суток (`DeviceTestClock.advanceTo`), а не
 *    оставлены на базовом моменте 09:01. Сценарий требует «приёма на 08:00 в дне нет» после
 *    правки, а перепланирование снимает только ожидающиеся приёмы **впереди** текущего
 *    момента (`IntakePlanDiffer`): уже наступившее время продукт оставляет в дне как историю —
 *    его окно длится до конца суток (`EndOfDayIntakeWindow`). Поэтому человек правит
 *    расписание утром, до своего времени приёма: посеянные 08:00 ещё ожидаются, и правка
 *    их снимает. Сдвиг идёт вперёд — туда, куда управляемые часы и предназначены.
 * 2. Время меняется настоящим тайм-пикером: строка времени, клавиатурный ввод (`TimeInput`),
 *    «Готово». Два поля ввода живут под одним тегом и адресуются порядком (часы, минуты);
 *    ход похода утверждает подпись строки сразу после подтверждения, поэтому промах ввода
 *    падает на ней, а не уходит тихо в карточку.
 *
 * Доза утверждается фразой карточки, собранной из ресурсов продукта: склонение зависит
 * от числа, и литерал в проверке разошёлся бы с показанным человеку.
 */
@HiltAndroidTest
class MedicationEditJourneyTest : JourneyTest() {

    /** «Магний B6» — имя из строк стенда: медицинских данных человека в походе нет. */
    private val name: String get() = testContext.getString(R.string.store_medication_magnesium)

    @Test
    fun thePersonMovesTheTimeAndTheDoseAndLeavesTheLastEditUnsaved() {
        // Часы: утро суток, где посеянный приём ещё впереди, — случай, в котором расписание
        // в дне действительно заменяется (см. первый пункт KDoc).
        DeviceTestClock.advanceTo(DeviceTestClock.today.plusDays(1).atTime(EDIT_MOMENT))
        seedJourney()
        launchApp()
        awaitRoot()

        seeTheSeededSchedule()
        takeTheFilledFormAndEditIt()
        saveAndSeeTheNewSchedule()
        seeTheDayWithTheNewTime()
        leaveTheLastEditUnsaved()
    }

    /** Сид похода: «Магний B6» с приёмом в 08:00 и дозой одна таблетка; приёмы спланированы. */
    private fun seedJourney() {
        seedMedication(id = MEDICATION_ID, name = name, times = listOf(SEEDED_TIME))
        planIntakes()
    }

    /** Карточка до правки: названо посеянное время и посеянная доза; так же выглядит и день. */
    private fun seeTheSeededSchedule() {
        openMedications()
        openMedicationCard(name)
        waitUntil(SCREEN_TIMEOUT_MS, "посеянного расписания «$SEEDED_LABEL» в карточке") {
            textExists(SEEDED_LABEL) && textAppears(dosePhrase(SEEDED_DOSE))
        }
        assertEquals("the seeded day must hold the morning intake", listOf(SEEDED_TIME), dayTimes())
        capture("card-seeded")
    }

    /** Форма правки открыта заполненной — прежним названием и прежним временем, а не пустой. */
    private fun takeTheFilledFormAndEditIt() {
        click(TAG_EDIT)
        waitForTag(TAG_MEDICATION_FORM)
        waitUntil(SCREEN_TIMEOUT_MS, "заполненной формы правки") {
            textExists(name) && textExists(SEEDED_LABEL)
        }
        capture("form-filled")

        replaceText(TAG_DOSE_FIELD, NEW_DOSE)
        pickTime(NEW_HOUR, NEW_MINUTE)
        waitUntil(SCREEN_TIMEOUT_MS, "строки времени «$NEW_LABEL» в форме") { textExists(NEW_LABEL) }
        capture("form-edited")

        click(TAG_SAVE_BUTTON)
    }

    /** Карточка после сохранения: новое время и новая доза; день перепланирован под них. */
    private fun saveAndSeeTheNewSchedule() {
        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        waitUntil(SCREEN_TIMEOUT_MS, "нового расписания «$NEW_LABEL» в карточке") {
            textExists(NEW_LABEL) && textAppears(dosePhrase(NEW_DOSE))
        }
        capture("card-edited")
        assertScheduleMoved()
        assertTrue("the edit of the schedule must reach the log", opLogHas(OpEvent.MEDICATION_UPDATED))
    }

    /** День после правки: приём на 09:30 ожидается, посеянного 08:00 в дне нет. */
    private fun seeTheDayWithTheNewTime() {
        pressBack()
        openToday()
        waitUntil(SCREEN_TIMEOUT_MS, "ожидающегося приёма на «$NEW_LABEL» в дне") {
            textExists(name) && textExists(NEW_LABEL) && !textExists(SEEDED_LABEL)
        }
        capture("today-after-edit")
    }

    /** Правка, из которой человек ушёл, не сохраняя: расписание и доза остались прежними. */
    private fun leaveTheLastEditUnsaved() {
        openMedications()
        openMedicationCard(name)
        click(TAG_EDIT)
        waitForTag(TAG_MEDICATION_FORM)
        waitUntil(SCREEN_TIMEOUT_MS, "черновика правки с расписанием «$NEW_LABEL»") {
            textExists(NEW_LABEL)
        }
        replaceText(TAG_DOSE_FIELD, UNSAVED_DOSE)
        capture("form-unsaved")

        pressBack()
        // RT-1: грязная форма спрашивает подтверждение; «сбросить» закрывает её без записи.
        waitForTag(TAG_CONFIRM_DIALOG)
        click(TAG_CONFIRM_CONFIRM)
        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        assertTrue(
            "leaving the form must keep the saved schedule and dose",
            textExists(NEW_LABEL) && textAppears(dosePhrase(NEW_DOSE)),
        )
        assertTrue("the dose left in the form must not reach the card", !textAppears(dosePhrase(UNSAVED_DOSE)))
        capture("card-unchanged")
        assertScheduleMoved()
    }

    /**
     * Смена времени настоящим тайм-пикером: строка времени → диалог → клавиатурный ввод.
     *
     * Поля клавиатурного ввода живут под одним тегом [TAG_TIME_INPUT], поэтому адресуются
     * порядком дерева — часы, затем минуты. Ввод идёт заменой текста поля, а не набором
     * в конец: у пикера своя проверка значения, и дописанное «0809» она отвергает.
     */
    private fun pickTime(hour: String, minute: String) {
        scrollToAndClick(TAG_TIME_ROW)
        waitForTag(TAG_DATE_TIME_PICKER)
        compose.onNodeWithText(ui(UiR.string.cd_switch_time_input), useUnmergedTree = true).performClick()
        waitForTag(TAG_TIME_INPUT)
        capture("time-picker", compose.onNode(isDialog()))

        val fields = compose.onAllNodes(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_TIME_INPUT)),
            useUnmergedTree = true,
        )
        assertEquals(
            "the keyboard input must offer the hour and the minute",
            2,
            fields.fetchSemanticsNodes().size,
        )
        fields[0].performTextReplacement(hour)
        fields[1].performTextReplacement(minute)
        compose.onNodeWithText(ui(UiR.string.action_done), useUnmergedTree = true).performClick()
    }

    /**
     * День похода держит только новое время: посеянное 08:00 перепланирование сняло.
     *
     * Перепланирование идёт вслед за записью курса, поэтому состояние дня ждётся, а не
     * читается сразу: карточка показывает новое расписание раньше, чем план приёмов готов.
     */
    private fun assertScheduleMoved() {
        waitUntil(SCREEN_TIMEOUT_MS, "перепланирования дня и следующего дня под «$NEW_LABEL»") {
            timesOf(dayIntakes()) == listOf(NEW_TIME) &&
                timesOf(dayIntakes(DeviceTestClock.today.plusDays(1))) == listOf(NEW_TIME)
        }
        assertTrue("the new intake must be expected", dayIntakes().single().isPending)
        assertEquals("the day must hold exactly the new time", listOf(NEW_TIME), dayTimes())
        assertEquals(
            "the whole schedule must move, not only the edited day",
            listOf(NEW_TIME),
            timesOf(dayIntakes(DeviceTestClock.today.plusDays(1))),
        )
    }

    /** Времена приёмов дня в порядке назначения: состояние, а не картинка. */
    private fun dayTimes(): List<LocalTime> = timesOf(dayIntakes())

    private fun timesOf(intakes: List<Intake>): List<LocalTime> = intakes.map { it.scheduledAt.toLocalTime() }

    /**
     * «по 2 таблетки» — фраза дозы в сводке расписания. Собирается из ресурсов продукта:
     * склонение зависит от числа, и литерал в проверке разошёлся бы с показанным человеку.
     */
    private fun dosePhrase(amount: String): String = targetContext.resources.getQuantityString(
        MedsR.plurals.card_dose_tablets,
        amount.toInt(),
        amount,
    )

    /** Показанный текст содержит подстроку: фраза дозы — часть одной строки сводки расписания. */
    private fun textAppears(text: String): Boolean = compose
        .onAllNodesWithText(text, substring = true, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()

    private companion object {
        const val MEDICATION_ID = "journey-edit"
        const val SEEDED_DOSE = "1"
        const val NEW_DOSE = "2"
        const val UNSAVED_DOSE = "5"
        const val SEEDED_LABEL = "08:00"
        const val NEW_LABEL = "09:30"
        const val NEW_HOUR = "09"
        const val NEW_MINUTE = "30"
        val SEEDED_TIME: LocalTime = LocalTime.of(8, 0)
        val NEW_TIME: LocalTime = LocalTime.of(9, 30)

        /** Момент правки: утро, до посеянного времени приёма (см. KDoc похода). */
        val EDIT_MOMENT: LocalTime = LocalTime.of(6, 0)
    }
}
