package ru.stohastika.sklyanka.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.ui.component.SklyankaMark
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.MarkEnvironment
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.token.markTileBackground
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Блокирующий экран установки старой сборки поверх новой базы (Р-44, `ADR-0012` п. 7).
 *
 * Что человек должен понять с этого экрана: приложение не сломалось, данные целы, лечится
 * это установкой новой версии. Поэтому экран называет и причину, и следствие словами,
 * и **не даёт ни одного способа продолжить**: продолжить нельзя — база новее, и эта сборка
 * её не знает.
 *
 * Действий нет намеренно: приложение не умеет ставить само себя, и кнопка «Обновить»
 * вела бы в никуда — это был бы тот же лгущий вход, что лже-отчёт (П-7). Ни «Сегодня»,
 * ни настройки здесь не рисуются: любой экран продукта открыл бы базу.
 *
 * Вид — как у заставки: тёмное полотно, марка приложения, тема тёмная независимо от
 * настройки. Это не «ошибка приложения», а состояние установки, и пугать человека красным
 * нечем: с данными ничего не случилось.
 */
@Composable
fun DatabaseTooNewScreen(modifier: Modifier = Modifier) {
    SklyankaTheme(dark = true) {
        Box(
            modifier = modifier
                .fillMaxSize()
                // Полотно — холодная плитка знака, как у заставки: системная заставка
                // рисует ту же краску (Theme.Sklyanka.Splash), и продолжение не даёт шва (Р-57).
                .background(markTileBackground(MarkEnvironment.Dark))
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag(TAG_DATABASE_TOO_NEW),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SklyankaSpacing.Block),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Section),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SklyankaMark(
                    contentDescription = stringResource(UiR.string.cd_app_mark),
                    size = SklyankaSizes.SplashMark,
                )
                Text(
                    text = stringResource(R.string.database_too_new_title),
                    style = SklyankaType.Title,
                    color = SklyankaTheme.colors.text,
                )
                Text(
                    text = stringResource(R.string.database_too_new_explanation),
                    style = SklyankaType.BodyDefault,
                    color = SklyankaTheme.colors.textMuted,
                )
                Text(
                    text = stringResource(R.string.database_too_new_data_kept),
                    style = SklyankaType.Caption,
                    color = SklyankaTheme.colors.textMuted,
                )
            }
        }
    }
}

/** Тег экрана отказа: приёмка ищет его, а не текст заголовка. */
const val TAG_DATABASE_TOO_NEW = "database_too_new"
