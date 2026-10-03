package ru.stohastika.sklyanka.acceptance.rollback

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.TAG_ANALYTICS_ROW
import ru.stohastika.sklyanka.startup.StartDestination
import ru.stohastika.sklyanka.startup.StartupFrame
import java.io.File

/**
 * Экранные шаги SKL-100 (группа `@screen`).
 *
 * «Согласие не показывается» проверяется двумя сторонами: продуктовая навигация входа
 * не имеет (структурная проверка исходников), а настоящее решение запуска ведёт в
 * онбординг. «Строки «Аналитика» нет» проверяется на настоящем экране настроек.
 */
@OptIn(ExperimentalTestApi::class)
class AnalyticsRollbackSteps {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Допустим("^установка чистая и решение по аналитике не принималось$")
    fun theInstallIsClean() {
        // Состояние по умолчанию: решения нет и спросить его в релизе нечем (ПМ-31).
    }

    @Когда("^приложение открывается на чистой установке$")
    fun theAppStartsForTheFirstTime() {
        AnalyticsRollbackStand.startFirstLaunch()
    }

    @Тогда("^экран согласия не показывается$")
    fun theConsentScreenIsNotShown() {
        val main = read("app/src/main/kotlin/ru/stohastika/sklyanka/MainActivity.kt")
        assertTrue(
            "вход к экрану согласия обязан быть снят",
            !main.contains("ConsentRoute("),
        )
        assertEquals(
            "чистый запуск обязан вести в онбординг разрешений",
            StartDestination.Onboarding,
            AnalyticsRollbackStand.decision?.destination,
        )
    }

    @Тогда("^первым экраном идёт «Настроить надёжность»$")
    fun theOnboardingIsTheFirstScreen() {
        assertEquals(StartupFrame.FirstScreen, AnalyticsRollbackStand.frame)
        assertEquals(StartDestination.Onboarding, AnalyticsRollbackStand.decision?.destination)
    }

    @Тогда("^после «Настроить надёжность» открывается приложение$")
    fun theAppOpensAfterOnboarding() {
        // Экран разрешений больше не первый; корень удерживает заставку до готовности
        // «Сегодня» — это обычный запуск, а не второй онбординг.
        assertEquals(StartupFrame.AppSplash, AnalyticsRollbackStand.afterOnboarding)
    }

    @Допустим("^человек открыл «Настройки»$")
    fun thePersonOpenedSettings() {
        // Экран компонуется в шаге «читает раздел «Данные»».
    }

    @Когда("^человек читает раздел «Данные»$")
    fun thePersonReadsTheDataSection() {
        AnalyticsRollbackStand.settingsCheck { waitForIdle() }
    }

    @Тогда("^строки «Аналитика» в разделе нет$")
    fun thereIsNoAnalyticsRow() {
        val label = context.getString(R.string.settings_analytics)
        AnalyticsRollbackStand.settingsCheck {
            onAllNodesWithTag(TAG_ANALYTICS_ROW).assertCountEquals(0)
            onAllNodesWithText(label).assertCountEquals(0)
        }
    }

    @Тогда("^переключателя аналитики на экране нет$")
    fun thereIsNoAnalyticsSwitch() {
        AnalyticsRollbackStand.settingsCheck {
            onAllNodesWithTag(TAG_ANALYTICS_ROW).assertCountEquals(0)
        }
    }

    @Тогда("^остальные строки раздела на месте$")
    fun theOtherRowsStay() {
        val history = context.getString(R.string.settings_history)
        val transfer = context.getString(R.string.transfer_title)
        val policy = context.getString(R.string.settings_policy)
        AnalyticsRollbackStand.settingsCheck {
            onNodeWithText(history).assertExists()
            onNodeWithText(transfer).assertExists()
            onNodeWithText(policy).assertExists()
        }
    }

    private fun read(path: String): String = File(RepositoryLayout.root, path).readText()
}
