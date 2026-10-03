package ru.stohastika.sklyanka.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertFalse
import org.junit.Test
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActivity
import ru.stohastika.sklyanka.feature.onboarding.TAG_DONE
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.onboarding.R as OnboardingR

/**
 * J — экран разрешений из настроек (SKL-98, TDD §6).
 *
 * Повторный вход: источник `SETTINGS` — значит, индикатора шагов онбординга на экране
 * нет (`PermissionsScreen.kt:144`), а видимый выход — «Готово», закрывающее Activity.
 *
 * Индикатор — графика без видимого текста: единственный его адрес — подпись для чтения
 * с экрана (`cd_onboarding_steps`), по ней и утверждается отсутствие. `TAG_STEPS_LIST` —
 * это список шагов, и он присутствует на любом входе: утверждать по нему нельзя
 * (находка SKL-98).
 */
@HiltAndroidTest
class PermissionsFromSettingsDeviceTest : DeviceScreenTest() {

    @Test
    fun theSettingsEntryShowsAndClosesTheScreen() {
        launchScreen(PermissionsActivity.intentFor(targetContext, OnboardingSource.SETTINGS))
        awaitActivity(PermissionsActivity::class.java)

        waitForTag(TAG_PERMISSIONS_SCREEN)
        waitForTag(TAG_DONE)
        assertFalse("the onboarding step indicator must stay on the first-launch screen", stepIndicatorExists())
        capture("permissions-settings")

        click(TAG_DONE)
        awaitScreenClosed(PermissionsActivity::class.java)
    }

    /** Индикатор шагов виден как подпись «Шаг N из M»: ищем узлы с этой подписью. */
    private fun stepIndicatorExists(): Boolean {
        val sample = targetContext.getString(OnboardingR.string.cd_onboarding_steps, 0, 0)
        val labelPrefix = sample.substringBefore('0').ifBlank { sample }
        val indicator = SemanticsMatcher("onboarding step indicator") { node ->
            node.config
                .getOrNull(SemanticsProperties.ContentDescription)
                .orEmpty()
                .any { label -> label.startsWith(labelPrefix) }
        }
        return compose.onAllNodes(indicator, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
}
