package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки универсального онбординга надёжности (SKL-112, группа `@screen`).
 *
 * Модуль свой: экран «Настроить надёжность» живёт здесь, и шаги обязаны видеть собранную
 * композицию и ресурсы модуля (`SKL-26/TDD.md §4.3`). Профиль прошивки ставит мир сценария —
 * на JVM `Build.MANUFACTURER` взять неоткуда, — а состав и слова шагов берёт домен
 * ([ru.stohastika.sklyanka.domain.onboarding.VendorSteps],
 * [ru.stohastika.sklyanka.feature.onboarding.OnboardingProfileTexts]).
 *
 * Из прогона исключены соседние группы, и у каждой своя область видимости: композиция
 * нескольких экранов — `app`, диагностика — `feature-reminder`, сводное состояние строки
 * настроек — `feature-settings`.
 */
@RunWith(AndroidJUnit4::class)
// Экран с пятью карточками, плашкой и футером: без запаса по высоте нижние блоки
// не собираются, и проверка видела бы верх экрана. Язык — часть условий: тексты сценариев
// записаны по-русски, и слова прошивки сверяются с ресурсами русской локали.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class Sk112VendorAcceptanceTest {

    @Test
    fun theVendorScenariosOfSk112RunOnTheStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
        } finally {
            PermissionsStand.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-112"
        const val TAGS = "@screen and not @composition and not @diagnostics and not @settings"
    }
}
