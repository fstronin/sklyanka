package ru.stohastika.sklyanka.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.ui.component.ProfileStripe
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Оформление профиля, действующее в этом поддереве.
 *
 * `static`, а не обычный local: значение меняется только при переключении профиля,
 * и перечитывать его на каждом чтении незачем.
 *
 * Компонентам оно нужно не для того, чтобы они решали, показывать ли признак: решение
 * принято здесь. Шапка читает имя и рисует надпись и баннер **всегда**, когда имя есть,
 * и не имеет параметра, которым это можно отменить.
 */
val LocalProfileChrome = staticCompositionLocalOf<ProfileChromeState> { ProfileChromeState.Owner }

/**
 * Оформление чужого профиля — пять признаков из шести одной обёрткой (ПРФ-11, ПРФ-12).
 *
 * | # | Признак | Откуда берётся |
 * |---|---|---|
 * | 1 | Цветная полоса вверху | [ProfileStripe] здесь же, над содержимым |
 * | 2 | Подкрашенная шапка и фон | роли `profileHeader` и `background` подменяет тема |
 * | 3 | Надпись «Профиль · Имя» | шапка читает [LocalProfileChrome] |
 * | 4 | Несворачиваемый баннер | шапка рисует его рядом с надписью |
 * | 5 | Акцент действий | роль `accent` подменяет тема |
 *
 * Шестой признак — подпись «отметили вы» — принадлежит спискам приёмов и истории: он
 * относится к отдельной отметке, а не к экрану.
 *
 * **Параметров, включающих и выключающих отдельные признаки, нет и не будет.**
 * Избыточность индикации и есть защита от отметки чужого приёма как своего (ПРФ-10),
 * и «настроить» её значит снять.
 *
 * Обёртка ставится **один раз, в корне приложения**, а не на каждом экране. Так решено
 * не ради экономии: одиннадцать экранов — это одиннадцать мест, где обёртку можно забыть,
 * а экран без индикации — дыра в защите (ПРФ-12). В корне забыть нечего.
 */
@Composable
fun ProfileChrome(state: ProfileChromeState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SklyankaTheme(profile = state.accent) {
        CompositionLocalProvider(LocalProfileChrome provides state) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(SklyankaTheme.colors.background),
            ) {
                ProfileStripe()
                content()
            }
        }
    }
}
