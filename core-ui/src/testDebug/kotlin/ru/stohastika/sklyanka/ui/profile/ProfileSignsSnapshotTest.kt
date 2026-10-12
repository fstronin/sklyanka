package ru.stohastika.sklyanka.ui.profile

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.snapshot.Snapshots

/**
 * Эталонные снимки шести признаков чужого профиля и их отсутствия у владельца
 * (ПРФ-11, ПРФ-16, критерии приёмки 4, 7 и 8).
 *
 * Зачем снимки именно здесь. Избыточная индикация — свойство, которое проверяется глазом
 * и теряется молча: любой из шести признаков можно убрать правкой, не сломав ни одного
 * утверждения о составе экрана. Снимок ловит ровно это. Цена пропущенной правки — отметка
 * чужого приёма как своего, то есть ложь сразу в двух историях (ПРФ-10).
 *
 * Снимки парные: каждый признак снят и в чужом профиле, и в своём. Пара — это и есть
 * проверяемое утверждение: у владельца признака нет вовсе, и отсутствие индикации само
 * по себе сигнал (ПРФ-16). Одиночный снимок чужого профиля не отличил бы «признак есть»
 * от «он есть всегда».
 *
 * Признак 1 (полоса) снимается в группе компонентов — `profile-stripe-owner` и
 * `profile-stripe-foreign`: кадры здесь были их побайтовыми копиями и сняты решением
 * Р-61 (SKL-112).
 *
 * Признаки 2, 3 и 4 стоят на одном снимке не для экономии: шапка получает имя профиля
 * из окружения и рисует подкраску, надпись и баннер вместе. Порознь их не снять, потому
 * что порознь они не существуют, и это само требование.
 *
 * Стенд — имитация Android, а не EMUI: снимок говорит, что вёрстка не изменилась, и ничего
 * не говорит о том, как экран выглядит на настоящем устройстве
 * (`docs/archive/tasks/SKL-24/TDD.md §2`).
 */
@RunWith(AndroidJUnit4::class)
class ProfileSignsSnapshotTest {

    /** Признаки 2, 3 и 4: подкрашенная шапка, надпись «Профиль · Имя» и баннер. */
    @Test
    fun theTintedHeaderWithTheLabelAndTheBanner() {
        pair("signs-2-3-4-header") { ProfileHeaderSign() }
    }

    /** Признак 5: акцентный цвет действий — кнопка «Принято» в цвете профиля. */
    @Test
    fun theAccentOfActions() {
        pair("sign-5-action") { ProfileActionSign() }
    }

    /** Признак 6: подпись «отметили вы» под фактическим временем отметки. */
    @Test
    fun theMarkedByYouSignature() {
        capture("sign-6-marked-by-you-foreign", foreign = true) { ProfileMarkedByYouSign(markedByYou = true) }
        capture("sign-6-marked-by-you-owner", foreign = false) { ProfileMarkedByYouSign(markedByYou = false) }
    }

    /**
     * Все шесть признаков разом — то, что человек видит на экране чужого профиля,
     * и то же самое место в своём профиле.
     *
     * Главный снимок группы: ПРФ-10 требует, чтобы менялось общее впечатление от экрана,
     * а не деталь. Общее впечатление проверяется только целым листом.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun allSixSignsAtOnce() {
        listOf(true, false).forEach { foreign ->
            val name = if (foreign) "all-signs-foreign" else "all-signs-owner"
            captureRoboImage(
                file = Snapshots.golden(GROUP, name),
                roborazziComposeOptions = Snapshots.options(heightDp = SHEET_HEIGHT),
            ) {
                ProfileSignsSheet(if (foreign) foreignProfileChrome() else ProfileChromeState.Owner)
            }
        }
    }

    private fun pair(name: String, content: @Composable () -> Unit) {
        capture("$name-foreign", foreign = true, content = content)
        capture("$name-owner", foreign = false, content = content)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, foreign: Boolean, content: @Composable () -> Unit) {
        captureRoboImage(
            file = Snapshots.golden(GROUP, name),
            roborazziComposeOptions = Snapshots.options(),
        ) {
            ProfileSignSurface(if (foreign) foreignProfileChrome() else ProfileChromeState.Owner) { content() }
        }
    }

    private companion object {
        const val GROUP = "profile"

        /** Высота листа: шапка с баннером, карточка и кнопка помещаются целиком. */
        const val SHEET_HEIGHT = 520
    }
}
