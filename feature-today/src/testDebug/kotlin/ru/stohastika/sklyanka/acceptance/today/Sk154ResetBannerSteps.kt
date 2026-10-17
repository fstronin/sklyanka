package ru.stohastika.sklyanka.acceptance.today

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.InMemoryDatabaseLife
import ru.stohastika.sklyanka.domain.startup.ExplainDatabaseResetUseCase
import ru.stohastika.sklyanka.feature.today.R
import ru.stohastika.sklyanka.feature.today.TAG_DB_RESET_BANNER
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Экранные шаги приёмки SKL-154: баннер объяснения сброса данных на «Сегодня»
 * (RT-2, МГ-8, Р-87 п. 2).
 *
 * Признак объяснения приходит не рисованным флагом, а настоящим правилом продукта
 * ([ExplainDatabaseResetUseCase] на отметках жизни [InMemoryDatabaseLife]): стенд зовёт его
 * так же, как `TodayViewModel` на старте экрана, и собирает состояние тем же
 * [TodayStand]. Правило «объясняется один раз» домен доказывает сценарием самой задачи
 * в прогоне `acceptance`; здесь проверяется контракт экрана: баннер показан, действие
 * «Закрыть» уходит намерением маршруту, а после закрытия баннера на экране нет.
 *
 * Композиция живёт внутри одного шага (конвенция [TodayScreenProbe]): «Когда» запоминает
 * наблюдение, «Тогда» утверждает его.
 */
@OptIn(ExperimentalTestApi::class)
class Sk154ResetBannerSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Отметки жизни базы сценария: те же, что держит продукт вне файла базы (МГ-8). */
    private val life = InMemoryDatabaseLife()

    private var bannerVisible = false
    private var bannerText = ""
    private var intents: List<TodayIntent> = emptyList()

    @Допустим("^база пересоздана, а прежняя жизнь не объяснена$")
    fun theDatabaseWasRecreatedOverALivedLife() {
        runBlocking { life.markLife() }
        life.recreate()
    }

    @Когда("^человек открывает «Сегодня» после сброса$")
    fun thePersonOpensTodayAfterTheReset() {
        val explained = runBlocking { explain().explanationOnStartup() }
        var text = ""
        bannerVisible = onToday(explained) { test ->
            val nodes = test.onAllNodesWithTag(TAG_DB_RESET_BANNER).fetchSemanticsNodes()
            text = nodes.flatMap { TodayScreenProbe.textsOf(it) }.joinToString(separator = " ")
            nodes.isNotEmpty()
        }
        bannerText = text
    }

    @Тогда("^объяснение сброса показано баннером$")
    fun theResetExplanationIsShownAsBanner() {
        assertTrue("«Сегодня» после сброса жившей базы обязано показать объяснение", bannerVisible)
        // В поддереве баннера два текста: объяснение и подпись действия «Закрыть».
        assertTrue(
            "баннер обязан говорить словами продукта, а не стенда: $bannerText",
            bannerText.startsWith(context.getString(R.string.today_db_reset_banner)),
        )
    }

    @Когда("^человек выбирает «Закрыть» в объяснении сброса$")
    fun thePersonChoosesCloseInTheResetExplanation() {
        intents = onTodayActing(reset = true) { test ->
            test.onNodeWithText(context.getString(R.string.today_db_reset_action)).performClick()
        }
        // Стенд применяет намерение так же, как маршрут в продукте: модель признаёт событие
        // объяснённым в постоянных отметках (МГ-8).
        if (TodayIntent.DismissResetExplanation in intents) {
            runBlocking { explain().dismissExplanation() }
        }
    }

    @Тогда("^экран отдаёт маршруту намерение закрыть объяснение$")
    fun theScreenHandsTheDismissIntentToTheRoute() {
        assertTrue(
            "действие «Закрыть» обязано уйти из экрана намерением: $intents",
            TodayIntent.DismissResetExplanation in intents,
        )
    }

    @Тогда("^баннер на «Сегодня» больше не показывается$")
    fun theBannerIsNotShownAgain() {
        val explainedAgain = runBlocking { explain().explanationOnStartup() }
        assertFalse("признанное событие не обязано возвращаться на следующем старте", explainedAgain)
        val visible = onToday(explainedAgain) { test ->
            test.onAllNodesWithTag(TAG_DB_RESET_BANNER).fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse("после закрытия баннера на экране нет", visible)
    }

    private fun explain() = ExplainDatabaseResetUseCase(life, world.clock.time)

    /** Собрать экран и задать вопрос его композиции. */
    private fun <T> onToday(reset: Boolean, question: (ComposeUiTest) -> T): T {
        var answer: T? = null
        runComposeUiTest {
            setContent { SklyankaTheme { TodayScreen(state = state(reset), onIntent = {}) } }
            answer = question(this)
        }
        @Suppress("UNCHECKED_CAST")
        return answer as T
    }

    /** Действие человека: экран отдаёт намерения, шаг их записывает. */
    private fun onTodayActing(reset: Boolean, action: (ComposeUiTest) -> Unit): List<TodayIntent> {
        val collected = mutableListOf<TodayIntent>()
        runComposeUiTest {
            setContent { SklyankaTheme { TodayScreen(state = state(reset), onIntent = collected::add) } }
            action(this)
            waitForIdle()
        }
        return collected
    }

    /**
     * Состояние экрана собирает стенд тем же доменом, что и продукт ([TodayStand]),
     * а признак объяснения приходит из настоящего правила старта.
     */
    private fun state(reset: Boolean): TodayUiState = when (val state = TodayStand.state(world)) {
        is TodayUiState.Empty -> state.copy(databaseReset = reset)
        is TodayUiState.Content -> state.copy(databaseReset = reset)
        TodayUiState.Opening -> state
    }
}
