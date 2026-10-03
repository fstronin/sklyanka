package ru.stohastika.sklyanka.screen

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertFalse
import org.junit.Test
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckActivity
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_ACKNOWLEDGE
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_TIME
import ru.stohastika.sklyanka.feature.reminder.TAG_DELIVERY_CHECK
import ru.stohastika.sklyanka.feature.reminder.TAG_NAME
import ru.stohastika.sklyanka.feature.reminder.TAG_REMINDER
import ru.stohastika.sklyanka.screen.support.DeviceTestClock

/**
 * M — карточка самопроверки доставки (SKL-98, TDD §6).
 *
 * Тестовое срабатывание показывается той же полноэкранной карточкой, но не притворяется
 * приёмом: состава слота и медицинских данных на ней нет, действие одно — «Понятно»,
 * и оно закрывает Activity.
 */
@HiltAndroidTest
class DeliveryCheckDeviceTest : DeviceScreenTest() {

    @Test
    fun theDeliveryCheckCardIsNotAnIntake() {
        launchScreen(DeliveryCheckActivity.intentFor(targetContext, DeviceTestClock.scheduledAt))
        awaitActivity(DeliveryCheckActivity::class.java)

        waitForTag(TAG_DELIVERY_CHECK)
        waitForTag(TAG_CHECK_TIME)
        assertFalse("the check card must not carry an intake card", exists(TAG_REMINDER))
        assertFalse("the check card must not carry a medication name", exists(TAG_NAME))
        capture("delivery-check")

        click(TAG_CHECK_ACKNOWLEDGE)
        awaitScreenClosed(DeliveryCheckActivity::class.java)
    }
}
