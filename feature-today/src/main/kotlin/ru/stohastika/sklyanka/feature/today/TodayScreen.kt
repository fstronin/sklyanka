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
import ru.stohastika.sklyanka.domain.undo.UndoableDeletion
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.component.BottomBar
import ru.stohastika.sklyanka.ui.component.BottomBarTab
import ru.stohastika.sklyanka.ui.component.EmptyState
import ru.stohastika.sklyanka.ui.component.PinnedHeaderBar
import ru.stohastika.sklyanka.ui.component.ProgressAction
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.ScreenHeaderState
import ru.stohastika.sklyanka.ui.component.SklyankaProgressBar
import ru.stohastika.sklyanka.ui.component.Snackbar
import ru.stohastika.sklyanka.ui.component.rememberScreenHeaderState
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
 * дублирует, и `docs/norm/04-behavior.md` предписывает дублирование входа убрать.
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
        val deletion = when (state) {
            is TodayUiState.Empty -> state.deletion
            is TodayUiState.Content -> state.deletion
            TodayUiState.Opening -> null
        }
        // Содержимое и снэкбар живут в одном Box над нижней панелью: снэкбар всплывает
        // поверх контента, а панель остаётся видимой и нажимаемой (ОВ-81/ОВ-6, SKL-30).
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (state) {
                TodayUiState.Opening -> Box(Modifier.fillMaxSize())
                is TodayUiState.Empty -> TodayEmpty(state, onIntent, Modifier.fillMaxSize())
                is TodayUiState.Content -> TodayContent(state, onIntent, Modifier.fillMaxSize())
            }
            UndoSnackbar(
                undo = (state as? TodayUiState.Content)?.undo,
                deletion = deletion,
                onIntent = onIntent,
            )
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
private fun BoxScope.UndoSnackbar(undo: UndoNotice?, deletion: UndoableDeletion?, onIntent: (TodayIntent) -> Unit) {
    var lastUndo by remember { mutableStateOf<UndoNotice?>(null) }
    var lastDeletion by remember { mutableStateOf<UndoableDeletion?>(null) }
    LaunchedEffect(undo, deletion) {
        if (undo != null) lastUndo = undo
        if (deletion != null) lastDeletion = deletion
    }
    val visible = undo != null || deletion != null
    val rise = with(LocalDensity.current) { SklyankaSpacing.Block.roundToPx() }
    val enterTiming = motionTiming(MotionPhase.SnackbarEnter)
    val exitTiming = motionTiming(MotionPhase.SnackbarExit)
    AnimatedVisibility(
        visible = visible,
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
        // Ячейка «последнее действие» одна (ОВ-124): показывается либо отметка,
        // либо удаление; одновременными они не бывают.
        if (undo != null) {
            lastUndo?.let { notice ->
                Snackbar(
                    text = notice.text(),
                    actionLabel = stringResource(R.string.today_undo_action),
                    onAction = { onIntent(TodayIntent.UndoMark(notice.intakeIds)) },
                )
            }
        } else {
            lastDeletion?.let { shown ->
                Snackbar(
                    text = stringResource(R.string.today_deleted_snackbar, shown.medicationName),
                    actionLabel = stringResource(R.string.today_undo_action),
                    onAction = { onIntent(TodayIntent.UndoDeletion(shown.medicationId)) },
                )
            }
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
        // Объяснение сброса показывается и над пустым состоянием: стёртая база
        // открывает ровно его, и без баннера потеря осталась бы неназванной (МГ-8).
        if (state.databaseReset) {
            DatabaseResetBanner(
                onDismiss = { onIntent(TodayIntent.DismissResetExplanation) },
                modifier = Modifier.padding(
                    start = SklyankaSpacing.Section,
                    end = SklyankaSpacing.Section,
                    bottom = SklyankaSpacing.Large,
                ),
            )
        }
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
    val header = rememberScreenHeaderState()
    val labels = ScreenHeaderLabels(
        title = stringResource(R.string.today_title),
        overline = state.date.headerDate(),
    )
    Box(
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Сверху поля нет: шапка начинается своим верхним полем (30 dp, в чужом профиле
            // 24 dp) — лишние 8 dp сдвигали её вниз (§4, V-13).
            contentPadding = PaddingValues(bottom = SklyankaSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Section),
        ) {
            item(key = KEY_TODAY_HEADER) { TodayHeader(state, onIntent, labels, header) }
            when (val layout = state.layout) {
                is TodayLayout.Plain -> plainLanes(layout, state, onIntent)
                is TodayLayout.Dense -> denseSections(layout, state, onIntent)
            }
            prnBlock(state, onIntent)
            if (state.nothingToday) todayItem(key = KEY_NOTHING_TODAY) { NothingToday() }
        }
        // Ряд — слой над прокруткой, а не сосед в столбце: до порога его не видно,
        // и места в потоке он не держит (§7). Аватар-индикатор профиля на пороге
        // переезжает сюда из шапки — тот же аватар 44 dp (§5.8).
        PinnedHeaderBar(
            labels = labels,
            state = header,
            modifier = Modifier.align(Alignment.TopCenter),
            trailing = { ProfileAvatar(onOpenProfiles = { onIntent(TodayIntent.OpenSettings) }) },
        )
    }
}

/** Шапка: дата и заголовок, полоса прогресса дня, плашка остатка (`Main.dc.html`). */
@Composable
private fun TodayHeader(
    state: TodayUiState.Content,
    onIntent: (TodayIntent) -> Unit,
    labels: ScreenHeaderLabels,
    header: ScreenHeaderState,
) {
    // Аватар-индикатор профиля стоит в прокручиваемой шапке — справа от даты и заголовка
    // (§7, `Main.dc.html`); на пороге его место берёт закреплённый ряд (§5.8).
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large)) {
        ScreenHeader(
            labels = labels,
            pinning = header,
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
            // Объяснение сброса данных — после предупреждения о ненадёжности: сначала
            // о том, что ломается сейчас, потом о том, что уже случилось (МГ-8).
            if (state.databaseReset) {
                DatabaseResetBanner(onDismiss = { onIntent(TodayIntent.DismissResetExplanation) })
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
}

/**
 * Объяснение сброса данных после повреждения базы (МГ-8, Р-87 п. 2).
 *
 * Немодальный баннер предупреждения с одним действием — «Закрыть»: человек узнаёт,
 * что данные потеряны не по его вине, и сам решает, когда убрать объяснение.
 * Закрытый баннер не возвращается до следующего пересоздания базы.
 */
@Composable
private fun DatabaseResetBanner(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Banner(
        text = stringResource(R.string.today_db_reset_banner),
        kind = BannerKind.Warning,
        actionLabel = stringResource(R.string.today_db_reset_action),
        onAction = onDismiss,
        modifier = modifier.testTag(TAG_DB_RESET_BANNER),
    )
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
 * (`docs/norm/04-behavior.md` прямо это запрещает).
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

/**
 * Элемент ленты «Сегодня»: боковое поле экрана стоит на элементе, а не на списке.
 *
 * Крупный заголовок — тоже элемент списка, и он занимает всю ширину (§7): поле,
 * поставленное на список, сузило бы и его, и заголовок перестал бы совпадать
 * с закреплённым рядом.
 */
internal fun LazyListScope.todayItem(key: Any, content: @Composable () -> Unit) {
    item(key = key) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = SklyankaSpacing.Section)) {
            content()
        }
    }
}

/** Обычный день: ленты приёмов подряд (`Main.dc.html`). */
private fun LazyListScope.plainLanes(
    layout: TodayLayout.Plain,
    state: TodayUiState.Content,
    onIntent: (TodayIntent) -> Unit,
) {
    layout.groups.forEach { group ->
        todayItem(key = "lane-${group.time}") { TimeLane(group, state, onIntent) }
    }
}

const val TAG_TODAY_SCREEN = "today_screen"
const val TAG_TODAY_EMPTY = "today_empty"
const val TAG_DAY_PROGRESS = "day_progress"
const val TAG_STOCK_BANNER = "stock_banner"
const val TAG_RELIABILITY_BANNER = "reliability_banner"
const val TAG_DB_RESET_BANNER = "db_reset_banner"
const val TAG_NOTHING_TODAY = "nothing_today"

/** Ключи элементов ленты: у списка «Сегодня» состав меняется от состояния. */
private const val KEY_TODAY_HEADER = "today-header"
private const val KEY_NOTHING_TODAY = "nothing-today"
