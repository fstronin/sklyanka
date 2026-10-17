package ru.stohastika.sklyanka.screen

import android.content.Intent
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.feature.reminder.ReminderActivity
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_SKIP
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_SNOOZE
import ru.stohastika.sklyanka.feature.reminder.TAG_ACTION_TAKEN
import ru.stohastika.sklyanka.feature.reminder.TAG_REMINDER
import ru.stohastika.sklyanka.reminder.ReminderActionReceiver
import ru.stohastika.sklyanka.screen.support.DeviceActivities
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.test.R

/**
 * L — карточка напоминания и три её действия (SKL-98, TDD §6).
 *
 * Карточка открывается тем же намерением, что ставит будильник, и показывает состав
 * слота. Следствия действий проверяются в данных через тестовый граф: «Принято» —
 * `TAKEN`, «Пропустить» — `SKIPPED`, «Отложить» — приём остаётся `PENDING`,
 * а отложенная запись получает показ `0` и момент в будущем.
 *
 * Три проверки — три свежих состояния: после состоявшейся отметки слот пуст,
 * и повторно открыть его нечем.
 */
@HiltAndroidTest
class ReminderCardDeviceTest : DeviceScreenTest() {

    private val name: String get() = testContext.getString(R.string.device_test_medication_reminder)

    @Test
    fun theTakenActionMarksTheIntake() {
        seedPendingIntake()
        openCard()
        capture("reminder-card")

        click(TAG_ACTION_TAKEN)
        awaitScreenClosed(ReminderActivity::class.java)

        assertEquals(IntakeStatus.TAKEN, currentIntake().status)
    }

    @Test
    fun theSnoozeActionDefersTheFiring() {
        seedPendingIntake()
        openCard()

        click(TAG_ACTION_SNOOZE)
        awaitScreenClosed(ReminderActivity::class.java)

        val intake = currentIntake()
        assertEquals(IntakeStatus.PENDING, intake.status)
        val deferred = requireNotNull(DeviceTestSeed.deferredFor(targetContext, intake.id)) {
            "the snooze must leave a deferred record"
        }
        assertEquals("manual snooze is the first attempt", 0, deferred.attempt)
        assertTrue(
            "the firing must move forward",
            deferred.fireAt.isAfter(DeviceTestClock.source.now()),
        )
    }

    @Test
    fun theSkipActionMarksTheIntakeSkipped() {
        seedPendingIntake()
        openCard()

        click(TAG_ACTION_SKIP)
        awaitScreenClosed(ReminderActivity::class.java)

        assertEquals(IntakeStatus.SKIPPED, currentIntake().status)
    }

    /**
     * Системная «Назад» с карточки — выход без потерь и без побочного отложения: приём
     * остаётся `PENDING`, отложенной записи нет, слот не израсходован и карточка
     * открывается повторно тем же намерением. Находка теста релиза: карточка не должна
     * становиться путём, из которого человек выходит потерянным напоминанием.
     */
    @Test
    fun theBackDismissKeepsTheIntakePending() {
        seedPendingIntake()
        openCard()

        pressBack()
        awaitScreenClosed(ReminderActivity::class.java)

        val intake = currentIntake()
        assertEquals(IntakeStatus.PENDING, intake.status)
        assertNull(
            "dismissal must not defer the firing",
            DeviceTestSeed.deferredFor(targetContext, intake.id),
        )

        openCard()
    }

    /**
     * Действие «Принято» из шторки отмечает приём, не открывая карточку (RT-4, Р-87 п. 3).
     *
     * Действие уведомления — явный broadcast в [ReminderActionReceiver], а не активность:
     * отметка идёт тем же сценарием, что и карточка (единственная точка отметки), но карточка
     * не поднимается, а уведомление снимается последствием отметки (`DismissReminderUseCase`).
     * Здесь проверяется сам приёмник на настоящем устройстве: отметка случается через боевой
     * граф и карточка не открывается. Состав уведомления (три действия) стережёт unit-тест
     * построителя (`ReminderNotifierActionsTest`).
     */
    @Test
    fun theShadeTakenActionMarksTheIntakeWithoutOpeningTheCard() {
        seedPendingIntake()
        val intake = currentIntake()

        val action = Intent(targetContext, ReminderActionReceiver::class.java)
            .setAction(ReminderActionReceiver.ACTION_TAKEN)
            .putExtra(ReminderActionReceiver.EXTRA_INTAKE_IDS, arrayOf(intake.id))
        targetContext.sendBroadcast(action)

        waitUntil(SCREEN_TIMEOUT_MS, "отметки приёма действием из шторки") {
            currentIntake().status == IntakeStatus.TAKEN
        }
        assertTrue(
            "marking from the shade must not open the card",
            DeviceActivities.resumedActivity() !is ReminderActivity,
        )
    }

    private fun seedPendingIntake() {
        seedMedication(id = MEDICATION_ID, name = name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()
    }

    private fun openCard() {
        launchScreen(ReminderActivity.intentFor(targetContext, ownerProfile.id, DeviceTestClock.scheduledAt))
        awaitActivity(ReminderActivity::class.java)
        waitForTag(TAG_REMINDER)
        waitUntil(SCREEN_TIMEOUT_MS, "состава карточки «$name»") { textExists(name) }
    }

    private fun currentIntake(): Intake =
        DeviceTestSeed.dayIntakes(targetContext, ownerProfile.id, DeviceTestClock.today)
            .single { it.medicationId == MEDICATION_ID }

    private companion object {
        const val MEDICATION_ID = "dev-med-reminder"
    }
}
