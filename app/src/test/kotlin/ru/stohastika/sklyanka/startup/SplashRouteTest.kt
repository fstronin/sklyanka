package ru.stohastika.sklyanka.startup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Куда уходит заставка (`acceptance.feature`, «Куда уходит заставка»; `TDD.md` §6).
 *
 * Таблица сценария разобрана построчно. Правило простое ровно до того момента,
 * когда кто-нибудь решит «на повторном запуске разрешения можно не проверять» —
 * и напоминания перестанут приходить у всех, кто отозвал разрешение после
 * онбординга. Поэтому проверяется и причина, а не только экран: причина и есть то,
 * что попадает в журнал запуска и отвечает на жалобу.
 */
class SplashRouteTest {

    @Test
    fun `the first launch goes to the permissions screen`() {
        val conditions = StartupConditions(
            firstLaunch = true,
            notificationsAllowed = true,
            exactAlarmsAllowed = true,
        )
        assertEquals(StartDestination.Onboarding, SplashRoute.destinationOf(conditions))
        assertEquals(StartReason.FirstLaunch, SplashRoute.reasonOf(conditions))
    }

    @Test
    fun `notifications switched off send a returning user back to the permissions screen`() {
        val conditions = StartupConditions(
            firstLaunch = false,
            notificationsAllowed = false,
            exactAlarmsAllowed = true,
        )
        assertEquals(StartDestination.Onboarding, SplashRoute.destinationOf(conditions))
        assertEquals(StartReason.NotificationsDenied, SplashRoute.reasonOf(conditions))
    }

    @Test
    fun `exact alarms switched off do the same`() {
        val conditions = StartupConditions(
            firstLaunch = false,
            notificationsAllowed = true,
            exactAlarmsAllowed = false,
        )
        assertEquals(StartDestination.Onboarding, SplashRoute.destinationOf(conditions))
        assertEquals(StartReason.ExactAlarmsDenied, SplashRoute.reasonOf(conditions))
    }

    @Test
    fun `a returning user with every permission granted goes straight to today`() {
        val conditions = StartupConditions(
            firstLaunch = false,
            notificationsAllowed = true,
            exactAlarmsAllowed = true,
        )
        assertEquals(StartDestination.Today, SplashRoute.destinationOf(conditions))
        assertEquals(StartReason.Ready, SplashRoute.reasonOf(conditions))
    }

    @Test
    fun `today is the only destination that means everything is in place`() {
        // Обратная сторона правила: «Сегодня» не должно открыться ни при одном
        // наборе признаков, в котором чего-то не хватает.
        val everyCombination = listOf(true, false).flatMap { first ->
            listOf(true, false).flatMap { notifications ->
                listOf(true, false).map { alarms -> StartupConditions(first, notifications, alarms) }
            }
        }
        everyCombination.forEach { conditions ->
            val complete = !conditions.firstLaunch &&
                conditions.notificationsAllowed &&
                conditions.exactAlarmsAllowed
            val expected = if (complete) StartDestination.Today else StartDestination.Onboarding
            assertEquals(conditions.toString(), expected, SplashRoute.destinationOf(conditions))
        }
    }

    @Test
    fun `the reason names the first missing thing and nothing else`() {
        // Порядок причин — не украшение: на первом запуске разрешений нет никогда,
        // и причина «уведомления выключены» увела бы разбор жалобы не туда.
        val firstLaunchWithoutAnything = StartupConditions(
            firstLaunch = true,
            notificationsAllowed = false,
            exactAlarmsAllowed = false,
        )
        assertEquals(StartReason.FirstLaunch, SplashRoute.reasonOf(firstLaunchWithoutAnything))

        val bothDenied = StartupConditions(
            firstLaunch = false,
            notificationsAllowed = false,
            exactAlarmsAllowed = false,
        )
        assertEquals(StartReason.NotificationsDenied, SplashRoute.reasonOf(bothDenied))
    }
}
