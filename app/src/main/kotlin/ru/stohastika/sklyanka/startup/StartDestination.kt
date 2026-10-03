package ru.stohastika.sklyanka.startup

import ru.stohastika.sklyanka.domain.analytics.ConsentSource

/**
 * Куда уходит заставка.
 *
 * Заставка односостоятельна: разветвление происходит не на ней, а при уходе с неё
 * (`docs/spec/03-screens.md` «Заставка»). Поэтому это перечисление, а не состояние
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

    /**
     * Согласие на аналитику ещё не принято (или устарело после изменения текста):
     * первым экраном идёт согласие (ПМ-25, ПМ-27).
     */
    ConsentPending,

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
 * `consentAsked` — решение по аналитике принято и его версия текущая; `shortcutLaunch` —
 * запуск пришёл ярлыком «Добавить лекарство»: явное действие человека сильнее самопоказа
 * согласия (ПМ-25 п. 5), и такой запуск экран согласия не показывает.
 */
data class StartupConditions(
    val firstLaunch: Boolean,
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
    val consentAsked: Boolean = false,
    val shortcutLaunch: Boolean = false,
)

/**
 * Что показать первым и куда идти после.
 *
 * Согласие — не альтернатива онбордингу, а шаг перед ним, поэтому решение хранит оба
 * ответа сразу: [consentPending] говорит, показать ли экран согласия, а [destination] —
 * куда вести после него (или сразу, если согласие уже решено). Иначе показ согласия
 * «съел» бы онбординг: к моменту решения первый запуск перестал бы быть первым.
 */
data class StartupDecision(
    val consentPending: Boolean,
    val destination: StartDestination,
    val reason: StartReason,
    /**
     * Каким источником назвать показ согласия: первый запуск или повторное
     * информирование после изменения предмета согласия (ПМ-27). `null` — показа нет.
     */
    val consentSource: ConsentSource? = null,
)

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
     * Полное решение запуска: показать ли согласие и куда вести после.
     *
     * Причина `ConsentPending` называет журналу, почему первым показано согласие;
     * [StartupDecision.destination] при этом остаётся прежним — онбординг разрешений
     * после решения согласия никуда не девается.
     */
    fun decisionOf(conditions: StartupConditions): StartupDecision {
        val consentPending = !conditions.consentAsked && !conditions.shortcutLaunch
        return StartupDecision(
            consentPending = consentPending,
            destination = destinationOf(conditions),
            reason = if (consentPending) StartReason.ConsentPending else reasonOf(conditions),
        )
    }
}
