package ru.stohastika.sklyanka.acceptance.reminder

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.onboarding.OnboardingAppProbe
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckReason
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_SHEET
import ru.stohastika.sklyanka.feature.reminder.TAG_DIAGNOSTICS

/**
 * Шаги приёмки второй точки входа самопроверки (SKL-86, ОВ-108, группа `@reliability`).
 *
 * Живут в `app`, а не в `feature-onboarding`, как записано в комментарии сценария:
 * `feature-onboarding` не видит «Проверку напоминаний» и не может её собрать
 * (`ADR-0007`), а композиция `app` видит оба экрана сразу — только там переход
 * «плашка → экран с поднятой шторкой → запуск» проверяется целиком.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class DeliveryCheckCompositionSteps(private val world: AcceptanceWorld) {

    @Когда("^человек нажимает «Проверить доставку» в плашке «Всё настроено» на экране «Настроить надёжность»$")
    fun hePressesCheckDeliveryInThePlaque() {
        OnboardingAppProbe.open(world, OnboardingSource.SETTINGS)
        OnboardingAppProbe.pressCheckDelivery(world)
        assertTrue(
            OnboardingAppProbe.checkDeliveryOpened,
            "the action must lead to the reminders check screen, not open a second copy of the sheet",
        )
    }

    @Тогда("^открывается экран «Проверка напоминаний»$")
    fun theCheckScreenOpens() {
        assertTrue(OnboardingAppProbe.checkDeliveryOpened, "the app must open the checks screen")
        OnboardingAppProbe.askCheckScreen(world) { onNodeWithTag(TAG_DIAGNOSTICS).assertIsDisplayed() }
    }

    @И("^шторка «Проверка доставки» уже открыта$")
    fun theSheetIsAlreadyOpen() {
        assertTrue(
            OnboardingAppProbe.diagnosticsState(world).checkSheetOpen,
            "the entry must raise the delivery check sheet",
        )
        OnboardingAppProbe.askCheckScreen(world) { onNodeWithTag(TAG_CHECK_SHEET).assertIsDisplayed() }
    }

    @И("^«Начать проверку» ставит тестовое срабатывание тем же путём$")
    fun startPutsTheTestFiringTheSameWay() {
        OnboardingAppProbe.startCheckFromSheet(world)
        val dueAt = world.reminders.deliveryCheckAlarm()
        assertNotNull(dueAt, "the start must arm the test firing")
        assertEquals(world.clock.now().plusSeconds(90), dueAt, "the same 90 second lead as the check screen")
        assertTrue(
            world.reminders.deliveryCheckRecords().any { it.reason == DeliveryCheckReason.SCHEDULED },
            "the same scheduled record as the check screen",
        )
    }
}
