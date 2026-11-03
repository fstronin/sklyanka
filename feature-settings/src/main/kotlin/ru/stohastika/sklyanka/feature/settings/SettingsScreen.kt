package ru.stohastika.sklyanka.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.domain.pro.ProNotice
import ru.stohastika.sklyanka.domain.pro.ProStatus
import ru.stohastika.sklyanka.domain.profile.ProfileCreationDecision
import ru.stohastika.sklyanka.domain.reminder.SnoozeIntervals
import ru.stohastika.sklyanka.ui.component.BottomBar
import ru.stohastika.sklyanka.ui.component.BottomBarTab
import ru.stohastika.sklyanka.ui.component.CheckGlyph
import ru.stohastika.sklyanka.ui.component.FlatMark
import ru.stohastika.sklyanka.ui.component.ListPickerDialog
import ru.stohastika.sklyanka.ui.component.PinnedHeaderBar
import ru.stohastika.sklyanka.ui.component.ProMark
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.SklyankaSwitchRow
import ru.stohastika.sklyanka.ui.component.rememberScreenHeaderState
import ru.stohastika.sklyanka.ui.locale.interfaceLocale
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.pro.LocalProUiEnabled
import ru.stohastika.sklyanka.ui.text.proDate
import ru.stohastika.sklyanka.ui.text.proText
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.MarkEnvironment
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.token.flatMarkColor

/**
 * Экран «Настройки» (`design/screens/Settings.dc.html`).
 *
 * Третья вкладка и единственное место, где человек видит систему целиком: чей приём он
 * ведёт, как приложение будет напоминать, выданы ли разрешения, где лежат данные и чем
 * приложение не является. Строки часов на экране нет: шов вырезан из сборки
 * (Р-3, SKL-106).
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
fun SettingsScreen(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    val header = rememberScreenHeaderState()
    val labels = ScreenHeaderLabels(title = stringResource(R.string.settings_title))
    // Блок «Склянка Про» существует только в платном варианте сборки: в поставляемой
    // бесплатной его нет вовсе, и строки «Не оформлена» человек не видит (Р-86, ADR-0016).
    val proUiEnabled = LocalProUiEnabled.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(TAG_SETTINGS_SCREEN),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Крупный заголовок — контент и уезжает вместе с содержимым; «Настройки» — корень
            // без возврата и без действия, поэтому ряд несёт только компактный заголовок (§7).
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = SklyankaSpacing.Section),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Block),
            ) {
                item { ScreenHeader(labels = labels, pinning = header) }
                profilesBlock(state, onIntent)
                remindersBlock(state, onIntent)
                systemBlock(state, onIntent)
                // «Оформление» — между «Системой» и «Данными» (Р-67 п. 6, §5.17): оформление
                // принадлежит приложению, а не данным, и под строку «Склянка Про» ему не место.
                appearanceBlock(state, onIntent)
                dataBlock(onIntent)
                // Строка «Склянка Про» — только в платном варианте (Р-86): в бесплатной
                // сборке входа в покупку нет, и блока нет вовсе, а не «пустого».
                if (proUiEnabled) {
                    proBlock(state, onIntent)
                }
                item {
                    AboutPlate(state.version, modifier = Modifier.padding(horizontal = SklyankaSpacing.Section))
                }
            }
            BottomBar(
                selected = BottomBarTab.Settings,
                onSelect = { tab ->
                    when (tab) {
                        BottomBarTab.Today -> onIntent(SettingsIntent.OpenToday)
                        BottomBarTab.Meds -> onIntent(SettingsIntent.OpenMedications)
                        BottomBarTab.Settings -> Unit
                    }
                },
            )
            SettingsModals(state, onIntent)
        }
        // Ряд — слой над прокруткой: до порога его не видно, и места в потоке он не держит (§7).
        PinnedHeaderBar(
            labels = labels,
            state = header,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * Модальные слои настроек: откладывание и оформление.
 *
 * Вынесены отдельной функцией, а не тремя `if` в экране: экран от этого перестаёт
 * быть ветвлением на каждый диалог, а порядок слоёв остаётся в одном месте — затемнение
 * одно на все модальные поверхности (§2.3).
 */
@Composable
private fun SettingsModals(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    if (state.snoozePickerVisible) {
        SnoozePicker(state, onIntent)
    }
    // Диалоги блока «Оформление» — тем же слоем, что выбор интервала откладывания:
    // затемнение одно на все модальные слои (§2.3), и своё у диалога темы разошлось бы
    // с ним на первой же правке.
    if (state.themePickerVisible) {
        ThemePickerDialog(state, onIntent)
    }
    if (state.iconPickerVisible) {
        IconPickerDialog(state, onIntent)
    }
}

/**
 * Выбор интервала откладывания (SKL-78, §4.17, ОВ-98).
 *
 * Набор берётся из [SnoozeIntervals] — одного места на домен, — а подписи складываются
 * `plurals`: «15 минут» — это число с единицей, а не склеенная строка (НФТ-44).
 * Сохранённого значения может не быть в наборе (его принёс файл переноса): тогда
 * ни одна строка не отмечена, и человек выбирает значение заново, а не видит ложную
 * отметку чужого числа.
 */
@Composable
private fun SnoozePicker(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    ListPickerDialog(
        title = stringResource(R.string.settings_snooze),
        options = SnoozeIntervals.OPTIONS,
        selected = state.reminders.snoozeMinutes.takeIf(SnoozeIntervals::isAllowed),
        label = { minutes -> pluralStringResource(R.plurals.settings_snooze_minutes, minutes, minutes) },
        onSelect = { minutes -> onIntent(SettingsIntent.SetSnooze(minutes)) },
        onDismiss = { onIntent(SettingsIntent.DismissSnooze) },
        modifier = Modifier.testTag(TAG_SNOOZE_DIALOG),
    )
}

/**
 * Раздел «Профили» (ПРФ-19, ПРФ-26, ПРФ-7).
 *
 * Свой профиль первым с подписью «Мои лекарства», чужой — с числом лекарств и состоянием
 * дублирования. У активного чужого профиля следом, за разделителем, стоит строка удаления
 * (ОВ-7, SKL-36): удалить можно ровно того, в чьём контексте человек находится, и только
 * его одного. Само переключение — SKL-15: экран только сообщает, какую строку нажали.
 *
 * Строка «Добавить профиль» вернулась в SKL-121 вместе с мастером (П-7, Р-29): это функция
 * базы (ПРФ-9), и она встаёт последней в перечне профилей, **перед** строкой удаления —
 * разрушительная строка остаётся последней в разделе (ОВ-7).
 */
private fun LazyListScope.profilesBlock(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    item {
        SettingsBlock(stringResource(R.string.settings_rubric_profiles)) {
            state.profiles.forEachIndexed { index, profile ->
                if (index > 0) RowDivider()
                ProfileRow(
                    profile = profile,
                    onSelect = { onIntent(SettingsIntent.SelectProfile(profile.id)) },
                    onEdit = { onIntent(SettingsIntent.EditProfile(profile.id)) },
                )
            }
            RowDivider()
            AddProfileRow(state.profileCreation) { onIntent(SettingsIntent.AddProfile) }
            state.deletableProfile?.let { profile ->
                RowDivider(modifier = Modifier.testTag(TAG_DELETE_DIVIDER))
                DeleteProfileRow(profile) { onIntent(SettingsIntent.DeleteProfile) }
            }
        }
    }
}

/**
 * Строка «Добавить профиль» — последняя в перечне профилей и перед разрушительной
 * строкой удаления (§5.13, ПРФ-9).
 *
 * Три состояния, и все они — про следующее создание, а не про строку: без метки, пока
 * создание доступно; с меткой «Про», когда следующее создание платное (Р-84 п. 3, только
 * в платном варианте сборки — в бесплатном `ProMark` не рисует ничего); при пяти профилях —
 * без метки и со строкой причины, чтобы про предел человек узнал **до** нажатия (ПРФ-34).
 * Строка нажимается в любом состоянии и ведёт в мастер: витрину она не открывает
 * ни в одном из них — покупку открывает кнопка мастера (§5.12, §5.13).
 */
@Composable
private fun AddProfileRow(decision: ProfileCreationDecision, onClick: () -> Unit) {
    val colors = SklyankaTheme.colors
    SettingsRow(
        title = stringResource(R.string.profile_add_action),
        titleStyle = SklyankaType.BodyDefaultMedium,
        titleColor = colors.accent,
        // Причина предела — второй строкой подписи: про предел человек узнаёт заранее
        // (ПРФ-34), а не от недоступной кнопки в мастере.
        subtitle = when (decision) {
            ProfileCreationDecision.BlockedByLimit ->
                RowSubtitle(stringResource(R.string.profile_add_blocked_limit))

            else -> null
        },
        titleTrailing = when (decision) {
            // Метка — только у платного исхода (Р-71): когда доступ есть, норма не продаёт
            // то, что уже открыто; при пределе метки нет — предел не продаётся (ПРФ-34).
            ProfileCreationDecision.BlockedByPro ->
                ({ ProMark(modifier = Modifier.padding(start = SklyankaSpacing.Small)) })

            else -> null
        },
        leading = { ProfileAddPlate() },
        onClick = onClick,
        modifier = Modifier.testTag(TAG_ADD_PROFILE_ROW),
    )
}

/**
 * Строка удаления чужого профиля (ОВ-7, SKL-36).
 *
 * Последняя строка раздела, за разделителем, и только у активного чужого профиля: свой
 * не удаляется никогда (ПРФ-28). Строка называет профиль по имени — «тот самый» виден
 * до нажатия, а не выясняется на экране подтверждения.
 */
@Composable
private fun DeleteProfileRow(profile: ProfileRowState, onClick: () -> Unit) {
    SettingsRow(
        title = stringResource(R.string.settings_profile_delete, profile.name),
        onClick = onClick,
        modifier = Modifier.testTag(TAG_DELETE_PROFILE_ROW),
    )
}

/**
 * Строка профиля: тело переключает профиль, тихое «Изменить» — правит его (§5.14).
 *
 * Два действия одной строки разведены целями: тап по телу меняет активный профиль
 * (ПРФ-17, ПРФ-19) и это поведение не меняется, а слово справа открывает правку — в том
 * числе у неактивного профиля: чтобы исправить имя или цвет, в профиль не переключаются
 * (переключение меняет весь экран и является осознанным действием).
 */
@Composable
private fun ProfileRow(profile: ProfileRowState, onSelect: () -> Unit, onEdit: () -> Unit) {
    val colors = SklyankaTheme.colors
    // Владельцу — мягкий «принято», чужому — цвет его профиля из закрытого набора
    // дизайн-системы (ПРФ-15). Индикации «чужого» своему профилю не добавляется (ПРФ-16).
    val activeLabel = stringResource(R.string.cd_settings_profile_active)
    val plate = if (profile.isSelf) colors.takenSoft else ProfilePalette.of(profile.colorArgb).color
    val onPlate = if (profile.isSelf) colors.taken else colors.surface
    SettingsRow(
        title = if (profile.isSelf) stringResource(R.string.settings_profile_self) else profile.name,
        subtitle = if (profile.isSelf) null else RowSubtitle(profile.subtitle()),
        onClick = onSelect,
        modifier = Modifier.testTag(TAG_PROFILE_ROW),
        leading = { ProfileInitial(profile.initial(), plate, onPlate) },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
            ) {
                QuietButton(
                    text = stringResource(R.string.profile_row_edit),
                    onClick = onEdit,
                    modifier = Modifier.testTag(TAG_EDIT_PROFILE_ROW),
                )
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
    return name.trim().take(1).uppercase(interfaceLocale()).ifEmpty { fallback }
}

/**
 * Раздел «Напоминания» (`ОВ-11`, SKL-78).
 *
 * Три строки, а не четыре: «Тихие часы» в продукт не идут.
 *
 * «Системные настройки уведомлений» — переход в системный экран уведомлений приложения,
 * и значения у строки нет (Р-52):
 * выбор звука и важности живёт в системном экране каналов, а фальшивое «Мягкий сигнал»
 * описывало звук, которого не существует (ПМ-15 п. 3, ОВ-98). «Отложить на» — обычная
 * настройка: показывает текущий интервал и открывает выбор из набора [SnoozeIntervals].
 */
private fun LazyListScope.remindersBlock(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    item {
        SettingsBlock(stringResource(R.string.settings_rubric_reminders)) {
            SettingsRow(
                title = stringResource(R.string.settings_sound),
                subtitle = RowSubtitle(stringResource(R.string.settings_sound_caption)),
                onClick = { onIntent(SettingsIntent.OpenSound) },
                modifier = Modifier.testTag(TAG_SOUND_ROW),
            )
            RowDivider()
            SklyankaSwitchRow(
                label = stringResource(R.string.settings_persistent),
                caption = stringResource(R.string.settings_persistent_caption),
                checked = state.reminders.persistentEnabled,
                onCheckedChange = { onIntent(SettingsIntent.SetPersistentReminder(it)) },
            )
            RowDivider()
            SettingsRow(
                title = stringResource(R.string.settings_snooze),
                value = pluralStringResource(
                    R.plurals.settings_snooze_minutes,
                    state.reminders.snoozeMinutes,
                    state.reminders.snoozeMinutes,
                ),
                onClick = { onIntent(SettingsIntent.OpenSnooze) },
                modifier = Modifier.testTag(TAG_SNOOZE_ROW),
            )
        }
    }
}

/**
 * Раздел «Система» (НФТ-8, НАП-20).
 *
 * Строки «Часы Huawei» в сборке нет: шов часов вырезан (Р-3, SKL-106), и строка
 * состояния связи, которая не может измениться, была бы ложью.
 */
private fun LazyListScope.systemBlock(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    item {
        SettingsBlock(stringResource(R.string.settings_rubric_system)) {
            PermissionsRow(state.permissions) { onIntent(SettingsIntent.OpenPermissions) }
        }
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

/** Раздел «Данные»: история приёмов (SKL-18), перенос данных (SKL-19) и политика. */
private fun LazyListScope.dataBlock(onIntent: (SettingsIntent) -> Unit) {
    item {
        SettingsBlock(stringResource(R.string.settings_rubric_data)) {
            SettingsRow(
                title = stringResource(R.string.settings_history),
                onClick = { onIntent(SettingsIntent.OpenHistory) },
                modifier = Modifier.testTag(TAG_HISTORY_ROW),
            )
            RowDivider()
            // «Экспорт истории» — сразу после «Истории приёмов» и до «Переноса данных» (Р-85 п. 2):
            // строка стоит рядом с тем, что выгружает, и до того, что переносит всё.
            SettingsRow(
                title = stringResource(R.string.settings_export_history),
                subtitle = RowSubtitle(stringResource(R.string.settings_export_note)),
                // Метка остаётся в разметке ради платного варианта, а в бесплатной сборке
                // `ProMark` не рисует ничего — выгрузка CSV доступна всем (Р-86).
                // Зазор между последним словом подписи и меткой — `Small` (§5.9).
                titleTrailing = { ProMark(modifier = Modifier.padding(start = SklyankaSpacing.Small)) },
                onClick = { onIntent(SettingsIntent.OpenHistoryExport) },
                modifier = Modifier.testTag(TAG_EXPORT_ROW),
            )
            RowDivider()
            SettingsRow(
                title = stringResource(R.string.transfer_title),
                subtitle = RowSubtitle(stringResource(R.string.settings_transfer_caption)),
                onClick = { onIntent(SettingsIntent.OpenDataTransfer) },
                modifier = Modifier.testTag(TAG_TRANSFER_ROW),
            )
            RowDivider()
            SettingsRow(
                title = stringResource(R.string.settings_policy),
                onClick = { onIntent(SettingsIntent.OpenPolicy) },
                modifier = Modifier.testTag(TAG_POLICY_ROW),
            )
        }
    }
}

/**
 * Блок «Склянка Про» — пятый блок настроек, после «Данных» и перед плашкой «О приложении»
 * (Р-67 п. 6, SKL-122).
 *
 * **В бесплатной сборке этого блока нет.** Строки «Не оформлена» в поставляемом продукте
 * не существует: блок зовётся только при [LocalProUiEnabled] (§5.9, Р-86, ADR-0016).
 *
 * Рубрики у блока нет, и это не упущение: прописная «СКЛЯНКА ПРО» над строкой «Склянка Про»
 * повторила бы подпись дважды, а макет даёт блок карточкой без рубрики
 * (`design/screens/Pro.dc.html`, фигура 5).
 *
 * Метки «Про» на строке тоже нет: её называют сами слова «Склянка Про» (Р-67 п. 5) — метка
 * ставится там, где нужен признак платности у элемента, название которого о цене не говорит.
 */
private fun LazyListScope.proBlock(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    item {
        Box(modifier = Modifier.padding(horizontal = SklyankaSpacing.Section)) {
            SettingsCard {
                SettingsRow(
                    title = proText(ProNotice.SHEET_TITLE),
                    value = proRowValue(state.pro),
                    onClick = { onIntent(SettingsIntent.OpenPro) },
                    modifier = Modifier.testTag(TAG_PRO_ROW),
                )
            }
        }
    }
}

/**
 * Значение строки блока: «Не оформлена» либо «Активна до <дата>».
 *
 * Дата подставляется платформенным форматтером с локалью интерфейса (НФТ-43а), а не склейкой
 * строк: в шести языках дата стоит в разных местах предложения, и порядок слов задаёт язык,
 * а не код.
 */
@Composable
private fun proRowValue(pro: ProStatus): String {
    val until = pro.until ?: return proText(pro.notice)
    return proText(pro.notice, proDate(until))
}

/**
 * Плашка «О приложении».
 *
 * Формулировка «не является медицинским изделием» стоит здесь и видна сразу: ни нажатия,
 * ни раскрытия, ни прокрутки длинного текста (`design/SPEC-original.md` §1, принцип `П-4`).
 * Нажимается ровно одна строка — адрес сайта студии (ПМ-16): системный переход
 * `ACTION_VIEW` открывает браузер, порта сети в приложении по-прежнему нет
 * (НФТ-14, НФТ-15), а проверки обновлений на экране нет и быть не может.
 */
@Composable
private fun AboutPlate(version: String, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    val markLabel = stringResource(R.string.cd_settings_about_mark)
    val site = stringResource(R.string.settings_about_site)
    val siteUrl = stringResource(R.string.settings_about_site_url)
    val uriHandler = LocalUriHandler.current
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
            // Краска знака, а не роль интерфейса: холодное стекло остаётся холодным
            // островом и внутри тёплого списка настроек (Р-56, §2.4 нормы).
            color = flatMarkColor(MarkEnvironment.Light),
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
            Text(
                text = site,
                style = SklyankaType.CaptionSmall,
                color = colors.accent,
                modifier = Modifier
                    .clickable { uriHandler.openUri(siteUrl) }
                    .minTouchTarget()
                    .testTag(TAG_ABOUT_SITE),
            )
        }
    }
}

/**
 * Рубрика и карточка раздела одним блоком: они всегда идут парами.
 *
 * Боковое поле экрана стоит на блоке, а не на списке: крупный заголовок — тоже элемент
 * списка, и он обязан занимать всю ширину, тогда как карточкам нужно поле (§7).
 */
@Composable
internal fun SettingsBlock(rubric: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = SklyankaSpacing.Section),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        SectionRubric(rubric)
        SettingsCard { content() }
    }
}

internal const val TAG_DELETE_PROFILE_ROW = "settings_delete_profile_row"

/**
 * Строка «Добавить профиль» (SKL-121, ПРФ-9).
 *
 * Отдельный тег: по нему экранный стенд и поход находят вход в мастер, и он же называет
 * поверхность в реестре `docs/tasks/SKL-140/TDD.md` §7.
 */
internal const val TAG_ADD_PROFILE_ROW = "settings_add_profile_row"

/** Тихая строка «Изменить» в строке профиля — вход в правку (§5.14, SKL-121). */
internal const val TAG_EDIT_PROFILE_ROW = "settings_edit_profile_row"
internal const val TAG_DELETE_DIVIDER = "settings_delete_divider"
internal const val TAG_HISTORY_ROW = "settings_history_row"

/**
 * Строка «Экспорт истории» (SKL-119, Р-35, §5.16).
 *
 * Отдельный тег: по нему поход и экранный стенд находят вход в шторку экспорта, и он же
 * называет поверхность в реестре `docs/tasks/SKL-140/TDD.md` §7.
 */
internal const val TAG_EXPORT_ROW = "settings_export_row"
internal const val TAG_TRANSFER_ROW = "settings_transfer_row"

internal const val TAG_POLICY_ROW = "settings_policy_row"

/**
 * Строка блока «Склянка Про» (SKL-122, Р-67 п. 6).
 *
 * Отдельный тег, а не тег строки политики: по нему поход и экранный стенд находят вход
 * в витрину, и он же называет поверхность в реестре `docs/tasks/SKL-140/TDD.md` §7.
 * В бесплатной сборке узла с этим тегом на экране нет (§5.9, Р-86).
 */
internal const val TAG_PRO_ROW = "settings_pro_row"
