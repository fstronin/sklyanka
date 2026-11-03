package ru.stohastika.sklyanka.acceptance.settings

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentState
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.ui.analytics.TAG_ANALYTICS_CONSENT_ALLOW

/**
 * Экранные шаги строки «Аналитика» в настройках (SKL-165, группа `@screen`).
 *
 * Стенд — настоящие `SettingsRoute`, `SettingsViewModel` и доменные сценарии согласия:
 * строка показывает решение из хранилища, включение открывает вопрос, а «Разрешить» —
 * единственная точка, где согласие даётся. События пишет настоящий рекордер: стенд только
 * подменяет его порты и больше ничего (TDD §11.5).
 *
 * Шаги Предыстории («выбрал «Сейчас не надо»», «аналитика включена») объявлены рядом
 * с первым запуском: здесь они не дублируются, а мир берётся тем же — [SettingsAppProbe].
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsAnalyticsSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Когда("^он открывает настройки, раздел данных, (.+)$")
    fun heOpensTheDataBlock(@Suppress("UNUSED_PARAMETER") moment: String) {
        // Прокрутка до строки — и есть проверка «раздел данных открыт»: строка живёт
        // только в нём, и на экране без блока поиск упал бы.
        SettingsAppProbe.askSettings(world) {
            onNodeWithText(string(R.string.settings_analytics), useUnmergedTree = true)
                .performScrollTo()
        }
    }

    /**
     * Слово строки сверяется с ресурсом, а не с литералом: тексты живут в одном месте.
     *
     * Включённое и выключенное состояние различаются и словом, и положением переключателя:
     * статус не передаётся одним бегунком (НФТ-30, НФТ-33).
     */
    @Тогда("^строка «Аналитика» отмечена «(Включено|Выключено)»$")
    fun theAnalyticsRowIsMarked(word: String) {
        assertAnalyticsRow(word)
    }

    /**
     * Тот же выбор спустя время: решение живёт в хранилище, а не в кадре экрана.
     *
     * Отдельный шаг, потому что сценарий говорит о нём словами «по-прежнему»: проверка
     * обязана быть такой же строгой, как в момент выбора, а не «экрана не изменилось».
     */
    @Тогда("^строка «Аналитика» по-прежнему отмечена «(Включено|Выключено)»$")
    fun theAnalyticsRowStillShows(word: String) {
        assertAnalyticsRow(word)
    }

    /**
     * Слово строки после действия — тем же правилом, что в первом сценарии блока.
     *
     * Сценарий называет строку короче («строка отмечена …»): их две на экране, поэтому
     * шаг проверяет именно «Аналитику» — по подписи и по выбранному состоянию.
     */
    @Тогда("^строка отмечена «(Включено|Выключено)»$")
    fun theRowIsMarked(word: String) {
        assertAnalyticsRow(word)
    }

    private fun assertAnalyticsRow(word: String) {
        val enabled = when (word) {
            string(R.string.settings_analytics_on) -> true
            string(R.string.settings_analytics_off) -> false
            else -> error("the scenario names a state the settings row does not know: $word")
        }
        val caption = context.getString(R.string.settings_analytics_caption, word)
        SettingsAppProbe.askSettings(world) {
            onNodeWithText(string(R.string.settings_analytics)).performScrollTo().let { row ->
                if (enabled) row.assertIsOn() else row.assertIsOff()
            }
            onNodeWithText(caption, useUnmergedTree = true).performScrollTo()
        }
        assertEquals(
            if (enabled) AnalyticsConsentState.ALLOWED else AnalyticsConsentState.DECLINED,
            SettingsAppProbe.settingsState(world).analytics,
        )
    }

    /**
     * Пояснение под строкой — те же слова, что в ресурсе подписи.
     *
     * Сценарий идёт после отказа, поэтому подпись собирается из значения «Выключено»:
     * подпись — одно знание в `strings.xml`, а не склейка в коде (НФТ-42, НФТ-44).
     */
    @И("^под ней пояснение, что включение помогает найти неудобные места$")
    fun theCaptionExplainsTheChoice() {
        val caption = context.getString(
            R.string.settings_analytics_caption,
            string(R.string.settings_analytics_off),
        )
        SettingsAppProbe.askSettings(world) {
            onNodeWithText(caption, useUnmergedTree = true).performScrollTo()
        }
        assertTrue(
            caption.length > string(R.string.settings_analytics_off).length,
            "the caption must say more than the state word: $caption",
        )
    }

    /**
     * Включение: строка просит вопрос, «Разрешить» — решение (ПМ-25 п. 4, Р-96).
     *
     * Экран не даёт согласия нажатием переключателя: до «Разрешить» ничего не отправляется,
     * и это же проверяет следующее утверждение сценария.
     */
    @Когда("^он включает «Аналитику» в настройках (.+)$")
    fun heTurnsAnalyticsOn(moment: String) {
        SettingsAppProbe.actSettings(world) {
            onNodeWithText(string(R.string.settings_analytics)).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(TAG_ANALYTICS_CONSENT_ALLOW).performClick()
        }
        assertEquals(
            AnalyticsConsentState.ALLOWED,
            SettingsAppProbe.settingsState(world).analytics,
            "enabling must leave a decision and not just a tap ($moment)",
        )
    }

    /** Выключение — одно нажатие, без вопроса и без подтверждения (АН-1, АН-2). */
    @Когда("^(?:он|человек) выключает «Аналитику» (.+)$")
    fun heTurnsAnalyticsOff(moment: String) {
        SettingsAppProbe.actSettings(world) {
            onNodeWithText(string(R.string.settings_analytics)).performScrollTo().performClick()
        }
        assertEquals(
            AnalyticsConsentState.DECLINED,
            SettingsAppProbe.settingsState(world).analytics,
            "switching off must be an immediate refusal ($moment)",
        )
    }

    /**
     * После согласия события действительно уходят: следующий показ экрана даёт `screen_view`.
     *
     * Проверяется настоящий путь показа — маршрут сообщает о себе в `LocalScreenTracker`,
     * а рекордер решает по согласию; синтетического события стенд не пишет.
     */
    @Тогда("^следующие действия в приложении попадают в события$")
    fun theFollowingActionsBecomeEvents() {
        val before = world.analytics.recordedNames()
        SettingsAppProbe.askSettings(world) {}
        val after = world.analytics.recordedNames()

        assertTrue(
            after.contains(AnalyticsEventName.ANALYTICS_ENABLED),
            "the enablement itself must be recorded: $after",
        )
        assertTrue(
            after.drop(before.size).contains(AnalyticsEventName.SCREEN_VIEW),
            "a following screen view must be recorded after consent: $after",
        )
    }

    /**
     * После отказа не пишется ничего: следующий показ экрана в очередь не попадает (АН-2).
     *
     * Сравнивается журнал записей, а не очередь: отправленное из очереди удаляется,
     * и «пусто сейчас» ничего не доказывало бы.
     */
    @Тогда("^действия после этого момента в события не попадают$")
    fun theFollowingActionsStayOutOfTheEvents() {
        val before = world.analytics.recordedNames()
        SettingsAppProbe.askSettings(world) {}
        val after = world.analytics.recordedNames()

        assertEquals(before, after, "nothing may be recorded after the refusal")
    }

    private fun string(id: Int): String = context.getString(id)
}
