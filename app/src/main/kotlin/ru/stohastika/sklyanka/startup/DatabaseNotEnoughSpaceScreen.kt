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
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Блокирующий экран нехватки места перед обновлением схемы (`ADR-0012` п. 3).
 *
 * Что человек должен понять: приложение не сломалось, история цела, обновление отложено —
 * и причина ровно одна, она измерима и её видно ([missingMegabytes]). Поэтому экран называет
 * число и **не даёт ни одного способа продолжить**: продолжить нельзя — миграцию без копии
 * норма запрещает, а открыть не мигрировавшую схему значит либо упасть, либо тронуть данные
 * (МГ-2).
 *
 * Действий нет намеренно, как и на экране отказа на базе новее: приложение не распоряжается
 * чужими файлами и не убирает место само, а кнопка «Повторить» обещала бы то, чего приложение
 * не проверяет. Освободив место, человек открывает приложение — и оно продолжается с того же
 * места, потому что ни одна строка не переписана.
 *
 * Вид — как у заставки: тёмное полотно, марка приложения, тема тёмная независимо от настройки.
 * Это состояние установки, а не ошибка приложения: с данными ничего не случилось.
 */
@Composable
fun DatabaseNotEnoughSpaceScreen(missingMegabytes: Long, modifier: Modifier = Modifier) {
    SklyankaTheme(dark = true) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(SklyankaTheme.colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag(TAG_DATABASE_NOT_ENOUGH_SPACE),
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
                    text = stringResource(R.string.database_no_space_title),
                    style = SklyankaType.Title,
                    color = SklyankaTheme.colors.text,
                )
                Text(
                    text = stringResource(R.string.database_no_space_missing, missingMegabytes),
                    style = SklyankaType.BodyDefault,
                    color = SklyankaTheme.colors.text,
                )
                Text(
                    text = stringResource(R.string.database_no_space_explanation),
                    style = SklyankaType.BodyDefault,
                    color = SklyankaTheme.colors.textMuted,
                )
                Text(
                    text = stringResource(R.string.database_no_space_data_kept),
                    style = SklyankaType.Caption,
                    color = SklyankaTheme.colors.textMuted,
                )
            }
        }
    }
}

/** Тег экрана: приёмка ищет его, а не текст заголовка. */
const val TAG_DATABASE_NOT_ENOUGH_SPACE = "database_not_enough_space"
