package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckRule
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckActivity
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsActivity
import ru.stohastika.sklyanka.feature.reminder.TAG_ALL_GRANTED
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_ACKNOWLEDGE
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_BLOCK
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_DELIVERY
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_FIX
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_RESULT
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_RESULT_ACKNOWLEDGE
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_RUNNING
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_SHEET
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_SHEET_TEXT
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_START
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_TIME
import ru.stohastika.sklyanka.feature.reminder.TAG_DELIVERY_CHECK
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import ru.stohastika.sklyanka.feature.reminder.TAG_PERMISSIONS
import ru.stohastika.sklyanka.feature.reminder.TAG_PERMISSION_REST_GRANTED
import ru.stohastika.sklyanka.feature.reminder.TAG_REMINDER
import ru.stohastika.sklyanka.feature.reminder.TAG_VERDICT
import ru.stohastika.sklyanka.feature.reminder.TAG_WEEK_SUMMARY
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import java.time.Duration
import ru.stohastika.sklyanka.feature.reminder.R as ReminderR
import ru.stohastika.sklyanka.feature.settings.R as SettingsR

/**
 * Поход «Проверка напоминаний и самопроверка доставки» (SKL-140, реестр походов № 11).
 *
 * Человек заходит в «Проверку напоминаний» строкой настроек «Разрешения и автозапуск»
 * (ОВ-49), читает первый уровень — вердикт надёжности, разрешения и сводку недели (ОВ-126), —
 * открывает шторку запуска самопроверки, читает требование закрыть приложение и нажимает
 * единственную кнопку запуска. Дальше он видит тестовое срабатывание и исход, названный
 * в блоке «Проверка доставки».
 *
 * **Время приёма — стендовое [DeviceTestClock.scheduledTime] (09:00), а не «08:00» сценария.**
 * Базовый момент стенда стоит минутой позже него (09:01), поэтому приём уже наступил и ещё
 * не считается несостоявшимся (`OutageDetector.GRACE` — две минуты). При 08:00 приём
 * оказывается за запасом, срабатывания в журнале нет, и продукт по правилу перерыва
 * (ОВ-132, AC-5) ставит первым не вердикт, а баннер `TAG_OUTAGE`: сценарий же обещает
 * вердикт надёжности, и подменять его перерывом значило бы ослабить заявление. Расхождение
 * уже названо походом остатка (`StockJourneyTest`).
 *
 * **«Всё в порядке» и «Разрешения выданы» — разные поверхности.** Вердикт надёжности —
 * строка `TAG_VERDICT` («Всё в порядке», `diagnostics_reliable`): она стоит первой, только
 * когда нет ни причины ненадёжности, ни перерыва, ни неподтверждённого провала проверки
 * (ОВ-130, AC-2). Строка `TAG_ALL_GRANTED` («Разрешения выданы») — футер карточки
 * разрешений, когда исправлять нечего; она говорит про права, а не про надёжность
 * напоминаний. На образе API 35 автозапуск и доступ к политике «Не беспокоить» не читаются
 * («проверьте вручную»), поэтому исправлять есть что и футер называет выданными остальные
 * (`TAG_PERMISSION_REST_GRANTED`) — а вердикт остаётся «Всё в порядке». Поход утверждает обе
 * поверхности по отдельности.
 *
 * **Тестовое срабатывание предъявляется карточкой продукта, а не доставкой уведомления.**
 * Фактическая доставка требует системного времени и прошивки и походом не автоматизируется
 * (`TDD.md` §8): поход открывает [DeliveryCheckActivity] её же намерением — ту самую карточку,
 * которую приносит полноэкранное уведомление, — и убеждается, что она не притворяется приёмом:
 * карточки приёма на экране нет (ОВ-108).
 *
 * **Исход называет продукт, а не поход.** Блок «Проверка доставки» говорит об исходе словами
 * продукта; каким именно — решает журнал проверки: сработавшее срабатывание либо закрытие
 * истёкшей проверки при входе (`FinishDeliveryCheckOnEntryUseCase`). Поэтому, предъявив
 * срабатывание, поход двигает управляемые часы за назначенный момент (`DeliveryCheckRule.DELAY`)
 * и заходит на экран снова — так же, как человек, вернувшийся к проверке позже, — и сверяет,
 * что назван один из словарей исходов продукта.
 *
 * **Проверка не приём.** Доказательство — не картинка, а состояние: приём дня остаётся
 * неотмеченным, в журнале операций нет ни одной отметки, а карточки приёма за весь поход
 * не показывалось ни разу (ОВ-108, `TDD.md` §11).
 */
@HiltAndroidTest
class DiagnosticsJourneyTest : JourneyTest() {

    @Test
    fun thePersonRunsTheDeliverySelfCheckAndTheAppNamesTheResult() {
        seedMedication(id = MEDICATION_ID, name = name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()

        launchApp()
        awaitRoot()
        openSettings()
        openDiagnostics()
        assertTheFirstLevel()

        openTheSelfCheck()
        startTheSelfCheck()
        presentTheTestFiring()
        assertTheOutcomeIsNamed()
        assertNothingWasMarked()
    }

    /**
     * Первый уровень: вердикт надёжности, разрешения и сводка недели (ОВ-126).
     *
     * Утверждается именно строка «Всё в порядке» (`TAG_VERDICT`, ОВ-130, AC-2) — признак
     * того, что причинам ненадёжности и перерыву взяться неоткуда. Футер разрешений
     * («Разрешения выданы» либо «Остальные разрешения выданы») — другая поверхность:
     * он говорит про права, и его наличие вердикта не заменяет.
     */
    private fun assertTheFirstLevel() {
        waitUntil(SCREEN_TIMEOUT_MS, "вердикта «Всё в порядке»") { exists(TAG_VERDICT) }
        waitForTag(TAG_PERMISSIONS)
        assertTrue(
            "the permissions card must name the granted permissions",
            exists(TAG_ALL_GRANTED) || exists(TAG_PERMISSION_REST_GRANTED),
        )
        capture("diagnostics")

        scrollTo(TAG_DIAGNOSTICS, TAG_WEEK_SUMMARY)
        capture("diagnostics-week")
    }

    /** Шторка запуска: сказано закрыть приложение и предложена одна кнопка запуска (ОВ-108). */
    private fun openTheSelfCheck() {
        scrollTo(TAG_DIAGNOSTICS, TAG_CHECK_BLOCK)
        click(TAG_CHECK_DELIVERY)
        waitForTag(TAG_CHECK_SHEET)
        assertEquals(
            "the sheet must tell the person to close the app",
            ui(ReminderR.string.diagnostics_check_sheet_text),
            nodeText(TAG_CHECK_SHEET_TEXT),
        )
        assertTrue("the sheet must offer the start button", exists(TAG_CHECK_START))
        assertEquals("the sheet must offer exactly one action", 1, sheetActions())
        capture("check-sheet")
    }

    /** Запуск: шторка закрыта, блок ушёл из исходного состояния — «идёт» или называет исход. */
    private fun startTheSelfCheck() {
        click(TAG_CHECK_START)
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия шторки запуска") { !exists(TAG_CHECK_SHEET) }
        // Список собирается заново: до блока самопроверки прокручивают снова.
        scrollTo(TAG_DIAGNOSTICS, TAG_CHECK_BLOCK)
        waitUntil(SCREEN_TIMEOUT_MS, "хода самопроверки") { exists(TAG_CHECK_RUNNING) || exists(TAG_CHECK_RESULT) }
        assertFalse("the start action must be gone once the check has begun", exists(TAG_CHECK_DELIVERY))
    }

    /** Тестовое срабатывание: карточка проверки, а не карточка приёма (ОВ-108). */
    private fun presentTheTestFiring() {
        launchScreen(DeliveryCheckActivity.intentFor(targetContext, DeviceTestClock.scheduledAt))
        awaitActivity(DeliveryCheckActivity::class.java)
        waitForTag(TAG_DELIVERY_CHECK)
        waitForTag(TAG_CHECK_TIME)
        assertTrue(
            "the check card must carry the test wording",
            textExists(ui(ReminderR.string.reminder_check_text)),
        )
        assertFalse("the check card must not be an intake card", exists(TAG_REMINDER))
        capture("check-card")
        click(TAG_CHECK_ACKNOWLEDGE)
        awaitScreenClosed(DeliveryCheckActivity::class.java)
    }

    /**
     * Исход в «Проверке напоминаний»: часы уходят за момент срабатывания, и экран,
     * открытый заново тем же путём, называет исход словами продукта (ОВ-108).
     */
    private fun assertTheOutcomeIsNamed() {
        DeviceTestClock.advanceBy(DeliveryCheckRule.DELAY.plus(MARGIN))
        reopenDiagnostics()
        scrollTo(TAG_DIAGNOSTICS, TAG_CHECK_BLOCK)
        waitForTag(TAG_CHECK_RESULT)
        val named = nodeText(TAG_CHECK_RESULT)
        assertTrue("the block must name one of the product outcomes, got «$named»", namesAnOutcome(named))
        assertTrue(
            "a named outcome must offer its action",
            exists(TAG_CHECK_RESULT_ACKNOWLEDGE) || exists(TAG_CHECK_FIX),
        )
        assertFalse("the start action must be gone once the outcome is named", exists(TAG_CHECK_DELIVERY))
        capture("check-result")
    }

    /** Проверка не приём: приём дня не отмечен, отметки нет, карточки приёма не было (ОВ-108). */
    private fun assertNothingWasMarked() {
        val today = dayIntakes()
        assertEquals(
            "the day must hold the seeded intake",
            1,
            today.count { it.scheduledAt.toLocalTime() == DeviceTestClock.scheduledTime },
        )
        assertTrue(
            "no intake may be marked by the self-check, got ${today.map { it.status }}",
            today.none { it.status == IntakeStatus.TAKEN },
        )
        assertEquals("the self-check must leave no mark in the operation log", 0, opLogCount(OpEvent.INTAKE_MARKED))
        assertFalse("the self-check must not show the intake card", exists(TAG_REMINDER))
    }

    /** Вход в «Проверку напоминаний» из настроек: строка называется «Разрешения и автозапуск». */
    private fun openDiagnostics() {
        clickRow(ui(SettingsR.string.settings_permissions))
        awaitActivity(DiagnosticsActivity::class.java)
        waitForTag(TAG_DIAGNOSTICS)
    }

    /** Выйти с «Проверки напоминаний» и зайти снова — так человек возвращается к экрану позже. */
    private fun reopenDiagnostics() {
        // Карточка проверки живёт в своей задаче: возврат с неё возобновляет экран под ней.
        awaitActivity(DiagnosticsActivity::class.java)
        pressBack()
        waitForTag(TAG_SETTINGS_SCREEN)
        openDiagnostics()
    }

    /**
     * Строка настроек адресуется названием: её тег внутренний для `feature-settings`
     * и снаружи модуля не виден. К строке прокручивают контейнером — нижние строки
     * ленивого списка не компонуются, пока к ним не прокрутили (`TDD.md` §7).
     */
    private fun clickRow(text: String) {
        compose.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag(TAG_SETTINGS_SCREEN)),
            useUnmergedTree = true,
        ).performScrollToNode(hasText(text))
        compose.onNodeWithText(text, useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    /** Действия шторки: их число и есть ответ на «предложена одна кнопка запуска» (ОВ-108). */
    private fun sheetActions(): Int = compose.onAllNodes(
        hasClickAction() and hasAnyAncestor(hasTestTag(TAG_CHECK_SHEET)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size

    /**
     * Названный исход — из словаря продукта: своих слов у похода нет.
     *
     * Успех блока звучит «Последняя проверка пройдена…», а не «Проверка доставки пройдена…»:
     * это разные поверхности (ОВ-131). Фразу опоздания продукт собирает склоняемой формой
     * минут (НФТ-44), поэтому у неё сверяется начало, а не вся фраза.
     */
    private fun namesAnOutcome(named: String): Boolean {
        val words = listOf(
            ui(ReminderR.string.diagnostics_check_delivered),
            ui(ReminderR.string.diagnostics_check_not_delivered),
            ui(ReminderR.string.diagnostics_check_not_completed),
        )
        val late = targetContext.getString(ReminderR.string.diagnostics_check_late, LATENESS_MARK)
        return named in words || named.startsWith(late.substringBefore(LATENESS_MARK))
    }

    private val name: String get() = testContext.getString(R.string.device_test_medication_name, "journey-diagnostics")

    private companion object {
        /** Запас поверх срока срабатывания: управляемые часы обязаны уйти за назначенный момент. */
        val MARGIN: Duration = Duration.ofSeconds(30)

        /** Метка подстановки в фразе опоздания: по ней от фразы отделяется начало. */
        const val LATENESS_MARK = "\u0000"

        const val MEDICATION_ID = "journey-diagnostics-med"
    }
}
