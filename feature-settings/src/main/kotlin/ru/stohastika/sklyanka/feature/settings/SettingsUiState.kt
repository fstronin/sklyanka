package ru.stohastika.sklyanka.feature.settings

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
