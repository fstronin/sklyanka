package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertFalse
import org.junit.Test
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActivity
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_NOTIFICATIONS
import ru.stohastika.sklyanka.feature.onboarding.TAG_ISSUE
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsActivity
import ru.stohastika.sklyanka.feature.reminder.TAG_ALL_GRANTED
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS
import ru.stohastika.sklyanka.feature.reminder.TAG_OUTAGE
import ru.stohastika.sklyanka.feature.reminder.TAG_PERMISSIONS
import ru.stohastika.sklyanka.feature.reminder.TAG_RESTART_SETUP
import ru.stohastika.sklyanka.feature.reminder.TAG_WARNING
import ru.stohastika.sklyanka.feature.today.TAG_RELIABILITY_BANNER
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.feature.settings.R as SettingsR

/**
 * Поход «Права не выданы» (SKL-140, поход реестра № 13).
 *
 * Единственный поход, который идёт в своей обстановке: разрешения отзывает **скрипт**
 * группы перед этим запуском (`run-journeys.sh`, фаза `not-granted`) — диалог системы
 * автоматизации недоступен, и подделывать состояние разрешений внутри теста нельзя.
 *
 * Продуктовое правило, которое здесь и проверяется: экран разрешений показывается **один раз**
 * (первый запуск или первый отказ), а на повторном запуске с отозванным разрешением человек
 * остаётся на «Сегодня» и видит предупреждение о ненадёжности. Поэтому поход не ждёт
 * автоматического открытия онбординга: он идёт путём человека — видит предупреждение,
 * затем сам открывает состояние надёжности из настроек (через «Проверку напоминаний»,
 * единственный путь к нему из настроек) и там читает, что разрешение не выдано.
 *
 * Чего здесь нет: «будильники не поставлены вовсе» — свойство домена, его держат проверки
 * напоминаний (НАП-*) и испытания И-14/И-15; на стенде право точных будильников и вовсе
 * принадлежит продукту, а не тестовому пакету (TDD §8).
 */
@HiltAndroidTest
class PermissionsNotGrantedJourneyTest : JourneyTest() {

    @Test
    fun thePersonSeesTheUnreliabilityAndReachesTheSetup() {
        // День должен быть не пустым: предупреждение о ненадёжности говорит о том, что
        // напоминание не придёт к запланированному приёму, и без приёма ему не о чем
        // говорить — продукт в пустом дне его и не рисует.
        val name = testContext.getString(R.string.device_test_medication_name, "journey-not-granted")
        seedMedication(id = "jny-not-granted", name = name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()

        launchApp()

        // Разрешение на уведомления не выдано, экран разрешений человек уже видел: продукт
        // не открывает его повторно, а говорит о ненадёжности прямо на «Сегодня».
        waitUntil(ROOT_TIMEOUT_MS, "«Сегодня» с предупреждением") { exists(TAG_TODAY_SCREEN) }
        waitForTag(TAG_RELIABILITY_BANNER)
        capture("today-unreliable")

        // Состояние надёжности из настроек: строка «Разрешения и автозапуск» ведёт в
        // «Проверку напоминаний», оттуда — «Пройти настройку заново».
        openSettings()
        compose.onNodeWithText(ui(SettingsR.string.settings_permissions))
            .performScrollTo()
            .performClick()
        awaitActivity(DiagnosticsActivity::class.java)
        waitForTag(TAG_DIAGNOSTICS)
        waitForTag(TAG_PERMISSIONS)
        assertFalse("the diagnostics screen must not claim every permission is granted", exists(TAG_ALL_GRANTED))
        // Вердикт называет ненадёжность: при невыданном разрешении экран показывает либо
        // перерыв («напоминания не приходили с…»), либо предупреждение о причинах, но не
        // спокойную строку «всё в порядке».
        waitUntil(SCREEN_TIMEOUT_MS, "названной ненадёжности") { exists(TAG_OUTAGE) || exists(TAG_WARNING) }
        capture("diagnostics-not-granted")

        scrollTo(TAG_DIAGNOSTICS, TAG_RESTART_SETUP)
        click(TAG_RESTART_SETUP)
        awaitActivity(PermissionsActivity::class.java)
        waitForTag(TAG_PERMISSIONS_SCREEN)
        waitForTag(TAG_CARD_NOTIFICATIONS)
        // Невыданное разрешение на этом входе помечено не словом «отозвано» (оно у первого
        // запуска), а знаком вопроса: продукт говорит «проверьте вручную», потому что
        // состояние читается из системы, а не помнится.
        waitForTag(TAG_ISSUE)
        capture("permissions-not-granted")
    }
}
