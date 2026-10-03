package ru.stohastika.sklyanka.acceptance.analytics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentDecision
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentVersion
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.ConsentSource
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.startup.StartDestination

/**
 * Экранные шаги приёмки аналитического согласия (SKL-82, группа `@screen`).
 *
 * Шаги не пересказывают поведение, а исполняют его: экран согласия собирается настоящим
 * `ConsentRoute`, строка настроек — настоящим `SettingsRoute`, а маршрут первого запуска
 * считается настоящим `SplashRoute` (см. [AnalyticsConsentStand]). «До согласия — ничего»
 * проверяется числом событий, дошедших до подменённого sink.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class AnalyticsConsentSteps(private val world: AcceptanceWorld) {

    // --- Первый запуск и решение -------------------------------------------------------------

    @Когда("^человек впервые открывает приложение после установки$")
    fun thePersonOpensTheAppForTheFirstTime() {
        AnalyticsConsentStand.openFirstLaunch()
        assertTrue(
            "первый запуск обязан ждать согласия",
            AnalyticsConsentStand.decision?.consentPending == true,
        )
    }

    @Тогда("^первым экраном первого запуска показывается «Согласие на аналитику»$")
    fun theFirstScreenIsTheConsentScreen() {
        AnalyticsConsentStand.titleIsShown()
    }

    @Тогда("^«Разрешить аналитику» и «Продолжить без аналитики» имеют один габарит$")
    fun bothActionsHaveTheSameSize() {
        val (allow, decline) = AnalyticsConsentStand.buttonBounds()
        assertEquals("кнопки одного габарита по высоте", allow.height, decline.height, 1f)
        assertEquals("кнопки одного габарита по ширине", allow.width, decline.width, 1f)
    }

    @Тогда("^тумблеров и галочек на экране нет$")
    fun thereAreNoSwitchesOrCheckboxes() {
        assertEquals(0, AnalyticsConsentStand.toggleCount())
    }

    @Тогда("^индикатор шагов онбординга разрешений на экран не переносится$")
    fun theOnboardingStepIndicatorIsAbsent() {
        assertEquals(0, AnalyticsConsentStand.stepIndicatorCount())
    }

    @Допустим("^человек видит экран согласия на первом запуске$")
    fun thePersonSeesTheConsentScreenOnTheFirstLaunch() {
        thePersonOpensTheAppForTheFirstTime()
        theFirstScreenIsTheConsentScreen()
    }

    @Когда("^человек нажимает «Разрешить аналитику»$")
    fun thePersonAllowsAnalytics() {
        AnalyticsConsentStand.clickAllow()
        assertTrue("экран обязан закрыться после решения", AnalyticsConsentStand.exited)
    }

    @Тогда("^согласие записывается вместе с версией предмета согласия$")
    fun theConsentIsStoredWithTheMaterialVersion() {
        assertEquals(
            AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL),
            AnalyticsConsentStand.consent(),
        )
    }

    @Тогда("^аналитический контур активируется$")
    fun theAnalyticsContourIsActivated() {
        assertTrue(
            "событие решения доходит до канала только из активированного контура",
            AnalyticsConsentStand.events().any { it is AnalyticsEvent.ConsentShown },
        )
    }

    @Тогда("^после решения человек попадает на экран «Настроить надёжность»$")
    fun thePersonLandsOnTheReliabilitySetup() {
        assertTrue("маршрут обязан уйти с экрана согласия", AnalyticsConsentStand.exited)
        assertEquals(
            "следующий экран первого запуска — настройка надёжности",
            StartDestination.Onboarding,
            AnalyticsConsentStand.decision?.destination,
        )
    }

    @Тогда("^при следующем обычном запуске экран согласия сам не показывается$")
    fun theNextLaunchDoesNotShowTheConsentScreen() {
        assertFalse(AnalyticsConsentStand.nextLaunchDecision().consentPending)
    }

    @Когда("^человек нажимает «Продолжить без аналитики» или системную «Назад»$")
    fun thePersonDeclinesOrPressesBack() {
        // Оба пути обязаны дать один исход: кнопка отказа и системная «Назад».
        AnalyticsConsentStand.reset()
        AnalyticsConsentStand.openFirstLaunch()
        AnalyticsConsentStand.clickDecline()
        assertTrue("кнопка отказа обязана закрыть экран", AnalyticsConsentStand.exited)
        assertEquals(
            AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL),
            AnalyticsConsentStand.consent(),
        )
        assertTrue("первичный отказ не отправляет событий", AnalyticsConsentStand.events().isEmpty())

        AnalyticsConsentStand.reset()
        AnalyticsConsentStand.openFirstLaunch()
        AnalyticsConsentStand.pressSystemBack()
        assertTrue("системная «Назад» обязана закрыть экран", AnalyticsConsentStand.exited)
        assertEquals(
            AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL),
            AnalyticsConsentStand.consent(),
        )
        assertTrue("«Назад» не отправляет событий", AnalyticsConsentStand.events().isEmpty())
    }

    @Тогда("^решение «отказано» записывается без второго вопроса$")
    fun theRefusalIsStored() {
        assertEquals(
            AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL),
            AnalyticsConsentStand.consent(),
        )
    }

    @Тогда("^аналитический контур остаётся выключенным$")
    fun theContourStaysSilent() {
        assertTrue(
            "до согласия ни одно событие не доходит до канала",
            AnalyticsConsentStand.events().isEmpty(),
        )
    }

    @Тогда("^функции и онбординг разрешений работают полностью$")
    fun functionsAndOnboardingStillWork() {
        assertEquals(
            "отказ не гейтит онбординг разрешений: маршрут запуска прежний",
            StartDestination.Onboarding,
            AnalyticsConsentStand.decision?.destination,
        )
        assertTrue(AnalyticsConsentStand.events().isEmpty())
    }

    @Тогда("^при следующем запуске экран согласия сам не показывается$")
    fun theNextLaunchDoesNotAskAgain() {
        assertFalse(AnalyticsConsentStand.nextLaunchDecision().consentPending)
    }

    // --- Ярлык --------------------------------------------------------------------------------

    @Допустим("^человек ещё не принимал решения по аналитике$")
    fun thePersonHasNotDecidedYet() {
        AnalyticsConsentStand.reset()
        assertEquals(AnalyticsConsent.NOT_ASKED, AnalyticsConsentStand.consent())
    }

    @Когда("^приложение запускается ярлыком «Добавить лекарство»$")
    fun theAppIsLaunchedByTheShortcut() {
        AnalyticsConsentStand.launchByShortcut()
        assertTrue("ярлык обязан открыть форму", AnalyticsConsentStand.medicationFormOpened)
    }

    @Тогда("^форма нового лекарства открывается$")
    fun theMedicationFormOpens() {
        assertTrue(AnalyticsConsentStand.shortcutFormsTheMedication())
    }

    @Тогда("^экран согласия в этом запуске не показывается$")
    fun theConsentScreenIsSkippedInThisLaunch() {
        assertFalse(
            "явное действие сильнее самопоказа согласия",
            AnalyticsConsentStand.decision?.consentPending == true,
        )
    }

    @Тогда("^экран согласия покажется при следующем обычном запуске$")
    fun theConsentScreenAppearsOnTheNextRegularLaunch() {
        assertTrue(AnalyticsConsentStand.nextLaunchDecision().consentPending)
    }

    // --- Настройки ----------------------------------------------------------------------------

    @Допустим("^человек отказался от аналитики$")
    fun thePersonRefusedAnalytics() {
        AnalyticsConsentStand.reset()
        AnalyticsConsentStand.recordDeclined()
    }

    @Когда("^человек открывает «Настройки», раздел «Данные»$")
    fun thePersonOpensTheSettingsDataSection() {
        AnalyticsConsentStand.openSettings(world)
    }

    @Тогда("^строка «Аналитика» показывает переключатель выключенным$")
    fun theAnalyticsSwitchIsOff() {
        AnalyticsConsentStand.analyticsToggleIsOff(world)
    }

    @Тогда("^строка «Политика конфиденциальности» доступна и открывает политику в браузере$")
    fun thePolicyRowOpensThePolicy() {
        AnalyticsConsentStand.clickPolicy(world)
        val expected = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.settings_policy_url)
        assertEquals(expected, AnalyticsConsentStand.openedUri)
    }

    @Когда("^человек включает «Аналитику»$")
    fun thePersonTurnsAnalyticsOn() {
        AnalyticsConsentStand.turnAnalyticsOn(world)
        assertEquals("включение обязано открыть экран согласия", 1, AnalyticsConsentStand.consentOpenings)
        assertEquals(
            "переключатель не включается до подтверждения",
            AnalyticsConsent(
                AnalyticsConsentDecision.DECLINED,
                AnalyticsConsentVersion.MATERIAL,
            ),
            AnalyticsConsentStand.consent(),
        )
        assertTrue("до подтверждения события не уходят", AnalyticsConsentStand.events().isEmpty())
    }

    @Тогда("^открывается тот же экран согласия с отказной подписью «Оставить отключённой»$")
    fun theSameConsentScreenOpensWithTheSettingsLabel() {
        AnalyticsConsentStand.showSettingsConsent {
            onNodeWithText(AnalyticsConsentStand.allowLabel(), useUnmergedTree = true).assertExists()
            onNodeWithText(AnalyticsConsentStand.settingsDeclineLabel(), useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^аналитика включается только после «Разрешить аналитику»$")
    fun analyticsTurnsOnOnlyAfterConfirmation() {
        AnalyticsConsentStand.clickAllowOnSettingsConsent()
        assertEquals(
            AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL),
            AnalyticsConsentStand.consent(),
        )
        assertTrue(
            "событие показа согласия уходит после подтверждения",
            AnalyticsConsentStand.events().any { it is AnalyticsEvent.ConsentShown },
        )
    }

    @Тогда("^согласие записывается заново с текущей версией$")
    fun theConsentIsStoredAgainWithTheCurrentVersion() {
        assertEquals(AnalyticsConsentVersion.MATERIAL, AnalyticsConsentStand.consent().materialVersion)
        assertEquals(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentStand.consent().decision)
    }

    @Тогда("^событие показа согласия уходит с источником «настройки»$")
    fun theConsentShownCarriesTheSettingsSource() {
        assertTrue(
            AnalyticsConsentStand.events().any {
                it is AnalyticsEvent.ConsentShown && it.source == ConsentSource.SETTINGS
            },
        )
    }

    @Тогда("^событие включения аналитики уходит после подтверждения$")
    fun theAnalyticsEnabledEventArrives() {
        assertTrue(
            AnalyticsConsentStand.events().any { it is AnalyticsEvent.AnalyticsEnabled },
        )
    }
}
