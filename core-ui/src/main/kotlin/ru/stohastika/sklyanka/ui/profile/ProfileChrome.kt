package ru.stohastika.sklyanka.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.ui.component.LocalScreenTopInset
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
fun ProfileChrome(
    state: ProfileChromeState,
    modifier: Modifier = Modifier,
    /**
     * Акцентная тема «Про» (§2.5, SKL-120). Через обёртку, а не мимо неё: она ставит
     * [SklyankaTheme] — единственное место, где роли становятся действующими, — и тема
     * с тёмной средой обязаны прийти вместе с оформлением профиля, иначе экран под
     * обёрткой получил бы базовый акцент «Оливы» при выбранной «Аметисте».
     */
    theme: AccentTheme = AccentTheme.DEFAULT,
    /** Тёмная среда интерфейса из системной настройки (Р-23, §9). */
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    SklyankaTheme(profile = state.accent, theme = theme, dark = dark) {
        // Верхний инсет обёртка знает первой — она и резервирует высоту системной панели
        // полосой цвета шапки. Тот же инсет она отдаёт потомкам (Р-54): шапка считает от него
        // своё поле, и правило «инсет + поле ≤ 56 dp» проверяется на стенде без окна, а не
        // чтением платформы по месту экрана. Подставленное значение (стенд, тест) берётся
        // как есть — и полоса резервируется той же высоты, иначе замер по кадру врал бы.
        val override = LocalScreenTopInset.current
        val inset = override ?: WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        CompositionLocalProvider(
            LocalProfileChrome provides state,
            LocalScreenTopInset provides inset,
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(SklyankaTheme.colors.background),
            ) {
                // Полоса под статус-баром цветом шапки (SKL-72, §5.1): у владельца
                // `profileHeader` равен фону — серой полосы нет; в чужом профиле под
                // системной панелью лежит подкраска шапки, а полоса профиля 6 dp —
                // первый элемент содержимого ниже неё (ОВ-92).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SklyankaTheme.colors.profileHeader)
                        .then(
                            if (override != null) {
                                Modifier.height(override)
                            } else {
                                Modifier.windowInsetsTopHeight(WindowInsets.statusBars)
                            },
                        ),
                )
                ProfileStripe()
                content()
            }
        }
    }
}
