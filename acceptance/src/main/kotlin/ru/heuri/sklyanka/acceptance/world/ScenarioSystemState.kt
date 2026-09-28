package ru.heuri.sklyanka.acceptance.world

import ru.heuri.sklyanka.domain.reminder.port.PermissionState
import ru.heuri.sklyanka.domain.reminder.port.PermissionStateReader
import ru.heuri.sklyanka.domain.reminder.port.ReminderPermission
import ru.heuri.sklyanka.domain.watch.WatchLinkState

/**
 * Состояние системы, которое экран настроек и диагностика не помнят, а спрашивают
 * (SKL-20, НФТ-52).
 *
 * Разрешения и связь с часами — чужое состояние: человек меняет его в системных настройках
 * мимо приложения. В приёмке оно живёт здесь и меняется строками Предыстории и действий,
 * а читается настоящими портами (`PermissionStateReader`, `WatchLinkStateReader`) — второй
 * копии «что выдано» в шагах нет.
 *
 * Умолчание: всё выдано, кроме автозапуска и энергосбережения EMUI. Это не догадка,
 * а состояние эталонного устройства: фирменные ограничения EMUI программно не читаются
 * (НАП-20), и честный ответ о них — «проверьте вручную».
 */
class ScenarioSystemState : PermissionStateReader {

    private val states = linkedMapOf<ReminderPermission, PermissionState>()

    /** Состояние связи с часами: строка Предыстории, а не факт платформы (ЧАС-18, ADR-0009). */
    var watch: WatchLinkState = WatchLinkState.NotConfigured
        private set

    override fun state(permission: ReminderPermission): PermissionState =
        states[permission] ?: if (permission == ReminderPermission.AUTOSTART) {
            PermissionState.UNKNOWN
        } else {
            PermissionState.GRANTED
        }

    fun set(permission: ReminderPermission, state: PermissionState) {
        states[permission] = state
    }

    fun setWatch(state: WatchLinkState) {
        watch = state
    }

    /** Правда о числе невыданных: строка настроек считает по этому ответу (НФТ-8). */
    fun deny(vararg permissions: ReminderPermission) {
        permissions.forEach { set(it, PermissionState.DENIED) }
    }

    fun grant(vararg permissions: ReminderPermission) {
        permissions.forEach { set(it, PermissionState.GRANTED) }
    }
}
