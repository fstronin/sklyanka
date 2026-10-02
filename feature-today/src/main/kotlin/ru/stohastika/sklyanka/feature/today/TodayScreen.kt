package ru.stohastika.sklyanka.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ru.stohastika.sklyanka.domain.reminder.ReminderWarning
import ru.stohastika.sklyanka.domain.today.TodayLayout
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.component.BottomBar
import ru.stohastika.sklyanka.ui.component.BottomBarTab
import ru.stohastika.sklyanka.ui.component.EmptyState
import ru.stohastika.sklyanka.ui.component.ProgressAction
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.SklyankaProgressBar
import ru.stohastika.sklyanka.ui.component.Snackbar
import ru.stohastika.sklyanka.ui.motion.MotionPhase
import ru.stohastika.sklyanka.ui.motion.motionTiming
import ru.stohastika.sklyanka.ui.profile.ProfileAvatar
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Главный экран продукта (`design/screens/Main.dc.html`).
 *
 * Состояния не держит вовсе: всё приходит в [state], наружу уходят только намерения.
 * Порядок блоков — за макетом: шапка с датой, полоса прогресса дня, плашка остатка,
 * ленты приёмов по времени, блок «По потребности», нижняя панель.
 *
 * Кнопки-шестерёнки в шапке нет намеренно: вкладка «Настройки» нижней панели её
 * дублирует, и `docs/spec/03-screens.md` предписывает дублирование входа убрать.
 * Аватар профиля в шапке при этом есть — он индикатор, а не вторая шестерёнка, и ведёт
 * в список профилей настроек (ПРФ-19, решение по `ОВ-54`).
 *
 * Оформление профиля экран не собирает и не выбирает: полосу, подкраску, надпись
 * «Профиль · Имя», баннер и акцент даёт `ProfileChrome` в корне приложения (ПРФ-12).
 * SKL-08 держала здесь `ProfileStripe` и поля профиля в состоянии — SKL-15 подняла их
 * в корень: экранов в контексте профиля одиннадцать, и решение на стороне экрана — это
 * одиннадцать мест, где его можно забыть, причём забыть незаметно.
 */
@Composable
fun TodayScreen(state: TodayUiState, onIntent: (TodayIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(TAG_TODAY_SCREEN),
    ) {
        val undo = (state as? TodayUiState.Content)?.undo
        // Содержимое и снэкбар живут в одном Box над нижней панелью: снэкбар всплывает
        // поверх контента, а панель остаётся видимой и нажимаемой (ОВ-81/ОВ-6, SKL-30).
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (state) {
                TodayUiState.Opening -> Box(Modifier.fillMaxSize())
                is TodayUiState.Empty -> TodayEmpty(state, onIntent, Modifier.fillMaxSize())
                is TodayUiState.Content -> TodayContent(state, onIntent, Modifier.fillMaxSize())
            }
            UndoSnackbar(undo = undo, onIntent = onIntent)
        }
        BottomBar(
            selected = BottomBarTab.Today,
            onSelect = { tab ->
                when (tab) {
                    BottomBarTab.Meds -> onIntent(TodayIntent.OpenMedicationList)
                    BottomBarTab.Settings -> onIntent(TodayIntent.OpenSettings)
                    BottomBarTab.Today -> Unit
                }
            },
        )
    }
}

@Composable
private fun BoxScope.UndoSnackbar(undo: UndoNotice?, onIntent: (TodayIntent) -> Unit) {
    var lastUndo by remember { mutableStateOf<UndoNotice?>(null) }
    LaunchedEffect(undo) { if (undo != null) lastUndo = undo }
    val rise = with(LocalDensity.current) { SklyankaSpacing.Block.roundToPx() }
    val enterTiming = motionTiming(MotionPhase.SnackbarEnter)
    val exitTiming = motionTiming(MotionPhase.SnackbarExit)
    AnimatedVisibility(
        visible = undo != null,
        enter = slideInVertically(
            animationSpec = tween(enterTiming.durationMillis, easing = enterTiming.easing),
            initialOffsetY = { rise },
        ) + fadeIn(tween(enterTiming.durationMillis, easing = enterTiming.easing)),
        exit = slideOutVertically(
            animationSpec = tween(exitTiming.durationMillis, easing = exitTiming.easing),
            targetOffsetY = { rise },
        ) + fadeOut(tween(exitTiming.durationMillis, easing = exitTiming.easing)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                start = SklyankaSpacing.Section,
                end = SklyankaSpacing.Section,
                bottom = SklyankaSpacing.Medium,
            ),
    ) {
        lastUndo?.let { notice ->
            Snackbar(
                text = notice.text(),
                actionLabel = stringResource(R.string.today_undo_action),
                onAction = { onIntent(TodayIntent.UndoMark(notice.intakeIds)) },
            )
        }
    }
}

/**
 * Пустое состояние первого запуска (`Empty.dc.html`).
 *
 * Шапка урезана до даты и заголовка, полосы прогресса нет: считать нечего.
 */
@Composable
private fun TodayEmpty(state: TodayUiState.Empty, onIntent: (TodayIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            labels = ScreenHeaderLabels(
                title = stringResource(R.string.today_title),
                overline = state.date.headerDate(),
            ),
            trailing = { ProfileAvatar(onOpenProfiles = { onIntent(TodayIntent.OpenSettings) }) },
        )
        Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
            EmptyState(
                title = stringResource(R.string.today_empty_title),
                text = stringResource(R.string.today_empty_text),
                actionLabel = stringResource(R.string.today_empty_action),
                onAction = { onIntent(TodayIntent.AddMedication) },
                modifier = Modifier.testTag(TAG_TODAY_EMPTY),
            )
        }
    }
}

@Composable
private fun TodayContent(state: TodayUiState.Content, onIntent: (TodayIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(state.confirmingGroup) {
                if (state.confirmingGroup != null) {
                    // Наблюдатель стоит на родителе: в главном проходе указателя дети
                    // получают событие раньше, и нажатие по карточке или кнопке уже
                    // потреблено — сюда доходит только нажатие «в никуда», то есть
                    // ровно «вне кнопки» (ОВ-32). Прокрутка — перетаскивание, а не тап:
                    // `detectTapGestures` его не поймает.
                    detectTapGestures { onIntent(TodayIntent.DismissGroupConfirm) }
                }
            },
    ) {
        TodayHeader(state, onIntent)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = SklyankaSpacing.Section,
                end = SklyankaSpacing.Section,
                top = SklyankaSpacing.Small,
                bottom = SklyankaSpacing.Section,
            ),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Section),
        ) {
            when (val layout = state.layout) {
                is TodayLayout.Plain -> plainLanes(layout, state, onIntent)
                is TodayLayout.Dense -> denseSections(layout, state, onIntent)
            }
            prnBlock(state, onIntent)
            if (state.nothingToday) item { NothingToday() }
        }
    }
}

/** Шапка: дата и заголовок, полоса прогресса дня, плашка остатка (`Main.dc.html`). */
@Composable
private fun TodayHeader(state: TodayUiState.Content, onIntent: (TodayIntent) -> Unit) {
    ScreenHeader(
        labels = ScreenHeaderLabels(
            title = stringResource(R.string.today_title),
            overline = state.date.headerDate(),
        ),
        // Аватар — индикатор активного профиля; нажатие ведёт в список профилей
        // настроек, а не раскрывает меню (ПРФ-19, `ОВ-54`).
        trailing = { ProfileAvatar(onOpenProfiles = { onIntent(TodayIntent.OpenSettings) }) },
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SklyankaSpacing.Section),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        // Предупреждение о ненадёжности — первым в шапке, выше полосы прогресса и плашки
        // остатка (ОВ-82): пока напоминания не придут, об этом надо сказать раньше всего.
        state.reliability?.let { warning ->
            Banner(
                text = warning.text(),
                kind = BannerKind.Warning,
                actionLabel = stringResource(R.string.today_reliability_action),
                onAction = { onIntent(TodayIntent.OpenPermissions) },
                modifier = Modifier.testTag(TAG_RELIABILITY_BANNER),
            )
        }
        // Полоса дня показывается, только когда есть что считать: «Принято 0 из 0»
        // не сообщает ничего, а место занимает.
        if (state.progress.hasPlan) {
            SklyankaProgressBar(
                progress = state.progress.fraction,
                label = state.progress.caption(dense = state.isDense),
                action = ProgressAction(
                    label = stringResource(R.string.today_history_link),
                    onClick = { onIntent(TodayIntent.OpenHistory) },
                ),
                modifier = Modifier.testTag(TAG_DAY_PROGRESS),
            )
        }
        state.stockWarning?.let { notice ->
            Banner(
                text = notice.text(),
                actionLabel = stringResource(R.string.today_stock_action),
                onAction = { onIntent(TodayIntent.OpenStock(notice.medicationId)) },
                modifier = Modifier.testTag(TAG_STOCK_BANNER),
            )
        }
    }
}

/**
 * Причина ненадёжности словами человека — одна формулировка на причину (ОВ-82, ОВ-107).
 *
 * Текст берётся из ресурсов, а правило «какая причина» оставлено домену: разметка
 * не пересобирает `ReminderReliability` из флагов (НФТ-42). Те же формулировки называют
 * карточка «Настроить надёжность» и диагностика — перечисление одно на всех.
 */
@Composable
private fun ReminderWarning.text(): String = stringResource(
    when (this) {
        ReminderWarning.NOTIFICATIONS_OFF -> R.string.today_reliability_notifications_off
        ReminderWarning.NOTIFICATION_CHANNEL_OFF -> R.string.today_reliability_channel_off
        ReminderWarning.EXACT_ALARMS_OFF -> R.string.today_reliability_exact_alarms_off
        ReminderWarning.FULL_SCREEN_INTENT_OFF -> R.string.today_reliability_full_screen_off
        ReminderWarning.BOTH_OFF -> R.string.today_reliability_both_off
    },
)

/**
 * Лекарства есть, а приёмов на сегодня нет (ОВ-34).
 *
 * Тихая подпись, а не призыв завести первое лекарство: человеку, у которого лекарства
 * уже заведены, пустое состояние первого запуска сообщало бы неправду
 * (`docs/spec/03-screens.md` прямо это запрещает).
 */
@Composable
private fun NothingToday() {
    Text(
        text = stringResource(R.string.today_nothing_today),
        style = SklyankaType.BodyDefault,
        color = SklyankaTheme.colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SklyankaSpacing.Screen)
            .testTag(TAG_NOTHING_TODAY),
    )
}

/** Обычный день: ленты приёмов подряд (`Main.dc.html`). */
private fun LazyListScope.plainLanes(
    layout: TodayLayout.Plain,
    state: TodayUiState.Content,
    onIntent: (TodayIntent) -> Unit,
) {
    layout.groups.forEach { group ->
        item(key = "lane-${group.time}") { TimeLane(group, state, onIntent) }
    }
}

const val TAG_TODAY_SCREEN = "today_screen"
const val TAG_TODAY_EMPTY = "today_empty"
const val TAG_DAY_PROGRESS = "day_progress"
const val TAG_STOCK_BANNER = "stock_banner"
const val TAG_RELIABILITY_BANNER = "reliability_banner"
const val TAG_NOTHING_TODAY = "nothing_today"
