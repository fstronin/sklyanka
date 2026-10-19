package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActivity
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_APP_STARTUP
import ru.stohastika.sklyanka.feature.onboarding.TAG_DONE
import ru.stohastika.sklyanka.feature.onboarding.TAG_GRANTED_MARK
import ru.stohastika.sklyanka.feature.onboarding.TAG_NEXT
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.onboarding.TAG_STEPS_LIST
import ru.stohastika.sklyanka.feature.onboarding.TAG_UNVERIFIABLE
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsActivity
import ru.stohastika.sklyanka.feature.reminder.TAG_RESTART_SETUP
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_EMPTY
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.startup.TAG_PRIVACY_CONSENT
import ru.stohastika.sklyanka.startup.TAG_PRIVACY_CONSENT_AGREE
import ru.stohastika.sklyanka.feature.settings.R as SettingsR
import ru.stohastika.sklyanka.feature.today.R as TodayR

/**
 * Поход «Первый запуск и настройка надёжности» (SKL-140, реестр походов № 1).
 *
 * Человек берёт телефон впервые: приложение начинается экраном «Настроить надёжность», он
 * проходит его и оказывается на «Сегодня» с пустым днём. Потом он заходит на тот же экран
 * из настроек повторно и возвращается на «Сегодня».
 *
 * Отметку «онбординг пройден» поход НЕ сеет ([seedDefaultState] пуст): первым запуском он
 * и начинается, и сид базы скрыл бы ровно тот экран, который поход проверяет. Владельца
 * база сеет сама.
 *
 * Экран разрешений при первом запуске живёт внутри `MainActivity` (маршрут `FIRST_LAUNCH`),
 * а не в отдельной активности. Строка «Разрешения и автозапуск» в настройках ведёт
 * на «Проверку напоминаний» ([DiagnosticsActivity]) — это факт продукта (`ОВ-48`: сначала
 * диагностика, потом онбординг), а не обход, — и уже оттуда «Пройти настройку заново»
 * открывает [PermissionsActivity] с той же картой шагов.
 */
@HiltAndroidTest
class FirstLaunchJourneyTest : JourneyTest() {

    override fun seedDefaultState() {
        // Первый запуск: отметки «онбординг пройден» нет, иначе экрана разрешений не будет.
    }

    @Test
    fun thePersonPassesOnboardingAndReopensReliabilityFromSettings() {
        launchApp()
        passFirstLaunchOnboarding()
        assertEmptyToday()
        reopenReliabilityFromSettings()
        returnToToday()
    }

    /** Первый запуск: политика → экран разрешений → «Дальше» → онбординг пройден. */
    private fun passFirstLaunchOnboarding() {
        // Не awaitRoot(): на первом запуске раньше «Сегодня» видны диалог политики
        // и экран разрешений.
        waitUntil(ROOT_TIMEOUT_MS, "диалога политики при первом запуске") { exists(TAG_PRIVACY_CONSENT) }
        capture("privacy-first-launch", compose.onNode(isDialog()))
        click(TAG_PRIVACY_CONSENT_AGREE)
        waitUntil(ROOT_TIMEOUT_MS, "исчезновения диалога политики") { !exists(TAG_PRIVACY_CONSENT) }

        waitUntil(ROOT_TIMEOUT_MS, "экрана разрешений при первом запуске") { exists(TAG_PERMISSIONS_SCREEN) }
        waitForTag(TAG_STEPS_LIST)
        capture("permissions-first-launch")

        // Выданные разрешения названы выданными: уведомления выданы стендом, точные будильники
        // разрешены установочно (ADR-0003), и такой шаг помечен «Есть».
        assertTrue("granted permissions must be named granted", exists(TAG_GRANTED_MARK))
        // Вендорские шаги (на EMUI их три) система проверить не может и называет непроверяемыми.
        // На образе API 35 их нет вовсе, поэтому сверяем их только там, где они показаны.
        if (exists(TAG_CARD_APP_STARTUP)) {
            assertTrue("vendor steps must be named unverifiable", exists(TAG_UNVERIFIABLE))
        }

        click(TAG_NEXT)
    }

    /** «Сегодня» в пустом состоянии: призыв завести лекарство и ни одного приёма. */
    private fun assertEmptyToday() {
        waitForTag(TAG_TODAY_SCREEN)
        waitForTag(TAG_TODAY_EMPTY)
        assertTrue(
            "the empty day must invite the person to add a medication",
            textExists(ui(TodayR.string.today_empty_action)),
        )
        assertTrue("the first-run day must hold no intakes", dayIntakes().isEmpty())
        capture("today-empty")
    }

    /** Повторный вход из настроек: строка → «Проверка напоминаний» → та же карта шагов. */
    private fun reopenReliabilityFromSettings() {
        openSettings()
        val permissionsRow = ui(SettingsR.string.settings_permissions)
        compose.onNodeWithText(permissionsRow).performScrollTo().performClick()
        awaitActivity(DiagnosticsActivity::class.java)
        scrollToAndClick(TAG_RESTART_SETUP)

        awaitActivity(PermissionsActivity::class.java)
        waitForTag(TAG_PERMISSIONS_SCREEN)
        waitForTag(TAG_STEPS_LIST)
        // Источник `SETTINGS`: индикатора шагов нет, видимый выход — «Готово».
        waitForTag(TAG_DONE)
        capture("permissions-from-settings")
    }

    /** Возврат: с экрана разрешений «Назад» — на диагностику, оттуда — в настройки и на «Сегодня». */
    private fun returnToToday() {
        pressBack()
        awaitActivity(DiagnosticsActivity::class.java)
        pressBack()
        waitForTag(TAG_SETTINGS_SCREEN)
        openToday()
        waitForTag(TAG_TODAY_SCREEN)
        waitForTag(TAG_TODAY_EMPTY)
        capture("today-again")
    }
}
