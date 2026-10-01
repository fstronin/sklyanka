package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.feature.onboarding.TAG_EXACT_ALARMS_INDICATOR
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN

/**
 * Экранные шаги приёмки SKL-85: пояснение системного индикатора будильника (ОВ-110).
 *
 * Шаги живут в `feature-onboarding`, потому что говорят о шаге «Точные будильники»:
 * состояние собирает настоящий `OnboardingViewModel` на мире сценария ([PermissionsStand]),
 * а текст берётся из документа приёмки дословно и сверяется с ресурсом экрана.
 */
@OptIn(ExperimentalTestApi::class)
class IndicatorExplanationSteps(private val world: AcceptanceWorld) {

    @Когда("^человек открывает настройку надёжности напоминаний$")
    fun thePersonOpensTheReliabilitySetup() {
        // Вход из настроек: тот же экран, что на первом запуске, но без индикатора шагов
        // (НАП-21). Состояние цепочки читается у системы при открытии.
        PermissionsStand.open(world, OnboardingSource.SETTINGS)
        PermissionsStand.ask(world) { onNodeWithTag(TAG_PERMISSIONS_SCREEN).assertIsDisplayed() }
    }

    @Тогда("^под шагом «([^»]+)» сказано: «(.+)»$")
    fun theStepSays(step: String, text: String) {
        assertEquals("Точные будильники", step, "the scenario speaks about the exact alarms step")
        PermissionsStand.ask(world) {
            onNodeWithTag(TAG_EXACT_ALARMS_INDICATOR).performScrollTo().assertIsDisplayed()
            onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
    }
}
