package ru.stohastika.sklyanka.screen

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsActivity
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_NOW
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import ru.stohastika.sklyanka.feature.reminder.TAG_JOURNAL_ROW
import ru.stohastika.sklyanka.feature.reminder.TAG_RESCHEDULE
import ru.stohastika.sklyanka.feature.reminder.TAG_SUPPORT_TOGGLE
import ru.stohastika.sklyanka.feature.reminder.TAG_WEEK_SUMMARY
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed

/**
 * I — диагностика напоминаний (SKL-98, TDD §6).
 *
 * Экран открывается прямым намерением продукта, «Проверить сейчас» пересобирает окно
 * и оставляет в журнале запись с причиной «вручную», недельная сводка видна.
 * Раскрытие «Для поддержки» показывает технический блок с журналом.
 *
 * Экспорт журнала через SAF проверкой не запускается: на `aosp_atd` нет `DocumentsUI`
 * (задача later, `tech-options.md` §3). Состояния предупреждений о разрешениях
 * не утверждаются — они зависят от образа, а не от сценария.
 */
@HiltAndroidTest
class DiagnosticsDeviceTest : DeviceScreenTest() {

    @Test
    fun theCheckNowReschedulesAndKeepsTheWeekSummary() {
        launchScreen(DiagnosticsActivity.intentFor(targetContext))
        awaitActivity(DiagnosticsActivity::class.java)

        waitForTag(TAG_DIAGNOSTICS)
        waitForTag(TAG_CHECK_NOW)
        waitForTag(TAG_WEEK_SUMMARY)
        capture("diagnostics")

        click(TAG_CHECK_NOW)
        waitUntil(SCREEN_TIMEOUT_MS, "записи пересборки в журнале") {
            DeviceTestSeed.lastJournalEvent(targetContext, ReminderEvent.RESCHEDULED)
                ?.reason == RescheduleReason.MANUAL.code
        }

        scrollTo(TAG_DIAGNOSTICS, TAG_SUPPORT_TOGGLE)
        click(TAG_SUPPORT_TOGGLE)
        scrollTo(TAG_DIAGNOSTICS, TAG_RESCHEDULE)
        waitForTag(TAG_RESCHEDULE)
        scrollTo(TAG_DIAGNOSTICS, TAG_JOURNAL_ROW)
        capture("diagnostics-support")
    }
}
