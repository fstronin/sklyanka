package ru.heuri.sklyanka.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import ru.heuri.sklyanka.ui.R
import ru.heuri.sklyanka.ui.component.ProfileStripe
import ru.heuri.sklyanka.ui.preview.BannerGallery
import ru.heuri.sklyanka.ui.preview.ButtonGallery
import ru.heuri.sklyanka.ui.preview.ChipGallery
import ru.heuri.sklyanka.ui.preview.EmptyStateGallery
import ru.heuri.sklyanka.ui.preview.IntakeCardGallery
import ru.heuri.sklyanka.ui.preview.PREVIEW_WIDTH
import ru.heuri.sklyanka.ui.preview.ProfileStripeGallery
import ru.heuri.sklyanka.ui.preview.ProgressGallery
import ru.heuri.sklyanka.ui.preview.ScreenHeaderGallery
import ru.heuri.sklyanka.ui.preview.SwitchGallery
import ru.heuri.sklyanka.ui.preview.TextFieldGallery
import ru.heuri.sklyanka.ui.preview.TypographyGallery
import ru.heuri.sklyanka.ui.theme.ProfileAccent
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.ProfileColor
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType

/**
 * Каталог компонентов — экран отладочной сборки `core-ui`.
 *
 * Живёт в `src/debug`, поэтому в релизный артефакт не попадает и в навигацию
 * приложения не входит. Его ценность — критерий 10: состояние, которого нет в
 * каталоге, не существует, и видно это сразу, а не на четвёртом экране четвёртого
 * этапа.
 *
 * Тема ставится параметром [profile]: тот же каталог, открытый в чужом профиле,
 * показывает подкрашенные поверхности и цвет профиля на действиях — без единой
 * правки в самих компонентах (критерии 1 и 2).
 */
@Composable
fun Showcase(modifier: Modifier = Modifier, profile: ProfileAccent = ProfileAccent.Owner, dark: Boolean = false) {
    SklyankaTheme(profile = profile, dark = dark) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(SklyankaTheme.colors.background)
                .verticalScroll(rememberScrollState())
                .testTag(TAG_SHOWCASE),
        ) {
            ProfileStripe()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SklyankaSpacing.Section),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Screen),
            ) {
                Text(
                    text = stringResource(R.string.catalog_title),
                    style = SklyankaType.TitleScreen,
                    color = SklyankaTheme.colors.text,
                )
                TypographyGallery()
                IntakeCardGallery()
                ChipGallery()
                ButtonGallery()
                TextFieldGallery()
                SwitchGallery()
                BannerGallery()
                ProgressGallery()
                EmptyStateGallery()
                ScreenHeaderGallery()
                ProfileStripeGallery()
            }
        }
    }
}

@Preview(name = "Catalog, owner", widthDp = PREVIEW_WIDTH, heightDp = CATALOG_PREVIEW_HEIGHT)
@Composable
internal fun ShowcaseOwnerPreview() = Showcase()

@Preview(name = "Catalog, foreign profile", widthDp = PREVIEW_WIDTH, heightDp = CATALOG_PREVIEW_HEIGHT)
@Composable
internal fun ShowcaseForeignPreview() = Showcase(profile = ProfileAccent.Foreign(ProfileColor.Terracotta))

@Preview(
    name = "Catalog, font 200%",
    widthDp = PREVIEW_WIDTH,
    heightDp = CATALOG_PREVIEW_HEIGHT,
    fontScale = 2.0f,
)
@Composable
internal fun ShowcaseLargeFontPreview() = Showcase()

/** Высота снимка каталога: он длинный, и обрезать его до экрана телефона незачем. */
private const val CATALOG_PREVIEW_HEIGHT = 2400

const val TAG_SHOWCASE = "showcase"
