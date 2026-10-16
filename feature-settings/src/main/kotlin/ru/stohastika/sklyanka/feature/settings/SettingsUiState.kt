package ru.stohastika.sklyanka.feature.settings

import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import ru.stohastika.sklyanka.domain.pro.ProStatus
import ru.stohastika.sklyanka.domain.pro.ProSubscription
import ru.stohastika.sklyanka.domain.profile.ProfileCreationDecision
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues

/**
 * Что показывает экран «Настройки» (`design/screens/Settings.dc.html`).
 *
 * Половина экрана — утверждения о состоянии, которое приложению не принадлежит: выданы ли
 * разрешения. Поэтому в состоянии нет ни одного поля, которое можно было
 * бы «запомнить с онбординга»: всё, что не поток, читается заново при каждом показе
 * (НФТ-52). Ложная подпись «всё в порядке» здесь стоит пропущенного приёма.
 *
 * Тихих часов в состоянии нет и заготовки под них не будет: настройка, глушащая
 * напоминание, противоречит НАП-1, а звук — свойство неизменяемого канала (`ADR-0004`).
 * Расхождение с макетом зафиксировано решением по `ОВ-11`.
 */
data class SettingsUiState(
    val profiles: List<ProfileRowState> = emptyList(),
    /**
     * Профиль, который можно удалить: активный и чужой (ОВ-7, SKL-36).
     *
     * Null в своём профиле: строки удаления у владельца нет никогда (ПРФ-28).
     * Считается тем же проходом, что строки списка: второй ответ «кто сейчас активен»
     * разошёлся бы с отметкой активного профиля, и строка удаления назвала бы не того.
     */
    val deletableProfile: ProfileRowState? = null,
    val reminders: ReminderSettingsValues = ReminderSettingsValues(),
    /**
     * Открыт ли выбор интервала откладывания (SKL-78).
     *
     * Видимость живёт в состоянии экрана, а не в `remember` композиции: стенд приёмки
     * пересобирает композицию на каждом шаге, и `remember` терял бы выбор между «открыл»
     * и «выбрал» (`TDD.md §5.2`).
     */
    val snoozePickerVisible: Boolean = false,
    val permissions: PermissionsSummary = PermissionsSummary(),
    /** Версия приложения — та, что собрана, а не та, что нарисована в макете. */
    val version: String = "",
    /**
     * Состояние подписки «Склянка Про» для строки блока (SKL-122, Р-67 п. 6).
     *
     * Читается из кэша последнего ответа магазина по `SettingsViewModel.refresh` — при показе
     * экрана, при возвращении в приложение и при закрытии шторки витрины: после покупки или
     * восстановления человек возвращается на этот же экран, не покидая приложения, и строка
     * обязана назвать новое состояние, а не то, что было на момент открытия (`ADR-0015`, SKL-122).
     */
    val pro: ProStatus = ProStatus(ProSubscription.None),
    /**
     * Исход гейта на **следующее** заведение профиля (SKL-121, ПРФ-33, Р-84 п. 3).
     *
     * Строка «Добавить профиль» показывает метку «Про» тогда и только тогда, когда
     * следующее создание платное ([ProfileCreationDecision.BlockedByPro]), а предел пяти
     * называет словами до нажатия ([ProfileCreationDecision.BlockedByLimit], ПРФ-34).
     * Решение принимает доменный [ru.stohastika.sklyanka.domain.profile.ProfileCreationGate]
     * по числу заведённых профилей и доступу — не экран.
     */
    val profileCreation: ProfileCreationDecision = ProfileCreationDecision.Allowed,
    /**
     * Выбранная акцентная тема (§5.17, Р-31).
     *
     * Умолчание — базовая «Олива» (Р-20): она показывает и то, что человек не выбирал
     * ничего, и то, что выбранное не читается с диска мгновенно. Поток темы подменяет её
     * первым же значением, и на экране это не мигает: тема применяется ко всему приложению
     * до первой композиции (`TDD.md` §5).
     */
    val accentTheme: AccentTheme = AccentTheme.DEFAULT,
    /** Выбранный вариант иконки (§5.17, Р-31). Умолчание — базовая «Ледяная» (Р-56). */
    val iconVariant: IconVariant = IconVariant.DEFAULT,
    /**
     * Открыт ли выбор темы; видимость живёт в состоянии, а не в `remember` композиции,
     * по той же причине, что у выбора интервала откладывания: стенд приёмки пересобирает
     * композицию на каждом шаге (`TDD.md` §5.2).
     */
    val themePickerVisible: Boolean = false,
    /** Открыт ли выбор иконки. */
    val iconPickerVisible: Boolean = false,
)

/**
 * Строка профиля в списке (ПРФ-7, ПРФ-26).
 *
 * Число лекарств и признак дублирования лежат в строке, а не вычисляются вёрсткой: подпись
 * «4 лекарства · напоминания дублируются мне» — утверждение о данных, и собирать его
 * должен тот, кто эти данные читал.
 */
data class ProfileRowState(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val isSelf: Boolean,
    val isActive: Boolean,
    val medicationCount: Int,
    val mirrorReminders: Boolean,
)

/**
 * Сводка разрешений для строки «Разрешения и автозапуск» (НФТ-8, НАП-15).
 *
 * Невыданные и непроверяемые считаются раздельно и намеренно. Автозапуск EMUI в публичный
 * API не выставлен (НАП-20), и если сложить его с отказами, строка никогда не станет
 * нейтральной — человек, выдавший всё, что можно выдать, будет вечно видеть акцентную
 * подпись и перестанет ей верить. Если же выдать его за выданный, приложение соврёт.
 * Поэтому: отказы — числом и акцентом, непроверяемое — словами «проверьте вручную».
 */
data class PermissionsSummary(val denied: Int = 0, val manual: Int = 0) {

    /** Есть ли то, что человек может исправить сам. Акцент ставится только по этому признаку. */
    val hasDenied: Boolean get() = denied > 0

    companion object {

        fun of(states: Map<ReminderPermission, PermissionState>): PermissionsSummary = PermissionsSummary(
            denied = states.count { it.value == PermissionState.DENIED },
            manual = states.count { it.value == PermissionState.UNKNOWN },
        )
    }
}
