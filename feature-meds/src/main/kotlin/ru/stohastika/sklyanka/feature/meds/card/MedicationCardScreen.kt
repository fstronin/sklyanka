package ru.stohastika.sklyanka.feature.meds.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.feature.meds.BackGlyph
import ru.stohastika.sklyanka.feature.meds.GlyphButton
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.ReleaseFormGlyph
import ru.stohastika.sklyanka.feature.meds.screenPadding
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.component.ProfileIndication
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Карточка лекарства (`design/screens/MedCard.dc.html`).
 *
 * Без состояния: всё приходит в [state], наружу уходят только намерения. Экран
 * прокручивается целиком — при крупном системном шрифте блоки растут по высоте, и
 * кнопка «В архив» обязана остаться достижимой (НФТ-31).
 *
 * Строки выбора звука напоминания здесь нет и не будет: индивидуальный звук отложен
 * во вторую версию (НАП-27), а вход на экран выбора звука не нарисован нигде.
 */
@Composable
fun MedicationCardScreen(state: MedCardUiState, onIntent: (MedCardIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(TAG_MEDICATION_CARD_SCREEN),
    ) {
        // Индикация чужого профиля: шапка карточки собрана по месту, и надпись
        // «Профиль · Имя» с баннером выпадала из неё вместе с `ScreenHeader` —
        // дыра в защите, найденная исполнимой приёмкой SKL-15 (ПРФ-12, SKL-26).
        ProfileIndication()
        CardTopBar(onIntent)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .screenPadding()
                .padding(bottom = SklyankaSpacing.Block),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
        ) {
            Title(state)
            state.notice?.let { notice -> NoticeBanner(notice, onIntent) }
            if (state.courseFinishedOffer) {
                FinishedCourseOffer(onIntent)
            }
            ScheduleBlock(state.schedule)
            state.course?.let { course -> CourseBlock(course) }
            StockBlock(state, onIntent)
            WeekBlock(state.week)
        }
        ArchiveAction(state, onIntent)
    }
}

/**
 * Шапка карточки: стрелка «Назад» слева, «Изменить» справа (`MedCard.dc.html`).
 *
 * Собрана по месту, а не компонентом `ScreenHeader` вида «Вложенная»: тот всегда рисует
 * заголовок экрана, а заголовок этой карточки — название лекарства, которое стоит ниже,
 * рядом с плиткой значка 62 dp. Нарисовать его дважды значило бы прочитать макет вольно.
 *
 * У стрелки нет видимого текста, поэтому подпись для чтения с экрана обязательна и взята
 * дословно из `aria-label` макета (НФТ-30); цель нажатия доводится до 44 dp модификатором,
 * а не размером рисунка (НФТ-29).
 */
@Composable
private fun CardTopBar(onIntent: (MedCardIntent) -> Unit) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SklyankaSpacing.Small,
                end = SklyankaSpacing.Section,
                top = SklyankaSizes.HeaderTopPaddingProfile,
                bottom = SklyankaSpacing.Small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        GlyphButton(
            contentDescription = stringResource(UiR.string.cd_back),
            onClick = { onIntent(MedCardIntent.Back) },
            bordered = false,
            modifier = Modifier.testTag(TAG_BACK),
        ) {
            BackGlyph(color = colors.text)
        }
        Box(modifier = Modifier.weight(1f))
        QuietButton(
            text = stringResource(R.string.card_edit),
            onClick = { onIntent(MedCardIntent.Edit) },
            modifier = Modifier.testTag(TAG_EDIT),
        )
    }
}

/** Плитка значка, название и подпись из четырёх сведений (ЭКР-7). */
@Composable
private fun Title(state: MedCardUiState) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().testTag(TAG_TITLE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        Box(
            modifier = Modifier
                .size(SklyankaSizes.MedCardPlate)
                .background(
                    if (state.isArchived) colors.neutralSoft else colors.accentSoft,
                    SklyankaShapes.Large,
                ),
            contentAlignment = Alignment.Center,
        ) {
            ReleaseFormGlyph(
                form = state.form,
                color = if (state.isArchived) colors.textTertiary else colors.accent,
                size = SklyankaSizes.MedCardPlateGlyph,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro)) {
            Text(
                text = state.title,
                style = SklyankaType.Title,
                color = if (state.isArchived) colors.archiveText else colors.text,
            )
            Text(
                text = state.subtitle.text(),
                style = SklyankaType.Caption,
                color = colors.textMuted,
                modifier = Modifier.testTag(TAG_SUBTITLE),
            )
        }
    }
}

/**
 * Предложение убрать законченный курс в архив (ОВ-42).
 *
 * Приложение предлагает, человек подтверждает: курс не уходит из списка сам, но и не
 * остаётся напоминать о лекарстве, которое больше не пьют.
 */
@Composable
private fun FinishedCourseOffer(onIntent: (MedCardIntent) -> Unit) {
    Banner(
        text = stringResource(R.string.card_offer_course_finished),
        kind = BannerKind.Warning,
        actionLabel = stringResource(R.string.card_offer_action),
        onAction = { onIntent(MedCardIntent.ArchiveFinishedCourse) },
        modifier = Modifier.testTag(TAG_FINISHED_OFFER),
    )
}

/** То, что нельзя проглотить: отказ в возврате из архива и отказ пополнения. */
@Composable
private fun NoticeBanner(notice: MedCardNotice, onIntent: (MedCardIntent) -> Unit) {
    val text = when (notice) {
        MedCardNotice.PeriodNeedsUpdate -> stringResource(R.string.card_notice_period_needs_update)
        MedCardNotice.StockNegative -> stringResource(R.string.error_stock_negative)
    }
    Banner(
        text = text,
        kind = BannerKind.Warning,
        actionLabel = stringResource(R.string.card_notice_dismiss),
        onAction = { onIntent(MedCardIntent.NoticeDismissed) },
        modifier = Modifier.testTag(TAG_NOTICE),
    )
}

/**
 * Действие внизу экрана: «В архив» у активного курса, «Вернуть» у архивированного.
 *
 * Одна кнопка на два действия, а не две рядом: архивный вид карточки макета не имеет,
 * и словами `docs/spec/03-screens.md` задано именно замещение.
 */
@Composable
private fun ArchiveAction(state: MedCardUiState, onIntent: (MedCardIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .screenPadding()
            .padding(PaddingValues(top = SklyankaSpacing.Block, bottom = SklyankaSpacing.Screen)),
    ) {
        SecondaryButton(
            text = if (state.isArchived) {
                stringResource(R.string.card_restore)
            } else {
                stringResource(R.string.card_archive)
            },
            onClick = {
                onIntent(if (state.isArchived) MedCardIntent.Restore else MedCardIntent.Archive)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(if (state.isArchived) TAG_RESTORE else TAG_ARCHIVE),
        )
    }
}

const val TAG_MEDICATION_CARD_SCREEN = "medication_card_screen"
const val TAG_BACK = "med_card_back"
const val TAG_TITLE = "med_card_title"
const val TAG_SUBTITLE = "med_card_subtitle"
const val TAG_EDIT = "med_card_edit"
const val TAG_ARCHIVE = "med_card_archive"
const val TAG_RESTORE = "med_card_restore"
const val TAG_FINISHED_OFFER = "med_card_finished_offer"
const val TAG_NOTICE = "med_card_notice"
