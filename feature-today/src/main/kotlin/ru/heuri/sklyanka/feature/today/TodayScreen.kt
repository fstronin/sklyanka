package ru.heuri.sklyanka.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import ru.heuri.sklyanka.domain.today.TodayLayout
import ru.heuri.sklyanka.ui.component.Banner
import ru.heuri.sklyanka.ui.component.EmptyState
import ru.heuri.sklyanka.ui.component.ScreenHeader
import ru.heuri.sklyanka.ui.component.ScreenHeaderLabels
import ru.heuri.sklyanka.ui.component.SklyankaProgressBar
import ru.heuri.sklyanka.ui.profile.ProfileAvatar
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType

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
        when (state) {
            TodayUiState.Opening -> Box(Modifier.fillMaxSize().weight(1f))
            is TodayUiState.Empty -> TodayEmpty(state, onIntent, Modifier.weight(1f))
            is TodayUiState.Content -> TodayContent(state, onIntent, Modifier.weight(1f))
        }
        TodayBottomBar(onIntent)
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
    Column(modifier = modifier.fillMaxWidth()) {
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
        // Полоса дня показывается, только когда есть что считать: «Принято 0 из 0»
        // не сообщает ничего, а место занимает.
        if (state.progress.hasPlan) {
            SklyankaProgressBar(
                progress = state.progress.fraction,
                label = state.progress.caption(dense = state.isDense),
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
const val TAG_NOTHING_TODAY = "nothing_today"
