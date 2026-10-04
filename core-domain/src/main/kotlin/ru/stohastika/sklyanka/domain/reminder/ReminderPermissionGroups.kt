package ru.stohastika.sklyanka.domain.reminder

import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission

/**
 * Группа строки «Разрешений» по последствию (AC-9, ОВ-126).
 *
 * Группа отвечает на вопрос «что чинить первым»: [FIX_FIRST] — без этого напоминания
 * не придут или опоздают, [CAN_DEFER] — придут не полностью.
 */
enum class PermissionGroup { FIX_FIRST, CAN_DEFER }

/**
 * Маркер строки «Разрешений» (AC-10).
 *
 * [WARNING] — «не выдано» системного разрешения, [NEUTRAL] — «проверьте вручную»
 * и фирменные ограничения EMUI. Смысл несут и маркер, и слово состояния (НФТ-30).
 */
enum class PermissionMarker { WARNING, NEUTRAL }

/**
 * Правила карточки «Разрешения»: что показывать, в какую группу и с каким маркером.
 *
 * Живут в домене, чтобы порядок и группировка не расползлись по экрану: карточка только
 * рисует то, что решило правило.
 */
object ReminderPermissionGroups {

    /** Строка появляется, только если требует действия (AC-8). */
    fun requiresAction(permission: ReminderPermission, state: PermissionState): Boolean =
        permission != ReminderPermission.BOOT_RECEIVER && state != PermissionState.GRANTED

    fun groupOf(permission: ReminderPermission): PermissionGroup = when (permission) {
        ReminderPermission.NOTIFICATIONS,
        ReminderPermission.NOTIFICATION_CHANNEL,
        ReminderPermission.EXACT_ALARMS,
        ReminderPermission.AUTOSTART,
        -> PermissionGroup.FIX_FIRST

        ReminderPermission.FULL_SCREEN_INTENT,
        ReminderPermission.DND_POLICY_ACCESS,
        -> PermissionGroup.CAN_DEFER

        // Приёмник загрузки человеку исправлять нечего (ПЛ-9); в списке не появляется.
        ReminderPermission.BOOT_RECEIVER -> PermissionGroup.FIX_FIRST
    }

    /** «Не выдано» — предупреждение; «проверьте вручную» и ограничения — нейтрально (AC-10). */
    fun markerOf(permission: ReminderPermission, state: PermissionState): PermissionMarker = when {
        state == PermissionState.DENIED && permission in SYSTEM_GRANTABLE -> PermissionMarker.WARNING
        else -> PermissionMarker.NEUTRAL
    }

    /**
     * Состояние, которым строка называет себя человеку (AC-10, НФТ-30).
     *
     * «Не беспокоить» — ограничение с нейтральным «проверьте вручную», а не право
     * с предупреждением «не выдано»: политика доступа не чинится выдачей, её последствие
     * человек проверяет сам. Поэтому невыданный доступ называется [PermissionState.UNKNOWN]
     * и несёт нейтральный маркер. Прочие разрешения называют состояние как есть.
     */
    fun stateShown(permission: ReminderPermission, state: PermissionState): PermissionState =
        if (permission == ReminderPermission.DND_POLICY_ACCESS && state != PermissionState.GRANTED) {
            PermissionState.UNKNOWN
        } else {
            state
        }

    private val SYSTEM_GRANTABLE = setOf(
        ReminderPermission.NOTIFICATIONS,
        ReminderPermission.NOTIFICATION_CHANNEL,
        ReminderPermission.EXACT_ALARMS,
        ReminderPermission.FULL_SCREEN_INTENT,
    )
}
