package ru.stohastika.sklyanka.feature.meds.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ru.stohastika.sklyanka.feature.meds.BackGlyph
import ru.stohastika.sklyanka.feature.meds.GlyphButton
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.screenPadding
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.component.MedicationGlyph
import ru.stohastika.sklyanka.ui.component.ProfileIndication
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.QuietButtonKind
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import kotlin.math.ceil
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
    val scroll = rememberScrollState()
    val bounds = remember { MedCardScrollBounds() }

    // Отказ «Остаток не может быть отрицательным» печатается под полем и растит строку
    // пополнения: ряд действий уезжает вниз на её высоту, а прокрутка за ростом не следует.
    // Человек, нажавший «Сохранить», видел наполовину срезанную кнопку (V-39). Ряд доводится
    // до вида ровно настолько, насколько вышел, — мгновенно, а не анимацией: сдвиг короче
    // строки, и кадр «до/после» обязан быть устойчивым.
    LaunchedEffect(state.refill?.invalid) {
        if (state.refill?.invalid != true) return@LaunchedEffect
        // Раскладку со строкой отказа читаем следующим кадром: в кадре самого изменения положение
        // ряда ещё прежнее. Попыток две — на случай, когда рост строки доедет раскладкой кадром
        // позже; у ряда, который уже цел, вторая попытка ничего не делает.
        repeat(2) {
            withFrameNanos { }
            val beyond = bounds.actionsBeyondViewport
            if (beyond > 0f) scroll.scrollTo(scroll.value + ceil(beyond).toInt())
        }
    }

    // Корень — `Box`: шторка удаления лежит поверх содержимого, а не рядом с ним.
    // Соседом в столбце полноэкранное полотно шторки отняло бы высоту у содержимого
    // карточки — тот же приём, что у истории и снэкбара «Сегодня».
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(TAG_MEDICATION_CARD_SCREEN),
    ) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            // Индикация чужого профиля: шапка карточки собрана по месту, и надпись
            // «Профиль · Имя» с баннером выпадала из неё вместе с `ScreenHeader` —
            // дыра в защите, найденная исполнимой приёмкой SKL-15 (ПРФ-12, SKL-26).
            ProfileIndication()
            CardTopBar(onIntent)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_CARD_SCROLL)
                    .onGloballyPositioned { coordinates ->
                        bounds.reportViewportBottom(coordinates.boundsInRoot().bottom)
                    }
                    .verticalScroll(scroll)
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
                StockBlock(
                    state = state,
                    onIntent = onIntent,
                    onActionsBottom = { bottom -> bounds.reportActionsBottom(bottom) },
                )
                WeekBlock(state.week)
            }
            ArchiveAction(state, onIntent)
        }
        if (state.deleteConfirmation) {
            DeleteMedicationSheet(
                medicationName = state.title,
                onDismiss = { onIntent(MedCardIntent.DeleteDismissed) },
                onConfirm = { onIntent(MedCardIntent.DeleteConfirmed) },
            )
        }
    }
}

/**
 * Границы карточки, снятые с раскладки, — одной мерой и в координатах одного корня.
 *
 * Нижняя грань окна прокрутки и низ ряда «Отмена/Сохранить» приходят от `onGloballyPositioned`
 * так же, как границы закреплённой шапки (§7): по ним видно, вышел ли ряд за грань, и ничего
 * не приходится домысливать о высоте строки отказа. Держатся полями, а не состояниями: пишутся
 * они во время раскладки, и состояние будило бы композицию на каждой прокрутке без нужды.
 */
@Stable
internal class MedCardScrollBounds {

    private var viewportBottom: Float = 0f
    private var actionsBottom: Float = 0f

    /** Нижняя грань окна прокрутки; у неподвижного окна значение не меняется. */
    fun reportViewportBottom(bottom: Float) {
        viewportBottom = bottom
    }

    /** Низ ряда «Отмена/Сохранить» при текущей прокрутке. */
    fun reportActionsBottom(bottom: Float) {
        actionsBottom = bottom
    }

    /** Насколько ряд действий вышел за нижнюю грань окна; ноль и меньше — ряд цел (V-39). */
    val actionsBeyondViewport: Float get() = actionsBottom - viewportBottom
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
            MedicationGlyph(
                form = state.form,
                shape = state.subtitle.shape,
                color = if (state.isArchived) colors.textTertiary else colors.accent,
                size = SklyankaSizes.MedCardPlateGlyph,
                modifier = Modifier.testTag(TAG_CARD_PLATE_GLYPH),
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
 * и словами `docs/norm/04-behavior.md` задано именно замещение.
 *
 * Ниже, если удаление доступно (ПМ-24, ОВ-124), стоит тихая кнопка `Danger` «Удалить»
 * и пояснение условия: удалить можно только запись без единой отметки «принято».
 * При наличии `TAKEN` в разметке нет ни кнопки, ни пояснения — состояние `canDelete`.
 */
@Composable
private fun ArchiveAction(state: MedCardUiState, onIntent: (MedCardIntent) -> Unit) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .screenPadding()
            .padding(PaddingValues(top = SklyankaSpacing.Block, bottom = SklyankaSpacing.Screen)),
        horizontalAlignment = Alignment.CenterHorizontally,
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
        if (state.canDelete) {
            QuietButton(
                text = stringResource(R.string.card_delete),
                onClick = { onIntent(MedCardIntent.DeleteRequested) },
                kind = QuietButtonKind.Danger,
                modifier = Modifier
                    .padding(top = SklyankaSpacing.Small)
                    .testTag(TAG_DELETE),
            )
            Text(
                text = stringResource(R.string.card_delete_hint),
                style = SklyankaType.CaptionSmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SklyankaSpacing.Micro)
                    .testTag(TAG_DELETE_HINT),
            )
        }
    }
}

const val TAG_MEDICATION_CARD_SCREEN = "medication_card_screen"

/** Окно прокрутки карточки: по его нижней грани видно, срезан ли ряд действий (V-39). */
const val TAG_CARD_SCROLL = "med_card_scroll"
const val TAG_BACK = "med_card_back"
const val TAG_TITLE = "med_card_title"
const val TAG_CARD_PLATE_GLYPH = "med_card_plate_glyph"
const val TAG_SUBTITLE = "med_card_subtitle"
const val TAG_EDIT = "med_card_edit"
const val TAG_ARCHIVE = "med_card_archive"
const val TAG_RESTORE = "med_card_restore"
const val TAG_DELETE = "med_card_delete"
const val TAG_DELETE_HINT = "med_card_delete_hint"
const val TAG_FINISHED_OFFER = "med_card_finished_offer"
const val TAG_NOTICE = "med_card_notice"
