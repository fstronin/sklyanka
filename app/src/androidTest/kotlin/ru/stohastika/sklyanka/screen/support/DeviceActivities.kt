package ru.stohastika.sklyanka.screen.support

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import ru.stohastika.sklyanka.MainActivity

/**
 * Запуск и закрытие настоящей `MainActivity` без `ActivityScenario` (SKL-97, TDD §7.1).
 *
 * `ActivityScenario` ждёт простоя главного потока, а приложение держит заставку до
 * готовности первого экрана и асинхронно читает DataStore: ручной запуск и ожидание
 * условия дают проверке свой момент старта и не зависят от синхронизации с заставкой.
 */
internal object DeviceActivities {

    const val LOG_TAG = "SKL97"

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    fun launch(context: Context, intent: Intent = Intent(context, MainActivity::class.java)) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun resumed(): MainActivity? = inStages(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()

    fun closeAll(compose: ComposeTestRule) {
        val alive = inStages(
            Stage.PRE_ON_CREATE,
            Stage.CREATED,
            Stage.STARTED,
            Stage.RESUMED,
            Stage.PAUSED,
            Stage.STOPPED,
        )
        if (alive.isEmpty()) return
        instrumentation.runOnMainSync { alive.forEach(Activity::finish) }
        compose.waitUntil(CLOSE_TIMEOUT_MS) {
            val live = inStages(Stage.RESUMED, Stage.PAUSED, Stage.STARTED, Stage.CREATED)
            if (live.isNotEmpty()) instrumentation.runOnMainSync { live.forEach(Activity::finish) }
            live.isEmpty()
        }
        instrumentation.waitForIdleSync()
    }

    private fun inStages(vararg stages: Stage): List<Activity> {
        var result: List<Activity> = emptyList()
        instrumentation.runOnMainSync {
            val registry = ActivityLifecycleMonitorRegistry.getInstance()
            result = stages.flatMap { registry.getActivitiesInStage(it) }.distinct()
        }
        return result
    }

    private const val CLOSE_TIMEOUT_MS = 30_000L
}
