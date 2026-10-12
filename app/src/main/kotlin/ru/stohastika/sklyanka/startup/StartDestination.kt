package ru.stohastika.sklyanka.startup

/**
 * Куда уходит заставка.
 *
 * Заставка односостоятельна: разветвление происходит не на ней, а при уходе с неё
 * (`docs/norm/04-behavior.md` «Заставка»). Поэтому это перечисление, а не состояние
 * экрана, и живёт оно рядом с решением, а не внутри отрисовки.
 */
enum class StartDestination {
    /** Настройка разрешений: первый запуск или невыданные разрешения (SKL-14). */
    Onboarding,

    /** Обычный запуск: «Сегодня» (SKL-08). */
    Today,
}

/**
 * Почему заставка ушла именно туда.
 *
 * Нужно журналу запуска (`TDD.md` §8): жалоба «приложение каждый раз просит
 * разрешения» разбирается только по причине, а не по самому факту показа
 * онбординга. Медицинских данных в причине нет и быть не может (НФТ-19).
 */
enum class StartReason {
    /** Приложение запускается впервые. */
    FirstLaunch,

    /** Уведомления выключены — напоминание не дойдёт. */
    NotificationsDenied,

    /** Точные будильники не разрешены — напоминание придёт когда-нибудь. */
    ExactAlarmsDenied,

    /** Всё на месте. */
    Ready,
}

/**
 * Признаки, по которым принимается решение.
 *
 * Разрешения читаются из системы при каждом запуске, а не из сохранённого флага
 * (НФТ-52): человек мог отозвать разрешение из настроек, и сохранённое «онбординг
 * пройден» означало бы молча сломанные напоминания.
 *
 * Согласия на аналитику здесь нет: в первой публикации UX согласия скрыт до
 * подключения провайдера (ПМ-31), и первый запуск идёт сразу в онбординг разрешений.
 */
data class StartupConditions(
    val firstLaunch: Boolean,
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
)

/**
 * Что показать первым и куда идти после.
 *
 * [destination] — экран онбординга разрешений или корень; [reason] объясняет журналу,
 * почему заставка ушла именно туда.
 */
data class StartupDecision(val destination: StartDestination, val reason: StartReason)

/**
 * Правило ухода с заставки — единственное место, где оно записано.
 *
 * Порядок проверок задаёт и причину в журнале: первый запуск объясняет онбординг
 * сам по себе, а на повторном запуске важно, какое именно разрешение отозвано.
 */
object SplashRoute {

    /** Куда уходить. */
    fun destinationOf(conditions: StartupConditions): StartDestination =
        if (reasonOf(conditions) == StartReason.Ready) StartDestination.Today else StartDestination.Onboarding

    /** Почему именно туда. */
    fun reasonOf(conditions: StartupConditions): StartReason = when {
        conditions.firstLaunch -> StartReason.FirstLaunch
        !conditions.notificationsAllowed -> StartReason.NotificationsDenied
        !conditions.exactAlarmsAllowed -> StartReason.ExactAlarmsDenied
        else -> StartReason.Ready
    }

    /**
     * Полное решение запуска: куда вести и почему.
     *
     * Согласия в решении нет — ПМ-31: UX согласия скрыт до подключения провайдера,
     * первый запуск идёт «заставка → «Настроить надёжность» → приложение».
     */
    fun decisionOf(conditions: StartupConditions): StartupDecision = StartupDecision(
        destination = destinationOf(conditions),
        reason = reasonOf(conditions),
    )
}
