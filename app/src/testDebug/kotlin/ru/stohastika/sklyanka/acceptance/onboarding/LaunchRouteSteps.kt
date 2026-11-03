package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.startup.LaunchStep
import java.time.LocalTime

/**
 * Экранные шаги маршрута первого запуска (SKL-165, группа `@screen`, АН-1).
 *
 * Стенд — [LaunchRouteStand]: настоящее правило маршрута [LaunchStep], настоящий диалог
 * политики и настоящий экран согласия. Шаг не изображает решение, а спрашивает продукт:
 * какой шаг выбрало правило и что человек видит на экране.
 *
 * «Перезапуск» — это не сброс мира: политика и согласие живут в настройках устройства
 * (ПВ-3, АН-2) и переживают закрытие приложения, а мир сценария — нет. Поэтому повторный
 * запуск проверяет именно то, что решение не спрашивается снова.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class LaunchRouteSteps(private val world: AcceptanceWorld) {

    @Допустим("^политика конфиденциальности принята (.+?) в (\\d{2}:\\d{2})$")
    fun thePolicyWasAccepted(date: String, time: String) {
        moment(date, time)
        LaunchRouteStand.acknowledgePrivacy(world)
    }

    /**
     * Первый кадр приложения: маршрут решён тем же правилом, что зовёт `MainActivity`.
     *
     * Ожидание — вопрос об аналитике: политика принята, а решения ещё нет (АН-1).
     */
    @Когда("^человек оказывается на первом экране приложения (.+?) в (\\d{2}:\\d{2})$")
    fun theFirstScreenAppears(date: String, time: String) {
        moment(date, time)
        assertEquals(
            LaunchStep.ANALYTICS_CONSENT,
            LaunchRouteStand.step(world),
            "after the policy the first step is the analytics question (АН-1)",
        )
    }

    @Тогда("^он видит экран «Помочь сделать приложение лучше\\?»$")
    fun theConsentScreenIsVisible() {
        assertEquals(LaunchStep.ANALYTICS_CONSENT, LaunchRouteStand.step(world))
        LaunchRouteStand.ask(world) {
            onNodeWithText(LaunchRouteStand.consentTitle).assertIsDisplayed()
        }
    }

    /**
     * Пояснение называет ровно то, что уходит: экраны и действия, и ничего записанного.
     *
     * Проверяется текст продукта, а не пересказ: сценарий утверждает смысл пояснения,
     * и смысл должен быть в самом ресурсе (АН-1, ПМ-25).
     */
    @И("^пояснение говорит, что отправляются только обезличенные события экранов и действий$")
    fun theExplanationNamesWhatIsSent() {
        val body = LaunchRouteStand.consentBody
        assertTrue(body.contains(TEXT_SCREENS), "the explanation must name screens: $body")
        assertTrue(body.contains(TEXT_ACTIONS), "the explanation must name actions: $body")
        assertTrue(
            body.contains(TEXT_NOTHING_SENT),
            "the explanation must say that what the person records is not sent: $body",
        )
        LaunchRouteStand.ask(world) {
            onNodeWithText(body).assertIsDisplayed()
        }
    }

    @И("^внизу кнопки «Разрешить» и «Сейчас не надо»$")
    fun bothActionsArePresent() {
        LaunchRouteStand.ask(world) {
            onNodeWithText(LaunchRouteStand.consentAllow)
                .assertIsDisplayed()
                .assertIsEnabled()
                .assert(hasClickAction())
            onNodeWithText(LaunchRouteStand.consentDecline)
                .assertIsDisplayed()
                .assertIsEnabled()
                .assert(hasClickAction())
        }
    }

    /** «Сегодня» открывается после решения: отказ не запирает приложение (АН-1). */
    @Когда("^он открывает «Сегодня» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonOpensToday(date: String, time: String) {
        moment(date, time)
        LaunchRouteStand.ensureAnIntake(world)
        assertEquals(
            LaunchStep.ROOT,
            LaunchRouteStand.step(world),
            "a decision already given must not bring the question back (АН-2)",
        )
    }

    @Тогда("^приёмы дня видны и доступны для отметки$")
    fun theIntakesOfTheDayAreAvailable() {
        LaunchRouteStand.ask(world) {
            assertTodayIsVisible()
            onNodeWithText(LaunchRouteStand.medicationShown(world), substring = true)
                .performScrollTo()
                .assertIsDisplayed()
            assertMarkIsAvailable()
        }
    }

    /** Отказ никого не запирает: ни вопроса, ни запрета действия (АН-1, П-3). */
    @И("^ни одно действие в приложении не заблокировано$")
    fun nothingIsLocked() {
        assertEquals(
            AnalyticsConsentState.DECLINED,
            world.analytics.consent,
            "the scenario is about a refusal, and a refusal must be a decision, not silence",
        )
        LaunchRouteStand.ask(world) {
            assertTodayIsVisible()
            assertMarkIsAvailable()
            assertNoConsentScreen()
        }
    }

    /** Закрыть и открыть снова: новый «процесс», те же настройки устройства. */
    @Когда("^он закрывает и снова открывает приложение (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonReopensTheApplication(date: String, time: String) {
        moment(date, time)
        world.analytics.restart()
        LaunchRouteStand.ensureAnIntake(world)
        assertEquals(
            LaunchStep.ROOT,
            LaunchRouteStand.step(world),
            "the question is asked once and never again (АН-2)",
        )
    }

    @Тогда("^экрана про аналитику нет$")
    fun theConsentScreenIsGone() {
        assertEquals(LaunchStep.ROOT, LaunchRouteStand.step(world))
        LaunchRouteStand.ask(world) {
            assertNoConsentScreen()
        }
    }

    @И("^сразу открывается «Сегодня»$")
    fun todayOpensRightAway() {
        LaunchRouteStand.ask(world) {
            assertTodayIsVisible()
        }
    }

    private fun moment(date: String, time: String) {
        world.clock.moveTo(RussianDates.date(date, world.clock.today()).atTime(LocalTime.parse(time)))
    }

    private companion object {
        /** Слова самого продукта: смысл пояснения проверяется по ним, а не по памяти шага. */
        const val TEXT_SCREENS = "экранов"
        const val TEXT_ACTIONS = "действий"
        const val TEXT_NOTHING_SENT = "не отправляется"
    }
}
