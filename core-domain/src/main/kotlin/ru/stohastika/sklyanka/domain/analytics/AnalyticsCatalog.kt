package ru.stohastika.sklyanka.domain.analytics

/**
 * Каталог событий аналитики — 13 событий базы (ПМ-26, Р-96).
 *
 * Каталог — не удобство, а граница приватности: свойство события может быть только
 * значением закрытого перечисления, и проводное имя каждого значения живёт здесь же.
 * Свободный текст, идентификатор, доза, название лекарства или профиля не пролезают
 * в событие конструктором: конструкторы [AnalyticsEvent] принимают только типы из этого
 * файла, поэтому «случайно подставить строку» нельзя (АН-3, ПМ-26).
 *
 * События воронки подписки (`paywall_shown`, `paywall_plan_selected`, `purchase_result`,
 * H1) каталогом зарезервированы, но в перечисление не входят: платной зоны нет (Р-94),
 * и отправлять нечего. Новое событие или новое свойство — решение продакт-менеджера;
 * компилятор заставит пройти через этот файл.
 */
enum class AnalyticsEventName(val wire: String) {
    /** Показ экрана согласия: охват первого запуска и повторных входов (Р-96). */
    CONSENT_SHOWN("consent_shown"),

    /** Исход согласия. Отправляется только `accepted`: у `not_now` пути отправки нет. */
    CONSENT_CHOICE("consent_choice"),

    /** Включение аналитики позже, из настроек. Выключение события не отправляет. */
    ANALYTICS_ENABLED("analytics_enabled"),

    /** Вход в «Настроить надёжность»: воронка по входам. */
    ONBOARDING_STARTED("onboarding_started"),

    /** Шаг цепочки разрешений: где рвётся настройка надёжности. */
    ONBOARDING_STEP_RESULT("onboarding_step_result"),

    /** Исход онбординга: дошли или пропустили. */
    ONBOARDING_FINISHED("onboarding_finished"),

    /** Использование экранов: удержание и шаги сценариев. */
    SCREEN_VIEW("screen_view"),

    /** Жизненный цикл записи лекарства: добавление, архив, мягкое удаление, отмена. */
    MEDICATION_ACTION("medication_action"),

    /** Базовый цикл отметки: отметка, пропуск, отмена. */
    INTAKE_ACTION("intake_action"),

    /** Использование «Отложить». */
    REMINDER_SNOOZED("reminder_snoozed"),

    /** Использование отчёта врачу. */
    REPORT_EXPORTED("report_exported"),

    /** Перенос данных: пользуются ли, где сбоит. */
    TRANSFER_RESULT("transfer_result"),

    /** Самопроверка доставки: доля устройств с проблемами. */
    DELIVERY_CHECK_RESULT("delivery_check_result"),
}

/** Ключи свойств: ровно десять на весь каталог (ПМ-26). */
enum class AnalyticsProperty(val wire: String) {
    SOURCE("source"),
    CHOICE("choice"),
    ENTRY("entry"),
    STEP("step"),
    RESULT("result"),
    SCREEN("screen"),
    ACTION("action"),
    MODE("mode"),
    OUTCOME("outcome"),
    DIRECTION("direction"),
}

/**
 * Контур согласия.
 *
 * Событие о согласии пишется до согласия и поэтому живёт отдельным контуром: очередь
 * знает, что такие записи нельзя отправлять без согласия, а продукт — что они уже
 * накоплены (TDD §7.3).
 */
enum class AnalyticsScope(val wire: String) {
    PRODUCT("product"),
    CONSENT("consent"),
}

/** Источник показа экрана согласия. Третье значение — повторный вход из настроек (Р-96). */
enum class ConsentShownSource(val wire: String) {
    FIRST_LAUNCH("first_launch"),
    POLICY_UPDATE("policy_update"),
    SETTINGS("settings"),
}

/**
 * Ответ человека на вопрос о согласии.
 *
 * `NOT_NOW` остаётся в каталоге как часть продуктового контракта, но пути отправки
 * у него нет: при отказе очередь очищается целиком (TDD §7.4).
 */
enum class ConsentAnswer(val wire: String) {
    ACCEPTED("accepted"),
    NOT_NOW("not_now"),
}

/** Вход, из которого включили аналитику: событие есть только у включения из настроек. */
enum class AnalyticsEnabledSource(val wire: String) {
    SETTINGS("settings"),
}

/** Вход в онбординг разрешений. */
enum class OnboardingEntry(val wire: String) {
    FIRST_LAUNCH("first_launch"),
    TODAY_BANNER("today_banner"),
    BREAK_BANNER("break_banner"),
    SETTINGS("settings"),
    DELIVERY_CHECK("delivery_check"),
}

/** Шаг цепочки разрешений. */
enum class OnboardingStep(val wire: String) {
    NOTIFICATIONS("notifications"),
    EXACT_ALARMS("exact_alarms"),
    APP_LAUNCH("app_launch"),
    BATTERY("battery"),
    RECENTS("recents"),
}

/** Результат шага цепочки разрешений. */
enum class StepResult(val wire: String) {
    GRANTED("granted"),
    CONFIRMED("confirmed"),
    SKIPPED("skipped"),
    NOT_APPLICABLE("not_applicable"),
}

/** Исход онбординга. */
enum class OnboardingOutcome(val wire: String) {
    COMPLETED("completed"),
    SKIPPED("skipped"),
}

/**
 * Закрытый список экранов.
 *
 * `WATCH_SETUP` и `ABOUT` остаются осознанно: часов нет (Р-3, SKL-106), а «О приложении» —
 * плашка внутри настроек. Каталог — список разрешённого, а не обязательство отправлять всё.
 */
enum class AnalyticsScreen(val wire: String) {
    TODAY("today"),
    MEDICATIONS("medications"),
    ARCHIVE("archive"),
    MEDICATION_CARD("medication_card"),
    MEDICATION_FORM("medication_form"),
    HISTORY("history"),
    REPORT("report"),
    SETTINGS("settings"),
    DATA_TRANSFER("data_transfer"),
    REMINDERS_CHECK("reminders_check"),
    PROFILES("profiles"),
    WATCH_SETUP("watch_setup"),
    ABOUT("about"),
}

/** Действие жизненного цикла записи лекарства. */
enum class MedicationActionKind(val wire: String) {
    ADDED("added"),
    ARCHIVED("archived"),
    DELETED("deleted"),
    UNDO("undo"),
}

/** Режим лекарства: свойство есть только у добавления. */
enum class MedicationMode(val wire: String) {
    SCHEDULED("scheduled"),
    PRN("prn"),
}

/** Действие отметки приёма. */
enum class IntakeActionKind(val wire: String) {
    TAKEN("taken"),
    SKIPPED("skipped"),
    UNDO("undo"),
}

/**
 * Источник действия.
 *
 * `WATCH` остаётся в закрытом списке, хотя шов часов вырезан (Р-3, SKL-106): значение
 * не имеет точки вызова, но и не расширяет каталог.
 */
enum class ActionSource(val wire: String) {
    PHONE("phone"),
    WATCH("watch"),
}

/** Исход операции: отчёт врачу и перенос данных отвечают на один и тот же вопрос. */
enum class OperationResult(val wire: String) {
    SUCCESS("success"),
    CANCELLED("cancelled"),
    ERROR("error"),
}

/** Направление переноса данных. */
enum class TransferDirection(val wire: String) {
    EXPORT("export"),
    IMPORT("import"),
}

/** Исход самопроверки доставки. */
enum class DeliveryCheckOutcome(val wire: String) {
    PASSED("passed"),
    LATE("late"),
    FAILED("failed"),
}
