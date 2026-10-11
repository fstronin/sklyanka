package ru.stohastika.sklyanka.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.component.BrandTitle
import ru.stohastika.sklyanka.ui.component.SklyankaMark
import ru.stohastika.sklyanka.ui.locale.interfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.MarkEnvironment
import ru.stohastika.sklyanka.ui.token.SklyankaBrandType
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.flatMarkColor
import ru.stohastika.sklyanka.ui.token.markCaptionColor
import ru.stohastika.sklyanka.ui.token.markTileBackground
import ru.stohastika.sklyanka.ui.token.markVendorColor

/**
 * Заставка: знак, название, подпись и издатель на тёмном полотне
 * (`design/screens/Splash.dc.html`).
 *
 * Это не первый кадр запуска — первый кадр рисует система по теме
 * `Theme.Sklyanka.Splash`, ещё до того, как Activity создана. Здесь — продолжение
 * того же кадра средствами приложения: полотно и знак совпадают, поэтому переход
 * от системной заставки к приложению не мигает.
 *
 * Показывается она от снятия системного кадра до готовности первого экрана показать
 * своё содержимое — и ни кадром дольше (НФТ-21, ОВ-87). **Индикатора загрузки нет
 * намеренно** (`docs/v2/10-behavior.md` «Заставка»): индикатор превращает короткую
 * паузу в ожидание. Если восстановление базы затянулось, заставка просто держится
 * дольше — собственной паузы у неё нет.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    SklyankaTheme(dark = true) {
        Box(
            modifier = modifier
                .fillMaxSize()
                // Полотно — холодная плитка знака, а не фон тёмной среды интерфейса:
                // системная заставка рисует ту же краску (Theme.Sklyanka.Splash), и переход
                // к приложению не мигает (Р-57, §2.4 нормы).
                .background(markTileBackground(MarkEnvironment.Dark))
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag(TAG_SPLASH),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SklyankaSizes.BrandGap),
            ) {
                SklyankaMark(
                    contentDescription = stringResource(R.string.cd_app_mark),
                    size = SklyankaSizes.SplashMark,
                    environment = MarkEnvironment.Dark,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
                ) {
                    // Название — краска знака, не роль текста: знак и надпись вместе
                    // несут холодное стекло, и акцентная тема его не перекрашивает (Р-56).
                    // Кегль уменьшается сам, если слово не помещается (V-16).
                    BrandTitle(
                        text = stringResource(R.string.app_name),
                        style = SklyankaBrandType.SplashTitle,
                        color = flatMarkColor(MarkEnvironment.Dark),
                    )
                    Text(
                        // Прописные — отрисовкой, а не в ресурсе: читалка должна
                        // произносить слова, а не перечислять буквы (НФТ-30).
                        text = stringResource(R.string.brand_tagline).uppercase(interfaceLocale()),
                        style = SklyankaBrandType.SplashTagline,
                        // Макет задаёт подписи #8C837A, а издателю #6B635B. Холодные
                        // производные краски знака выводятся долей смешения с плиткой
                        // (§2.4): 0,62 даёт 6,23 к полотну, 0,54 — 5,05; обе выше AA,
                        // чего прежние роли тёплого серого не гарантировали.
                        color = markCaptionColor(),
                    )
                }
            }
            Text(
                text = stringResource(R.string.brand_vendor),
                style = SklyankaBrandType.SplashVendor,
                color = markVendorColor(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = SklyankaSizes.SplashVendorBottom),
            )
        }
    }
}

/** Опора экранного теста: на заставке нет ни одного действия, зацепиться не за что. */
const val TAG_SPLASH = "splash_screen"
