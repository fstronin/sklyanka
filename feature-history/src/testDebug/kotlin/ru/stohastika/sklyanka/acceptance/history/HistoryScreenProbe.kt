package ru.stohastika.sklyanka.acceptance.history

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.feature.history.HistoryIntent
import ru.stohastika.sklyanka.feature.history.HistoryScreen
import ru.stohastika.sklyanka.feature.history.HistoryUiState
import ru.stohastika.sklyanka.feature.history.ReportUi
import ru.stohastika.sklyanka.feature.history.TAG_NEXT_MONTH
import ru.stohastika.sklyanka.feature.history.TAG_PREVIOUS_MONTH
import ru.stohastika.sklyanka.feature.history.TextDoctorReportRenderer
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.YearMonth

/**
 * Стенд экрана истории для шагов приёмки: собранная композиция и намерения человека.
 *
 * Состояние собирает настоящий домен из мира сценария ([HistoryFromWorld]), а нажатия
 * уходят намерениями и применяются к миру тем же, чем в продукте: переключение месяца —
 * арифметикой `HistoryViewModel`, «Отчёт врачу» — настоящим `BuildDoctorReportUseCase`,
 * отрисовка документа — настоящим `TextDoctorReportRenderer`. Копии правил в стенде нет;
 * повторено только отображение намерений на состояние — это разметка, а не правило.
 *
 * Месяц переживает отдельные вызовы шагов: сессия `runComposeUiTest` живёт внутри одного
 * шага, а стрелка месяца нажата в предыдущем. Накопленное хранится здесь и сбрасывается
 * со сменой мира сценария — как в стендах `feature-today` и `feature-meds`.
 */
@OptIn(ExperimentalTestApi::class)
internal object HistoryScreenProbe {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private var lastWorld: AcceptanceWorld? = null
    private var month: YearMonth = YearMonth.of(DEFAULT_YEAR, DEFAULT_MONTH)

    /** Месяц, о котором говорит сценарий: «человек смотрит на историю за …». */
    fun select(world: AcceptanceWorld, month: YearMonth) {
        resetFor(world)
        this.month = month
    }

    fun selected(world: AcceptanceWorld): YearMonth {
        resetFor(world)
        return month
    }

    /** Переключение месяца стрелками — как у человека, а не присвоением поля. */
    fun switchTo(world: AcceptanceWorld, target: YearMonth) {
        var guard = 0
        while (selected(world) != target) {
            val previous = selected(world) > target
            act(world) { test ->
                test.onNodeWithTag(if (previous) TAG_PREVIOUS_MONTH else TAG_NEXT_MONTH).performClick()
            }
            check(++guard <= MONTH_GUARD) { "the month did not switch to $target" }
        }
    }

    /** Состояние, которое продукт показал бы сейчас. */
    fun state(world: AcceptanceWorld): HistoryUiState {
        val selected = selected(world)
        val period = HistoryFromWorld.period(world, selected)
        return HistoryUiState(
            month = selected,
            period = period,
            insightTime = MissedPatternRule().dominantMissedTime(period),
            report = world.history.report?.let { report ->
                ReportUi(TextDoctorReportRenderer(context).render(report, world.clock.zone))
            },
        )
    }

    /** Собрать экран и задать вопрос его композиции. */
    fun <T> ask(world: AcceptanceWorld, chrome: ProfileChromeState? = null, question: (ComposeUiTest) -> T): T {
        var answer: T? = null
        runComposeUiTest {
            setContent { Content(world, chrome, onIntent = {}) }
            answer = question(this)
        }
        @Suppress("UNCHECKED_CAST")
        return answer as T
    }

    /**
     * Действие человека: экран отдаёт намерения, стенд применяет их к миру.
     *
     * @return намерения, которые экран успел отдать: проверка «нажатие не отдало лишнего» —
     *   утверждение о том, чего среди них не оказалось
     */
    fun act(
        world: AcceptanceWorld,
        chrome: ProfileChromeState? = null,
        action: (ComposeUiTest) -> Unit,
    ): List<HistoryIntent> {
        val intents = mutableListOf<HistoryIntent>()
        runComposeUiTest {
            setContent { Content(world, chrome, onIntent = intents::add) }
            action(this)
            waitForIdle()
        }
        intents.forEach { apply(world, it) }
        return intents
    }

    @Composable
    private fun Content(world: AcceptanceWorld, chrome: ProfileChromeState?, onIntent: (HistoryIntent) -> Unit) {
        val screen: @Composable () -> Unit = { HistoryScreen(state = state(world), onIntent = onIntent) }
        if (chrome == null) {
            SklyankaTheme { screen() }
        } else {
            // Оформление профиля само ставит тему и фон: вторая обёртка закрасила бы
            // подкраску, ради которой собирается контекст чужого профиля (ПРФ-11).
            ProfileChrome(chrome) { screen() }
        }
    }

    private fun apply(world: AcceptanceWorld, intent: HistoryIntent) {
        when (intent) {
            HistoryIntent.PreviousMonth -> month = month.minusMonths(1)
            HistoryIntent.NextMonth -> month = month.plusMonths(1)
            // «Отчёт врачу» — тот же путь, что в `HistoryViewModel.prepareReport`:
            // состав собирает домен, отрисовка подставляется подтверждённым форматом.
            HistoryIntent.ReportRequested -> world.history.buildReport(month)
            // Переходы наружу и системный выбор места экран не исполняет: первое решает
            // маршрут `app`, второе — системный диалог.
            else -> Unit
        }
    }

    private fun resetFor(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            month = YearMonth.from(world.clock.today())
        }
    }

    private const val DEFAULT_YEAR = 2026
    private const val DEFAULT_MONTH = 9
    private const val MONTH_GUARD = 24
}
