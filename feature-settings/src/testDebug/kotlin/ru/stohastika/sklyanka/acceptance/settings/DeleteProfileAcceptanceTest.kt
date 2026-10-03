package ru.stohastika.sklyanka.acceptance.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun

/**
 * Экранный прогон приёмки удаления профиля (SKL-36, группа `@screen`).
 *
 * Шаги видят ресурсы и композицию `feature-settings` (`SKL-26/TDD.md §4.3`): экран
 * подтверждения и строка в настройках живут в этом модуле, и отдельный стенд в `app`
 * был бы вторым способом собрать те же состояния.
 *
 * Здесь же исполняется сценарий SKL-15 «Удаление активного профиля…» (тег `@deletion`):
 * он говорит о явном возврате в свой профиль после каскадного удаления (`ПРФ-30`,
 * `ПРФ-31`), а его шаги — экран удаления; из прогона `app` он исключён по тегу.
 */
@RunWith(AndroidJUnit4::class)
// Экран подтверждения длиннее окна телефона: числа, слово, выгрузка и кнопки иначе
// не собираются, и проверка видела бы не экран, а его верх. Язык — часть условий:
// подписи и факт выгрузки читаются с ресурсов русской локали.
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class DeleteProfileAcceptanceTest {

    @Test
    fun theDeletionScenariosRunOnTheScreenStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = TASK))
        } finally {
            DeleteProfileScreenProbe.dropMainDispatcher()
            SettingsScreenProbe.dropMainDispatcher()
        }
    }

    @Test
    fun theActiveProfileDeletionScenarioOfSkl15RunsOnTheScreenStand() {
        try {
            assertNull(ScreenAcceptanceRun.run(task = PROFILE_TASK, tags = PROFILE_DELETION_TAG))
        } finally {
            DeleteProfileScreenProbe.dropMainDispatcher()
            SettingsScreenProbe.dropMainDispatcher()
        }
    }

    private companion object {
        const val TASK = "SKL-36"
        const val PROFILE_TASK = "SKL-15"
        const val PROFILE_DELETION_TAG = "@deletion"
    }
}
