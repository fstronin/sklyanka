package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.locale.interfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.MarkEnvironment
import ru.stohastika.sklyanka.ui.token.SklyankaBrandType
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.flatMarkColor
import ru.stohastika.sklyanka.ui.token.markCaptionColor
import ru.stohastika.sklyanka.ui.token.markTileBackground

/**
 * Логотип с названием: знак слева, «Склянка» и «приём лекарств» справа на тёмном фоне
 * (`design/screens/SklyankaLockup.dc.html`).
 *
 * Компонент, а не картинка. Логотип понадобится материалам магазинов (SKL-22),
 * и снимать его оттуда придётся с живого экрана: экспортированный PNG расходится
 * со знаком при первой же правке геометрии, а расхождение логотипа с иконкой
 * в магазине видно всем и сразу.
 *
 * Собственного фона у компонента нет по краям намеренно: он рисует свою тёмную
 * плашку и не претендует на экран, в который его вставили.
 */
@Composable
fun SklyankaLockup(modifier: Modifier = Modifier) {
    // Фирменный блок всегда стоит в тёмной среде — и в макете, и в магазине.
    SklyankaTheme(dark = true) {
        Row(
            modifier = modifier
                // Полотно — холодная плитка знака, а не фон тёмной среды интерфейса (§2.4).
                .background(markTileBackground(MarkEnvironment.Dark))
                .padding(SklyankaSpacing.Screen)
                .testTag(TAG_LOCKUP),
            horizontalArrangement = Arrangement.spacedBy(SklyankaSizes.BrandGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SklyankaMark(
                contentDescription = stringResource(R.string.cd_app_mark),
                size = SklyankaSizes.LockupMark,
                environment = MarkEnvironment.Dark,
            )
            Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default)) {
                // Название — краска знака, подпись — её производная (§2.4): блок целиком
                // холодный, и роли текста тёплой тёмной среды здесь не читаются.
                // Кегль уменьшается сам, если слово не помещается (V-16).
                BrandTitle(
                    text = stringResource(R.string.app_name),
                    style = SklyankaBrandType.LockupTitle,
                    color = flatMarkColor(MarkEnvironment.Dark),
                )
                Text(
                    text = stringResource(R.string.brand_tagline).uppercase(interfaceLocale()),
                    style = SklyankaBrandType.LockupTagline,
                    color = markCaptionColor(),
                )
            }
        }
    }
}

/** Опора экранного теста: у логотипа нет ни одного интерактивного элемента. */
const val TAG_LOCKUP = "sklyanka_lockup"
