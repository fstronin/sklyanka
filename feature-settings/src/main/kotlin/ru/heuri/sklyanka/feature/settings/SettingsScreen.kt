package ru.heuri.sklyanka.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.heuri.sklyanka.domain.watch.WatchLinkState
import ru.heuri.sklyanka.ui.component.CheckGlyph
import ru.heuri.sklyanka.ui.component.FlatMark
import ru.heuri.sklyanka.ui.component.ScreenHeader
import ru.heuri.sklyanka.ui.component.ScreenHeaderLabels
import ru.heuri.sklyanka.ui.component.SklyankaSwitchRow
import ru.heuri.sklyanka.ui.locale.InterfaceLocale
import ru.heuri.sklyanka.ui.modifier.minTouchTarget
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.ProfilePalette
import ru.heuri.sklyanka.ui.token.SklyankaShapes
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType

/**
 * Экран «Настройки» (`design/screens/Settings.dc.html`).
 *
 * Третья вкладка и единственное место, где человек видит систему целиком: чей приём он
 * ведёт, как приложение будет напоминать, на связи ли часы, выданы ли разрешения, где
 * лежат данные и чем приложение не является.
 *
 * Своего состояния экран не держит: всё приходит в [state], наружу уходят намерения.
 * Каждая подпись под строкой — утверждение о состоянии системы, и ложное утверждение
 * здесь дороже отсутствующего (`TDD.md §2`).
 *
 * **Строки «Тихие часы» на экране нет.** В макете она есть, в продукте её не будет:
 * настройка, глушащая напоминание, противоречит НАП-1, а звук — свойство неизменяемого
 * канала (`ADR-0004`). Это зафиксированное расхождение макета и продукта (решение
 * по `ОВ-11`), а не забытая строка, поэтому и заготовки под неё в коде нет.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
    canOpenWatchSetup: Boolean = true,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(TAG_SETTINGS_SCREEN),
    ) {
        ScreenHeader(labels = ScreenHeaderLabels(title = stringResource(R.string.settings_title)))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = SklyankaSpacing.Section,
                end = SklyankaSpacing.Section,
                bottom = SklyankaSpacing.Section,
            ),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Block),
        ) {
            profilesBlock(state, onIntent)
            remindersBlock(state, onIntent)
            systemBlock(state, canOpenWatchSetup, onIntent)
            dataBlock(onIntent)
            item { AboutPlate(state.version) }
        }
        SettingsBottomBar(onIntent)
    }
}

/**
 * Раздел «Профили» (ПРФ-19, ПРФ-26, ПРФ-7).
 *
 * Свой профиль первым с подписью «Мои лекарства», чужой — с числом лекарств и состоянием
 * дублирования, последней строкой «Добавить профиль». Само переключение — SKL-15: экран
 * только сообщает, какую строку нажали.
 */
private fun LazyListScope.profilesBlock(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    item {
        SettingsBlock(stringResource(R.string.settings_rubric_profiles)) {
            state.profiles.forEachIndexed { index, profile ->
                if (index > 0) RowDivider()
                ProfileRow(profile) { onIntent(SettingsIntent.SelectProfile(profile.id)) }
            }
            if (state.profiles.isNotEmpty()) RowDivider()
            AddProfileRow { onIntent(SettingsIntent.AddProfile) }
        }
    }
}

@Composable
private fun ProfileRow(profile: ProfileRowState, onClick: () -> Unit) {
    val colors = SklyankaTheme.colors
    // Владельцу — мягкий «принято», чужому — цвет его профиля из закрытого набора
    // дизайн-системы (ПРФ-15). Индикации «чужого» своему профилю не добавляется (ПРФ-16).
    val activeLabel = stringResource(R.string.cd_settings_profile_active)
    val plate = if (profile.isSelf) colors.takenSoft else ProfilePalette.of(profile.colorArgb).color
    val onPlate = if (profile.isSelf) colors.taken else colors.surface
    SettingsRow(
        title = if (profile.isSelf) stringResource(R.string.settings_profile_self) else profile.name,
        subtitle = if (profile.isSelf) null else RowSubtitle(profile.subtitle()),
        onClick = onClick,
        modifier = Modifier.testTag(TAG_PROFILE_ROW),
        leading = { ProfileInitial(profile.initial(), plate, onPlate) },
        trailing = {
            if (profile.isActive) {
                // Отметка активного профиля названа словами: статус не передаётся одним
                // значком без подписи для чтения с экрана (НФТ-30, НФТ-33).
                CheckGlyph(
                    color = colors.taken,
                    modifier = Modifier
                        .semantics { contentDescription = activeLabel }
                        .testTag(TAG_ACTIVE_PROFILE),
                )
            } else {
                ChevronGlyph(colors.iconFaint)
            }
        },
    )
}

/**
 * Подпись строки чужого профиля: «4 лекарства · напоминания дублируются мне» (ПРФ-7).
 *
 * Собирается из ресурсов, а не склейкой строк в коде: и число лекарств, и разделитель
 * живут в `strings.xml` (НФТ-42, НФТ-44).
 */
@Composable
private fun ProfileRowState.subtitle(): String {
    val medications = pluralStringResource(R.plurals.settings_profile_medications, medicationCount, medicationCount)
    if (!mirrorReminders) return medications
    val mirrored = stringResource(R.string.settings_profile_mirrored)
    return stringResource(R.string.settings_profile_subtitle, medications, mirrored)
}

/**
 * Инициал профиля.
 *
 * У владельца — подпись «Я» из макета, а не первая буква его имени: имя владельца человек
 * может переименовать (`ПРФ-27` не решён), а подпись строки — нет.
 */
@Composable
private fun ProfileRowState.initial(): String {
    val fallback = stringResource(R.string.settings_profile_self_initial)
    if (isSelf) return fallback
    return name.trim().take(1).uppercase(InterfaceLocale.VALUE).ifEmpty { fallback }
}

/**
 * «Добавить профиль» (`ПРФ-9`, `ОВ-3`).
 *
 * Экран добавления профиля не зафиксирован, и строка ведёт в механику SKL-15; куда именно —
 * решает тот, кто собрал маршруты.
 */
@Composable
private fun AddProfileRow(onClick: () -> Unit) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minTouchTarget()
            .testTag(TAG_ADD_PROFILE)
            .padding(horizontal = SklyankaSpacing.Block, vertical = SklyankaSpacing.Large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        DashedCircle(colors.lineInteractive)
        Text(
            text = stringResource(R.string.settings_profile_add),
            style = SklyankaType.BodyDefaultStrong,
            color = colors.accent,
        )
    }
}

/**
 * Раздел «Напоминания» (`ОВ-11`).
 *
 * Три строки, а не четыре: «Тихие часы» в продукт не идут. Звук и интервал откладывания
 * показывают текущее значение и переходов не обещают — экрана выбора звука в первой версии
 * нет (`docs/spec/09-scope.md` §2), а значения откладывания задаёт SKL-13.
 */
private fun LazyListScope.remindersBlock(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    item {
        SettingsBlock(stringResource(R.string.settings_rubric_reminders)) {
            SettingsValueRow(
                title = stringResource(R.string.settings_sound),
                value = stringResource(R.string.settings_sound_value),
                caption = stringResource(R.string.settings_sound_caption),
            )
            RowDivider()
            SklyankaSwitchRow(
                label = stringResource(R.string.settings_persistent),
                caption = stringResource(R.string.settings_persistent_caption),
                checked = state.reminders.persistentEnabled,
                onCheckedChange = { onIntent(SettingsIntent.SetPersistentReminder(it)) },
            )
            RowDivider()
            SettingsValueRow(
                title = stringResource(R.string.settings_snooze),
                value = pluralStringResource(
                    R.plurals.settings_snooze_minutes,
                    state.reminders.snoozeMinutes,
                    state.reminders.snoozeMinutes,
                ),
            )
        }
    }
}

/** Раздел «Часы и система» (ЧАС-18, НФТ-8, НАП-20). */
private fun LazyListScope.systemBlock(
    state: SettingsUiState,
    canOpenWatchSetup: Boolean,
    onIntent: (SettingsIntent) -> Unit,
) {
    item {
        SettingsBlock(stringResource(R.string.settings_rubric_system)) {
            WatchRow(state.watch, canOpenWatchSetup) { onIntent(SettingsIntent.OpenWatchSetup) }
            RowDivider()
            PermissionsRow(state.permissions) { onIntent(SettingsIntent.OpenPermissions) }
        }
    }
}

@Composable
private fun WatchRow(link: WatchLinkState, canOpen: Boolean, onClick: () -> Unit) {
    val colors = SklyankaTheme.colors
    // Подключённое состояние — цветом «принято», как в макете; остальные нейтральны:
    // «часы не настроены» не отказ, и красить его в предупреждение было бы неправдой
    // (ЧАС-19).
    val subtitle = when (link) {
        is WatchLinkState.Connected -> stringResource(R.string.settings_watch_connected, link.model)
        WatchLinkState.NotConnected -> stringResource(R.string.settings_watch_disconnected)
        WatchLinkState.NotConfigured -> stringResource(R.string.settings_watch_not_configured)
    }
    val line = RowSubtitle(subtitle, if (link is WatchLinkState.Connected) colors.taken else null)
    if (canOpen) {
        SettingsRow(
            title = stringResource(R.string.settings_watch),
            subtitle = line,
            onClick = onClick,
            modifier = Modifier.testTag(TAG_WATCH_ROW),
        )
    } else {
        SettingsStateRow(
            title = stringResource(R.string.settings_watch),
            subtitle = line,
            modifier = Modifier.testTag(TAG_WATCH_ROW),
        )
    }
}

/**
 * Строка «Разрешения и автозапуск» (НФТ-8, НФТ-52).
 *
 * Акцент ставится только на то, что человек может исправить сам. Непроверяемый автозапуск
 * EMUI (НАП-20) в число отказов не входит и назван словами: иначе строка либо врала бы
 * зелёным, либо навсегда осталась бы красной.
 */
@Composable
private fun PermissionsRow(permissions: PermissionsSummary, onClick: () -> Unit) {
    val colors = SklyankaTheme.colors
    val subtitle = when {
        permissions.denied == 1 -> stringResource(R.string.settings_permissions_denied_one)
        permissions.denied > 1 -> stringResource(R.string.settings_permissions_denied_many, permissions.denied)
        permissions.manual > 0 -> stringResource(R.string.settings_permissions_granted_manual)
        else -> stringResource(R.string.settings_permissions_granted)
    }
    SettingsRow(
        title = stringResource(R.string.settings_permissions),
        subtitle = RowSubtitle(subtitle, if (permissions.hasDenied) colors.accent else null),
        onClick = onClick,
        modifier = Modifier.testTag(TAG_PERMISSIONS_ROW),
    )
}

/** Раздел «Данные»: история приёмов (SKL-18) и перенос данных (SKL-19). */
private fun LazyListScope.dataBlock(onIntent: (SettingsIntent) -> Unit) {
    item {
        SettingsBlock(stringResource(R.string.settings_rubric_data)) {
            SettingsRow(
                title = stringResource(R.string.settings_history),
                onClick = { onIntent(SettingsIntent.OpenHistory) },
                modifier = Modifier.testTag(TAG_HISTORY_ROW),
            )
            RowDivider()
            SettingsRow(
                title = stringResource(R.string.transfer_title),
                subtitle = RowSubtitle(stringResource(R.string.settings_transfer_caption)),
                onClick = { onIntent(SettingsIntent.OpenDataTransfer) },
                modifier = Modifier.testTag(TAG_TRANSFER_ROW),
            )
        }
    }
}

/**
 * Плашка «О приложении».
 *
 * Формулировка «не является медицинским изделием» стоит здесь и видна сразу: ни нажатия,
 * ни раскрытия, ни прокрутки длинного текста (`design/SPEC-original.md` §1, принцип `П-4`).
 * Плашка не нажимается вовсе — ни ссылки наружу, ни проверки обновлений на экране нет
 * и быть не может: порта сети в проекте не существует (НФТ-14, НФТ-15).
 */
@Composable
private fun AboutPlate(version: String, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    val markLabel = stringResource(R.string.cd_settings_about_mark)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.neutralSoft, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block)
            .testTag(TAG_ABOUT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        FlatMark(
            color = colors.textTertiary,
            size = SklyankaSizes.IconPlate,
            modifier = Modifier.semantics { contentDescription = markLabel },
        )
        Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair)) {
            Text(
                text = stringResource(R.string.settings_about_app, version),
                style = SklyankaType.BodyDefaultStrong,
                color = colors.text,
            )
            Text(
                text = stringResource(R.string.settings_about_vendor),
                style = SklyankaType.CaptionSmall,
                color = colors.textMuted,
            )
        }
    }
}

/** Рубрика и карточка раздела одним блоком: они всегда идут парой. */
@Composable
private fun SettingsBlock(rubric: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        SectionRubric(rubric)
        SettingsCard { content() }
    }
}

internal const val TAG_ADD_PROFILE = "settings_add_profile"
internal const val TAG_HISTORY_ROW = "settings_history_row"
internal const val TAG_TRANSFER_ROW = "settings_transfer_row"
