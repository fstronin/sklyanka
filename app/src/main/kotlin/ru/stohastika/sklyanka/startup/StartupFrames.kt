package ru.stohastika.sklyanka.startup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import ru.stohastika.sklyanka.feature.today.TodayUiState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Какой кадр запуска рисуется прямо сейчас.
 *
 * На холодном старте кадров три: системный (его рисует платформа по теме
 * `Theme.Sklyanka.Splash`, приложению он не подчиняется), заставка приложения
 * и первый экран. Здесь названы два последних — то, чем распоряжается приложение.
 */
enum class StartupFrame {

    /**
     * Заставка приложения: экран удержания от снятия системного кадра до готовности
     * первого экрана показать своё содержимое (`docs/spec/03-screens.md` «Заставка»).
     */
    AppSplash,

    /** Первый экран запуска: разрешения, онбординг или «Сегодня» с содержимым. */
    FirstScreen,
}

/**
 * Правило показа кадров запуска — единственное место, где оно записано.
 *
 * Заставка приложения держится, пока первый экран не готов показать своё содержимое,
 * и ни кадром дольше; собственной паузы у неё нет (НФТ-21). Готовность «Сегодня» —
 * это не [TodayUiState.Opening]: `Opening` — пустой кадр до первого значения потока,
 * и показывать его человеку вместо заставки нельзя. Экран разрешений содержимое
 * показывает сразу, поэтому его ветка готовности не ждёт.
 *
 * [destination] == `null` означает «маршрут ещё не решён»: первый экран неизвестен,
 * значит не готов, и на экране заставка. Правило сознательно читает состояние,
 * а не получает событий готовности из экранов: иначе каждый экран завёл бы свой
 * сигнал, и один из них однажды не позвали бы.
 */
fun startupFrame(
    destination: StartDestination?,
    onboardingUnseen: Boolean,
    onboardingDone: Boolean,
    today: TodayUiState,
): StartupFrame = when {
    destination == null -> StartupFrame.AppSplash
    destination == StartDestination.Onboarding && onboardingUnseen && !onboardingDone -> StartupFrame.FirstScreen
    today is TodayUiState.Opening -> StartupFrame.AppSplash
    else -> StartupFrame.FirstScreen
}

/**
 * Один кадр запуска на экране: заставка приложения или первый экран.
 *
 * Оповещение о первом нарисованном кадре — сигнал системе снять свой кадр
 * (`setKeepOnScreenCondition`): пока оно не прозвучало, системный кадр держится,
 * а нарисованный под ним кадр приложения ждёт. Поэтому признак ставится в фазе
 * **отрисовки**, после `drawContent()`: снятие системного кадра обязано открыть
 * уже нарисованное содержимое, иначе между кадрами мелькнёт светлый фон окна
 * (`postSplashScreenTheme` — светлая тема).
 *
 * Оповещение приходит ровно один раз за запуск: дальше оно не нужно, а повторные
 * вызовы на каждом кадре — лишняя работа в горячем пути отрисовки. Флаг — не
 * состояние Compose: условие снятия системного кадра опрашивается предрисовочным
 * слушателем платформы и в композиции не участвует.
 *
 * В ветке заставки [firstScreen] не компонуется вовсе: пустой кадр первого экрана
 * (например, «Сегодня» в `Opening`) не может мелькнуть даже на один кадр.
 */
@Composable
fun StartupFrames(
    frame: StartupFrame,
    onFirstFrameDrawn: () -> Unit,
    firstScreen: @Composable () -> Unit,
) {
    val drawn = remember { AtomicBoolean(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                if (drawn.compareAndSet(false, true)) {
                    onFirstFrameDrawn()
                }
            },
    ) {
        when (frame) {
            StartupFrame.AppSplash -> SplashScreen()
            StartupFrame.FirstScreen -> firstScreen()
        }
    }
}
